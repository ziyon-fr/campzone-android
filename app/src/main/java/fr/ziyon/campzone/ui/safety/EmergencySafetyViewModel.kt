package fr.ziyon.campzone.ui.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.i18n.StringProvider
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.safety.EmergencySafetyHub
import fr.ziyon.campzone.data.safety.EmergencySafetyHubService
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EmergencySafetyUiState(
    val loading: Boolean = true,
    val camping: Camping? = null,
    val hub: EmergencySafetyHub? = null,
    val canManage: Boolean = false,
    val canBroadcast: Boolean = false,
    val saving: Boolean = false,
    val broadcasting: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class EmergencySafetyViewModel @Inject constructor(
    private val campingService: CampingService,
    private val safetyService: EmergencySafetyHubService,
    private val stringProvider: StringProvider,
) : ViewModel() {
    private val permissions = AppPermissionEvaluator()
    private val _uiState = MutableStateFlow(EmergencySafetyUiState())
    val uiState: StateFlow<EmergencySafetyUiState> = _uiState.asStateFlow()

    fun load(campingId: String, user: AuthenticatedUser) {
        viewModelScope.launch {
            _uiState.value = EmergencySafetyUiState(loading = true)
            runCatching {
                val camping = campingService.fetchCamping(campingId)
                val permissionUser = PermissionUser(user.role, user.uid, user.church)
                val context = camping.permissionContext()
                val hub = safetyService.load(camping)
                EmergencySafetyUiState(
                    loading = false,
                    camping = camping,
                    hub = hub,
                    canManage = permissions.canManageAnnouncements(permissionUser, context) ||
                        permissions.canManageSchedule(permissionUser, context) ||
                        permissions.canEditCamping(permissionUser, context),
                    canBroadcast = permissions.canManageAnnouncements(permissionUser, context),
                )
            }.onSuccess { _uiState.value = it }
                .onFailure { _uiState.value = EmergencySafetyUiState(loading = false, error = it.message) }
        }
    }

    fun save(hub: EmergencySafetyHub) {
        val camping = _uiState.value.camping ?: return
        val normalized = hub.normalized()
        val validationError = when {
            normalized.emergencyContacts.any { it.name.isBlank() } -> stringProvider.get(R.string.safety_contact_name_required)
            normalized.emergencyContacts.any { it.phoneNumber.isBlank() } -> stringProvider.get(R.string.safety_contact_phone_required)
            normalized.emergencyInstructions.isBlank() -> stringProvider.get(R.string.safety_instructions_required)
            normalized.firstAidInfo.isBlank() -> stringProvider.get(R.string.safety_first_aid_required)
            else -> null
        }
        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null, message = null) }
            runCatching { safetyService.save(camping.id, normalized) }
                .onSuccess { saved -> _uiState.update { it.copy(hub = saved, saving = false, message = stringProvider.get(R.string.safety_saved_message)) } }
                .onFailure { error -> _uiState.update { it.copy(saving = false, error = error.message ?: stringProvider.get(R.string.safety_save_error)) } }
        }
    }

    fun broadcast(user: AuthenticatedUser, title: String, body: String) {
        val camping = _uiState.value.camping ?: return
        if (title.isBlank() || body.isBlank()) {
            _uiState.update { it.copy(error = stringProvider.get(R.string.safety_alert_title_body_required)) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(broadcasting = true, error = null, message = null) }
            runCatching { safetyService.sendUrgentBroadcast(camping, user, title, body) }
                .onSuccess { _uiState.update { it.copy(broadcasting = false, message = stringProvider.get(R.string.safety_alert_sent_message)) } }
                .onFailure { error -> _uiState.update { it.copy(broadcasting = false, error = error.message ?: stringProvider.get(R.string.safety_alert_send_error)) } }
        }
    }
}

private fun Camping.permissionContext() = CampingPermissionContext(
    organizerLevelType = organizerLevel.type.wireValue,
    organizerLevelValue = organizerLevel.value,
    createdByUid = createdByUid,
)
