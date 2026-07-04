package fr.ziyon.campzone.ui.attendance

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.attendance.FakeProgramAttendanceService
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.camping.FakeCampingService
import fr.ziyon.campzone.data.model.CampDay
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.CampingSchedule
import fr.ziyon.campzone.data.model.CheckInMethod
import fr.ziyon.campzone.data.model.CheckInQrPayload
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.data.model.Program
import fr.ziyon.campzone.data.model.ProgramAttendanceRecord
import fr.ziyon.campzone.data.model.ProgramAttendanceScanResult
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.schedule.FakeScheduleService
import fr.ziyon.campzone.testing.FakeStringProvider
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgramAttendanceViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun scanRecordsApprovedAttendeeForProgram() = runTest {
        val attendance = FakeProgramAttendanceService()
        val viewModel = viewModel(attendance, attendees = listOf(approved("a1", "Maria")))
        viewModel.start("camp-1", "program-1", admin())
        advanceUntilIdle()

        viewModel.handleScan(qr("a1"))
        advanceUntilIdle()

        val result = viewModel.lastScanResult.value
        assertTrue(result is ProgramAttendanceScanResult.Success)
        assertEquals(CheckInMethod.Qr, (result as ProgramAttendanceScanResult.Success).record.method)
        assertEquals("program-1", result.record.programId)
        assertEquals(listOf("a1"), attendance.loadRecords("camp-1", "program-1").map { it.attendeeId })
    }

    @Test
    fun scanReportsAlreadyRecordedWithoutWritingDuplicate() = runTest {
        val existing = ProgramAttendanceRecord(
            id = "a1",
            campingId = "camp-1",
            programId = "program-1",
            programTitle = "Morning Worship",
            attendeeId = "a1",
            userId = "a1",
            displayName = "Maria",
            method = CheckInMethod.Qr,
            checkedInBy = "leader-1",
            checkedInAt = Date(10),
        )
        val attendance = FakeProgramAttendanceService(records = listOf(existing))
        val viewModel = viewModel(attendance, attendees = listOf(approved("a1", "Maria")))
        viewModel.start("camp-1", "program-1", admin())
        advanceUntilIdle()

        viewModel.handleScan(qr("a1"))
        advanceUntilIdle()

        assertTrue(viewModel.lastScanResult.value is ProgramAttendanceScanResult.AlreadyRecorded)
        assertEquals(1, attendance.loadRecords("camp-1", "program-1").size)
    }

    @Test
    fun scanRejectsWrongCampUnknownAndPendingAttendees() = runTest {
        val attendance = FakeProgramAttendanceService()
        val viewModel = viewModel(
            attendance,
            attendees = listOf(
                approved("a1", "Maria"),
                attendee("a2", "Joao", RegistrationApprovalStatus.Pending),
            ),
        )
        viewModel.start("camp-1", "program-1", admin())
        advanceUntilIdle()

        viewModel.handleScan(CheckInQrPayload(campingId = "other", attendeeId = "a1", userId = "a1").encoded())
        advanceUntilIdle()
        assertEquals(ProgramAttendanceScanResult.WrongCamping, viewModel.lastScanResult.value)

        viewModel.dismissScanResult()
        viewModel.handleScan(qr("ghost"))
        advanceUntilIdle()
        assertEquals(ProgramAttendanceScanResult.UnknownAttendee, viewModel.lastScanResult.value)

        viewModel.dismissScanResult()
        viewModel.handleScan(qr("a2"))
        advanceUntilIdle()
        assertEquals(ProgramAttendanceScanResult.NotApproved, viewModel.lastScanResult.value)
        assertTrue(attendance.loadRecords("camp-1", "program-1").isEmpty())
    }

    @Test
    fun scanSaveFailureReportsDistinctResultAndAllowsRetry() = runTest {
        val attendance = FakeProgramAttendanceService()
        val viewModel = viewModel(attendance, attendees = listOf(approved("a1", "Maria")))
        val code = qr("a1")
        viewModel.start("camp-1", "program-1", admin())
        advanceUntilIdle()

        attendance.shouldFail = true
        viewModel.handleScan(code)
        advanceUntilIdle()

        assertEquals(ProgramAttendanceScanResult.SaveFailed, viewModel.lastScanResult.value)

        attendance.shouldFail = false
        viewModel.handleScan(code)
        advanceUntilIdle()

        assertTrue(viewModel.lastScanResult.value is ProgramAttendanceScanResult.Success)
        assertEquals(1, attendance.loadRecords("camp-1", "program-1").size)
    }

    @Test
    fun restrictedUserCannotRecordAttendance() = runTest {
        val attendance = FakeProgramAttendanceService()
        val viewModel = viewModel(
            attendance,
            attendees = listOf(approved("a1", "Maria")),
            createdByUid = "someone-else",
        )
        viewModel.start("camp-1", "program-1", user(role = UserRole.User, uid = "intruder", church = "Other"))
        advanceUntilIdle()

        assertEquals(ProgramAttendanceUiState.Restricted, viewModel.uiState.value)

        viewModel.handleScan(qr("a1"))
        advanceUntilIdle()
        assertTrue(attendance.loadRecords("camp-1", "program-1").isEmpty())
    }

    private fun viewModel(
        attendance: FakeProgramAttendanceService,
        attendees: List<CampingAttendee>,
        createdByUid: String? = null,
    ) = ProgramAttendanceViewModel(
        attendanceService = attendance,
        campingService = campingService(attendees, createdByUid),
        scheduleService = FakeScheduleService(mutableMapOf("camp-1" to schedule())),
        stringProvider = FakeStringProvider(),
    )

    private fun qr(attendeeId: String) =
        CheckInQrPayload(campingId = "camp-1", attendeeId = attendeeId, userId = attendeeId).encoded()

    private fun campingService(
        attendees: List<CampingAttendee>,
        createdByUid: String?,
    ) = FakeCampingService(
        initial = listOf(camping(createdByUid)),
        attendeesByCamping = mapOf("camp-1" to attendees),
    )

    private fun camping(createdByUid: String? = null) = Camping(
        id = "camp-1",
        title = "Summer Camp",
        description = "A week of fun",
        startDate = Date(1_000_000),
        endDate = Date(2_000_000),
        organizerLevel = OrganizerLevel(OrganizerType.Church, "Paris Central SDA"),
        location = "Lake Annecy",
        registrationStatus = CampingRegistrationStatus.Open,
        createdByUid = createdByUid,
    )

    private fun schedule() = CampingSchedule(
        campingId = "camp-1",
        days = listOf(
            CampDay(
                id = "day-1",
                campingId = "camp-1",
                date = Date(1_000_000),
                programs = listOf(
                    Program(
                        id = "program-1",
                        campingId = "camp-1",
                        campDayId = "day-1",
                        title = "Morning Worship",
                        startDate = Date(1_000_000),
                        endDate = Date(1_500_000),
                    ),
                ),
            ),
        ),
    )

    private fun approved(id: String, name: String) =
        attendee(id, name, RegistrationApprovalStatus.Approved)

    private fun attendee(
        id: String,
        name: String,
        status: RegistrationApprovalStatus,
    ) = CampingAttendee(
        id = id,
        userId = id,
        displayName = name,
        church = "Paris Central SDA",
        age = 20,
        languages = listOf("fr"),
        registrationStatus = status,
    )

    private fun admin() = user(role = UserRole.Admin, uid = "admin-1", church = "Paris Central SDA")

    private fun user(role: UserRole, uid: String, church: String) = AuthenticatedUser(
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
}
