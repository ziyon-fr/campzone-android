package fr.ziyon.campzone.ui.camping.registrations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AttendeeProfileUiState(
    val isLoading: Boolean = true,
    val camping: Camping? = null,
    val attendee: CampingAttendee? = null,
    val canViewProfile: Boolean = false,
    val canRemoveAttendee: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
) {
    val isRestricted: Boolean
        get() = camping != null && !canViewProfile
}

@HiltViewModel
class AttendeeProfileViewModel @Inject constructor(
    private val service: CampingService,
) : ViewModel() {

    private val permissions = AppPermissionEvaluator()
    private val _uiState = MutableStateFlow(AttendeeProfileUiState())
    val uiState: StateFlow<AttendeeProfileUiState> = _uiState.asStateFlow()

    private var loadedKey: Triple<String, String, String>? = null
    private var permissionUser: PermissionUser? = null

    fun load(campingId: String, attendeeId: String, user: AuthenticatedUser) {
        val key = Triple(campingId, attendeeId, user.uid)
        if (loadedKey == key && !_uiState.value.isLoading) return
        loadedKey = key
        permissionUser = PermissionUser(role = user.role, userId = user.uid, church = user.church)
        viewModelScope.launch {
            _uiState.value = AttendeeProfileUiState(isLoading = true)
            runCatching { service.fetchCamping(campingId) }
                .onSuccess { camping ->
                    publish(camping = camping, attendeeId = attendeeId)
                }
                .onFailure { error ->
                    loadedKey = null
                    _uiState.value = AttendeeProfileUiState(
                        isLoading = false,
                        errorMessage = error.message ?: "Attendee could not be loaded.",
                    )
                }
        }
    }

    fun updateStatus(status: RegistrationApprovalStatus) {
        val state = _uiState.value
        val camping = state.camping ?: return
        val attendee = state.attendee ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }
            runCatching {
                service.updateRegistrationStatus(
                    attendeeId = attendee.id,
                    status = status,
                    campingId = camping.id,
                )
            }.onSuccess { updatedCamping ->
                publish(
                    camping = updatedCamping,
                    attendeeId = attendee.id,
                    successMessage = when (status) {
                        RegistrationApprovalStatus.Approved -> "Registration approved."
                        RegistrationApprovalStatus.Rejected -> "Registration rejected."
                        RegistrationApprovalStatus.Pending -> "Registration moved to pending."
                        RegistrationApprovalStatus.Waitlisted -> "Registration moved to waitlist."
                    },
                )
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.message ?: "Registration could not be updated.",
                    )
                }
            }
        }
    }

    fun deleteAttendee(onDeleted: () -> Unit) {
        val state = _uiState.value
        val camping = state.camping ?: return
        val attendee = state.attendee ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }
            runCatching {
                service.deleteAttendee(
                    attendeeId = attendee.id,
                    campingId = camping.id,
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        attendee = null,
                        successMessage = "Attendee removed.",
                    )
                }
                onDeleted()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.message ?: "Attendee could not be removed.",
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    private fun publish(
        camping: Camping,
        attendeeId: String,
        successMessage: String? = null,
    ) {
        val user = permissionUser
        val context = camping.permissionContext()
        val canViewProfile = permissions.canViewParticipantProfiles(user, context)
        _uiState.value = AttendeeProfileUiState(
            isLoading = false,
            camping = camping,
            attendee = camping.attendees.firstOrNull { it.id == attendeeId },
            canViewProfile = canViewProfile,
            canRemoveAttendee = permissions.canManageAnyCamping(user) ||
                permissions.canApproveRegistrations(user, context),
            successMessage = successMessage,
        )
    }
}
