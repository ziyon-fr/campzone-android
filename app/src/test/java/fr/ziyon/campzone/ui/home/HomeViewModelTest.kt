package fr.ziyon.campzone.ui.home

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.announcements.FakeAnnouncementService
import fr.ziyon.campzone.data.camping.FakeCampingService
import fr.ziyon.campzone.data.checkin.FakeCheckInService
import fr.ziyon.campzone.data.dashboard.MainDashboardRepository
import fr.ziyon.campzone.data.lodging.FakeLodgingService
import fr.ziyon.campzone.data.model.Announcement
import fr.ziyon.campzone.data.model.AnnouncementAudienceScope
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.CheckInMethod
import fr.ziyon.campzone.data.model.CheckInRecord
import fr.ziyon.campzone.data.model.LodgingUnit
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import fr.ziyon.campzone.data.model.Team
import fr.ziyon.campzone.data.model.TeamMember
import fr.ziyon.campzone.data.schedule.FakeScheduleService
import fr.ziyon.campzone.data.teams.FakeTeamService
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Date
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun dashboardStillShowsAppWideAnnouncementsWithoutFeaturedCamping() = runTest {
        val viewModel = HomeViewModel(
            campingService = FakeCampingService(initial = emptyList()),
            dashboardRepository = FakeMainDashboardRepository(featuredCamping = null),
            scheduleService = FakeScheduleService(),
            announcementService = FakeAnnouncementService(
                mutableListOf(
                    announcement("global-new", createdAt = Date(3_000)),
                    announcement("camping", audienceScope = AnnouncementAudienceScope.Camping, createdAt = Date(2_000)),
                    announcement("role", role = UserRole.Leader, createdAt = Date(1_000)),
                    announcement("global-old", createdAt = Date(500)),
                ),
            ),
            checkInService = FakeCheckInService(),
            teamService = FakeTeamService(mutableListOf()),
            lodgingService = FakeLodgingService(),
        )
        viewModel.loadHome(forUserId = null)

        advanceUntilIdle()

        val loaded = viewModel.uiState.value.phase as HomePhase.Loaded
        assertNull(loaded.featuredCamping)
        assertEquals(listOf("global-new", "global-old"), loaded.announcements.map { it.id })
    }

    @Test
    fun dashboardRestartsWhenApprovedCampingIdsArriveFromLocalCampings() = runTest {
        val activeCamping = camping(
            id = "active",
            start = Date(1_000),
            end = Date(Date().time + 86_400_000),
            attendees = listOf(
                attendee(
                    id = "user-1",
                    userId = "user-1",
                    status = RegistrationApprovalStatus.Approved,
                ),
            ),
        )
        val pinnedCamping = camping(
            id = "pinned",
            start = Date(Date().time + 172_800_000),
            end = Date(Date().time + 259_200_000),
            isFeatured = true,
        )
        val campingService = FakeCampingService(initial = emptyList())
        val dashboardRepository = RecordingDashboardRepository { registeredIds ->
            if (activeCamping.id in registeredIds) activeCamping else pinnedCamping
        }
        val viewModel = HomeViewModel(
            campingService = campingService,
            dashboardRepository = dashboardRepository,
            scheduleService = FakeScheduleService(),
            announcementService = FakeAnnouncementService(mutableListOf()),
            checkInService = FakeCheckInService(),
            teamService = FakeTeamService(mutableListOf()),
            lodgingService = FakeLodgingService(),
        )

        viewModel.loadHome(forUserId = "user-1")
        advanceUntilIdle()

        assertEquals("pinned", (viewModel.uiState.value.phase as HomePhase.Loaded).featuredCamping?.id)

        campingService.saveCamping(activeCamping)
        advanceUntilIdle()

        assertEquals("active", (viewModel.uiState.value.phase as HomePhase.Loaded).featuredCamping?.id)
        assertEquals(listOf(emptySet<String>(), setOf("active")), dashboardRepository.requests)
    }

    @Test
    fun livePassInfoIncludesCheckInTeamAndLodgingForApprovedLiveCamping() = runTest {
        val activeCamping = camping(
            id = "active",
            start = Date(Date().time - 86_400_000),
            end = Date(Date().time + 86_400_000),
            attendees = listOf(
                attendee(
                    id = "user-1",
                    userId = "user-1",
                    status = RegistrationApprovalStatus.Approved,
                ),
            ),
        )
        val viewModel = HomeViewModel(
            campingService = FakeCampingService(initial = listOf(activeCamping)),
            dashboardRepository = FakeMainDashboardRepository(featuredCamping = activeCamping),
            scheduleService = FakeScheduleService(),
            announcementService = FakeAnnouncementService(mutableListOf()),
            checkInService = FakeCheckInService(
                records = listOf(
                    CheckInRecord(
                        campingId = activeCamping.id,
                        attendeeId = "user-1",
                        userId = "user-1",
                        displayName = "Camper",
                        method = CheckInMethod.Qr,
                        checkedInBy = "leader-1",
                    ),
                ),
            ),
            teamService = FakeTeamService(
                mutableListOf(
                    Team(
                        id = "team-1",
                        campingId = activeCamping.id,
                        name = "Trail Group 4",
                        members = listOf(
                            TeamMember(
                                id = "user-1",
                                userId = "user-1",
                                displayName = "Camper",
                                church = "Paris Central",
                            ),
                        ),
                    ),
                ),
            ),
            lodgingService = FakeLodgingService(
                initial = listOf(
                    LodgingUnit(
                        id = "cabin-7",
                        campingId = activeCamping.id,
                        name = "Cabin 7",
                        occupantIds = listOf("user-1"),
                    ),
                ),
            ),
        )

        viewModel.loadHome(forUserId = "user-1")
        advanceUntilIdle()

        val loaded = viewModel.uiState.value.phase as HomePhase.Loaded
        assertEquals("user-1", loaded.livePassInfo.checkInRecord?.attendeeId)
        assertEquals("Trail Group 4", loaded.livePassInfo.teamName)
        assertEquals("Cabin 7", loaded.livePassInfo.lodgingName)
    }

    private fun announcement(
        id: String,
        audienceScope: AnnouncementAudienceScope = AnnouncementAudienceScope.App,
        role: UserRole? = null,
        createdAt: Date,
    ) = Announcement(
        id = id,
        title = id,
        body = "Body for $id",
        audienceScopeRawValue = audienceScope.rawValue,
        notificationTargetRole = role,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun camping(
        id: String,
        start: Date,
        end: Date,
        status: CampingRegistrationStatus = CampingRegistrationStatus.Open,
        isFeatured: Boolean = false,
        attendees: List<CampingAttendee> = emptyList(),
    ) = Camping(
        id = id,
        title = id,
        description = "Description for $id",
        startDate = start,
        endDate = end,
        organizerLevel = OrganizerLevel(OrganizerType.Church, "Paris Central"),
        location = "Pine Valley",
        registrationStatus = status,
        isFeatured = isFeatured,
        attendees = attendees,
    )

    private fun attendee(
        id: String,
        userId: String,
        status: RegistrationApprovalStatus,
    ) = CampingAttendee(
        id = id,
        userId = userId,
        displayName = "Camper",
        church = "Paris Central",
        age = 16,
        languages = listOf("en"),
        registrationStatus = status,
        participantKind = RegistrationParticipantKind.SelfParticipant,
    )

    private class FakeMainDashboardRepository(
        private val featuredCamping: Camping?,
    ) : MainDashboardRepository {
        override fun observeFeaturedCamping(registeredCampingIds: Set<String>): Flow<Camping?> =
            flowOf(featuredCamping)

        override suspend fun loadFeaturedCamping(registeredCampingIds: Set<String>): Camping? =
            featuredCamping
    }

    private class RecordingDashboardRepository(
        private val select: (Set<String>) -> Camping?,
    ) : MainDashboardRepository {
        val requests = mutableListOf<Set<String>>()

        override fun observeFeaturedCamping(registeredCampingIds: Set<String>): Flow<Camping?> = flow {
            requests += registeredCampingIds
            emit(select(registeredCampingIds))
        }

        override suspend fun loadFeaturedCamping(registeredCampingIds: Set<String>): Camping? =
            select(registeredCampingIds)
    }
}
