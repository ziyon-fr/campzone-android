package fr.ziyon.campzone.ui.transportation

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
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import fr.ziyon.campzone.data.model.TransportationBoardingStatus
import fr.ziyon.campzone.data.model.TransportationBooking
import fr.ziyon.campzone.data.model.TransportationPaymentStatus
import fr.ziyon.campzone.data.model.TransportationScanResult
import fr.ziyon.campzone.data.model.TransportationTicketPayload
import fr.ziyon.campzone.data.transportation.FakeTransportationService
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransportationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun ticketsLoadUserAndGuardianBookings() = runTest {
        val service = FakeTransportationService(
            listOf(
                booking(id = "self-bus", participantName = "Ana", userId = "user-1"),
                booking(id = "child-bus", participantName = "Ben", userId = "child-1", guardianId = "user-1"),
                booking(id = "other-bus", participantName = "Cara", userId = "other"),
            ),
        )
        val viewModel = viewModel(service)

        viewModel.loadTickets("camp-1", user(uid = "user-1"))
        advanceUntilIdle()

        assertEquals(TransportationUiState.Ready, viewModel.uiState.value)
        assertEquals(listOf("Ana", "Ben"), viewModel.bookings.value.map { it.participantName })
    }

    @Test
    fun scanMarksPaidApprovedBookingAsBoarded() = runTest {
        val booking = booking(paymentStatus = TransportationPaymentStatus.Paid)
        val service = FakeTransportationService(listOf(booking))
        val viewModel = viewModel(service)
        viewModel.loadScanner("camp-1", user(role = UserRole.Admin, uid = "marshal-1"))
        advanceUntilIdle()

        viewModel.handleScan(qr(booking), now = Date(2_000))
        advanceUntilIdle()

        val result = viewModel.lastScanResult.value
        assertTrue(result is TransportationScanResult.Success)
        val updated = (result as TransportationScanResult.Success).booking
        assertEquals(TransportationBoardingStatus.Boarded, updated.boardingStatus)
        assertEquals("marshal-1", updated.boardedBy)
        assertEquals(TransportationBoardingStatus.Boarded, service.booking("camp-1", booking.id).boardingStatus)
    }

    @Test
    fun scanRejectsWrongCamping() = runTest {
        val booking = booking(paymentStatus = TransportationPaymentStatus.Paid)
        val service = FakeTransportationService(listOf(booking))
        val viewModel = viewModel(service)
        viewModel.loadScanner("camp-1", user(role = UserRole.Admin))
        advanceUntilIdle()

        viewModel.handleScan(
            TransportationTicketPayload.fromBooking(booking.copy(campingId = "other-camp")).encoded(),
            now = Date(2_000),
        )
        advanceUntilIdle()

        assertEquals(TransportationScanResult.WrongCamping, viewModel.lastScanResult.value)
        assertEquals(TransportationBoardingStatus.NotBoarded, service.booking("camp-1", booking.id).boardingStatus)
    }

    @Test
    fun scanRejectsTokenMismatch() = runTest {
        val booking = booking(paymentStatus = TransportationPaymentStatus.Paid)
        val service = FakeTransportationService(listOf(booking))
        val viewModel = viewModel(service)
        viewModel.loadScanner("camp-1", user(role = UserRole.Admin))
        advanceUntilIdle()

        viewModel.handleScan(qr(booking.copy(ticketToken = "forged-token")), now = Date(2_000))
        advanceUntilIdle()

        assertEquals(TransportationScanResult.TokenMismatch, viewModel.lastScanResult.value)
        assertEquals(TransportationBoardingStatus.NotBoarded, service.booking("camp-1", booking.id).boardingStatus)
    }

    @Test
    fun scanRejectsUnapprovedRegistration() = runTest {
        val booking = booking(paymentStatus = TransportationPaymentStatus.Paid)
        val service = FakeTransportationService(listOf(booking))
        val viewModel = viewModel(
            transportationService = service,
            attendees = listOf(attendee(RegistrationApprovalStatus.Pending)),
        )
        viewModel.loadScanner("camp-1", user(role = UserRole.Admin))
        advanceUntilIdle()

        viewModel.handleScan(qr(booking), now = Date(2_000))
        advanceUntilIdle()

        assertEquals(TransportationScanResult.RegistrationNotApproved, viewModel.lastScanResult.value)
        assertEquals(TransportationBoardingStatus.NotBoarded, service.booking("camp-1", booking.id).boardingStatus)
    }

    @Test
    fun scanRejectsUnpaidBooking() = runTest {
        val booking = booking(paymentStatus = TransportationPaymentStatus.Unpaid)
        val service = FakeTransportationService(listOf(booking))
        val viewModel = viewModel(service)
        viewModel.loadScanner("camp-1", user(role = UserRole.Admin))
        advanceUntilIdle()

        viewModel.handleScan(qr(booking), now = Date(2_000))
        advanceUntilIdle()

        assertTrue(viewModel.lastScanResult.value is TransportationScanResult.Unpaid)
        assertEquals(TransportationBoardingStatus.NotBoarded, service.booking("camp-1", booking.id).boardingStatus)
    }

    @Test
    fun scanRejectsExpiredBooking() = runTest {
        val booking = booking(
            paymentStatus = TransportationPaymentStatus.Paid,
            validFrom = Date(1_000),
            validUntil = Date(1_500),
        )
        val service = FakeTransportationService(listOf(booking))
        val viewModel = viewModel(service)
        viewModel.loadScanner("camp-1", user(role = UserRole.Admin))
        advanceUntilIdle()

        viewModel.handleScan(qr(booking), now = Date(2_000))
        advanceUntilIdle()

        assertEquals(TransportationScanResult.Expired, viewModel.lastScanResult.value)
        assertEquals(TransportationBoardingStatus.NotBoarded, service.booking("camp-1", booking.id).boardingStatus)
    }

    @Test
    fun repeatedScanAfterDismissReportsAlreadyBoarded() = runTest {
        val booking = booking(paymentStatus = TransportationPaymentStatus.Paid)
        val service = FakeTransportationService(listOf(booking))
        val viewModel = viewModel(service)
        viewModel.loadScanner("camp-1", user(role = UserRole.Admin))
        advanceUntilIdle()

        val qr = qr(booking)
        viewModel.handleScan(qr, now = Date(2_000))
        advanceUntilIdle()
        viewModel.dismissScanResult()
        viewModel.handleScan(qr, now = Date(2_000))
        advanceUntilIdle()

        assertTrue(viewModel.lastScanResult.value is TransportationScanResult.AlreadyBoarded)
    }

    @Test
    fun restrictedUserCannotScan() = runTest {
        val booking = booking(paymentStatus = TransportationPaymentStatus.Paid)
        val service = FakeTransportationService(listOf(booking))
        val viewModel = viewModel(
            transportationService = service,
            camping = camping(createdByUid = "someone-else"),
        )
        viewModel.loadScanner("camp-1", user(role = UserRole.User, uid = "plain-user"))
        advanceUntilIdle()

        assertEquals(TransportationUiState.Restricted, viewModel.uiState.value)
        viewModel.handleScan(qr(booking), now = Date(2_000))
        advanceUntilIdle()

        assertNull(viewModel.lastScanResult.value)
        assertEquals(TransportationBoardingStatus.NotBoarded, service.booking("camp-1", booking.id).boardingStatus)
    }

    private fun viewModel(
        transportationService: FakeTransportationService,
        camping: Camping = camping(),
        attendees: List<CampingAttendee> = listOf(attendee(RegistrationApprovalStatus.Approved)),
    ): TransportationViewModel = TransportationViewModel(
        transportationService = transportationService,
        campingService = FakeCampingService(
            initial = listOf(camping),
            attendeesByCamping = mapOf(camping.id to attendees),
        ),
    )

    private fun qr(booking: TransportationBooking): String =
        TransportationTicketPayload.fromBooking(booking).encoded()

    private fun booking(
        id: String = "participant-1-bus",
        participantName: String = "Maria",
        userId: String = "participant-1",
        guardianId: String? = null,
        paymentStatus: TransportationPaymentStatus = TransportationPaymentStatus.Unpaid,
        boardingStatus: TransportationBoardingStatus = TransportationBoardingStatus.NotBoarded,
        validFrom: Date = Date(1_000),
        validUntil: Date = Date(5_000),
    ) = TransportationBooking(
        id = id,
        campingId = "camp-1",
        registrationId = id.removeSuffix("-bus"),
        participantId = id.removeSuffix("-bus"),
        participantKind = if (guardianId == null) {
            RegistrationParticipantKind.SelfParticipant
        } else {
            RegistrationParticipantKind.Child
        },
        participantName = participantName,
        userId = userId,
        guardianId = guardianId,
        ticketToken = "token-$id",
        validFrom = validFrom,
        validUntil = validUntil,
        paymentStatus = paymentStatus,
        boardingStatus = boardingStatus,
    )

    private fun camping(createdByUid: String? = null) = Camping(
        id = "camp-1",
        title = "Summer Camp",
        description = "A week of fun",
        startDate = Date(1_000),
        endDate = Date(5_000),
        organizerLevel = OrganizerLevel(OrganizerType.Church, "Paris Central SDA"),
        location = "Lake Annecy",
        registrationStatus = CampingRegistrationStatus.Open,
        createdByUid = createdByUid,
    )

    private fun attendee(status: RegistrationApprovalStatus) = CampingAttendee(
        id = "participant-1",
        userId = "participant-1",
        displayName = "Maria",
        church = "Paris Central SDA",
        age = 20,
        languages = listOf("fr"),
        registrationStatus = status,
    )

    private fun user(
        role: UserRole = UserRole.User,
        uid: String = "user-1",
    ) = AuthenticatedUser(
        uid = uid,
        email = "$uid@example.com",
        displayName = "User $uid",
        photoUrl = null,
        role = role,
        church = "Paris Central SDA",
        age = 30,
        preferredLanguage = "fr",
        gender = UserGender.PreferNotToSay,
        onboardingCompleted = true,
    )
}
