package fr.ziyon.campzone.ui.schedule.food

import fr.ziyon.campzone.data.camping.FakeCampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.FoodMealKind
import fr.ziyon.campzone.data.model.FoodMenuEntry
import fr.ziyon.campzone.data.model.FoodMenuItem
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.data.schedule.FakeFoodMenuService
import fr.ziyon.campzone.testing.FakeStringProvider
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FoodMenuViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun editAndSavePreservesStructuredDishFields() = runTest {
        val campingId = "camp-1"
        val entry = FoodMenuEntry(
            id = "1970-01-01-dinner",
            campingId = campingId,
            date = Date(1_000_000),
            meal = FoodMealKind.Dinner,
            items = listOf(
                FoodMenuItem(
                    id = "dish-1",
                    name = "Satay",
                    details = "Grilled skewers",
                    allergens = listOf("peanuts"),
                    note = "Serve warm",
                ),
            ),
        )
        val foodService = FakeFoodMenuService(
            mutableMapOf(campingId to mutableListOf(entry)),
        )
        val viewModel = FoodMenuViewModel(
            foodMenuService = foodService,
            campingService = FakeCampingService(initial = listOf(camping(campingId))),
            stringProvider = FakeStringProvider(),
        )

        viewModel.load(campingId)
        advanceUntilIdle()
        viewModel.prepareEdit(entry)
        val draft = viewModel.editorForm.value.items.single()
        assertEquals("Grilled skewers", draft.details)
        assertEquals(listOf("peanuts"), draft.allergens)

        viewModel.updateDish("dish-1") {
            it.copy(allergens = listOf("peanuts", "soy"), note = "Fresh batch")
        }
        var saved = false
        viewModel.saveEntry(campingId) { saved = true }
        advanceUntilIdle()

        val persisted = foodService.loadMenu(campingId).single().items.single()
        assertTrue(saved)
        assertEquals(listOf("peanuts", "soy"), persisted.allergens)
        assertEquals("Fresh batch", persisted.note)
        assertEquals("Menu saved.", viewModel.operationMessage.value)
    }

    @Test
    fun emptyDishFormUsesLocalizedValidationMessage() = runTest {
        val campingId = "camp-1"
        val viewModel = FoodMenuViewModel(
            foodMenuService = FakeFoodMenuService(),
            campingService = FakeCampingService(initial = listOf(camping(campingId))),
            stringProvider = FakeStringProvider(),
        )
        viewModel.prepareNew(campingId)

        var saved = false
        viewModel.saveEntry(campingId) { saved = true }

        assertFalse(saved)
        assertEquals("Add at least one dish.", viewModel.operationError.value)
        assertTrue(viewModel.editorForm.value.items.isEmpty())
    }

    private fun camping(id: String) = Camping(
        id = id,
        title = "Summer Camp",
        description = "Camp",
        startDate = Date(0),
        endDate = Date(86_400_000),
        organizerLevel = OrganizerLevel(OrganizerType.Church, "Paris"),
        location = "Paris",
        registrationStatus = CampingRegistrationStatus.Open,
        participantCapacity = 100,
    )
}
