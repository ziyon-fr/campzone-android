package fr.ziyon.campzone.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.announcements.AnnouncementService
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.checkin.CheckInService
import fr.ziyon.campzone.data.dashboard.MainDashboardRepository
import fr.ziyon.campzone.data.lodging.LodgingService
import fr.ziyon.campzone.data.model.Announcement
import fr.ziyon.campzone.data.model.AnnouncementAudienceScope
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CheckInRecord
import fr.ziyon.campzone.data.model.Program
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import fr.ziyon.campzone.data.schedule.ScheduleService
import fr.ziyon.campzone.data.teams.TeamService
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeCampPassInfo(
    val attendee: CampingAttendee,
    val teamName: String? = null,
    val lodgingName: String? = null,
)

data class HomeLivePassInfo(
    val passes: List<HomeCampPassInfo> = emptyList(),
    val checkInRecord: CheckInRecord? = null,
) {
    val primaryPass: HomeCampPassInfo? get() = passes.firstOrNull()
    val teamName: String? get() = primaryPass?.teamName
    val lodgingName: String? get() = primaryPass?.lodgingName
}

sealed interface HomePhase {
    data object Loading : HomePhase
    data class Loaded(
        val featuredCamping: Camping?,
        val livePassInfo: HomeLivePassInfo = HomeLivePassInfo(),
        val upcomingPrograms: List<Program> = emptyList(),
        val announcements: List<Announcement> = emptyList(),
    ) : HomePhase

    data class Error(val message: String?) : HomePhase
}

data class HomeUiState(
    val phase: HomePhase = HomePhase.Loading,
)

