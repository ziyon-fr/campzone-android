package fr.ziyon.campzone.ui.payments

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.camping.FakeCampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingAgePrice
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.data.model.PaymentKind
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import fr.ziyon.campzone.data.model.TransportationPaymentStatus
import fr.ziyon.campzone.data.payments.PaymentConfirmation
import fr.ziyon.campzone.data.payments.PaymentRequest
import fr.ziyon.campzone.data.payments.PaymentService
import fr.ziyon.campzone.data.payments.PaymentSheetIntent
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RegistrationPaymentViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val adult = AuthenticatedUser(
        uid = "guardian-1",
        email = "guardian@example.com",
        displayName = "Maria Santos",
        photoUrl = null,
        role = UserRole.Adult,
        church = "Paris Central SDA",
        age = 39,
        preferredLanguage = "fr",
        gender = UserGender.Female,
        onboardingCompleted = true,
    )

    @Test
    fun loadBuildsPendingRegistrationPaymentRequestsForSelfAndFamily() {
        val viewModel = viewModel(
            service = FakeCampingService(
                initial = listOf(camping()),
                attendeesByCamping = mapOf(
                    "camp-1" to listOf(
                        attendee(id = "guardian-1", userId = "guardian-1", age = 39),
                        attendee(
                            id = "child-1",
                            userId = "child-1",
                            guardianId = "guardian-1",
                            kind = RegistrationParticipantKind.Child,
                            age = 12,
                        ),
                        attendee(id = "already-paid", userId = "guardian-1", paymentStatus = TransportationPaymentStatus.Paid),
                        attendee(id = "waitlisted", userId = "guardian-1", status = RegistrationApprovalStatus.Waitlisted),
                    ),
                ),
            ),
        )

        viewModel.load("camp-1", adult)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("guardian-1", "child-1"), state.items.map { it.participantId })
        assertEquals(listOf(3200, 1800), state.items.map { it.request.amountCents })
        assertTrue(state.items.all { it.request.kind == PaymentKind.Registration })
    }

    @Test
    fun prepareAndConfirmCurrentPaymentAdvancesQueue() {
        val paymentService = FakePaymentService()
        val viewModel = viewModel(
            service = FakeCampingService(
                initial = listOf(camping(registrationFeeCents = 2500)),
                attendeesByCamping = mapOf(
                    "camp-1" to listOf(
                        attendee(id = "guardian-1", userId = "guardian-1", age = 39),
                        attendee(
                            id = "child-1",
                            userId = "child-1",
                            guardianId = "guardian-1",
                            kind = RegistrationParticipantKind.Child,
                            age = 10,
                        ),
                    ),
                ),
            ),
            paymentService = paymentService,
        )

        viewModel.load("camp-1", adult)
        viewModel.prepareCurrentPayment()

        val prepared = viewModel.uiState.value.preparedPayment!!
        assertEquals("guardian-1", prepared.item.participantId)
        assertEquals(listOf("guardian-1"), paymentService.created.map { it.referenceId })

        viewModel.markPaymentSheetPresented()
        assertTrue(viewModel.uiState.value.preparedPayment!!.hasBeenPresented)

        viewModel.confirmPreparedPayment(adult)

        assertEquals(listOf("guardian-1"), paymentService.confirmedReferences)
        assertEquals(listOf("child-1"), viewModel.uiState.value.items.map { it.participantId })
        assertEquals("Payment completed. Continue with the next participant.", viewModel.uiState.value.successMessage)
    }

    private fun viewModel(
        service: FakeCampingService = FakeCampingService(
            initial = listOf(camping()),
            attendeesByCamping = mapOf("camp-1" to listOf(attendee())),
        ),
        paymentService: FakePaymentService = FakePaymentService(),
    ) = RegistrationPaymentViewModel(service, paymentService)

    private class FakePaymentService : PaymentService {
        val created = mutableListOf<PaymentRequest>()
        val confirmedReferences = mutableListOf<String>()

        override suspend fun createPaymentIntent(request: PaymentRequest): PaymentSheetIntent {
            created += request
            return PaymentSheetIntent(
                paymentIntentId = "pi_${request.referenceId}",
                paymentIntentClientSecret = "secret_${request.referenceId}",
                ephemeralKeySecret = "ek_${request.referenceId}",
                customerId = "cus_123",
                publishableKey = "pk_test_123",
                amountCents = request.amountCents,
                currency = request.currency,
            )
        }

        override suspend fun confirmPayment(
            paymentIntentId: String,
            kind: PaymentKind,
            campingId: String?,
            referenceId: String,
        ): PaymentConfirmation {
            confirmedReferences += referenceId
            return PaymentConfirmation(
                paid = true,
                status = "succeeded",
                kind = kind,
                campingId = campingId,
                referenceId = referenceId,
            )
        }
    }

    private companion object {
        fun camping(registrationFeeCents: Int? = null) = Camping(
            id = "camp-1",
            title = "Summer Camp",
            description = "A week of fun",
            startDate = Date(1_000_000),
            endDate = Date(2_000_000),
            organizerLevel = OrganizerLevel(OrganizerType.Church, "Paris Central SDA"),
            location = "Annecy",
            registrationStatus = CampingRegistrationStatus.Open,
            registrationFeeCents = registrationFeeCents,
            feeCurrency = "EUR",
            agePrices = listOf(
                CampingAgePrice(id = "child", label = "Child", minAge = 0, maxAge = 12, amountCents = 1800),
                CampingAgePrice(id = "adult", label = "Adult", minAge = 13, amountCents = 3200),
            ),
        )

        fun attendee(
            id: String = "guardian-1",
            userId: String = id,
            guardianId: String? = null,
            kind: RegistrationParticipantKind = RegistrationParticipantKind.SelfParticipant,
            age: Int = 39,
            status: RegistrationApprovalStatus = RegistrationApprovalStatus.Pending,
            paymentStatus: TransportationPaymentStatus = TransportationPaymentStatus.Unpaid,
        ) = CampingAttendee(
            id = id,
            userId = userId,
            displayName = id,
            church = "Paris Central SDA",
            age = age,
            languages = listOf("fr"),
            registrationStatus = status,
            preferredLanguage = "fr",
            participantKind = kind,
            guardianId = guardianId,
            paymentStatus = paymentStatus,
        )
    }
}
