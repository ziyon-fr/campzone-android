package fr.ziyon.campzone.ui.schedule

import fr.ziyon.campzone.data.camping.FakeCampingService
import fr.ziyon.campzone.data.model.CampDay
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.CampingSchedule
import fr.ziyon.campzone.data.model.DateKeys
import fr.ziyon.campzone.data.model.FoodMealKind
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.data.model.Program
import fr.ziyon.campzone.data.model.ProgramType
import fr.ziyon.campzone.data.schedule.FakeFoodMenuService
import fr.ziyon.campzone.data.schedule.FakeScheduleService
import fr.ziyon.campzone.testing.FakeStringProvider
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class ScheduleViewModelFoodMenuSyncTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun savingEditingAndDeletingMealProgramSyncsMenuEntry() = runTest {
        val campingId = "program-sync-camp"
        val dayDate = dateOf(2026, 8, 3)
        val day = CampDay(
            id = DateKeys.campDayId(campingId, dayDate),
            campingId = campingId,
            date = DateKeys.startOfDay(dayDate),
            title = "Day 1",
        )
        val foodMenuService = FakeFoodMenuService(mutableMapOf(campingId to mutableListOf()))
        val viewModel = ScheduleViewModel(
            scheduleService = FakeScheduleService(
                mutableMapOf(campingId to CampingSchedule(campingId = campingId, days = listOf(day))),
            ),
            campingService = FakeCampingService(listOf(camping(campingId, dayDate))),
            foodMenuService = foodMenuService,
            stringProvider = FakeStringProvider(),
        )

        viewModel.load(campingId)
        advanceUntilIdle()
        viewModel.prepareNewProgram(campingId, day.id)
        viewModel.updateEditorForm {
            it.copy(
                title = "Lunch",
                type = ProgramType.Lunch,
                startDate = dateOf(2026, 8, 3, 12, 45),
                endDate = dateOf(2026, 8, 3, 13, 45),
                location = "Dining hall",
                description = "- Chili\n- Rice\n\nNotes: Gluten-free",
            )
        }

        var lunchProgram: Program? = null
        viewModel.saveProgram(campingId) { lunchProgram = it }
        advanceUntilIdle()

        var entries = foodMenuService.loadMenu(campingId)
        assertEquals(listOf("2026-08-03-lunch"), entries.map { it.id })
        assertEquals(FoodMealKind.Lunch, entries.first().meal)
        assertEquals(listOf("Chili", "Rice"), entries.first().dishes)
        assertEquals("Gluten-free", entries.first().notes)

        assertNotNull(lunchProgram)
        viewModel.prepareEditingProgram(lunchProgram!!)
        viewModel.updateEditorForm {
            it.copy(
                title = "Dinner",
                type = ProgramType.Dinner,
                startDate = dateOf(2026, 8, 3, 18, 30),
                endDate = dateOf(2026, 8, 3, 19, 30),
                description = "- Soup\n- Bread",
            )
        }

        var dinnerProgram: Program? = null
        viewModel.saveProgram(campingId) { dinnerProgram = it }
        advanceUntilIdle()

        entries = foodMenuService.loadMenu(campingId)
        assertEquals(listOf("2026-08-03-dinner"), entries.map { it.id })
        assertEquals(FoodMealKind.Dinner, entries.first().meal)
        assertEquals(listOf("Soup", "Bread"), entries.first().dishes)

        assertNotNull(dinnerProgram)
        viewModel.deleteProgram(dinnerProgram!!.id, campingId)
        advanceUntilIdle()

        assertEquals(emptyList<String>(), foodMenuService.loadMenu(campingId).map { it.id })
    }

    private fun camping(campingId: String, startDate: Date): Camping =
        Camping(
            id = campingId,
            title = "Summer Camp",
            description = "Camp",
            startDate = DateKeys.startOfDay(startDate),
            endDate = DateKeys.startOfDay(Date(startDate.time + 2 * 86_400_000L)),
            organizerLevel = OrganizerLevel(OrganizerType.Church, "Paris Central SDA"),
            location = "Paris",
            registrationStatus = CampingRegistrationStatus.Open,
        )

    private fun dateOf(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
    ): Date =
        GregorianCalendar(TimeZone.getDefault()).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.time
}
