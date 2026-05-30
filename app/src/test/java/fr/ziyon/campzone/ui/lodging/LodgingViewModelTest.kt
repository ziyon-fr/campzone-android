package fr.ziyon.campzone.ui.lodging

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.camping.FakeCampingService
import fr.ziyon.campzone.data.lodging.FakeLodgingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.LodgingGenderPolicy
import fr.ziyon.campzone.data.model.LodgingKind
import fr.ziyon.campzone.data.model.LodgingUnit
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LodgingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val admin = user(uid = "admin-1", role = UserRole.Admin)

    @Test
    fun loadGatesManagementAndExposesUnitsAndApprovedAttendees() {
        val viewModel = viewModel(
            units = listOf(unit("t1", occupants = listOf("a1"))),
            attendees = listOf(
                attendee("a1", RegistrationApprovalStatus.Approved),
                attendee("p1", RegistrationApprovalStatus.Pending),
            ),
        )

        viewModel.load("camp-1", admin)

        val state = viewModel.uiState.value as LodgingUiState.Ready
        assertEquals(listOf("t1"), state.units.map { it.id })
        assertEquals(listOf("a1"), state.attendees.map { it.id }) // approved only
        assertEquals(setOf("a1"), state.assignedIds)
    }

    @Test
    fun nonManagerIsRestricted() {
        val viewModel = viewModel(units = emptyList(), attendees = emptyList())

        viewModel.load("camp-1", user(uid = "guest-1", role = UserRole.User))

        assertTrue(viewModel.uiState.value is LodgingUiState.Restricted)
    }

    @Test
    fun saveUnitAppearsInObservedList() {
        val viewModel = viewModel(units = emptyList(), attendees = emptyList())
        viewModel.load("camp-1", admin)

        viewModel.saveUnit(LodgingForm(name = "Tent Alpha", capacityText = "4", genderPolicy = LodgingGenderPolicy.Male))

        val state = viewModel.uiState.value as LodgingUiState.Ready
        assertEquals(listOf("Tent Alpha"), state.units.map { it.name })
        assertEquals(LodgingGenderPolicy.Male, state.units.first().genderPolicy)
    }

    @Test
    fun setOccupantsUpdatesTheUnit() {
        val viewModel = viewModel(
            units = listOf(unit("t1", occupants = emptyList())),
            attendees = listOf(attendee("a1", RegistrationApprovalStatus.Approved)),
        )
        viewModel.load("camp-1", admin)

        viewModel.setOccupants("t1", listOf("a1"))

        val state = viewModel.uiState.value as LodgingUiState.Ready
        assertEquals(listOf("a1"), state.units.first { it.id == "t1" }.occupantIds)
    }

    @Test
    fun filterRestrictsUnitsByGenderPolicy() {
        val viewModel = viewModel(
            units = listOf(
                unit("t1", policy = LodgingGenderPolicy.Male),
                unit("c1", policy = LodgingGenderPolicy.Family),
            ),
            attendees = emptyList(),
        )
        viewModel.load("camp-1", admin)

        viewModel.setFilter(LodgingGenderPolicy.Male)

        val state = viewModel.uiState.value as LodgingUiState.Ready
        assertEquals(listOf("t1"), state.filteredUnits.map { it.id })
        assertEquals(LodgingGenderPolicy.Male, state.filter)
    }

    private fun viewModel(units: List<LodgingUnit>, attendees: List<CampingAttendee>) =
        LodgingViewModel(
            lodgingService = FakeLodgingService(units),
            campingService = FakeCampingService(
                initial = listOf(camping()),
                attendeesByCamping = mapOf("camp-1" to attendees),
            ),
        )

    private fun camping() = Camping(
        id = "camp-1",
        title = "Summer Camp",
        description = "A week of fun",
        startDate = Date(1_000_000),
        endDate = Date(2_000_000),
        organizerLevel = OrganizerLevel(OrganizerType.Church, "Paris Central SDA"),
        location = "Annecy",
        registrationStatus = CampingRegistrationStatus.Open,
        createdByUid = "admin-1",
    )

    private fun unit(
        id: String,
        name: String = id,
        capacity: Int = 4,
        policy: LodgingGenderPolicy = LodgingGenderPolicy.Any,
        occupants: List<String> = emptyList(),
    ) = LodgingUnit(
        id = id,
        campingId = "camp-1",
        name = name,
        kind = LodgingKind.Tent,
        capacity = capacity,
        genderPolicy = policy,
        occupantIds = occupants,
    )

    private fun attendee(id: String, status: RegistrationApprovalStatus) = CampingAttendee(
        id = id,
        userId = id,
        displayName = id,
        church = "Paris Central SDA",
        age = 20,
        languages = listOf("fr"),
        registrationStatus = status,
        gender = UserGender.Male,
    )

    private fun user(uid: String, role: UserRole) = AuthenticatedUser(
        uid = uid,
        email = "$uid@example.com",
        displayName = uid,
        photoUrl = null,
        role = role,
        church = "Paris Central SDA",
        age = 40,
        preferredLanguage = "fr",
        gender = UserGender.Male,
        onboardingCompleted = true,
    )
}
