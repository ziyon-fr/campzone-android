package fr.ziyon.campzone.ui.camping.registrations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.core.permissions.AppPermission
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RegistrationReviewPhase {
    data object Loading : RegistrationReviewPhase
    data object Empty : RegistrationReviewPhase
    data class Loaded(val campings: List<Camping>) : RegistrationReviewPhase
    data class Error(val message: String?) : RegistrationReviewPhase
    data object Restricted : RegistrationReviewPhase
}

data class RegistrationReviewUiState(
    val phase: RegistrationReviewPhase = RegistrationReviewPhase.Loading,
    val operationMessage: String? = null,
    val operationError: String? = null,
    val isSaving: Boolean = false,
)

@HiltViewModel
class RegistrationReviewViewModel @Inject constructor(
    private val service: CampingService,
) : ViewModel() {

    private val permissions = AppPermissionEvaluator()
    private val _uiState = MutableStateFlow(RegistrationReviewUiState())
    val uiState: StateFlow<RegistrationReviewUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var allCampings: List<Camping> = emptyList()
    private var permissionUser: PermissionUser? = null

    fun load(user: AuthenticatedUser) {
        permissionUser = PermissionUser(
            role = user.role,
            userId = user.uid,
            church = user.church,
        )
        if (!permissions.can(permissionUser, AppPermission.ApproveRegistrations)) {
            observeJob?.cancel()
            _uiState.value = RegistrationReviewUiState(phase = RegistrationReviewPhase.Restricted)
            return
        }
        if (observeJob?.isActive == true) return
        observeJob = viewModelScope.launch {
            _uiState.update { it.copy(phase = RegistrationReviewPhase.Loading) }
            service.observeCampings()
                .catch { error ->
                    _uiState.update {
                        it.copy(phase = RegistrationReviewPhase.Error(error.message))
                    }
                }
                .collect { campings ->
                    allCampings = campings
                    publish()
                }
        }
    }

    fun retry(user: AuthenticatedUser) {
        observeJob?.cancel()
        observeJob = null
        load(user)
    }

    fun updateRegistration(
        campingId: String,
        attendeeId: String,
        status: RegistrationApprovalStatus,
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isSaving = true, operationMessage = null, operationError = null)
            }
            runCatching {
                service.updateRegistrationStatus(
                    attendeeId = attendeeId,
                    status = status,
                    campingId = campingId,
                )
            }.onSuccess { updatedCamping ->
                allCampings = allCampings.map { camping ->
                    if (camping.id == updatedCamping.id) updatedCamping else camping
                }
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        operationMessage = when (status) {
                            RegistrationApprovalStatus.Approved -> "Registration approved."
                            RegistrationApprovalStatus.Rejected -> "Registration rejected."
                            RegistrationApprovalStatus.Pending -> "Registration moved to pending."
                            RegistrationApprovalStatus.Waitlisted -> "Registration moved to waitlist."
                        },
                    )
                }
                publish()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        operationError = error.message ?: "Registration could not be updated.",
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(operationMessage = null, operationError = null) }
    }

    private fun publish() {
        val user = permissionUser
        val pendingCampings = allCampings
            .filter { camping ->
                (camping.pendingAttendees.isNotEmpty() || camping.waitlistedAttendees.isNotEmpty()) &&
                    permissions.canApproveRegistrations(user, camping.permissionContext())
            }
            .sortedBy { it.startDate }
        _uiState.update {
            it.copy(
                phase = if (pendingCampings.isEmpty()) {
                    RegistrationReviewPhase.Empty
                } else {
                    RegistrationReviewPhase.Loaded(pendingCampings)
                },
            )
        }
    }
}

internal fun Camping.permissionContext(): CampingPermissionContext =
    CampingPermissionContext(
        organizerLevelType = organizerLevel.type.wireValue,
        organizerLevelValue = organizerLevel.value,
        createdByUid = createdByUid,
    )
