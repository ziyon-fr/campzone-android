package fr.ziyon.campzone.ui.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.games.GameService
import fr.ziyon.campzone.data.model.Activity
import fr.ziyon.campzone.data.model.Team
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.PointRuleVisibility
import fr.ziyon.campzone.data.model.TeamMember
import fr.ziyon.campzone.data.model.TeamMemberRole
import fr.ziyon.campzone.data.model.TeamPenalty
import fr.ziyon.campzone.data.model.toTeamMember
import fr.ziyon.campzone.data.teams.TeamBalanceResult
import fr.ziyon.campzone.data.teams.TeamBalancer
import fr.ziyon.campzone.data.media.ImageUploader
import fr.ziyon.campzone.data.teams.TeamDraft
import fr.ziyon.campzone.data.teams.TeamNotificationDispatcher
import fr.ziyon.campzone.data.teams.TeamNotificationEvent
import fr.ziyon.campzone.data.teams.TeamNotificationRequest
import fr.ziyon.campzone.data.teams.TeamScoreRequest
import fr.ziyon.campzone.data.teams.TeamService
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ── UI state ──────────────────────────────────────────────────────────────────

sealed interface TeamsUiState {
    data object Loading : TeamsUiState
    data class Loaded(val teams: List<Team>) : TeamsUiState
    data object Empty : TeamsUiState
    data class Error(val message: String) : TeamsUiState
}

// ── Team editor form ──────────────────────────────────────────────────────────

data class TeamForm(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val slogan: String = "",
    val symbolName: String = Team.DEFAULT_SYMBOL,
    val colorHex: String = Team.DEFAULT_COLOR,
    val photoUrl: String? = null,
    val photoPublicId: String? = null,
) {
    val isValid: Boolean get() = name.isNotBlank()
    val validationErrors: List<String>
        get() = buildList { if (name.isBlank()) add("Team name is required.") }
}

