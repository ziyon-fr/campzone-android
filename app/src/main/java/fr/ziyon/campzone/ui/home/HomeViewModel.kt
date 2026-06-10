package fr.ziyon.campzone.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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

data class HomeLivePassInfo(
    val checkInRecord: CheckInRecord? = null,
    val teamName: String? = null,
    val lodgingName: String? = null,
)

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

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var currentUserId: String? = null

    fun retry() {
        _uiState.update { it.copy(phase = HomePhase.Loading) }
        observeDashboard(currentUserId)
    }

    fun loadHome(forUserId: String?) {
        if (observeJob?.isActive == true && currentUserId == forUserId) return
        currentUserId = forUserId
        _uiState.update { it.copy(phase = HomePhase.Loading) }
        observeDashboard(forUserId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeDashboard(forUserId: String?) {
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
            ) { featuredCamping, campings, announcements ->
                val displayCamping = featuredCamping
                    ?.let { camping ->
                        campings.firstOrNull { it.id == camping.id }
                            ?: campingService.cachedCamping(camping.id)
                            ?: camping
                    }
                HomeDashboardFrame(
                    featuredCamping = displayCamping,
                    announcements = announcements.homePreviews(),
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
                attendees.homePassAttendee(uid)
                    ?.takeIf { it.registrationStatus == RegistrationApprovalStatus.Approved }
            }
            .distinctUntilChanged()
            .flatMapLatest { attendee ->
                if (attendee == null) {
                    flowOf(HomeLivePassInfo())
                } else {
                    combine(
                        checkInService.observeRecord(camping.id, attendee.id)
                            .catch { emit(null) },
                        teamService.observeTeams(camping.id)
                            .map { teams ->
                                teams.firstOrNull { team ->
                                    team.members.any { member ->
                                        member.userId == attendee.userId || member.id == attendee.id
                                    }
                                }?.name?.takeUnless { it.isBlank() }
                            }
                            .catch { emit(null) },
                        lodgingService.observeUnits(camping.id)
                            .map { units ->
                                units.firstOrNull { unit ->
                                    unit.contains(attendee.id) || unit.contains(attendee.userId)
                                }?.name?.takeUnless { it.isBlank() }
                            }
                            .catch { emit(null) },
                    ) { checkInRecord, teamName, lodgingName ->
                        HomeLivePassInfo(
                            checkInRecord = checkInRecord,
                            teamName = teamName,
                            lodgingName = lodgingName,
                        )
                    }
                }
            }
    }

    private fun List<Announcement>.homePreviews(): List<Announcement> =
        filter { announcement ->
            announcement.audienceScope == AnnouncementAudienceScope.App &&
                announcement.notificationTargetRole == null
        }
            .sortedByDescending { it.createdAt?.time ?: 0L }
            .take(6)

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
