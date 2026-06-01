package fr.ziyon.campzone.ui.profile.badges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.i18n.StringProvider
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.badges.AchievementService
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Achievement
import fr.ziyon.campzone.data.model.BadgeViewModel
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.EarnedBadge
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.Team
import fr.ziyon.campzone.data.teams.TeamService
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AchievementUiState {
    data object Loading : AchievementUiState
    data class Loaded(
        val targetUserId: String,
        val earned: List<EarnedBadge>,
        val catalog: List<Achievement>,
    ) : AchievementUiState
    data class Error(val message: String) : AchievementUiState
}

sealed interface BadgeAwardUiState {
    data object Loading : BadgeAwardUiState
    data object Restricted : BadgeAwardUiState
    data class Loaded(
        val camping: Camping,
        val teams: List<Team>,
        val catalog: List<Achievement>,
        val selectedAchievementId: String,
        val selectedTargetMode: BadgeAwardTargetMode,
        val selectedTeamId: String?,
        val selectedAttendeeId: String?,
        val note: String,
    ) : BadgeAwardUiState
    data class Error(val message: String) : BadgeAwardUiState
}

enum class BadgeAwardTargetMode {
    Team,
    Individual,
}

@HiltViewModel
class AchievementViewModel @Inject constructor(
    private val achievementService: AchievementService,
    private val campingService: CampingService,
    private val teamService: TeamService,
    private val strings: StringProvider,
) : ViewModel() {
    private val permissions = AppPermissionEvaluator()

    private val _uiState = MutableStateFlow<AchievementUiState>(AchievementUiState.Loading)
    val uiState: StateFlow<AchievementUiState> = _uiState.asStateFlow()

    private val _awardState = MutableStateFlow<BadgeAwardUiState>(BadgeAwardUiState.Loading)
    val awardState: StateFlow<BadgeAwardUiState> = _awardState.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    fun loadProfileBadges(userId: String) {
        _uiState.value = AchievementUiState.Loading
        viewModelScope.launch {
            runCatching {
                val catalog = achievementService.loadCatalog()
                val catalogIds = catalog.map { it.id }.toSet()
                val earned = achievementService.loadEarned(userId)
                    .filter { it.id in catalogIds }
                AchievementUiState.Loaded(userId, earned, catalog)
            }
                .onSuccess { _uiState.value = it }
                .onFailure { _uiState.value = AchievementUiState.Error(it.message ?: strings.get(R.string.badges_load_error)) }
        }
    }

    fun loadAwardSurface(campingId: String, user: AuthenticatedUser) {
        _awardState.value = BadgeAwardUiState.Loading
        viewModelScope.launch {
            runCatching {
                val camping = campingService.fetchCamping(campingId)
                val permissionUser = PermissionUser(user.role, user.uid, user.church)
                if (!permissions.canAwardAchievements(permissionUser, camping.permissionContext())) {
                    return@runCatching null
                }
                val catalog = achievementService.loadCatalog()
                val teams = teamService.loadTeams(campingId)
                AwardData(camping, teams, catalog)
            }.onSuccess { data ->
                if (data == null) {
                    _awardState.value = BadgeAwardUiState.Restricted
                } else {
                    _awardState.value = data.toLoaded()
                }
            }.onFailure {
                _awardState.value = BadgeAwardUiState.Error(it.message ?: strings.get(R.string.badges_award_load_error))
            }
        }
    }

    fun selectAchievement(id: String) = updateAwardLoaded {
        copy(selectedAchievementId = id)
    }

    fun selectTargetMode(mode: BadgeAwardTargetMode) = updateAwardLoaded {
        copy(selectedTargetMode = mode)
    }

    fun selectTeam(id: String) = updateAwardLoaded {
        copy(selectedTeamId = id)
    }

    fun selectAttendee(id: String) = updateAwardLoaded {
        copy(selectedAttendeeId = id)
    }

    fun updateNote(note: String) = updateAwardLoaded {
        copy(note = note)
    }

    fun dismissOperationMessage() {
        _operationMessage.value = null
    }

    fun awardSelected(currentUserId: String) {
        val state = _awardState.value as? BadgeAwardUiState.Loaded ?: return
        val recipients = state.recipientUserIds()
        val achievement = state.catalog.firstOrNull { it.id == state.selectedAchievementId }
        if (achievement?.canBeAwardedManually != true) {
            _operationMessage.value = strings.get(R.string.badges_automatic_award_error)
            return
        }
        if (currentUserId in recipients) {
            _operationMessage.value = strings.get(R.string.badges_self_award_error)
            return
        }
        if (recipients.isEmpty()) {
            _operationMessage.value = strings.get(R.string.badges_select_participant_error)
            return
        }
        _isSaving.value = true
        viewModelScope.launch {
            runCatching {
                achievementService.award(
                    achievementId = state.selectedAchievementId,
                    userIds = recipients,
                    campingId = state.camping.id,
                    note = state.note,
                )
            }.onSuccess {
                _operationMessage.value =
                    if (it.size == 1) {
                        strings.get(R.string.badges_awarded)
                    } else {
                        strings.get(R.string.badges_awarded_many, it.size)
                    }
            }.onFailure {
                _operationMessage.value = it.message ?: strings.get(R.string.badges_award_error)
            }
            _isSaving.value = false
        }
    }

    fun badgesFor(earned: List<EarnedBadge>, catalog: List<Achievement>): List<BadgeViewModel> =
        catalog.map { achievement ->
            BadgeViewModel(achievement, earned.firstOrNull { it.id == achievement.id })
        }.sortedWith(
            compareByDescending<BadgeViewModel> { it.isEarned }
                .thenBy { it.achievement.title },
        )

    private fun updateAwardLoaded(transform: BadgeAwardUiState.Loaded.() -> BadgeAwardUiState.Loaded) {
        _awardState.update { state ->
            if (state is BadgeAwardUiState.Loaded) state.transform() else state
        }
    }

    private fun AwardData.toLoaded(): BadgeAwardUiState.Loaded {
        val approved = camping.attendees
            .filter { it.registrationStatus == RegistrationApprovalStatus.Approved }
            .sortedBy { it.displayName.lowercase() }
        return BadgeAwardUiState.Loaded(
            camping = camping,
            teams = teams,
            catalog = catalog,
            selectedAchievementId = catalog.firstOrNull { it.canBeAwardedManually }?.id.orEmpty(),
            selectedTargetMode = BadgeAwardTargetMode.Team,
            selectedTeamId = teams.firstOrNull()?.id,
            selectedAttendeeId = approved.firstOrNull()?.id,
            note = "",
        )
    }

    private data class AwardData(
        val camping: Camping,
        val teams: List<Team>,
        val catalog: List<Achievement>,
    )
}

private fun BadgeAwardUiState.Loaded.recipientUserIds(): List<String> =
    when (selectedTargetMode) {
        BadgeAwardTargetMode.Team -> teams.firstOrNull { it.id == selectedTeamId }
            ?.members
            .orEmpty()
            .map { it.userId }
        BadgeAwardTargetMode.Individual -> camping.attendees
            .firstOrNull { it.id == selectedAttendeeId && it.registrationStatus == RegistrationApprovalStatus.Approved }
            ?.let { listOf(it.userId) }
            .orEmpty()
    }.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()

private fun Camping.permissionContext(): CampingPermissionContext =
    CampingPermissionContext(
        organizerLevelType = organizerLevel.type.wireValue,
        organizerLevelValue = organizerLevel.value,
        createdByUid = createdByUid,
    )
