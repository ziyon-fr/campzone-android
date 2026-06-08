package fr.ziyon.campzone.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.NotificationSettings
import fr.ziyon.campzone.data.notifications.NotificationChannelsLoader
import fr.ziyon.campzone.data.notifications.NotificationSettingsRules
import fr.ziyon.campzone.data.notifications.NotificationSettingsService
import fr.ziyon.campzone.data.notifications.PersonalTeamChannel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Notification categories that map to a single boolean toggle. */
enum class NotificationCategory { Announcements, Chat, Reminders, Role, Team }

/** Localized-at-the-screen result of a save operation. */
enum class NotificationOpMessage { Saved, SaveFailed }

sealed interface NotificationSettingsUiState {
    data object Loading : NotificationSettingsUiState
    data class Loaded(
        val settings: NotificationSettings,
        val roleOptions: List<UserRole>,
    ) : NotificationSettingsUiState
    data class Error(val message: String) : NotificationSettingsUiState
}

data class NotificationChannelsState(
    val isLoading: Boolean = false,
    val campings: List<Camping> = emptyList(),
    val personalTeams: List<PersonalTeamChannel> = emptyList(),
)

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val service: NotificationSettingsService,
    private val channelsLoader: NotificationChannelsLoader,
) : ViewModel() {

    private val _uiState = MutableStateFlow<NotificationSettingsUiState>(NotificationSettingsUiState.Loading)
    val uiState: StateFlow<NotificationSettingsUiState> = _uiState.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _operationMessage = MutableStateFlow<NotificationOpMessage?>(null)
    val operationMessage: StateFlow<NotificationOpMessage?> = _operationMessage.asStateFlow()

    private val _channels = MutableStateFlow(NotificationChannelsState())
    val channels: StateFlow<NotificationChannelsState> = _channels.asStateFlow()

    private var uid: String = ""
    private var role: UserRole = UserRole.Guest
    private var loadedUid: String? = null
    private var channelsLoadedUid: String? = null

    fun load(uid: String, role: UserRole) {
        this.uid = uid
        this.role = role
        if (loadedUid == uid && _uiState.value is NotificationSettingsUiState.Loaded) return
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _uiState.value = NotificationSettingsUiState.Loading
            runCatching { service.load(uid, role) }
                .onSuccess { settings ->
                    loadedUid = uid
                    emitLoaded(settings)
                    pruneUnavailableChannelsIfNeeded()
                }
                .onFailure { error ->
                    _uiState.value = NotificationSettingsUiState.Error(
                        error.message ?: "Could not load your notification settings.",
                    )
                }
        }
    }

    fun setMasterEnabled(enabled: Boolean) = update { it.copy(isEnabled = enabled) }

    fun setCategory(category: NotificationCategory, enabled: Boolean) = update { settings ->
        when (category) {
            NotificationCategory.Announcements -> settings.copy(announcementsEnabled = enabled)
            NotificationCategory.Chat -> settings.copy(chatMessagesEnabled = enabled)
            NotificationCategory.Reminders -> settings.copy(scheduleRemindersEnabled = enabled)
            NotificationCategory.Role -> settings.copy(roleMessagesEnabled = enabled)
            NotificationCategory.Team -> settings.copy(teamUpdatesEnabled = enabled)
        }
    }

    fun toggleRole(target: UserRole, enabled: Boolean) {
        if (target !in NotificationSettingsRules.roleAudienceOptions(role)) return
        update { settings ->
            val roles = settings.subscribedRoles.toMutableSet()
            if (enabled) roles.add(target) else roles.remove(target)
            settings.copy(subscribedRoles = roles.toList())
        }
    }

    fun toggleCampingChannel(campingId: String, enabled: Boolean) = update { settings ->
        val ids = settings.subscribedCampingIds.toMutableSet()
        if (enabled) ids.add(campingId) else ids.remove(campingId)
        settings.copy(subscribedCampingIds = ids.toList())
    }

    fun toggleTeamChannel(teamId: String, enabled: Boolean) = update { settings ->
        val ids = settings.subscribedTeamIds.toMutableSet()
        if (enabled) ids.add(teamId) else ids.remove(teamId)
        settings.copy(subscribedTeamIds = ids.toList())
    }

    /** Lazily loads camping/team channel options the first time a picker opens. */
    fun loadChannelsIfNeeded() {
        if (channelsLoadedUid == uid) return
        if (_channels.value.isLoading) return
        viewModelScope.launch {
            _channels.value = _channels.value.copy(isLoading = true)
            val campings = runCatching { channelsLoader.attendedCampings(uid) }.getOrDefault(emptyList())
            val teams = runCatching { channelsLoader.personalTeams(uid) }.getOrDefault(emptyList())
            channelsLoadedUid = uid
            _channels.value = NotificationChannelsState(
                isLoading = false,
                campings = campings,
                personalTeams = teams,
            )
            pruneUnavailableChannelsIfNeeded()
        }
    }

    fun consumeOperationMessage() {
        _operationMessage.value = null
    }

    private fun update(
        showFeedback: Boolean = true,
        mutation: (NotificationSettings) -> NotificationSettings,
    ) {
        val current = (_uiState.value as? NotificationSettingsUiState.Loaded)?.settings ?: return
        val mutated = mutation(current)
        if (mutated == current) {
            emitLoaded(mutated)
            return
        }
        // Optimistically reflect the change, then persist.
        emitLoaded(mutated)
        viewModelScope.launch {
            _isSaving.value = true
            runCatching { service.save(mutated, uid, role) }
                .onSuccess { saved ->
                    emitLoaded(saved)
                    if (showFeedback) {
                        _operationMessage.value = NotificationOpMessage.Saved
                    }
                }
                .onFailure {
                    // Revert to the last known-good server state.
                    emitLoaded(current)
                    if (showFeedback) {
                        _operationMessage.value = NotificationOpMessage.SaveFailed
                    }
                }
            _isSaving.value = false
        }
    }

    private fun pruneUnavailableChannelsIfNeeded() {
        if (channelsLoadedUid != uid) return
        val availableCampingIds = _channels.value.campings.map { it.id }.toSet()
        val availableTeamIds = _channels.value.personalTeams.map { it.team.id }.toSet()

        update(showFeedback = false) { settings ->
            settings.copy(
                subscribedCampingIds = settings.subscribedCampingIds.filter { it in availableCampingIds },
                subscribedTeamIds = settings.subscribedTeamIds.filter { it in availableTeamIds },
            )
        }
    }

    private fun emitLoaded(settings: NotificationSettings) {
        _uiState.value = NotificationSettingsUiState.Loaded(
            settings = settings,
            roleOptions = NotificationSettingsRules.roleAudienceOptions(role),
        )
    }
}
