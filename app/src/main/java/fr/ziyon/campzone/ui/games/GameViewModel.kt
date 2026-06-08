package fr.ziyon.campzone.ui.games

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.media.CloudinaryAssetDeleter
import fr.ziyon.campzone.data.media.ImageUploader
import fr.ziyon.campzone.data.games.GameService
import fr.ziyon.campzone.data.model.Activity
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.Game
import fr.ziyon.campzone.data.model.GameInstructionAttachment
import fr.ziyon.campzone.data.model.GameInstructionAttachmentKind
import fr.ziyon.campzone.data.model.GameInstructions
import fr.ziyon.campzone.data.model.PointRule
import fr.ziyon.campzone.data.model.PointRuleTarget
import fr.ziyon.campzone.data.model.PointRuleVisibility
import fr.ziyon.campzone.data.model.Team
import fr.ziyon.campzone.data.model.TeamMember
import fr.ziyon.campzone.data.model.TeamPenalty
import fr.ziyon.campzone.data.model.VenuePoint
import fr.ziyon.campzone.data.model.WinnerRevealPolicy
import fr.ziyon.campzone.data.model.leadershipOnlyVenuePointIds
import fr.ziyon.campzone.data.teams.TeamNotificationDispatcher
import fr.ziyon.campzone.data.teams.TeamNotificationEvent
import fr.ziyon.campzone.data.teams.TeamNotificationRequest
import fr.ziyon.campzone.data.teams.TeamScoreRequest
import fr.ziyon.campzone.data.teams.TeamService
import fr.ziyon.campzone.data.venuemap.VenueMapService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

sealed interface GamesUiState {
    data object Loading : GamesUiState
    data class Loaded(val games: List<Game>, val activities: List<Activity>) : GamesUiState
    data object Empty : GamesUiState
    data class Error(val message: String) : GamesUiState
}

data class GameForm(
    val name: String = "",
    val rules: String = "",
    val pointRules: List<PointRule> = emptyList(),
    val venuePointIds: List<String> = emptyList(),
    val locationVisibleToAll: Boolean = false,
)

data class GameInstructionsForm(
    val title: String = "",
    val description: String = "",
    val images: List<GameInstructionAttachment> = emptyList(),
) {
    fun asInstructions(): GameInstructions =
        GameInstructions(
            title = title.trim(),
            description = description.trim(),
            images = images,
        )

    companion object {
        fun of(instructions: GameInstructions?) = GameInstructionsForm(
            title = instructions?.title.orEmpty(),
            description = instructions?.description.orEmpty(),
            images = instructions?.images.orEmpty(),
        )
    }
}

enum class GameValidationError { NameRequired, PointRulesEmpty }

data class ActivityRequest(
    val gameId: String,
    val pointRuleId: String?,
    val name: String,
    val points: Int,
    val reason: String,
    val targetTeamId: String?,
    val targetTeamName: String?,
    val targetUserId: String?,
    val targetUserName: String?,
    val visibility: PointRuleVisibility,
)