private data class AutoBalancePreviewSignature(
    val teamIds: List<String>,
    val attendeeKeys: List<String>,
) {
    companion object {
        fun from(attendees: List<CampingAttendee>, teamIds: List<String>) = AutoBalancePreviewSignature(
            teamIds = teamIds.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            attendeeKeys = attendees.map { attendee ->
                listOf(
                    attendee.id,
                    attendee.userId,
                    attendee.registrationStatus.wireValue,
                    attendee.church,
                    attendee.preferredLanguage,
                    attendee.ageGroup.wireValue,
                    attendee.gender?.wireValue ?: "unknown",
                    attendee.languages.joinToString(","),
                ).joinToString("|")
            },
        )
    }
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class TeamViewModel @Inject constructor(
    private val teamService: TeamService,
    private val gameService: GameService,
    private val imageUploader: ImageUploader,
    private val teamNotificationDispatcher: TeamNotificationDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TeamsUiState>(TeamsUiState.Loading)
    val uiState: StateFlow<TeamsUiState> = _uiState.asStateFlow()

    private val _form = MutableStateFlow(TeamForm())
    val form: StateFlow<TeamForm> = _form.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isUploadingPhoto = MutableStateFlow(false)
    val isUploadingPhoto: StateFlow<Boolean> = _isUploadingPhoto.asStateFlow()

    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    private val _autoBalancePreview = MutableStateFlow<TeamBalanceResult?>(null)
    val autoBalancePreview: StateFlow<TeamBalanceResult?> = _autoBalancePreview.asStateFlow()

    private val _isAutoBalancePreviewRunning = MutableStateFlow(false)
    val isAutoBalancePreviewRunning: StateFlow<Boolean> = _isAutoBalancePreviewRunning.asStateFlow()

    private val _editingTeamId = MutableStateFlow<String?>(null)
    val editingTeamId: StateFlow<String?> = _editingTeamId.asStateFlow()

    private val _allTeams = MutableStateFlow<Map<String, List<Team>>>(emptyMap())
    private var observeJob: Job? = null
    private var autoBalancePreviewJob: Job? = null
    private var autoBalancePreviewSignature: AutoBalancePreviewSignature? = null
    private var autoBalancePreviewRunId = 0
    private var observingCampingId: String? = null
    private var loadedCampingIds = mutableSetOf<String>()

    // ── Load / observe ────────────────────────────────────────────────────────

    fun startObserving(campingId: String) {
        if (observeJob?.isActive == true && observingCampingId == campingId) {
            if (loadedCampingIds.contains(campingId)) publishState(campingId)
            return
        }
        observeJob?.cancel()
        observingCampingId = campingId
        if (loadedCampingIds.contains(campingId)) {
            publishState(campingId)
        } else {
            _uiState.value = TeamsUiState.Loading
        }
        observeJob = viewModelScope.launch {
            try {
                teamService.observeTeams(campingId).collect { teams ->
                    _allTeams.value = _allTeams.value + (campingId to teams)
                    loadedCampingIds.add(campingId)
                    publishState(campingId)
                }
            } catch (_: CancellationException) {
                // Listener teardown is a normal lifecycle event, not a load failure.
            } catch (e: Exception) {
                _uiState.value = TeamsUiState.Error(e.message ?: "Failed to load teams.")
            }
        }
    }

    fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
        observingCampingId = null
    }

    fun loadIfNeeded(campingId: String) {
        if (loadedCampingIds.contains(campingId)) {
            publishState(campingId)
            return
        }
        startObserving(campingId)
    }

    fun refresh(campingId: String) {
        viewModelScope.launch {
            _operationError.value = null
            try {
                val teams = teamService.loadTeams(campingId)
                _allTeams.value = _allTeams.value + (campingId to teams)
                publishState(campingId)
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Failed to refresh."
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopObserving()
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    fun teams(campingId: String): List<Team> = _allTeams.value[campingId] ?: emptyList()

    fun team(id: String, campingId: String): Team? =
        _allTeams.value[campingId]?.firstOrNull { it.id == id }

    fun personalTeam(campingId: String, userId: String?): Team? {
        if (userId == null) return null
        return _allTeams.value[campingId]?.firstOrNull { t -> t.members.any { it.userId == userId } }
    }

    fun existingTeam(forUserId: String, campingId: String, excludingTeamId: String): Team? =
        _allTeams.value[campingId]?.firstOrNull { t ->
            t.id != excludingTeamId && t.members.any { it.userId == forUserId }
        }

    // ── Form ──────────────────────────────────────────────────────────────────

    fun prepareNew(campingId: String) {
        _form.value = TeamForm()
        _editingTeamId.value = null
        _operationError.value = null
    }

    fun prepareEdit(team: Team) {
        _form.value = TeamForm(
            id = team.id,
            name = team.name,
            slogan = team.slogan,
            symbolName = team.symbolName,
            colorHex = team.colorHex,
            photoUrl = team.photoUrl,
            photoPublicId = team.photoPublicId,
        )
        _editingTeamId.value = team.id
        _operationError.value = null
    }

    fun updateForm(update: (TeamForm) -> TeamForm) {
        _form.value = update(_form.value)
    }

    fun clearOperationError() { _operationError.value = null }
    fun clearOperationMessage() { _operationMessage.value = null }
    fun clearAutoBalancePreview() {
        autoBalancePreviewJob?.cancel()
        autoBalancePreviewJob = null
        autoBalancePreviewSignature = null
        _isAutoBalancePreviewRunning.value = false
        _autoBalancePreview.value = null
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    fun saveTeam(campingId: String, onSuccess: () -> Unit) {
        val f = _form.value
        if (!f.isValid) return
        viewModelScope.launch {
            _isSaving.value = true
            _operationError.value = null
            try {
                val isNewTeam = _editingTeamId.value == null || team(f.id, campingId) == null
                val draft = TeamDraft(
                    id = f.id,
                    campingId = campingId,
                    name = f.name.trim(),
                    slogan = f.slogan.trim(),
                    symbolName = f.symbolName,
                    colorHex = f.colorHex,
                    photoUrl = f.photoUrl,
                    photoPublicId = f.photoPublicId,
                )
                val saved = teamService.saveTeam(draft)
                updateLocal(campingId, saved)
                publishState(campingId)
                _operationMessage.value = "Team saved."
                dispatchTeamUpdate(
                    TeamNotificationRequest(
                        campingId = campingId,
                        teamId = saved.id,
                        teamName = saved.name,
                        event = if (isNewTeam) TeamNotificationEvent.Created else TeamNotificationEvent.Updated,
                        body = if (isNewTeam) "${saved.name} was created." else "${saved.name} details were updated.",
                    ),
                )
                _form.value = TeamForm()
                _editingTeamId.value = null
                onSuccess()
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Could not save team."
            } finally {
                _isSaving.value = false
            }
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    fun deleteTeam(id: String, campingId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isSaving.value = true
            _operationError.value = null
            try {
                teamService.deleteTeam(id, campingId)
                val updated = (_allTeams.value[campingId] ?: emptyList()).filter { it.id != id }
                _allTeams.value = _allTeams.value + (campingId to updated)
                publishState(campingId)
                onSuccess()
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Could not delete team."
            } finally {
                _isSaving.value = false
            }
        }
    }

    // ── Member management ──────────────────────────────────────────────────────

    fun assignMember(
        member: TeamMember,
        toTeamId: String,
        campingId: String,
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            _operationError.value = null
            try {
                val updated = teamService.assignMember(member, toTeamId, campingId)
                _allTeams.value = _allTeams.value + (campingId to updated)
                publishState(campingId)
                _operationMessage.value = "${member.displayName} added to team."
                updated.firstOrNull { it.id == toTeamId }?.let { team ->
                    dispatchTeamUpdate(
                        TeamNotificationRequest(
                            campingId = campingId,
                            teamId = team.id,
                            teamName = team.name,
                            event = TeamNotificationEvent.MemberAssigned,
                            body = "${member.displayName} joined ${team.name}.",
                            memberId = member.userId,
                            memberName = member.displayName,
                        ),
                    )
                }
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Could not assign member."
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun removeMember(memberId: String, fromTeamId: String, campingId: String) {
        viewModelScope.launch {
            _isSaving.value = true
            _operationError.value = null
            try {
                val teamBefore = team(fromTeamId, campingId)
                val removedMember = teamBefore?.members?.firstOrNull { it.id == memberId }
                val updated = teamService.removeMember(memberId, fromTeamId, campingId)
                _allTeams.value = _allTeams.value + (campingId to updated)
                publishState(campingId)
                if (removedMember != null) {
                    val originalTeam = requireNotNull(teamBefore)
                    val updatedTeam = updated.firstOrNull { it.id == fromTeamId } ?: originalTeam
                    dispatchTeamUpdate(
                        TeamNotificationRequest(
                            campingId = campingId,
                            teamId = updatedTeam.id,
                            teamName = updatedTeam.name,
                            event = TeamNotificationEvent.MemberRemoved,
                            body = "${removedMember.displayName} was removed from ${updatedTeam.name}.",
                            memberId = removedMember.userId,
                            memberName = removedMember.displayName,
                        ),
                    )
                }
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Could not remove member."
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun updateMemberRole(memberId: String, role: TeamMemberRole, teamId: String, campingId: String) {
        viewModelScope.launch {
            _isSaving.value = true
            _operationError.value = null
            try {
                val updated = teamService.updateMemberRole(memberId, role, teamId, campingId)
                _allTeams.value = _allTeams.value + (campingId to updated)
                publishState(campingId)
                val team = updated.firstOrNull { it.id == teamId }
                val member = team?.members?.firstOrNull { it.id == memberId }
                if (team != null && member != null) {
                    dispatchTeamUpdate(
                        TeamNotificationRequest(
                            campingId = campingId,
                            teamId = team.id,
                            teamName = team.name,
                            event = TeamNotificationEvent.MemberRoleUpdated,
                            body = "${member.displayName} is now ${member.role.displayNameForNotification()}.",
                            memberId = member.userId,
                            memberName = member.displayName,
                        ),
                    )
                }
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Could not update role."
            } finally {
                _isSaving.value = false
            }
        }
    }

    // ── Score / penalty ───────────────────────────────────────────────────────

    fun updateTeamScore(
        teamId: String,
        campingId: String,
        points: Int,
        reason: String,
        actor: AuthenticatedUser,
    ) {
        val trimmedReason = reason.trim()
        if (points == 0 || trimmedReason.isEmpty()) return
        viewModelScope.launch {
            _isSaving.value = true
            _operationError.value = null
            try {
                val saved = teamService.updateTeamScore(
                    TeamScoreRequest(
                        teamId = teamId,
                        campingId = campingId,
                        points = points,
                        reason = trimmedReason,
                    )
                )
                updateLocal(campingId, saved)
                publishState(campingId)
                recordManualAdjustment(
                    campingId = campingId,
                    name = "Manual adjustment",
                    points = points,
                    reason = trimmedReason,
                    targetTeamId = saved.id,
                    targetTeamName = saved.name,
                    targetUserId = null,
                    targetUserName = null,
                    previousScore = saved.totalScore - points,
                    newScore = saved.totalScore,
                    actor = actor,
                )
                _operationMessage.value = "Score updated."
                dispatchTeamUpdate(
                    TeamNotificationRequest(
                        campingId = campingId,
                        teamId = saved.id,
                        teamName = saved.name,
                        event = TeamNotificationEvent.ScoreChanged,
                        body = "${saved.name} score changed by ${points.signedForNotification()}.",
                        pointsDelta = points,
                        reason = trimmedReason,
                    ),
                )
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Could not update score."
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun applyPenalty(
        teamId: String,
        campingId: String,
        points: Int,
        reason: String,
        actor: AuthenticatedUser,
    ) {
        val trimmedReason = reason.trim()
        if (points <= 0 || trimmedReason.isEmpty()) return
        viewModelScope.launch {
            _isSaving.value = true
            _operationError.value = null
            try {
                val penalty = TeamPenalty(
                    id = UUID.randomUUID().toString(),
                    reason = trimmedReason,
                    points = points,
                    createdAt = Date(),
                )
                val saved = teamService.applyPenalty(penalty, teamId, campingId)
                updateLocal(campingId, saved)
                publishState(campingId)
                recordManualAdjustment(
                    campingId = campingId,
                    name = "Penalty",
                    points = -points,
                    reason = trimmedReason,
                    targetTeamId = saved.id,
                    targetTeamName = saved.name,
                    targetUserId = null,
                    targetUserName = null,
                    previousScore = saved.totalScore + points,
                    newScore = saved.totalScore,
                    actor = actor,
                )
                _operationMessage.value = "Penalty applied."
                dispatchTeamUpdate(
                    TeamNotificationRequest(
                        campingId = campingId,
                        teamId = saved.id,
                        teamName = saved.name,
                        event = TeamNotificationEvent.PenaltyApplied,
                        body = "${saved.name} received a $points-point penalty.",
                        pointsDelta = -points,
                        reason = trimmedReason,
                    ),
                )
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Could not apply penalty."
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun updateMemberScore(
        memberId: String,
        teamId: String,
        campingId: String,
        delta: Int,
        reason: String,
        actor: AuthenticatedUser,
    ) {
        val trimmedReason = reason.trim()
        if (delta == 0 || trimmedReason.isEmpty()) return
        viewModelScope.launch {
            _isSaving.value = true
            _operationError.value = null
            try {
                val saved = teamService.updateMemberScore(memberId, delta, teamId, campingId)
                updateLocal(campingId, saved)
                publishState(campingId)
                _operationMessage.value = "Personal score updated."
                saved.members.firstOrNull { it.id == memberId }?.let { member ->
                    recordManualAdjustment(
                        campingId = campingId,
                        name = "Manual adjustment",
                        points = delta,
                        reason = trimmedReason,
                        targetTeamId = null,
                        targetTeamName = null,
                        targetUserId = member.userId,
                        targetUserName = member.displayName,
                        previousScore = member.personalScore - delta,
                        newScore = member.personalScore,
                        actor = actor,
                    )
                    dispatchTeamUpdate(
                        TeamNotificationRequest(
                            campingId = campingId,
                            teamId = saved.id,
                            teamName = saved.name,
                            event = TeamNotificationEvent.MemberScoreChanged,
                            body = "${member.displayName} score changed by ${delta.signedForNotification()}.",
                            memberId = member.userId,
                            memberName = member.displayName,
                            pointsDelta = delta,
                            reason = trimmedReason,
                        ),
                    )
                }
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Could not update personal score."
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun previewAutoBalance(attendees: List<CampingAttendee>, teamIds: List<String>) {
        autoBalancePreviewJob?.cancel()
        val signature = AutoBalancePreviewSignature.from(attendees, teamIds)
        val runId = ++autoBalancePreviewRunId
        _autoBalancePreview.value = null
        autoBalancePreviewSignature = null
        _operationError.value = null
        _isAutoBalancePreviewRunning.value = true
        autoBalancePreviewJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val result = TeamBalancer().balance(attendees, teamIds) { !isActive }
                if (!isActive || autoBalancePreviewRunId != runId) return@launch
                autoBalancePreviewSignature = signature
                _autoBalancePreview.value = result
            } catch (_: CancellationException) {
                // Superseded by a newer preview request.
            } catch (e: Exception) {
                if (autoBalancePreviewRunId == runId) {
                    _operationError.value = e.message ?: "Could not preview team balance."
                }
            } finally {
                if (autoBalancePreviewRunId == runId) {
                    _isAutoBalancePreviewRunning.value = false
                    autoBalancePreviewJob = null
                }
            }
        }
    }

    fun applyAutoBalance(
        campingId: String,
        attendees: List<CampingAttendee>,
        teamIds: List<String>,
        onSuccess: () -> Unit = {},
    ) {
        val preview = _autoBalancePreview.value ?: return
        if (preview.assignmentsByTeamId.isEmpty()) return
        val currentSignature = AutoBalancePreviewSignature.from(attendees, teamIds)
        if (autoBalancePreviewSignature != currentSignature) {
            _autoBalancePreview.value = null
            autoBalancePreviewSignature = null
            _operationError.value = "Preview is out of date. Run it again before applying."
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            _operationError.value = null
            try {
                var latestTeams = teams(campingId)
                currentSignature.teamIds.forEach { teamId ->
                    preview.assignmentsByTeamId[teamId].orEmpty().forEach { attendee ->
                        val member = attendee.toTeamMember()
                        latestTeams = teamService.assignMember(member, teamId, campingId)
                        latestTeams.firstOrNull { it.id == teamId }?.let { team ->
                            dispatchTeamUpdate(
                                TeamNotificationRequest(
                                    campingId = campingId,
                                    teamId = team.id,
                                    teamName = team.name,
                                    event = TeamNotificationEvent.MemberAssigned,
                                    body = "${member.displayName} joined ${team.name}.",
                                    memberId = member.userId,
                                    memberName = member.displayName,
                                ),
                            )
                        }
                    }
                }
                _allTeams.value = _allTeams.value + (campingId to latestTeams)
                publishState(campingId)
                _autoBalancePreview.value = null
                autoBalancePreviewSignature = null
                _operationMessage.value = "Teams auto-balanced."
                onSuccess()
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Could not auto-balance teams."
            } finally {
                _isSaving.value = false
            }
        }
    }

    // ── Photo upload ──────────────────────────────────────────────────────────

    fun uploadPhoto(bytes: ByteArray, mimeType: String, fileExtension: String) {
        viewModelScope.launch {
            _isUploadingPhoto.value = true
            _operationError.value = null
            runCatching {
                imageUploader.uploadImage(
                    assetIdPrefix = "campzone/teams/${_form.value.id}",
                    folder = "campzone/teams",
                    tags = listOf("campzone", "teams"),
                    bytes = bytes,
                    mimeType = mimeType,
                    fileExtension = fileExtension,
                )
            }.onSuccess { result ->
                _form.value = _form.value.copy(
                    photoUrl = result.secureUrl,
                    photoPublicId = result.publicId,
                )
            }.onFailure {
                _operationError.value = it.message ?: "Photo upload failed."
            }
            _isUploadingPhoto.value = false
        }
    }

    fun removePhoto() {
        _form.value = _form.value.copy(photoUrl = null, photoPublicId = null)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun publishState(campingId: String) {
        val list = _allTeams.value[campingId] ?: emptyList()
        _uiState.value = if (list.isEmpty()) TeamsUiState.Empty else TeamsUiState.Loaded(list)
    }

    private fun updateLocal(campingId: String, team: Team) {
        val current = (_allTeams.value[campingId] ?: emptyList()).toMutableList()
        val idx = current.indexOfFirst { it.id == team.id }
        if (idx >= 0) current[idx] = team else current.add(team)
        val sorted = current.sortedWith(compareByDescending<Team> { it.totalScore }.thenBy { it.name.lowercase() })
        _allTeams.value = _allTeams.value + (campingId to sorted)
    }

    private suspend fun dispatchTeamUpdate(request: TeamNotificationRequest) {
        runCatching { teamNotificationDispatcher.dispatchTeamUpdate(request) }
    }

    /**
     * Appends the audit record after the score mutation succeeds. This is
     * intentionally best-effort: Firestore grants team management and point
     * assignment through separate gates, so a denied ledger write must not make
     * an already-persisted team change look rolled back.
     */
    private suspend fun recordManualAdjustment(
        campingId: String,
        name: String,
        points: Int,
        reason: String,
        targetTeamId: String?,
        targetTeamName: String?,
        targetUserId: String?,
        targetUserName: String?,
        previousScore: Int,
        newScore: Int,
        actor: AuthenticatedUser,
    ) {
        val activity = Activity(
            id = UUID.randomUUID().toString(),
            campingId = campingId,
            gameId = null,
            name = name,
            points = points,
            reason = reason,
            previousScore = previousScore,
            newScore = newScore,
            createdBy = actor.uid,
            createdByName = actor.preferredDisplayName,
            createdAt = Date(),
            targetTeamId = targetTeamId,
            targetTeamName = targetTeamName,
            targetUserId = targetUserId,
            targetUserName = targetUserName,
            visibility = PointRuleVisibility.Immediate,
        )
        runCatching { gameService.recordActivity(activity) }
    }
}

private fun Int.signedForNotification(): String =
    if (this > 0) "+$this" else "$this"

private fun TeamMemberRole.displayNameForNotification(): String = when (this) {
    TeamMemberRole.Captain -> "Captain"
    TeamMemberRole.ViceCaptain -> "Vice Captain"
    TeamMemberRole.Member -> "Member"
}
