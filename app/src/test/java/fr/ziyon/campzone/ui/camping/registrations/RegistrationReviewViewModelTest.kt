package fr.ziyon.campzone.ui.camping.registrations

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.CampingAgeGroup
import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.camping.FakeCampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationReviewViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun reviewQueueShowsOnlyScopedCampingsWithPendingOrWaitlistedAttendees() = runTest {
        val service = FakeCampingService(
            initial = listOf(
                camping("camp-1", organizerChurch = parisChurch),
                camping("camp-2", organizerChurch = parisChurch),
                camping("camp-3", organizerChurch = lyonChurch),
            ),
            attendeesByCamping = mapOf(
                "camp-1" to listOf(
                    attendee("pending-1", RegistrationApprovalStatus.Pending),
                    attendee("waitlist-1", RegistrationApprovalStatus.Waitlisted),
                ),
                "camp-2" to listOf(attendee("approved-1", RegistrationApprovalStatus.Approved)),
                "camp-3" to listOf(attendee("pending-2", RegistrationApprovalStatus.Pending, church = lyonChurch)),
            ),
        )
        val viewModel = RegistrationReviewViewModel(service)

        viewModel.load(user(UserRole.Leader, uid = "leader-1", church = parisChurch))

        val phase = viewModel.uiState.value.phase
        assertTrue(phase is RegistrationReviewPhase.Loaded)
        val campings = (phase as RegistrationReviewPhase.Loaded).campings
        assertEquals(listOf("camp-1"), campings.map { it.id })
        assertEquals(1, campings.single().pendingAttendees.size)
        assertEquals(1, campings.single().waitlistedAttendees.size)
    }

    @Test
    fun reviewQueueIsRestrictedWithoutApprovalPermission() = runTest {
        val viewModel = RegistrationReviewViewModel(
            FakeCampingService(initial = listOf(camping("camp-1"))),
        )

        viewModel.load(user(UserRole.User, uid = "user-1", church = parisChurch))

        assertTrue(viewModel.uiState.value.phase is RegistrationReviewPhase.Restricted)
    }

    @Test
    fun approvingRegistrationWritesStatusAndRefreshesQueue() = runTest {
        val service = FakeCampingService(
            initial = listOf(camping("camp-1", organizerChurch = parisChurch)),
            attendeesByCamping = mapOf(
                "camp-1" to listOf(attendee("pending-1", RegistrationApprovalStatus.Pending)),
            ),
        )
        val viewModel = RegistrationReviewViewModel(service)
        viewModel.load(user(UserRole.YouthDirector, uid = "yd-1", church = parisChurch))

        viewModel.updateRegistration(
            campingId = "camp-1",
            attendeeId = "pending-1",
            status = RegistrationApprovalStatus.Approved,
        )

        assertEquals(listOf("pending-1" to RegistrationApprovalStatus.Approved), service.reviewed)
        assertTrue(viewModel.uiState.value.phase is RegistrationReviewPhase.Empty)
        assertFalse(viewModel.uiState.value.isSaving)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CampingAttendeesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun leaderSeesFullRosterAndProfileAccess() = runTest {
        val viewModel = CampingAttendeesViewModel(
            service = service(
                attendees = listOf(
                    attendee("approved-1", RegistrationApprovalStatus.Approved),
                    attendee("pending-1", RegistrationApprovalStatus.Pending),
                    attendee("rejected-1", RegistrationApprovalStatus.Rejected),
                ),
            ),
        )

        viewModel.load("camp-1", user(UserRole.Leader, uid = "leader-1", church = parisChurch))

        val state = viewModel.uiState.value
        assertTrue(state.canViewAttendees)
        assertTrue(state.canViewProfiles)
        assertEquals(listOf("approved-1", "pending-1", "rejected-1"), state.visibleAttendees.map { it.id })
    }

    @Test
    fun approvedParticipantSeesOnlyApprovedAttendees() = runTest {
        val viewModel = CampingAttendeesViewModel(
            service = service(
                attendees = listOf(
                    attendee(
                        id = "participant-1",
                        status = RegistrationApprovalStatus.Approved,
                        userId = "participant-1",
                    ),
                    attendee("pending-1", RegistrationApprovalStatus.Pending),
                ),
            ),
        )

        viewModel.load("camp-1", user(UserRole.User, uid = "participant-1", church = lyonChurch))

        val state = viewModel.uiState.value
        assertTrue(state.canViewAttendees)
        assertFalse(state.canViewProfiles)
        assertEquals(listOf("participant-1"), state.visibleAttendees.map { it.id })
    }

    @Test
    fun guestWithoutApprovedRegistrationCannotSeeRoster() = runTest {
        val viewModel = CampingAttendeesViewModel(
            service = service(attendees = listOf(attendee("approved-1", RegistrationApprovalStatus.Approved))),
        )

        viewModel.load("camp-1", user(UserRole.User, uid = "user-1", church = lyonChurch))

        val state = viewModel.uiState.value
        assertFalse(state.canViewAttendees)
        assertTrue(state.visibleAttendees.isEmpty())
    }

    @Test
    fun attendeeSearchAndFiltersNarrowVisibleRoster() = runTest {
        val viewModel = CampingAttendeesViewModel(
            service = service(
                attendees = listOf(
                    attendee(
                        id = "kid-1",
                        status = RegistrationApprovalStatus.Approved,
                        name = "Ana Lyon",
                        church = lyonChurch,
                        age = 11,
                        languages = listOf("fr"),
                    ),
                    attendee(
                        id = "adult-1",
                        status = RegistrationApprovalStatus.Approved,
                        name = "Ana Paris",
                        church = parisChurch,
                        age = 41,
                        languages = listOf("pt"),
                    ),
                ),
            ),
        )
        viewModel.load("camp-1", user(UserRole.Leader, uid = "leader-1", church = parisChurch))

        viewModel.updateSearch("ana")
        val loaded = viewModel.uiState.value
        assertEquals(listOf(lyonChurch, parisChurch), loaded.availableChurches)
        assertEquals(listOf("fr", "pt"), loaded.availableLanguages)

        viewModel.updateFilters(
            AttendeeFilters(church = lyonChurch, ageGroup = CampingAgeGroup.Kids, language = "fr"),
        )

        assertEquals(listOf("kid-1"), viewModel.uiState.value.visibleAttendees.map { it.id })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AttendeeProfileViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun leaderCanViewProfileAndUpdateStatus() = runTest {
        val service = service(
            attendees = listOf(attendee("pending-1", RegistrationApprovalStatus.Pending)),
        )
        val viewModel = AttendeeProfileViewModel(service)

        viewModel.load(
            campingId = "camp-1",
            attendeeId = "pending-1",
            user = user(UserRole.Leader, uid = "leader-1", church = parisChurch),
        )
        viewModel.updateStatus(RegistrationApprovalStatus.Approved)

        val state = viewModel.uiState.value
        assertTrue(state.canViewProfile)
        assertTrue(state.canRemoveAttendee)
        assertEquals(RegistrationApprovalStatus.Approved, state.attendee?.registrationStatus)
        assertEquals(listOf("pending-1" to RegistrationApprovalStatus.Approved), service.reviewed)
    }

    @Test
    fun userWithoutProfilePermissionIsRestricted() = runTest {
        val viewModel = AttendeeProfileViewModel(
            service(attendees = listOf(attendee("approved-1", RegistrationApprovalStatus.Approved))),
        )

        viewModel.load(
            campingId = "camp-1",
            attendeeId = "approved-1",
            user = user(UserRole.User, uid = "user-1", church = lyonChurch),
        )

        assertTrue(viewModel.uiState.value.isRestricted)
        assertFalse(viewModel.uiState.value.canViewProfile)
    }

    @Test
    fun deleteAttendeeDelegatesToServiceAndReportsDeletion() = runTest {
        val service = service(attendees = listOf(attendee("approved-1", RegistrationApprovalStatus.Approved)))
        val viewModel = AttendeeProfileViewModel(service)
        var deleted = false
        viewModel.load(
            campingId = "camp-1",
            attendeeId = "approved-1",
            user = user(UserRole.YouthDirector, uid = "yd-1", church = parisChurch),
        )

        viewModel.deleteAttendee { deleted = true }

        assertTrue(deleted)
        assertEquals(listOf("approved-1"), service.deletedAttendees)
        assertEquals(null, viewModel.uiState.value.attendee)
    }
}

private const val parisChurch = "Paris Central SDA"
private const val lyonChurch = "Lyon SDA"

private fun service(
    attendees: List<CampingAttendee>,
    organizerChurch: String = parisChurch,
): FakeCampingService = FakeCampingService(
    initial = listOf(camping("camp-1", organizerChurch = organizerChurch)),
    attendeesByCamping = mapOf("camp-1" to attendees),
)

private fun camping(
    id: String,
    organizerChurch: String = parisChurch,
    participantCapacity: Int? = 120,
): Camping = Camping(
    id = id,
    title = "Summer Camp $id",
    description = "A week of fun",
    startDate = Date(1_000_000),
    endDate = Date(2_000_000),
    organizerLevel = OrganizerLevel(OrganizerType.Church, organizerChurch),
    location = "Annecy",
    registrationStatus = CampingRegistrationStatus.Open,
    participantCapacity = participantCapacity,
)

private fun attendee(
    id: String,
    status: RegistrationApprovalStatus,
    name: String = "Participant $id",
    church: String = parisChurch,
    age: Int = 20,
    languages: List<String> = listOf("fr"),
    userId: String = id,
    participantKind: RegistrationParticipantKind = RegistrationParticipantKind.SelfParticipant,
): CampingAttendee = CampingAttendee(
    id = id,
    userId = userId,
    displayName = name,
    church = church,
    age = age,
    languages = languages,
    registrationStatus = status,
    participantKind = participantKind,
)

private fun user(
    role: UserRole,
    uid: String,
    church: String,
): AuthenticatedUser = AuthenticatedUser(
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
