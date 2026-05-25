package fr.ziyon.campzone.ui.camping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.core.permissions.AppPermission
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.CampingAgeGroup
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

data class CampingAttendeeFilters(
    val church: String = "",
    val ageGroup: CampingAgeGroup? = null,
    val language: String = "",
) {
    val isEmpty: Boolean
        get() = church.isBlank() && ageGroup == null && language.isBlank()
}

data class CampingDetailUiState(
    val isLoading: Boolean = true,
    val camping: Camping? = null,
    val attendees: List<CampingAttendee> = emptyList(),
    val canViewParticipantProfiles: Boolean = false,
    val isApprovedParticipant: Boolean = false,
    val attendeeSearch: String = "",
    val filters: CampingAttendeeFilters = CampingAttendeeFilters(),
    val errorMessage: String? = null,
) {
    val canViewAttendees: Boolean
        get() = canViewParticipantProfiles || isApprovedParticipant

    val approvedAttendeeCount: Int
        get() = attendees.count { it.registrationStatus == RegistrationApprovalStatus.Approved }

    /** True only when we can see the full roster (leadership); participants see a partial list. */
    val isAtCapacity: Boolean
        get() = canViewParticipantProfiles &&
            (camping?.participantCapacity?.let { approvedAttendeeCount >= it } ?: false)

    val remainingSpots: Int?
        get() = if (canViewParticipantProfiles) {
            camping?.participantCapacity?.let { (it - approvedAttendeeCount).coerceAtLeast(0) }
        } else {
            null
        }

    val visibleAttendees: List<CampingAttendee>
        get() {
            if (!canViewAttendees) return emptyList()
            val source = if (canViewParticipantProfiles) {
                attendees
            } else {
                attendees.filter { it.registrationStatus == RegistrationApprovalStatus.Approved }
            }
            val query = attendeeSearch.trim()
            return source.filter { attendee ->
                matchesFilters(attendee) &&
                    (query.isBlank() ||
                        attendee.displayName.contains(query, ignoreCase = true) ||
                        attendee.church.contains(query, ignoreCase = true))
            }
        }

    private fun matchesFilters(attendee: CampingAttendee): Boolean {
        if (filters.church.isNotBlank() && !attendee.church.contains(filters.church, ignoreCase = true)) {
            return false
        }
        if (filters.ageGroup != null && attendee.ageGroup != filters.ageGroup) return false
        if (filters.language.isNotBlank() &&
            attendee.languages.none { it.contains(filters.language, ignoreCase = true) }
        ) {
            return false
        }
        return true
    }
}

@HiltViewModel
class CampingDetailViewModel @Inject constructor(
    private val service: CampingService,
) : ViewModel() {

    private val permissions = AppPermissionEvaluator()
    private val _uiState = MutableStateFlow(CampingDetailUiState())
    val uiState: StateFlow<CampingDetailUiState> = _uiState.asStateFlow()

    private var loadedId: String? = null

    fun load(campingId: String, user: AuthenticatedUser) {
        if (loadedId == campingId && !_uiState.value.isLoading) return
        loadedId = campingId

        viewModelScope.launch {
            _uiState.value = CampingDetailUiState(isLoading = true)
            runCatching { service.fetchCamping(campingId) }
                .onSuccess { camping ->
                    val attendees = runCatching { service.loadAttendees(campingId) }
                        .getOrDefault(emptyList())
                    val context = CampingPermissionContext(
                        organizerLevelType = camping.organizerLevel.type.wireValue,
                        organizerLevelValue = camping.organizerLevel.value,
                    )
                    val canViewProfiles = permissions.hasPermission(
                        user = PermissionUser(role = user.role, church = user.church),
                        permission = AppPermission.ViewParticipantProfiles,
                        camping = context,
                    )
                    val isApproved = attendees.any {
                        it.userId == user.uid &&
                            it.registrationStatus == RegistrationApprovalStatus.Approved
                    }
                    _uiState.value = CampingDetailUiState(
                        isLoading = false,
                        camping = camping,
                        attendees = attendees,
                        canViewParticipantProfiles = canViewProfiles,
                        isApprovedParticipant = isApproved,
                    )
                }
                .onFailure { error ->
                    _uiState.value = CampingDetailUiState(
                        isLoading = false,
                        errorMessage = error.message,
                    )
                }
        }
    }

    fun updateAttendeeSearch(value: String) = _uiState.update { it.copy(attendeeSearch = value) }

    fun updateFilters(filters: CampingAttendeeFilters) = _uiState.update { it.copy(filters = filters) }
}