private data class HomeDashboardFrame(
    val featuredCamping: Camping?,
    val announcements: List<Announcement>,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val campingService: CampingService,
    private val dashboardRepository: MainDashboardRepository,
    private val scheduleService: ScheduleService,
    private val announcementService: AnnouncementService,
    private val checkInService: CheckInService,
    private val teamService: TeamService,
    private val lodgingService: LodgingService,
) : ViewModel() {
    private val permissions = AppPermissionEvaluator()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var currentUserId: String? = null
    private var currentUser: AuthenticatedUser? = null

    fun retry() {
        _uiState.update { it.copy(phase = HomePhase.Loading) }
        observeDashboard(currentUserId, currentUser)
    }

    fun loadHome(forUserId: String?) {
        loadHome(forUserId, user = null)
    }

    fun loadHome(user: AuthenticatedUser) {
        loadHome(user.uid, user)
    }

    private fun loadHome(forUserId: String?, user: AuthenticatedUser?) {
        if (observeJob?.isActive == true && currentUserId == forUserId) return
        currentUserId = forUserId
        currentUser = user
        _uiState.update { it.copy(phase = HomePhase.Loading) }
        observeDashboard(forUserId, user)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeDashboard(forUserId: String?, user: AuthenticatedUser?) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            val localCampings = campingService.observeCampings()
                // Home can still render tiers 2-4 if the local camping observer
                // is unavailable; the registered override is best-effort local
                // context, matching iOS' separate CampingObserver dependency.
                .catch { emit(emptyList()) }
                .shareIn(this, SharingStarted.Eagerly, replay = 1)
            val registeredCampingIds = campingService.observeApprovedCampingIds(forUserId)
                .catch { emit(campingService.approvedCampingIds(forUserId)) }
                .onStart { emit(campingService.approvedCampingIds(forUserId)) }
                .distinctUntilChanged()
            val featuredCampings = registeredCampingIds
                .flatMapLatest { ids -> dashboardRepository.observeFeaturedCamping(ids) }
            combine(
                featuredCampings,
                localCampings.onStart { emit(emptyList()) },
                announcementService.loadAnnouncements(),
                registeredCampingIds,
            ) { featuredCamping, campings, announcements, registeredIds ->
                val displayCamping = featuredCamping
                    ?.let { camping ->
                        campings.firstOrNull { it.id == camping.id }
                            ?: campingService.cachedCamping(camping.id)
                            ?: camping
                    }
                val visibleCampingIds = campings
                    .filter { camping ->
                        camping.id in registeredIds || canManageScopedResources(user, camping)
                    }
                    .mapTo(registeredIds.toMutableSet()) { it.id }
                HomeDashboardFrame(
                    featuredCamping = displayCamping,
                    announcements = announcements.homePreviews(
                        roleRawValue = user?.role?.rawValue,
                        visibleCampingIds = visibleCampingIds,
                        canViewAll = user?.role?.isAdmin == true,
                        featuredCampingId = displayCamping?.id,
                        registeredCampingIds = registeredIds,
                    ),
                )
            }
                .flatMapLatest { frame ->
                    observeLoadedPhase(
                        featuredCamping = frame.featuredCamping,
                        announcementPreviews = frame.announcements,
                        forUserId = forUserId,
                    )
                }
                .catch { error ->
                    _uiState.update { it.copy(phase = HomePhase.Error(error.message)) }
                }
                .collect { loaded ->
                    _uiState.update {
                        it.copy(phase = loaded)
                    }
                }
        }
    }

    private fun observeLoadedPhase(
        featuredCamping: Camping?,
        announcementPreviews: List<Announcement>,
        forUserId: String?,
    ): Flow<HomePhase.Loaded> {
        if (featuredCamping == null) {
            return flowOf(
                HomePhase.Loaded(
                    featuredCamping = null,
                    announcements = announcementPreviews,
                ),
            )
        }

        val upcomingPrograms = scheduleService.observeSchedule(featuredCamping.id)
            .map { schedule -> upcomingPrograms(schedule.allPrograms) }
            .onStart { emit(loadUpcomingPrograms(featuredCamping.id)) }
            .catch { emit(emptyList()) }

        return combine(
            upcomingPrograms,
            observeLivePassInfo(featuredCamping, forUserId),
        ) { programs, livePassInfo ->
            HomePhase.Loaded(
                featuredCamping = featuredCamping,
                livePassInfo = livePassInfo,
                upcomingPrograms = programs,
                announcements = announcementPreviews,
            )
        }
    }

    private suspend fun loadUpcomingPrograms(
        campingId: String,
        now: Date = Date(),
    ): List<Program> = runCatching {
        scheduleService.loadSchedule(campingId)
            .allPrograms
            .let { upcomingPrograms(it, now) }
    }.getOrDefault(emptyList())

    private fun upcomingPrograms(
        programs: List<Program>,
        now: Date = Date(),
    ): List<Program> {
        val dayStart = Calendar.getInstance().apply {
            time = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        return programs
            .filter { program -> program.endDate >= dayStart }
            .sortedBy { it.endDate }
            .take(6)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeLivePassInfo(
        camping: Camping,
        forUserId: String?,
        now: Date = Date(),
    ): Flow<HomeLivePassInfo> {
        if (camping.startDate.after(now) || camping.endDate.before(now)) {
            return flowOf(HomeLivePassInfo())
        }
        val uid = forUserId?.takeUnless { it.isBlank() } ?: return flowOf(HomeLivePassInfo())

        return campingService.observeUserAttendees(camping.id, uid)
            .map { attendees ->
                attendees
                    .filter { it.registrationStatus == RegistrationApprovalStatus.Approved }
                    .sortedWith(
                        compareBy<CampingAttendee> {
                            if (it.participantKind == RegistrationParticipantKind.SelfParticipant) 0 else 1
                        }.thenBy { it.displayName.lowercase() },
                    )
            }
            .distinctUntilChanged()
            .flatMapLatest { attendees ->
                if (attendees.isEmpty()) {
                    flowOf(HomeLivePassInfo())
                } else {
                    val primaryAttendee = attendees.homePassAttendee(uid)
                    val checkInRecord = primaryAttendee?.let { attendee ->
                        checkInService.observeRecord(camping.id, attendee.id)
                            .catch { emit(null) }
                    } ?: flowOf(null)
                    combine(
                        checkInRecord,
                        teamService.observeTeams(camping.id).catch { emit(emptyList()) },
                        lodgingService.observeUnits(camping.id).catch { emit(emptyList()) },
                    ) { ownCheckInRecord, teams, units ->
                        HomeLivePassInfo(
                            passes = attendees.map { attendee ->
                                HomeCampPassInfo(
                                    attendee = attendee,
                                    teamName = teams.firstOrNull { team ->
                                        team.members.any { member ->
                                            member.userId == attendee.userId || member.id == attendee.id
                                        }
                                    }?.name?.takeUnless { it.isBlank() },
                                    lodgingName = units.firstOrNull { unit ->
                                        unit.contains(attendee.id) || unit.contains(attendee.userId)
                                    }?.name?.takeUnless { it.isBlank() },
                                )
                            },
                            checkInRecord = ownCheckInRecord,
                        )
                    }
                }
            }
    }

    private fun List<Announcement>.homePreviews(
        roleRawValue: String?,
        visibleCampingIds: Set<String>,
        canViewAll: Boolean,
        featuredCampingId: String?,
        registeredCampingIds: Set<String>,
    ): List<Announcement> =
        filter { it.isVisible(roleRawValue, visibleCampingIds, canViewAll) }
            .sortedWith(
                compareBy<Announcement> { announcement ->
                    when (announcement.targetCampingId) {
                        featuredCampingId -> 0
                        in registeredCampingIds -> 1
                        null -> 3
                        else -> 2
                    }
                }.thenByDescending { it.createdAt?.time ?: 0L },
            )
            .take(6)

    private fun canManageScopedResources(user: AuthenticatedUser?, camping: Camping): Boolean {
        user ?: return false
        val permissionUser = PermissionUser(user.role, user.uid, user.church)
        val context = CampingPermissionContext(
            organizerLevelType = camping.organizerLevel.type.wireValue,
            organizerLevelValue = camping.organizerLevel.value,
            createdByUid = camping.createdByUid,
        )
        return permissions.canManageAnnouncements(permissionUser, context) ||
            permissions.canManageSchedule(permissionUser, context) ||
            permissions.canManageTeams(permissionUser, context) ||
            permissions.canManagePolls(permissionUser, context) ||
            permissions.canManageTransportation(permissionUser, context) ||
            permissions.canManageCheckIns(permissionUser, context) ||
            permissions.canManageAlbumMedia(permissionUser, context)
    }

    private fun List<Camping>.approvedCampingIds(forUserId: String?): Set<String> =
        filter { camping -> camping.hasApprovedRegistrationForUser(forUserId) }
            .mapTo(mutableSetOf()) { it.id }

    private fun List<CampingAttendee>.homePassAttendee(userId: String): CampingAttendee? {
        return firstOrNull { attendee ->
            attendee.userId == userId &&
                attendee.participantKind == RegistrationParticipantKind.SelfParticipant
        } ?: firstOrNull { attendee -> attendee.id == userId }
    }
}
