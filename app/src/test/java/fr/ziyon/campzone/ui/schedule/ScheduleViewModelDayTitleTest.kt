package fr.ziyon.campzone.ui.schedule

import fr.ziyon.campzone.data.camping.FakeCampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.CampingSchedule
import fr.ziyon.campzone.data.model.CustomProgramType
import fr.ziyon.campzone.data.model.DateKeys
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelDayTitleTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun saveDayTitleAddsCustomTitleThenBlankClearsToDefault() = runTest {
        val campingId = "day-title-camp"
        val dayDate = dateOf(2026, 8, 3)
        val dayId = DateKeys.campDayId(campingId, dayDate)
        val viewModel = viewModel(
            camping = camping(campingId, dayDate),
            schedule = CampingSchedule(campingId = campingId),
        )

        viewModel.load(campingId)
        advanceUntilIdle()

        val defaultDay = viewModel.schedule(campingId)!!.sortedDays.first()
        assertEquals(dayId, defaultDay.id)
        assertEquals("Day 1", defaultDay.title)
        assertFalse(defaultDay.hasCustomTitle)

        viewModel.saveDayTitle(" Arrival Day ", dayId, campingId)
        advanceUntilIdle()

        val renamedDay = viewModel.schedule(campingId)!!.sortedDays.first()
        assertEquals("Arrival Day", renamedDay.title)
        assertTrue(renamedDay.hasCustomTitle)
        assertEquals("Day name saved.", viewModel.operationMessage.value)

        viewModel.saveDayTitle("   ", dayId, campingId)
        advanceUntilIdle()

        val resetDay = viewModel.schedule(campingId)!!.sortedDays.first()
        assertEquals(dayId, resetDay.id)
        assertEquals("Day 1", resetDay.title)
        assertFalse(resetDay.hasCustomTitle)
    }

    @Test
    fun customProgramRequiresPersonalizedTypeAndReusesSavedType() = runTest {
        val campingId = "custom-type-camp"
        val dayDate = dateOf(2026, 8, 3)
        val schedule = CampingSchedule(campingId = campingId)
        val viewModel = viewModel(
            camping = camping(campingId, dayDate),
            schedule = schedule,
        )

        viewModel.load(campingId)
        advanceUntilIdle()
        viewModel.prepareNewProgram(campingId)
        viewModel.updateEditorForm {
            it.copy(
                title = "Stories",
                location = "Fire ring",
                type = ProgramType.Custom,
                customType = null,
            )
        }

        var savedProgram: Program? = null
        viewModel.saveProgram(campingId) { savedProgram = it }
        advanceUntilIdle()

        assertNull(savedProgram)
        assertEquals(listOf(ProgramValidationError.CustomTypeRequired), viewModel.validationErrors.value)

        val customType = CustomProgramType(
            name = "Campfire",
            symbol = "flame.fill",
            colorHex = "#E2582B",
        )
        viewModel.updateEditorForm { it.copy(customType = customType) }
        viewModel.saveProgram(campingId) { savedProgram = it }
        advanceUntilIdle()

        assertEquals("Campfire", savedProgram!!.customTypeName)
        assertEquals("flame.fill", savedProgram!!.customTypeSymbol)
        assertEquals("#E2582B", savedProgram!!.customTypeColorHex)
        assertEquals(listOf(customType.id), viewModel.customProgramTypes(campingId).map { it.id })
    }

    private fun viewModel(camping: Camping, schedule: CampingSchedule): ScheduleViewModel =
        ScheduleViewModel(
            scheduleService = FakeScheduleService(mutableMapOf(camping.id to schedule)),
            campingService = FakeCampingService(listOf(camping)),
            foodMenuService = FakeFoodMenuService(),
            stringProvider = FakeStringProvider(),
        )

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
