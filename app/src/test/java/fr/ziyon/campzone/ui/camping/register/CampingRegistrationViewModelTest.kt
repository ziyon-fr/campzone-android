package fr.ziyon.campzone.ui.camping.register

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.camping.FakeCampingService
import fr.ziyon.campzone.data.family.FakeFamilyRepository
import fr.ziyon.campzone.data.family.sampleChild
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.CampingTransportationOption
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import fr.ziyon.campzone.data.model.TransportationChoice
import fr.ziyon.campzone.data.model.TransportationMode
import fr.ziyon.campzone.data.notifications.RegistrationNotificationDispatcher
import fr.ziyon.campzone.data.notifications.RegistrationNotificationRequest
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CampingRegistrationViewModelTest {
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
    fun loadIncludesSelfAndFamilyForAdult() {
        val viewModel = viewModel(
            familyRepository = FakeFamilyRepository(mapOf("guardian-1" to listOf(sampleChild()))),
        )

        viewModel.load("camp-1", adult)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("guardian-1", "child-1"), state.participants.map { it.id })
        assertEquals(setOf("guardian-1"), state.selectedParticipantIds)
    }

    @Test
    fun submitSavesSelectedParticipantsAndDispatchesNotification() {
        val dispatcher = FakeRegistrationNotificationDispatcher()
        val service = FakeCampingService(initial = listOf(camping()))
        val viewModel = viewModel(
            service = service,
            familyRepository = FakeFamilyRepository(mapOf("guardian-1" to listOf(sampleChild()))),
            dispatcher = dispatcher,
        )
        var requiresPayment: Boolean? = null

        viewModel.load("camp-1", adult)
        viewModel.toggleParticipant("guardian-1")
        viewModel.toggleParticipant("child-1")
        viewModel.selectTransportationChoice("child-1", TransportationChoice.ProvidedBus)
        viewModel.submit(adult) { requiresPayment = it }

        assertEquals(false, requiresPayment)
        assertEquals(1, service.submitted.size)
        assertEquals("child-1", service.submitted.single().participant.id)
        assertEquals(TransportationChoice.ProvidedBus, service.submitted.single().transportationChoice)
        assertEquals(RegistrationParticipantKind.Child, service.submitted.single().participant.kind)
        assertEquals(1, dispatcher.requests.size)
        assertEquals(1, dispatcher.requests.single().participantCount)
    }

    @Test
    fun genericTicketedTransportationOptionCreatesProvidedBusSubmission() {
        val service = FakeCampingService(
            initial = listOf(
                camping(
                    transportationOptions = listOf(
                        CampingTransportationOption(
                            id = "coach-1",
                            name = "Coach from Paris",
                            mode = TransportationMode.Coach,
                            details = "",
                            requiresTicket = true,
                        ),
                    ),
                ),
            ),
        )
        val viewModel = viewModel(service = service)

        viewModel.load("camp-1", adult.copy(role = UserRole.User))
        viewModel.selectTransportationOption("guardian-1", "coach-1")
        viewModel.submit(adult.copy(role = UserRole.User)) {}

        val submission = service.submitted.single()
        assertEquals(TransportationChoice.ProvidedBus, submission.transportationChoice)
        assertEquals("coach-1", submission.transportationOptionId)
        assertEquals("Coach from Paris", submission.transportationOptionName)
    }

    @Test
    fun paidCampingReportsPaymentRequiredAfterSubmit() {
        val service = FakeCampingService(initial = listOf(camping(registrationFeeCents = 2500)))
        val viewModel = viewModel(service = service)
        var requiresPayment: Boolean? = null

        viewModel.load("camp-1", adult.copy(role = UserRole.User))
        viewModel.submit(adult.copy(role = UserRole.User)) { requiresPayment = it }

        assertEquals(true, requiresPayment)
    }

    private fun viewModel(
        service: FakeCampingService = FakeCampingService(initial = listOf(camping())),
        familyRepository: FakeFamilyRepository = FakeFamilyRepository(),
        dispatcher: FakeRegistrationNotificationDispatcher = FakeRegistrationNotificationDispatcher(),
    ) = CampingRegistrationViewModel(service, familyRepository, dispatcher)

    private fun camping(
        registrationFeeCents: Int? = null,
        transportationOptions: List<CampingTransportationOption> = emptyList(),
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
        transportationOptions = transportationOptions,
    )
}

private class FakeRegistrationNotificationDispatcher : RegistrationNotificationDispatcher {
    val requests = mutableListOf<RegistrationNotificationRequest>()

    override suspend fun dispatchRegistrationRequest(request: RegistrationNotificationRequest) {
        requests += request
    }
}