@HiltViewModel
class GameViewModel @Inject constructor(
    private val gameService: GameService,
    private val teamService: TeamService,
    private val campingService: CampingService,
    private val teamNotificationDispatcher: TeamNotificationDispatcher,
    private val imageUploader: ImageUploader,
    private val assetDeleter: CloudinaryAssetDeleter,
    private val venueMapService: VenueMapService,
) : ViewModel() {

    private val _uiState = MutableStateFlow<GamesUiState>(GamesUiState.Loading)
    val uiState: StateFlow<GamesUiState> = _uiState.asStateFlow()

    private val gamesByCampingId = mutableMapOf<String, List<Game>>()
    private val activitiesByCampingId = mutableMapOf<String, List<Activity>>()
    private val venuePointsByCampingId = mutableStateMapOf<String, List<VenuePoint>>()
    private val instructionsByGameId = mutableMapOf<String, GameInstructions?>()
    private val loadedIds = mutableSetOf<String>()
    private val observeJobs = mutableMapOf<String, Job>()

    var form by mutableStateOf(GameForm())
    var editingGameId by mutableStateOf<String?>(null)
    var validationErrors by mutableStateOf<List<GameValidationError>>(emptyList())
    var instructionsForm by mutableStateOf(GameInstructionsForm())

    var isSaving by mutableStateOf(false)
    var isAwarding by mutableStateOf(false)
    var isResetting by mutableStateOf(false)
    var isSavingInstructions by mutableStateOf(false)
    var isUploadingInstructionImage by mutableStateOf(false)
    var isUpdatingReveal by mutableStateOf(false)
        private set
    var operationMessage by mutableStateOf<String?>(null)
    var operationError by mutableStateOf<String?>(null)
    var instructionsError by mutableStateOf<String?>(null)

    fun loadIfNeeded(campingId: String) {
        if (observeJobs[campingId]?.isActive == true) {
            publishState(campingId)
            return
        }
        load(campingId)
    }

    /**
     * Starts (or restarts) a live listener on the camping's games + activities so
     * awarded points and new games reflect on every device in real time. The
     * snapshot stream is the source of truth; the optimistic local upserts in the
     * mutation methods just remove perceived latency before the listener echoes.
     */
    fun load(campingId: String) {
        observeJobs[campingId]?.cancel()
        _uiState.value = GamesUiState.Loading
        operationError = null
        observeJobs[campingId] = viewModelScope.launch {
            try {
                combine(
                    gameService.observeGames(campingId),
                    gameService.observeActivities(campingId),
                ) { games, activities -> games to activities }
                    .collect { (games, activities) ->
                        gamesByCampingId[campingId] = games
                        activitiesByCampingId[campingId] = activities
                        loadedIds.add(campingId)
                        publishState(campingId)
                    }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                _uiState.value = GamesUiState.Error(e.message ?: "Failed to load games.")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        observeJobs.values.forEach { it.cancel() }
        observeJobs.clear()
    }

    fun gamesFor(campingId: String): List<Game> =
        gamesByCampingId[campingId]?.sortedByDescending { it.createdAt } ?: emptyList()

    fun game(id: String, campingId: String): Game? =
        gamesFor(campingId).firstOrNull { it.id == id }

    fun activitiesFor(campingId: String): List<Activity> =
        activitiesByCampingId[campingId]?.sortedByDescending { it.createdAt } ?: emptyList()

    fun venuePointsFor(campingId: String): List<VenuePoint> =
        venuePointsByCampingId[campingId].orEmpty()

    fun loadVenuePoints(campingId: String) {
        viewModelScope.launch {
            runCatching { venueMapService.loadMap(campingId).points }
                .onSuccess { venuePointsByCampingId[campingId] = it }
        }
    }

    fun instructions(gameId: String): GameInstructions? = instructionsByGameId[gameId]

    fun hiddenVenuePointIds(campingId: String, canSeeHiddenGameLocations: Boolean): Set<String> {
        if (canSeeHiddenGameLocations) return emptySet()
        return leadershipOnlyVenuePointIds(gamesFor(campingId))
    }

    fun visibleActivities(camping: Camping, canSeeHidden: Boolean): List<Activity> {
        val policy = camping.winnerRevealPolicy
        val scoresHidden = policy != null && !policy.isRevealed &&
            policy.hideDate?.let { it <= Date() } ?: false
        val revealFired = policy?.let {
            it.isRevealed || (it.revealDate?.let { rd -> rd <= Date() } ?: false)
        } ?: false

        return activitiesFor(camping.id).filter { activity ->
            if (canSeeHidden) return@filter true
            if (scoresHidden) return@filter false
            if (activity.visibility == PointRuleVisibility.AfterReveal) return@filter revealFired
            true
        }
    }

    fun prepareNewGame() {
        editingGameId = UUID.randomUUID().toString()
        form = GameForm()
        instructionsForm = GameInstructionsForm()
        validationErrors = emptyList()
        operationError = null
        operationMessage = null
    }

    fun prepareEditingGame(game: Game) {
        editingGameId = game.id
        form = GameForm(
            name = game.name,
            rules = game.rules,
            pointRules = game.pointRules,
            venuePointIds = game.venuePointIds,
            locationVisibleToAll = game.locationVisibleToAll,
        )
        validationErrors = emptyList()
        operationError = null
        operationMessage = null
    }

    fun updateForm(update: (GameForm) -> GameForm) { form = update(form) }

    suspend fun saveGame(campingId: String, createdBy: String): Game? {
        validationErrors = validate()
        if (validationErrors.isNotEmpty()) return null
        isSaving = true
        operationError = null
        return runCatching {
            val existing = editingGameId?.let { game(it, campingId) }
            val draft = Game(
                id = editingGameId ?: UUID.randomUUID().toString(),
                campingId = campingId,
                name = form.name.trim(),
                rules = form.rules.trim(),
                pointRules = form.pointRules,
                venuePointIds = form.venuePointIds.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
                locationVisibleToAll = form.locationVisibleToAll,
                createdBy = existing?.createdBy ?: createdBy,
                createdAt = existing?.createdAt,
                updatedAt = Date(),
            )
            val saved = gameService.saveGame(draft)
            upsertGame(saved)
            publishState(campingId)
            operationMessage = "Game saved."
            saved
        }.onFailure { e ->
            operationError = e.message ?: "Failed to save game."
        }.also {
            isSaving = false
        }.getOrNull()
    }

    suspend fun deleteGame(game: Game): Boolean {
        operationError = null
        return runCatching {
            gameService.deleteGame(game.id, game.campingId)
            gamesByCampingId[game.campingId] =
                (gamesByCampingId[game.campingId] ?: emptyList()).filter { it.id != game.id }
            publishState(game.campingId)
            operationMessage = "Game removed."
            true
        }.onFailure { e ->
            operationError = e.message ?: "Failed to delete game."
        }.getOrDefault(false)
    }

    suspend fun loadInstructions(gameId: String, campingId: String) {
        runCatching {
            gameService.loadInstructions(gameId, campingId)
        }.onSuccess { instructions ->
            instructionsByGameId[gameId] = instructions
        }.onFailure { e ->
            instructionsError = e.message ?: "Failed to load instructions."
        }
    }

    suspend fun prepareInstructions(gameId: String, campingId: String) {
        loadInstructions(gameId, campingId)
        instructionsForm = GameInstructionsForm.of(instructions(gameId))
    }

    fun updateInstructionsForm(update: (GameInstructionsForm) -> GameInstructionsForm) {
        instructionsForm = update(instructionsForm)
    }

    suspend fun saveInstructions(gameId: String, campingId: String): Boolean {
        isSavingInstructions = true
        instructionsError = null
        return runCatching {
            val instructions = instructionsForm.asInstructions()
            if (instructions.isEmpty) {
                gameService.deleteInstructions(gameId, campingId)
                instructionsByGameId[gameId] = null
            } else {
                val saved = gameService.saveInstructions(instructions, gameId, campingId)
                instructionsByGameId[gameId] = saved
            }
            true
        }.onFailure { e ->
            instructionsError = e.message ?: "Failed to save instructions."
        }.also {
            isSavingInstructions = false
        }.getOrDefault(false)
    }

    suspend fun uploadInstructionImage(
        bytes: ByteArray,
        mimeType: String,
        fileExtension: String,
        gameId: String,
        campingId: String,
    ) {
        isUploadingInstructionImage = true
        instructionsError = null
        runCatching {
            val result = imageUploader.uploadImage(
                assetIdPrefix = "instruction",
                folder = "campzone/campings/$campingId/games/$gameId/instructions",
                tags = listOf("campzone", "game_instructions", "camping_$campingId", "game_$gameId"),
                bytes = bytes,
                mimeType = mimeType,
                fileExtension = fileExtension,
            )
            instructionsForm = instructionsForm.copy(
                images = instructionsForm.images + GameInstructionAttachment(
                    url = result.secureUrl,
                    publicId = result.publicId,
                    kind = GameInstructionAttachmentKind.Image,
                ),
            )
            saveInstructions(gameId, campingId)
        }.onFailure { e ->
            instructionsError = e.message ?: "Failed to upload instruction image."
        }
        isUploadingInstructionImage = false
    }

    suspend fun removeInstructionAttachment(
        attachment: GameInstructionAttachment,
        gameId: String,
        campingId: String,
    ) {
        instructionsForm = instructionsForm.copy(
            images = instructionsForm.images.filterNot { it.id == attachment.id },
        )
        saveInstructions(gameId, campingId)
        runCatching {
            assetDeleter.deleteAsset(
                publicId = attachment.publicId,
                resourceType = if (attachment.kind == GameInstructionAttachmentKind.Pdf) "raw" else "image",
            )
        }
    }

    suspend fun awardPoints(
        request: ActivityRequest,
        camping: Camping,
        teams: List<Team>,
        actor: AuthenticatedUser,
        suppliedGame: Game? = null,
    ): Activity? {
        if (request.points == 0) {
            operationError = "Enter a non-zero point delta."
            return null
        }
        val targetKind = request.targetKind() ?: run {
            operationError = "Select one target team or participant."
            return null
        }
        val sourceGame = suppliedGame
            ?.takeIf { it.id == request.gameId && it.campingId == camping.id }
            ?: game(request.gameId, camping.id)
        request.pointRuleScopeError(targetKind, sourceGame)?.let { error ->
            operationError = error
            return null
        }

        val previousScore: Int
        val targetTeam: Team?
        val targetMemberId: String?

        when (targetKind) {
            ActivityTargetKind.Team -> {
                val team = teams.firstOrNull { it.id == request.targetTeamId }
                    ?: run { operationError = "Team not found."; return null }
                previousScore = team.totalScore
                targetTeam = team
                targetMemberId = null
            }
            ActivityTargetKind.User -> {
                val pair = teams.flatMap { t -> t.members.map { t to it } }
                    .firstOrNull { (_, m) -> m.userId == request.targetUserId }
                    ?: run { operationError = "Participant not found."; return null }
                previousScore = pair.second.personalScore
                targetTeam = pair.first
                targetMemberId = pair.second.id
            }
        }

        isAwarding = true
        operationError = null
        return runCatching {
            val activity = Activity(
                id = UUID.randomUUID().toString(),
                campingId = camping.id,
                gameId = request.gameId,
                name = request.name,
                points = request.points,
                previousScore = previousScore,
                newScore = previousScore + request.points,
                createdBy = actor.uid,
                createdByName = actor.displayName ?: actor.email,
                createdAt = Date(),
                reason = request.reason,
                pointRuleId = request.pointRuleId,
                targetTeamId = request.targetTeamId,
                targetTeamName = request.targetTeamName,
                targetUserId = request.targetUserId,
                targetUserName = request.targetUserName,
                visibility = request.visibility,
            )
            val recorded = gameService.recordActivity(activity)

            var updatedTeam: Team? = null
            var scoredMember: TeamMember? = null
            if (targetMemberId == null) {
                val scoredReason =
                    request.reason.takeUnless { it.isBlank() } ?: request.name
                if (request.points < 0) {
                    updatedTeam = teamService.applyPenalty(
                        TeamPenalty(
                            id = UUID.randomUUID().toString(),
                            reason = scoredReason,
                            points = -request.points,
                            createdAt = Date(),
                        ),
                        teamId = targetTeam!!.id,
                        campingId = camping.id,
                    )
                } else {
                    updatedTeam = teamService.updateTeamScore(
                        TeamScoreRequest(
                            teamId = targetTeam!!.id,
                            campingId = camping.id,
                            points = request.points,
                            reason = scoredReason,
                        ),
                    )
                }
            } else {
                updatedTeam = teamService.updateMemberScore(
                    memberId = targetMemberId,
                    delta = request.points,
                    teamId = targetTeam!!.id,
                    campingId = camping.id,
                )
                scoredMember = updatedTeam.members.firstOrNull { it.id == targetMemberId }
            }

            updatedTeam?.let { team ->
                dispatchGameTeamUpdate(team, recorded, scoredMember)
            }
            insertActivity(recorded)
            publishState(camping.id)
            operationMessage = if (request.points >= 0) "Points awarded." else "Points deducted."
            recorded
        }.onFailure { e ->
            operationError = e.message ?: "Award failed."
        }.also {
            isAwarding = false
        }.getOrNull()
    }

    suspend fun resetGameData(game: Game, teams: List<Team>): Boolean {
        isResetting = true
        operationError = null
        return runCatching {
            val gameActivities = gameService.loadActivitiesForGame(game.id, game.campingId)
            if (gameActivities.isNotEmpty()) {
                val teamDeltas = mutableMapOf<String, Int>()
                val memberDeltas = mutableMapOf<Pair<String, String>, Int>()

                for (activity in gameActivities) {
                    val reversal = -activity.points
                    if (activity.targetTeamId != null) {
                        teamDeltas[activity.targetTeamId] =
                            (teamDeltas[activity.targetTeamId] ?: 0) + reversal
                    } else if (activity.targetUserId != null) {
                        val pair = teams.flatMap { t -> t.members.map { t to it } }
                            .firstOrNull { (_, m) -> m.userId == activity.targetUserId }
                        if (pair != null) {
                            val key = pair.second.id to pair.first.id
                            memberDeltas[key] = (memberDeltas[key] ?: 0) + reversal
                        }
                    }
                }

                for ((teamId, delta) in teamDeltas) {
                    if (delta != 0) {
                        teamService.updateTeamScore(
                            TeamScoreRequest(
                                teamId = teamId,
                                campingId = game.campingId,
                                points = delta,
                                reason = "Game reset",
                            ),
                        )
                    }
                }
                for ((key, delta) in memberDeltas) {
                    val (memberId, teamId) = key
                    if (delta != 0) {
                        teamService.updateMemberScore(
                            memberId = memberId,
                            delta = delta,
                            teamId = teamId,
                            campingId = game.campingId,
                        )
                    }
                }

                gameService.deleteActivities(gameActivities.map { it.id }, game.campingId)
                activitiesByCampingId[game.campingId] =
                    (activitiesByCampingId[game.campingId] ?: emptyList())
                        .filter { it.gameId != game.id }
                publishState(game.campingId)
            }
            operationMessage = "Game reset."
            true
        }.onFailure { e ->
            operationError = e.message ?: "Reset failed."
        }.also {
            isResetting = false
        }.getOrDefault(false)
    }

    // region Winner reveal

    /**
     * Writes `winnerRevealPolicy` via the dedicated update path (forbidden in normal camp edit).
     * Returns the saved policy on success, null on failure.
     */
    suspend fun updateRevealPolicy(campingId: String, policy: WinnerRevealPolicy): WinnerRevealPolicy? {
        isUpdatingReveal = true
        operationError = null
        return runCatching {
            val camping = campingService.updateWinnerReveal(campingId, policy)
            operationMessage = "Reveal settings updated."
            camping.winnerRevealPolicy
        }.getOrElse { e ->
            operationError = e.message ?: "Failed to update reveal settings."
            null
        }.also { isUpdatingReveal = false }
    }

    /** Flip `isRevealed = true` and stamp the actor's uid/name/timestamp. */
    suspend fun reveal(
        campingId: String,
        currentPolicy: WinnerRevealPolicy?,
        actor: AuthenticatedUser,
    ): WinnerRevealPolicy? {
        val policy = (currentPolicy ?: WinnerRevealPolicy(isRevealed = false)).copy(
            isRevealed = true,
            revealedBy = actor.uid,
            revealedByName = actor.displayName,
            revealedAt = Date(),
        )
        return updateRevealPolicy(campingId, policy)
    }

    /**
     * Undo a reveal - clears isRevealed, sets hideDate=now so scores
     * immediately re-hide, and clears any auto-revealDate that has already fired.
     */
    suspend fun unreveal(campingId: String, currentPolicy: WinnerRevealPolicy?): WinnerRevealPolicy? {
        val now = Date()
        val base = currentPolicy ?: WinnerRevealPolicy(isRevealed = false)
        val clearedRevealDate = if (base.revealDate != null && now >= base.revealDate) null else base.revealDate
        val policy = base.copy(
            isRevealed = false,
            revealedBy = null,
            revealedByName = null,
            revealedAt = null,
            revealDate = clearedRevealDate,
            hideDate = now,
        )
        return updateRevealPolicy(campingId, policy)
    }

    // endregion

    private fun validate(): List<GameValidationError> = buildList {
        if (form.name.isBlank()) add(GameValidationError.NameRequired)
        if (form.pointRules.isEmpty()) add(GameValidationError.PointRulesEmpty)
    }

    private fun upsertGame(game: Game) {
        val list = (gamesByCampingId[game.campingId] ?: emptyList()).toMutableList()
        val idx = list.indexOfFirst { it.id == game.id }
        if (idx >= 0) list[idx] = game else list.add(game)
        gamesByCampingId[game.campingId] = list
    }

    private fun insertActivity(activity: Activity) {
        val list = (activitiesByCampingId[activity.campingId] ?: emptyList()).toMutableList()
        list.add(0, activity)
        activitiesByCampingId[activity.campingId] = list
    }

    private fun publishState(campingId: String) {
        val games = gamesFor(campingId)
        val activities = activitiesFor(campingId)
        _uiState.value = if (games.isEmpty() && activities.isEmpty()) {
            GamesUiState.Empty
        } else {
            GamesUiState.Loaded(games, activities)
        }
    }

    private suspend fun dispatchGameTeamUpdate(
        team: Team,
        activity: Activity,
        member: TeamMember?,
    ) {
        val reason = activity.reason.trim().takeUnless { it.isBlank() }
        val event: TeamNotificationEvent
        val body: String
        if (member != null) {
            event = TeamNotificationEvent.MemberScoreChanged
            body = "${member.displayName} score changed by ${activity.points.signedForNotification()} in ${activity.name}."
        } else if (activity.points < 0) {
            event = TeamNotificationEvent.PenaltyApplied
            body = "${team.name} received a ${-activity.points}-point penalty in ${activity.name}."
        } else {
            event = TeamNotificationEvent.ScoreChanged
            body = "${team.name} score changed by ${activity.points.signedForNotification()} in ${activity.name}."
        }

        runCatching {
            teamNotificationDispatcher.dispatchTeamUpdate(
                TeamNotificationRequest(
                    campingId = team.campingId,
                    teamId = team.id,
                    teamName = team.name,
                    event = event,
                    body = body,
                    memberId = member?.userId,
                    memberName = member?.displayName,
                    pointsDelta = activity.points,
                    reason = reason,
                ),
            )
        }
    }
}

private enum class ActivityTargetKind { Team, User }

private fun ActivityRequest.targetKind(): ActivityTargetKind? = when {
    targetTeamId != null && targetUserId == null -> ActivityTargetKind.Team
    targetTeamId == null && targetUserId != null -> ActivityTargetKind.User
    else -> null
}

private fun ActivityRequest.pointRuleScopeError(
    targetKind: ActivityTargetKind,
    sourceGame: Game?,
): String? {
    val ruleId = pointRuleId ?: return null
    val rule = sourceGame?.pointRules?.firstOrNull { it.id == ruleId }
        ?: return "Selected point rule could not be found."
    return when (rule.appliesTo) {
        PointRuleTarget.Any -> null
        PointRuleTarget.Team -> if (targetKind == ActivityTargetKind.Team) {
            null
        } else {
            "This point rule can only be awarded to teams."
        }
        PointRuleTarget.User -> if (targetKind == ActivityTargetKind.User) {
            null
        } else {
            "This point rule can only be awarded to participants."
        }
    }
}

private fun Int.signedForNotification(): String =
    if (this > 0) "+$this" else "$this"
