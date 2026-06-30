package fr.ziyon.campzone.ui.camping.registrations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.CampingAgeGroup
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AttendeeFilters(
    val church: String = "",
    val ageGroup: CampingAgeGroup? = null,
    val language: String = "",
) {
    val isEmpty: Boolean
        get() = church.isBlank() && ageGroup == null && language.isBlank()
}

data class CampingAttendeesUiState(
    val isLoading: Boolean = true,
    val camping: Camping? = null,
    val attendees: List<CampingAttendee> = emptyList(),
    val searchText: String = "",
    val filters: AttendeeFilters = AttendeeFilters(),
    val canViewAttendees: Boolean = false,
    val canViewProfiles: Boolean = false,
    val errorMessage: String? = null,
) {
    val visibleAttendees: List<CampingAttendee>
        get() {
            if (!canViewAttendees) return emptyList()
            val source = if (canViewProfiles) attendees else attendees.filter {
                it.registrationStatus == RegistrationApprovalStatus.Approved
            }
            val query = searchText.trim()
            return source.filter { attendee ->
                matchesFilters(attendee) &&
                    (query.isBlank() ||
                        attendee.displayName.contains(query, ignoreCase = true) ||
                        attendee.church.contains(query, ignoreCase = true))
            }
        }

    val totalVisibleScopeCount: Int
        get() = if (canViewProfiles) attendees.size else approvedCount

    val approvedCount: Int
        get() = attendees.count { it.registrationStatus == RegistrationApprovalStatus.Approved }

    val availableChurches: List<String>
        get() = attendees.map { it.church.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

    val availableLanguages: List<String>
        get() = attendees.flatMap { it.languages }
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy { it.lowercase() }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

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
class CampingAttendeesViewModel @Inject constructor(
    private val service: CampingService,
) : ViewModel() {

    private val permissions = AppPermissionEvaluator()
    private val _uiState = MutableStateFlow(CampingAttendeesUiState())
    val uiState: StateFlow<CampingAttendeesUiState> = _uiState.asStateFlow()

    private var loadedKey: Pair<String, String>? = null

    fun load(campingId: String, user: AuthenticatedUser) {
        val key = campingId to user.uid
        if (loadedKey == key && !_uiState.value.isLoading) return
        loadedKey = key
        viewModelScope.launch {
            _uiState.value = CampingAttendeesUiState(isLoading = true)
            runCatching { service.fetchCamping(campingId) }
                .onSuccess { camping ->
                    val permissionUser = PermissionUser(
                        role = user.role,
                        userId = user.uid,
                        church = user.church,
                    )
                    val context = camping.permissionContext()
                    val canViewProfiles = permissions.canViewParticipantProfiles(permissionUser, context)
                    val isApprovedParticipant = camping.attendees.any { attendee ->
                        attendee.registrationStatus == RegistrationApprovalStatus.Approved &&
                            (attendee.userId == user.uid ||
                                attendee.guardianId == user.uid ||
                                (attendee.participantKind == RegistrationParticipantKind.SelfParticipant &&
                                    attendee.id == user.uid))
                    }
                    _uiState.value = CampingAttendeesUiState(
                        isLoading = false,
                        camping = camping,
                        attendees = camping.attendees,
                        canViewAttendees = canViewProfiles || isApprovedParticipant,
                        canViewProfiles = canViewProfiles,
                    )
                }
                .onFailure { error ->
                    loadedKey = null
                    _uiState.value = CampingAttendeesUiState(
                        isLoading = false,
                        errorMessage = error.message ?: "Attendees could not be loaded.",
                    )
                }
        }
    }

    fun updateSearch(value: String) {
        _uiState.update { it.copy(searchText = value) }
    }

    fun updateFilters(filters: AttendeeFilters) {
        _uiState.update { it.copy(filters = filters) }
    }
}
