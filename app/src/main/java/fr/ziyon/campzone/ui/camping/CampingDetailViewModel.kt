package fr.ziyon.campzone.ui.camping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.core.permissions.AppPermission
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.analytics.AnalyticsService
import fr.ziyon.campzone.data.analytics.NoOpAnalyticsService
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.CampingAgeGroup
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.camping.CampingNotFoundException
import fr.ziyon.campzone.data.games.FakeGameService
import fr.ziyon.campzone.data.games.GameService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingPublicationStatus
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import fr.ziyon.campzone.data.model.TransportationPaymentStatus
import fr.ziyon.campzone.data.model.VenueMap
import fr.ziyon.campzone.data.model.hasContent
import fr.ziyon.campzone.data.model.visibleForGameLocationRules
import fr.ziyon.campzone.data.venuemap.FakeVenueMapService
import fr.ziyon.campzone.data.venuemap.VenueMapService
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

enum class CampingDetailOperationMessage {
    PinnedToHome,
    UnpinnedFromHome,
    CampingCancelled,
    CampingPublished,
}

data class CampingDetailUiState(
    val isLoading: Boolean = true,
    val camping: Camping? = null,
    val attendees: List<CampingAttendee> = emptyList(),
    val userRegistration: CampingAttendee? = null,
    val canViewParticipantProfiles: Boolean = false,
    val canRegisterForCampings: Boolean = false,
    val canManageFamilyRegistrations: Boolean = false,
    val canEditCamping: Boolean = false,
    val canCancelCamping: Boolean = false,
    val canViewSongbook: Boolean = false,
    val canApproveRegistrations: Boolean = false,
    val canManageSchedule: Boolean = false,
    val canManageFoodMenu: Boolean = false,
    val canEditGuidelines: Boolean = false,
    val canManageTeams: Boolean = false,
    val canManageStaffRoles: Boolean = false,
    val canManageGames: Boolean = false,
    val canRevealWinners: Boolean = false,
    val canManageAlbumMedia: Boolean = false,
    val canManageAlbumSettings: Boolean = false,
    val canManageCheckIns: Boolean = false,
    val canManageTransportation: Boolean = false,
    val canAwardAchievements: Boolean = false,
    val canManageAnyCamping: Boolean = false,
    val canPinCampingToHome: Boolean = false,
    val canCreateRecurringCamp: Boolean = false,
    val wasCreatedByCurrentUser: Boolean = false,
    val isApprovedParticipant: Boolean = false,
    val hasManagedRegistration: Boolean = false,
    val hasPendingRegistrationPayment: Boolean = false,
    val hasPayablePriceItems: Boolean = false,
    /** Loaded venue map; the entry card self-silences when this has no content. */
    val venueMap: VenueMap? = null,
    /** Attendee ids of the viewer's own children registered here; drives the
     *  self-silencing "Family at Camp" guardian card. */
    val guardianChildAttendeeIds: List<String> = emptyList(),
    val attendeeSearch: String = "",
    val filters: CampingAttendeeFilters = CampingAttendeeFilters(),
    val isSettingFeatured: Boolean = false,
    val isMutatingCamping: Boolean = false,
    val operationMessage: CampingDetailOperationMessage? = null,
    val operationError: String? = null,
    val errorMessage: String? = null,
    val campingNotFound: Boolean = false,
) {
    val canViewAttendees: Boolean
        get() = canViewParticipantProfiles || isApprovedParticipant

    val approvedAttendeeCount: Int
        get() = attendees.count { it.registrationStatus == RegistrationApprovalStatus.Approved }

    val pendingAttendeeCount: Int
        get() = attendees.count { it.registrationStatus == RegistrationApprovalStatus.Pending }

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

    val recentAttendees: List<CampingAttendee>
        get() {
            if (!canViewAttendees) return emptyList()
            val source = if (canViewParticipantProfiles) {
                attendees
            } else {
                attendees.filter { it.registrationStatus == RegistrationApprovalStatus.Approved }
            }
            return source.takeLast(3).asReversed()
        }

    val showRegisterCta: Boolean
        get() = camping?.acceptsRegistrations == true &&
            canRegisterForCampings &&
            (userRegistration == null || canManageFamilyRegistrations)

    val showManagementSection: Boolean
        get() = canManageAnyCamping || canManageTransportation || wasCreatedByCurrentUser ||
            canManageTeams || canManageStaffRoles || canManageSchedule || canManageCheckIns || canManageAlbumMedia ||
            canCreateRecurringCamp

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
    private val venueMapService: VenueMapService = FakeVenueMapService(),
    private val gameService: GameService = FakeGameService(),
    private val analyticsService: AnalyticsService = NoOpAnalyticsService,
) : ViewModel() {

    private val permissions = AppPermissionEvaluator()
    private val _uiState = MutableStateFlow(CampingDetailUiState())
    val uiState: StateFlow<CampingDetailUiState> = _uiState.asStateFlow()

    private data class LoadedRequestKey(
        val campingId: String,
        val userId: String,
        val role: UserRole,
        val church: String?,
    )

    private var loadedKey: LoadedRequestKey? = null
    private var observeJob: Job? = null

    fun load(campingId: String, user: AuthenticatedUser) {
        val requestKey = LoadedRequestKey(
            campingId = campingId,
            userId = user.uid,
            role = user.role,
            church = user.church?.trim()?.takeUnless { it.isBlank() },
        )
        if (loadedKey == requestKey && !_uiState.value.isLoading && observeJob?.isActive == true) return
        loadedKey = requestKey
        observeJob?.cancel()

        _uiState.value = CampingDetailUiState(isLoading = true)
        // Stream the camping doc live so winnerRevealPolicy / score-visibility /
        // capacity changes reach the detail, teams, and games screens in real
        // time. Attendees + venue map stay one-shot (loaded on the first
        // emission); analytics fires once.
        observeJob = viewModelScope.launch {
            var attendees: List<CampingAttendee> = emptyList()
            var venueMap: VenueMap? = null
            var games = emptyList<fr.ziyon.campzone.data.model.Game>()
            var loadedExtras = false
            var trackedView = false
            try {
                service.observeCamping(campingId).collect { camping ->
                    if (!loadedExtras) {
                        attendees = runCatching { service.loadAttendees(campingId) }
                            .getOrDefault(emptyList())
                        venueMap = runCatching { venueMapService.loadMap(campingId) }
                            .getOrNull()
                            ?.takeIf { it.hasContent }
                        games = runCatching { gameService.loadGames(campingId) }
                            .getOrDefault(emptyList())
                        loadedExtras = true
                    }
                    if (!trackedView) {
                        analyticsService.viewCamping(camping.id, camping.title)
                        trackedView = true
                    }
                    val guardianChildIds = attendees.filter {
                        it.participantKind == RegistrationParticipantKind.Child &&
                            it.guardianId == user.uid
                    }.map { it.id }
                    val context = CampingPermissionContext(
                        organizerLevelType = camping.organizerLevel.type.wireValue,
                        organizerLevelValue = camping.organizerLevel.value,
                        createdByUid = camping.createdByUid,
                    )
                    val permissionUser = PermissionUser(
                        role = user.role,
                        userId = user.uid,
                        church = user.church,
                    )
                    fun can(permission: AppPermission) = permissions.hasPermission(
                        user = permissionUser,
                        permission = permission,
                        camping = context,
                    )
                    val userRegistrations = attendees.filter { attendee ->
                        attendee.userId == user.uid ||
                            attendee.guardianId == user.uid ||
                            (attendee.participantKind == RegistrationParticipantKind.SelfParticipant &&
                                attendee.id == user.uid)
                    }
                    val userRegistration = userRegistrations.firstOrNull {
                        it.participantKind == RegistrationParticipantKind.SelfParticipant &&
                            it.userId == user.uid
                    } ?: userRegistrations.firstOrNull { it.id == user.uid }
                    val canViewProfiles = permissions.canViewParticipantProfiles(
                        permissionUser,
                        context,
                    )
                    val isApproved = userRegistrations.any {
                        it.registrationStatus == RegistrationApprovalStatus.Approved
                    }
                    val canManageSchedule = permissions.canManageSchedule(permissionUser, context)
                    val canManageTeams = permissions.canManageTeams(permissionUser, context)
                    val canManageStaffRoles = permissions.canManageStaffRoles(permissionUser, context)
                    val canManageGames = permissions.canManageGames(permissionUser, context)
                    val canCreateRecurringCamp = permissions.canCreateCamping(permissionUser, context) &&
                        (
                            canManageSchedule ||
                                canManageTeams ||
                                permissions.canManageSongbook(permissionUser, context) ||
                                permissions.canEditGuidelines(permissionUser, context)
                            )
                    val visibleVenueMap = venueMap
                        ?.visibleForGameLocationRules(
                            games = games,
                            canSeeHiddenGameLocations = canManageGames || canManageTeams || canManageSchedule,
                        )
                        ?.takeIf { it.hasContent }
                    val previousState = _uiState.value
                    _uiState.value = CampingDetailUiState(
                        isLoading = false,
                        camping = camping.copy(attendees = attendees),
                        attendees = attendees,
                        userRegistration = userRegistration,
                        canViewParticipantProfiles = canViewProfiles,
                        canRegisterForCampings = can(AppPermission.RegisterForCampings),
                        canManageFamilyRegistrations = can(AppPermission.ManageFamilyRegistrations),
                        canEditCamping = permissions.canEditCamping(permissionUser, context),
                        canCancelCamping = permissions.canCancelCamping(permissionUser, context),
                        canViewSongbook = permissions.can(permissionUser, AppPermission.ViewSongbook),
                        canApproveRegistrations = permissions.canApproveRegistrations(
                            permissionUser,
                            context,
                        ),
                        canManageSchedule = canManageSchedule,
                        canManageFoodMenu = permissions.canManageFoodMenu(permissionUser, context),
                        canEditGuidelines = permissions.canEditGuidelines(permissionUser, context),
                        canManageTeams = canManageTeams,
                        canManageStaffRoles = canManageStaffRoles,
                        canManageGames = canManageGames,
                        canRevealWinners = permissions.canRevealWinners(permissionUser, context),
                        canManageAlbumMedia = permissions.canManageAlbumMedia(permissionUser, context),
                        canManageAlbumSettings = permissions.canManageAlbumSettings(permissionUser, context),
                        canManageCheckIns = permissions.canManageCheckIns(permissionUser, context),
                        canManageTransportation = permissions.canManageTransportation(
                            permissionUser,
                            context,
                        ),
                        canAwardAchievements = permissions.canAwardAchievements(
                            permissionUser,
                            context,
                        ),
                        canManageAnyCamping = permissions.canManageAnyCamping(permissionUser),
                        canPinCampingToHome = permissions.canPinFeaturedCamping(permissionUser),
                        canCreateRecurringCamp = canCreateRecurringCamp,
                        wasCreatedByCurrentUser = camping.createdByUid == user.uid,
                        isApprovedParticipant = isApproved,
                        hasManagedRegistration = userRegistrations.isNotEmpty(),
                        hasPendingRegistrationPayment = userRegistrations.any { attendee ->
                            attendee.registrationStatus == RegistrationApprovalStatus.Pending &&
                                attendee.paymentStatus != TransportationPaymentStatus.Paid &&
                                camping.resolvedRegistrationFeeCents(attendee.age) > 0
                        },
                        hasPayablePriceItems = camping.priceItems.any { it.amountCents > 0 } &&
                            (
                                permissions.canManageAnyCamping(permissionUser) ||
                                    permissions.canEditCamping(permissionUser, context) ||
                                    userRegistrations.any {
                                        it.registrationStatus == RegistrationApprovalStatus.Approved ||
                                            it.registrationStatus == RegistrationApprovalStatus.Pending
                                    }
                                ),
                        venueMap = visibleVenueMap,
                        guardianChildAttendeeIds = guardianChildIds,
                        isSettingFeatured = previousState.isSettingFeatured,
                        isMutatingCamping = previousState.isMutatingCamping,
                        operationMessage = previousState.operationMessage,
                        operationError = previousState.operationError,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                loadedKey = null
                _uiState.value = CampingDetailUiState(
                    isLoading = false,
                    errorMessage = error.message,
                    campingNotFound = error is CampingNotFoundException,
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        observeJob?.cancel()
    }

    fun updateAttendeeSearch(value: String) = _uiState.update { it.copy(attendeeSearch = value) }

    fun updateFilters(filters: CampingAttendeeFilters) = _uiState.update { it.copy(filters = filters) }

    fun trackScheduleView(campingId: String) = analyticsService.viewSchedule(campingId)

    fun trackSongbookView(campingId: String) = analyticsService.viewSongbook(campingId)

    fun trackTeamsView(campingId: String) = analyticsService.viewTeams(campingId)

    fun setFeatured(campingId: String, isFeatured: Boolean) {
        if (!_uiState.value.canPinCampingToHome) return
        _uiState.update {
            it.copy(isSettingFeatured = true, operationMessage = null, operationError = null)
        }
        viewModelScope.launch {
            try {
                val updated = service.setFeatured(campingId, isFeatured)
                _uiState.update { state ->
                    state.copy(
                        camping = updated.copy(attendees = state.attendees),
                        isSettingFeatured = false,
                        operationMessage = if (isFeatured) {
                            CampingDetailOperationMessage.PinnedToHome
                        } else {
                            CampingDetailOperationMessage.UnpinnedFromHome
                        },
                        operationError = null,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isSettingFeatured = false,
                        operationError = error.message.orEmpty(),
                    )
                }
            }
        }
    }

    fun cancelCamping(campingId: String) {
        if (!_uiState.value.canCancelCamping || _uiState.value.isMutatingCamping) return
        _uiState.update {
            it.copy(isMutatingCamping = true, operationMessage = null, operationError = null)
        }
        viewModelScope.launch {
            runCatching { service.cancelCamping(campingId) }
                .onSuccess { updated ->
                    analyticsService.cancelCamping(campingId)
                    _uiState.update { state ->
                        state.copy(
                            camping = updated.copy(attendees = state.attendees),
                            isMutatingCamping = false,
                            operationMessage = CampingDetailOperationMessage.CampingCancelled,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isMutatingCamping = false, operationError = error.message.orEmpty())
                    }
                }
        }
    }

    fun publishCamping(campingId: String) {
        val state = _uiState.value
        val camping = state.camping ?: return
        if (!state.canEditCamping || !camping.isDraft || state.isMutatingCamping) return
        _uiState.update {
            it.copy(isMutatingCamping = true, operationMessage = null, operationError = null)
        }
        viewModelScope.launch {
            runCatching {
                service.updatePublicationStatus(campingId, CampingPublicationStatus.Published)
            }
                .onSuccess { updated ->
                    _uiState.update { current ->
                        current.copy(
                            camping = updated.copy(attendees = current.attendees),
                            isMutatingCamping = false,
                            operationMessage = CampingDetailOperationMessage.CampingPublished,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isMutatingCamping = false, operationError = error.message.orEmpty())
                    }
                }
        }
    }

    fun deleteCamping(campingId: String, onDeleted: () -> Unit) {
        val state = _uiState.value
        val canDelete = state.wasCreatedByCurrentUser || state.canCancelCamping
        if (!canDelete || state.isMutatingCamping) return
        _uiState.update {
            it.copy(isMutatingCamping = true, operationMessage = null, operationError = null)
        }
        viewModelScope.launch {
            runCatching { service.deleteCamping(campingId) }
                .onSuccess {
                    _uiState.update { it.copy(isMutatingCamping = false) }
                    onDeleted()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isMutatingCamping = false, operationError = error.message.orEmpty())
                    }
                }
        }
    }

    fun consumeOperationMessage() {
        _uiState.update { it.copy(operationMessage = null) }
    }

    fun consumeOperationError() {
        _uiState.update { it.copy(operationError = null) }
    }
}
