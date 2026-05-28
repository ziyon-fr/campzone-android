package fr.ziyon.campzone.ui.camping

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.camping.FakeCampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.TransportationPaymentStatus
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CampingDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val church = "Paris Central SDA"

    @Test
    fun leadershipSeesEntireRoster() = runTest {
        val service = service(attendees = listOf(attendee("a1", "Maria", RegistrationApprovalStatus.Approved), attendee("a2", "Joao", RegistrationApprovalStatus.Pending)))
        val viewModel = CampingDetailViewModel(service)

        viewModel.load("camp-1", user(role = UserRole.Leader, church = church, uid = "leader-1"))

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.canViewParticipantProfiles)
        assertEquals(2, state.visibleAttendees.size)
        assertEquals(1, state.approvedAttendeeCount)
    }

    @Test
    fun guestCannotViewAttendees() = runTest {
        val service = service(attendees = listOf(attendee("a1", "Maria", RegistrationApprovalStatus.Approved)))
        val viewModel = CampingDetailViewModel(service)

        viewModel.load("camp-1", user(role = UserRole.Guest, church = "Other", uid = "guest-1"))

        val state = viewModel.uiState.value
        assertFalse(state.canViewAttendees)
        assertTrue(state.visibleAttendees.isEmpty())
    }

    @Test
    fun pastorCanEditOwnChurchCampingDetail() = runTest {
        val service = service(attendees = emptyList())
        val viewModel = CampingDetailViewModel(service)

        viewModel.load("camp-1", user(role = UserRole.Pastor, church = church, uid = "pastor-1"))

        val state = viewModel.uiState.value
        assertTrue(state.canEditCamping)
        assertTrue(state.canManageSchedule)
        assertFalse(state.canApproveRegistrations)
        assertFalse(state.canManageTeams)
    }

    @Test
    fun youthDirectorCanEditOwnChurchCampingDetail() = runTest {
        val service = service(attendees = emptyList())
        val viewModel = CampingDetailViewModel(service)

        viewModel.load("camp-1", user(role = UserRole.YouthDirector, church = church, uid = "yd-1"))

        val state = viewModel.uiState.value
        assertTrue(state.canEditCamping)
        assertTrue(state.canApproveRegistrations)
        assertTrue(state.canManageTeams)
    }

    @Test
    fun ownChurchLeadersCannotEditAnotherChurchCampingDetail() = runTest {
        val service = service(
            attendees = emptyList(),
            organizerLevel = OrganizerLevel(OrganizerType.Church, "Lyon SDA"),
        )
        val viewModel = CampingDetailViewModel(service)

        viewModel.load("camp-1", user(role = UserRole.Pastor, church = church, uid = "pastor-1"))

        val state = viewModel.uiState.value
        assertFalse(state.canEditCamping)
        assertFalse(state.canManageSchedule)
    }

    @Test
    fun campingDetailPermissionsRecomputeWhenUserChanges() = runTest {
        val service = service(attendees = emptyList())
        val viewModel = CampingDetailViewModel(service)

        viewModel.load("camp-1", user(role = UserRole.Guest, church = "Other", uid = "guest-1"))
        assertFalse(viewModel.uiState.value.canEditCamping)

        viewModel.load("camp-1", user(role = UserRole.Pastor, church = church, uid = "pastor-1"))

        assertTrue(viewModel.uiState.value.canEditCamping)
    }

    @Test
    fun approvedParticipantSeesApprovedAttendees() = runTest {
        val service = service(
            attendees = listOf(
                attendee("user-1", "Me", RegistrationApprovalStatus.Approved, userId = "user-1"),
            ),
        )
        val viewModel = CampingDetailViewModel(service)

        viewModel.load("camp-1", user(role = UserRole.User, church = "Other", uid = "user-1"))

        val state = viewModel.uiState.value
        assertFalse(state.canViewParticipantProfiles)
        assertTrue(state.isApprovedParticipant)
        assertTrue(state.canViewAttendees)
        assertEquals(1, state.visibleAttendees.size)
    }

    @Test
    fun pendingPaidRegistrationSurfacesPaymentShortcut() = runTest {
        val service = service(
            attendees = listOf(
                attendee(
                    "user-1",
                    "Me",
                    RegistrationApprovalStatus.Pending,
                    userId = "user-1",
                    paymentStatus = TransportationPaymentStatus.Unpaid,
                ),
            ),
            registrationFeeCents = 12_00,
        )
        val viewModel = CampingDetailViewModel(service)

        viewModel.load("camp-1", user(role = UserRole.User, church = "Other", uid = "user-1"))

        assertTrue(viewModel.uiState.value.hasPendingRegistrationPayment)
    }

    @Test
    fun paidRegistrationDoesNotSurfacePaymentShortcut() = runTest {
        val service = service(
            attendees = listOf(
                attendee(
                    "user-1",
                    "Me",
                    RegistrationApprovalStatus.Pending,
                    userId = "user-1",
                    paymentStatus = TransportationPaymentStatus.Paid,
                ),
            ),
            registrationFeeCents = 12_00,
        )
        val viewModel = CampingDetailViewModel(service)

        viewModel.load("camp-1", user(role = UserRole.User, church = "Other", uid = "user-1"))

        assertFalse(viewModel.uiState.value.hasPendingRegistrationPayment)
    }

    @Test
    fun attendeeSearchFiltersRoster() = runTest {
        val service = service(
            attendees = listOf(
                attendee("a1", "Maria", RegistrationApprovalStatus.Approved),
                attendee("a2", "Joao", RegistrationApprovalStatus.Approved),
            ),
        )
        val viewModel = CampingDetailViewModel(service)
        viewModel.load("camp-1", user(role = UserRole.Leader, church = church, uid = "leader-1"))

        viewModel.updateAttendeeSearch("maria")
        assertEquals(1, viewModel.uiState.value.visibleAttendees.size)
    }

    @Test
    fun fetchFailureSurfacesError() = runTest {
        val viewModel = CampingDetailViewModel(FakeCampingService(emptyList()))
        viewModel.load("missing", user(role = UserRole.Guest, church = church, uid = "g"))

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
    }

    private fun service(
        attendees: List<CampingAttendee>,
        organizerLevel: OrganizerLevel = OrganizerLevel(OrganizerType.Church, church),
        createdByUid: String? = null,
        registrationFeeCents: Int? = null,
    ) = FakeCampingService(
        initial = listOf(
            Camping(
                id = "camp-1",
                title = "Summer Camp",
                description = "A week of fun",
                startDate = Date(1_000_000),
                endDate = Date(2_000_000),
                organizerLevel = organizerLevel,
                location = "Lake Annecy",
                registrationStatus = CampingRegistrationStatus.Open,
                participantCapacity = 120,
                registrationFeeCents = registrationFeeCents,
                createdByUid = createdByUid,
            ),
        ),
        attendeesByCamping = mapOf("camp-1" to attendees),
    )

    private fun attendee(
        id: String,
        name: String,
        status: RegistrationApprovalStatus,
        userId: String = id,
        paymentStatus: TransportationPaymentStatus = TransportationPaymentStatus.Unpaid,
    ) = CampingAttendee(
        id = id,
        userId = userId,
        displayName = name,
        church = church,
        age = 20,
        languages = listOf("fr"),
        registrationStatus = status,
        paymentStatus = paymentStatus,
    )

    private fun user(role: UserRole, church: String, uid: String) = AuthenticatedUser(
        uid = uid,
        email = "$uid@example.com",
        displayName = "User $uid",
        photoUrl = null,
        role = role,
        church = church,
        age = 30,
        preferredLanguage = "fr",
        gender = UserGender.PreferNotToSay,
        onboardingCompleted = true,
    )
}
