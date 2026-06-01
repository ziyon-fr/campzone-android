package fr.ziyon.campzone.ui.guardian

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.camping.FakeCampingService
import fr.ziyon.campzone.data.family.FakeFamilyRepository
import fr.ziyon.campzone.data.guardian.FakeGuardianUpdatesService
import fr.ziyon.campzone.data.guardian.GuardianUpdatesData
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.CheckInMethod
import fr.ziyon.campzone.data.model.CheckInRecord
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import fr.ziyon.campzone.data.model.Team
import fr.ziyon.campzone.data.model.TeamMember
import fr.ziyon.campzone.data.model.WinnerRevealPolicy
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

class GuardianUpdatesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val guardian = user("guardian1")

    @Test
    fun emptyWhenNoChildrenAtCamp() {
        val vm = viewModel(camping = camping(attendees = emptyList()))

        vm.load("camp-1", guardian)

        assertTrue(vm.uiState.value is GuardianUpdatesUiState.Empty)
    }

    @Test
    fun surfacesRegisteredChild() {
        val vm = viewModel(camping = camping(attendees = listOf(child("kid1"))))

        vm.load("camp-1", guardian)

        val state = vm.uiState.value as GuardianUpdatesUiState.Loaded
        assertEquals(listOf("kid1"), state.children.map { it.id })
        assertFalse(state.children.first().isCheckedIn)
    }

    @Test
    fun reflectsCheckInRecord() {
        val vm = viewModel(
            camping = camping(attendees = listOf(child("kid1"))),
            data = GuardianUpdatesData(checkIns = listOf(checkIn("kid1"))),
        )

        vm.load("camp-1", guardian)

        val child = (vm.uiState.value as GuardianUpdatesUiState.Loaded).children.first()
        assertTrue(child.isCheckedIn)
    }

    @Test
    fun showsTeamScoreWhenRevealPolicyAllows() {
        val vm = viewModel(
            // ends far in the future → scores not yet hidden
            camping = camping(
                attendees = listOf(child("kid1")),
                endDate = Date(System.currentTimeMillis() + 30 * DAY_MILLIS),
            ),
            data = GuardianUpdatesData(teams = listOf(team("kid1", score = 42))),
        )

        vm.load("camp-1", guardian)

        val child = (vm.uiState.value as GuardianUpdatesUiState.Loaded).children.first()
        assertEquals("Eagles", child.team?.name)
        assertEquals(42, child.personalScore)
    }

    @Test
    fun hidesTeamScoreBeforeReveal() {
        val vm = viewModel(
            // ends within 24h and not revealed → scores hidden
            camping = camping(
                attendees = listOf(child("kid1")),
                endDate = Date(System.currentTimeMillis() + 60 * 60 * 1000),
            ),
            data = GuardianUpdatesData(teams = listOf(team("kid1", score = 42))),
        )

        vm.load("camp-1", guardian)

        val child = (vm.uiState.value as GuardianUpdatesUiState.Loaded).children.first()
        assertNull(child.personalScore)
    }

    @Test
    fun errorWhenCampingLoadFails() {
        val vm = GuardianUpdatesViewModel(
            campingService = FakeCampingService(shouldFail = true),
            familyRepository = FakeFamilyRepository(),
            guardianService = FakeGuardianUpdatesService(),
        )

        vm.load("camp-1", guardian)

        assertTrue(vm.uiState.value is GuardianUpdatesUiState.Error)
    }

    // --- builders ---

    private fun viewModel(
        camping: Camping,
        data: GuardianUpdatesData = GuardianUpdatesData(),
    ) = GuardianUpdatesViewModel(
        campingService = FakeCampingService(initial = listOf(camping)),
        familyRepository = FakeFamilyRepository(),
        guardianService = FakeGuardianUpdatesService(data),
    )

    private fun camping(
        attendees: List<CampingAttendee>,
        endDate: Date = Date(System.currentTimeMillis() + 30 * DAY_MILLIS),
    ) = Camping(
        id = "camp-1",
        title = "Summer Camp",
        description = "Fun",
        startDate = Date(endDate.time - 5 * DAY_MILLIS),
        endDate = endDate,
        organizerLevel = OrganizerLevel(OrganizerType.Church, "Paris Central SDA"),
        location = "Annecy",
        registrationStatus = CampingRegistrationStatus.Open,
        attendees = attendees,
        winnerRevealPolicy = WinnerRevealPolicy(),
    )

    private fun child(id: String) = CampingAttendee(
        id = id,
        userId = id,
        displayName = id,
        church = "Paris Central SDA",
        age = 9,
        languages = listOf("fr"),
        registrationStatus = RegistrationApprovalStatus.Approved,
        participantKind = RegistrationParticipantKind.Child,
        guardianId = "guardian1",
    )

    private fun checkIn(attendeeId: String) = CheckInRecord(
        campingId = "camp-1",
        attendeeId = attendeeId,
        userId = attendeeId,
        displayName = attendeeId,
        method = CheckInMethod.Qr,
        checkedInBy = "admin-1",
        checkedInAt = Date(),
    )

    private fun team(memberUserId: String, score: Int) = Team(
        id = "t1",
        campingId = "camp-1",
        name = "Eagles",
        members = listOf(
            TeamMember(id = memberUserId, userId = memberUserId, displayName = memberUserId, church = "Paris Central SDA", personalScore = score),
        ),
    )

    private fun user(uid: String) = AuthenticatedUser(
        uid = uid,
        email = "$uid@example.com",
        displayName = uid,
        photoUrl = null,
        role = UserRole.Adult,
        church = "Paris Central SDA",
        age = 38,
        preferredLanguage = "fr",
        gender = UserGender.Female,
        onboardingCompleted = true,
    )
}
