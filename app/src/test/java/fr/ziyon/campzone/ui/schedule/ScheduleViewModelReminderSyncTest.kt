package fr.ziyon.campzone.ui.schedule

import fr.ziyon.campzone.data.camping.FakeCampingService
import fr.ziyon.campzone.data.model.CampDay
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.CampingSchedule
import fr.ziyon.campzone.data.model.DateKeys
import fr.ziyon.campzone.data.model.NotificationSettings
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.data.model.Program
import fr.ziyon.campzone.data.model.ProgramType
import fr.ziyon.campzone.data.model.ScheduleReminderTiming
import fr.ziyon.campzone.data.notifications.NotificationApi
import fr.ziyon.campzone.data.notifications.ProgramReminderPlan
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScheduleViewModelReminderSyncTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun saveReminderTimingReplacesCampingReminders() = runTest {
        val campingId = "camp-1"
        val notificationApi = RecordingNotificationApi()
        val viewModel = viewModel(campingId, notificationApi)

        viewModel.load(campingId)
        advanceUntilIdle()
        viewModel.setReminderTiming(ScheduleReminderTiming.FifteenMinutes)
        viewModel.saveReminderTiming(campingId)
        advanceUntilIdle()

        val replacement = notificationApi.replacedCampings.single()
        assertEquals(campingId, replacement.campingId)
        assertEquals(listOf("$campingId-worship"), replacement.reminders.map { it.id })
        assertEquals(listOf("worship"), replacement.reminders.map { it.programId })
    }

    @Test
    fun saveReminderTimingNoneCancelsCampingReminders() = runTest {
        val campingId = "camp-1"
        val notificationApi = RecordingNotificationApi()
        val viewModel = viewModel(campingId, notificationApi)

        viewModel.load(campingId)
        advanceUntilIdle()
        viewModel.setReminderTiming(ScheduleReminderTiming.None)
        viewModel.saveReminderTiming(campingId)
        advanceUntilIdle()

        val replacement = notificationApi.replacedCampings.single()
        assertEquals(campingId, replacement.campingId)
        assertTrue(replacement.reminders.isEmpty())
    }

    @Test
    fun saveProgramReplacesThatProgramReminder() = runTest {
        val campingId = "camp-1"
        val notificationApi = RecordingNotificationApi()
        val viewModel = viewModel(campingId, notificationApi)

        viewModel.load(campingId)
        advanceUntilIdle()
        viewModel.prepareNewProgram(campingId)
        viewModel.updateEditorForm {
            it.copy(
                title = "Workshop",
                location = "Hall",
                startDate = dateOf(2026, 8, 3, 15, 0),
                endDate = dateOf(2026, 8, 3, 16, 0),
            )
        }
        viewModel.saveProgram(campingId) {}
        advanceUntilIdle()

        val replacement = notificationApi.replacedPrograms.single()
        assertEquals(campingId, replacement.campingId)
        assertEquals(replacement.programIds, replacement.reminders.map { it.programId })
    }

    @Test
    fun deleteProgramCancelsThatProgramReminder() = runTest {
        val campingId = "camp-1"
        val notificationApi = RecordingNotificationApi()
        val viewModel = viewModel(campingId, notificationApi)

        viewModel.load(campingId)
        advanceUntilIdle()
        viewModel.deleteProgram("worship", campingId)
        advanceUntilIdle()

        val replacement = notificationApi.replacedPrograms.single()
        assertEquals(campingId, replacement.campingId)
        assertEquals(listOf("worship"), replacement.programIds)
        assertTrue(replacement.reminders.isEmpty())
    }

    private fun viewModel(
        campingId: String,
        notificationApi: RecordingNotificationApi,
    ): ScheduleViewModel {
        val startDate = dateOf(2026, 8, 3)
        val day = CampDay(
            id = DateKeys.campDayId(campingId, startDate),
            campingId = campingId,
            date = DateKeys.startOfDay(startDate),
            title = "Day 1",
            programs = listOf(
                Program(
                    id = "worship",
                    campingId = campingId,
                    campDayId = DateKeys.campDayId(campingId, startDate),
                    title = "Worship",
                    type = ProgramType.Preaching,
                    startDate = dateOf(2026, 8, 3, 10, 0),
                    endDate = dateOf(2026, 8, 3, 11, 0),
                    location = "Main tent",
                ),
            ),
        )
        return ScheduleViewModel(
            scheduleService = FakeScheduleService(
                mutableMapOf(
                    campingId to CampingSchedule(
                        campingId = campingId,
                        reminderTiming = ScheduleReminderTiming.FifteenMinutes,
                        days = listOf(day),
                    ),
                ),
            ),
            campingService = FakeCampingService(listOf(camping(campingId, startDate))),
            foodMenuService = FakeFoodMenuService(),
            stringProvider = FakeStringProvider(),
            notificationApi = notificationApi,
        )
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

private class RecordingNotificationApi : NotificationApi {
    val replacedCampings = mutableListOf<CampingReplacement>()
    val replacedPrograms = mutableListOf<ProgramReplacement>()

    override suspend fun registerDevice(
        token: String,
        roleRawValue: String,
        localeIdentifier: String,
        appVersion: String,
    ) = Unit

    override suspend fun syncSettings(settings: NotificationSettings, userId: String) = Unit

    override suspend fun replaceCampingReminders(
        campingId: String,
        reminders: List<ProgramReminderPlan>,
    ) {
        replacedCampings += CampingReplacement(campingId, reminders)
    }

    override suspend fun replaceProgramReminders(
        campingId: String,
        programIds: List<String>,
        reminders: List<ProgramReminderPlan>,
    ) {
        replacedPrograms += ProgramReplacement(campingId, programIds, reminders)
    }
}

private data class CampingReplacement(
    val campingId: String,
    val reminders: List<ProgramReminderPlan>,
)

private data class ProgramReplacement(
    val campingId: String,
    val programIds: List<String>,
    val reminders: List<ProgramReminderPlan>,
)
