package fr.ziyon.campzone.ui.camping.pricing

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.camping.FakeCampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingPriceItem
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import fr.ziyon.campzone.data.model.TransportationPaymentStatus
import fr.ziyon.campzone.data.payments.FakePaymentProofService
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CampingPricingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val user = AuthenticatedUser(
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
    fun loadFoldsUnpaidRegistrationFeesAndPriceItems() {
        val viewModel = CampingPricingViewModel(
            FakeCampingService(
                initial = listOf(camping(priceItems = listOf(priceItem()))),
                attendeesByCamping = mapOf(
                    "camp-1" to listOf(
                        attendee(id = "guardian-1", userId = "guardian-1"),
                        attendee(
                            id = "child-1",
                            userId = "child-1",
                            guardianId = "guardian-1",
                            kind = RegistrationParticipantKind.Child,
                            age = 11,
                        ),
                        attendee(id = "paid", userId = "guardian-1", paymentStatus = TransportationPaymentStatus.Paid),
                        attendee(id = "stranger", userId = "someone-else"),
                    ),
                ),
            ),
            FakePaymentProofService(),
        )

        viewModel.load("camp-1", user)

        val state = viewModel.uiState.value as CampingPricingUiState.Loaded
        // Only the signed-in user's own unpaid, fee-bearing registrations.
        assertEquals(listOf("guardian-1", "child-1"), state.registrationFees.map { it.attendeeId })
        assertEquals(listOf("eur", "eur"), state.registrationFees.map { it.currency })
        assertTrue(state.registrationFees.first { it.attendeeId == "guardian-1" }.isSelf)
        assertEquals(listOf("lodging"), state.priceItems.map { it.id })
        assertEquals(false, state.isEmpty)
    }

    @Test
    fun loadWithoutFeesOrItemsIsEmpty() {
        val viewModel = CampingPricingViewModel(
            FakeCampingService(
                initial = listOf(camping(registrationFeeCents = null, priceItems = emptyList())),
                attendeesByCamping = mapOf(
                    "camp-1" to listOf(attendee(id = "guardian-1", userId = "guardian-1")),
                ),
            ),
            FakePaymentProofService(),
        )

        viewModel.load("camp-1", user)

        val state = viewModel.uiState.value as CampingPricingUiState.Loaded
        assertTrue(state.isEmpty)
    }

    private fun camping(
        registrationFeeCents: Int? = 2500,
        priceItems: List<CampingPriceItem> = emptyList(),
    ) = Camping(
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
        priceItems = priceItems,
    )

    private fun priceItem() = CampingPriceItem(
        id = "lodging",
        name = "Lodging upgrade",
        details = "Private cabin",
        amountCents = 6000,
        currency = "EUR",
    )

    private fun attendee(
        id: String,
        userId: String,
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
