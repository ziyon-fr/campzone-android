package fr.ziyon.campzone.ui.checkin

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.camping.FakeCampingService
import fr.ziyon.campzone.data.checkin.FakeCheckInService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.CheckInMethod
import fr.ziyon.campzone.data.model.CheckInQrPayload
import fr.ziyon.campzone.data.model.CheckInRecord
import fr.ziyon.campzone.data.model.CheckInScanResult
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckInViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun scanRecordsApprovedAttendee() = runTest {
        val checkIns = FakeCheckInService()
        val viewModel = CheckInViewModel(checkIns, campingService(listOf(approved("a1", "Maria"))))
        viewModel.start("camp-1", admin())
        advanceUntilIdle()

        viewModel.handleScan(qr("a1"))
        advanceUntilIdle()

        val result = viewModel.lastScanResult.value
        assertTrue(result is CheckInScanResult.Success)
        assertEquals(CheckInMethod.Qr, (result as CheckInScanResult.Success).record.method)
        assertEquals(1, checkIns.loadRecords("camp-1").size)
        assertEquals("a1", checkIns.loadRecords("camp-1").single().attendeeId)
    }

    @Test
    fun scanRejectsWrongCamping() = runTest {
        val viewModel = CheckInViewModel(FakeCheckInService(), campingService(listOf(approved("a1", "Maria"))))
        viewModel.start("camp-1", admin())
        advanceUntilIdle()

        viewModel.handleScan(
            CheckInQrPayload(campingId = "other-camp", attendeeId = "a1", userId = "a1").encoded(),
        )
        advanceUntilIdle()

        assertEquals(CheckInScanResult.WrongCamping, viewModel.lastScanResult.value)
    }

    @Test
    fun scanRejectsUnknownAttendee() = runTest {
        val viewModel = CheckInViewModel(FakeCheckInService(), campingService(listOf(approved("a1", "Maria"))))
        viewModel.start("camp-1", admin())
        advanceUntilIdle()

        viewModel.handleScan(qr("ghost"))
        advanceUntilIdle()

        assertEquals(CheckInScanResult.UnknownAttendee, viewModel.lastScanResult.value)
    }

    @Test
    fun scanRejectsAttendeeWithForgedUserId() = runTest {
        val viewModel = CheckInViewModel(FakeCheckInService(), campingService(listOf(approved("a1", "Maria"))))
        viewModel.start("camp-1", admin())
        advanceUntilIdle()

        // Right attendee id, forged user id -> treated as unknown.
        viewModel.handleScan(
            CheckInQrPayload(campingId = "camp-1", attendeeId = "a1", userId = "forged").encoded(),
        )
        advanceUntilIdle()

        assertEquals(CheckInScanResult.UnknownAttendee, viewModel.lastScanResult.value)
    }

    @Test
    fun scanRejectsUnapprovedAttendee() = runTest {
        val pending = attendee("a2", "Joao", RegistrationApprovalStatus.Pending)
        val viewModel = CheckInViewModel(FakeCheckInService(), campingService(listOf(pending)))
        viewModel.start("camp-1", admin())
        advanceUntilIdle()

        viewModel.handleScan(qr("a2"))
        advanceUntilIdle()

        assertEquals(CheckInScanResult.NotApproved, viewModel.lastScanResult.value)
    }

    @Test
    fun scanReportsAlreadyCheckedIn() = runTest {
        val existing = CheckInRecord(
            campingId = "camp-1",
            attendeeId = "a1",
            userId = "a1",
            displayName = "Maria",
            method = CheckInMethod.Qr,
            checkedInBy = "leader-1",
            checkedInAt = Date(10),
        )
        val checkIns = FakeCheckInService(records = listOf(existing))
        val viewModel = CheckInViewModel(checkIns, campingService(listOf(approved("a1", "Maria"))))
        viewModel.start("camp-1", admin())
        advanceUntilIdle()

        viewModel.handleScan(qr("a1"))
        advanceUntilIdle()

        assertTrue(viewModel.lastScanResult.value is CheckInScanResult.AlreadyCheckedIn)
        assertEquals(1, checkIns.loadRecords("camp-1").size)
    }

    @Test
    fun scanReportsMalformedCode() = runTest {
        val viewModel = CheckInViewModel(FakeCheckInService(), campingService(listOf(approved("a1", "Maria"))))
        viewModel.start("camp-1", admin())
        advanceUntilIdle()

        viewModel.handleScan("https://example.com/not-a-pass")
        advanceUntilIdle()

        assertEquals(CheckInScanResult.Malformed, viewModel.lastScanResult.value)
    }

    @Test
    fun repeatedScanOfSameCodeRecordsOnce() = runTest {
        val checkIns = FakeCheckInService()
        val viewModel = CheckInViewModel(checkIns, campingService(listOf(approved("a1", "Maria"))))
        viewModel.start("camp-1", admin())
        advanceUntilIdle()

        viewModel.handleScan(qr("a1"))
        advanceUntilIdle()
        viewModel.handleScan(qr("a1"))
        advanceUntilIdle()

        assertEquals(1, checkIns.loadRecords("camp-1").size)
    }

    @Test
    fun manualCheckInRecordsAttendee() = runTest {
        val checkIns = FakeCheckInService()
        val viewModel = CheckInViewModel(checkIns, campingService(listOf(approved("a1", "Maria"))))
        viewModel.start("camp-1", admin())
        advanceUntilIdle()

        viewModel.manualCheckIn(approved("a1", "Maria"))
        advanceUntilIdle()

        val result = viewModel.lastScanResult.value
        assertTrue(result is CheckInScanResult.Success)
        assertEquals(CheckInMethod.Manual, (result as CheckInScanResult.Success).record.method)
        assertEquals(1, checkIns.loadRecords("camp-1").size)
    }

    @Test
    fun restrictedUserCannotScan() = runTest {
        val checkIns = FakeCheckInService()
        // Plain user, not the camp creator -> no check-in permission.
        val viewModel = CheckInViewModel(
            checkIns,
            campingService(listOf(approved("a1", "Maria")), createdByUid = "someone-else"),
        )
        viewModel.start("camp-1", user(role = UserRole.User, uid = "intruder"))
        advanceUntilIdle()

        assertEquals(CheckInUiState.Restricted, viewModel.uiState.value)

        viewModel.handleScan(qr("a1"))
        advanceUntilIdle()
        assertTrue(checkIns.loadRecords("camp-1").isEmpty())
    }

    @Test
    fun searchFiltersCheckedInRecords() = runTest {
        val checkIns = FakeCheckInService()
        val viewModel = CheckInViewModel(
            checkIns,
            campingService(listOf(approved("a1", "Maria"), approved("a2", "Joao"))),
        )
        backgroundScope.launch { viewModel.filteredRecords.collect {} } // activate stateIn
        viewModel.start("camp-1", admin())
        advanceUntilIdle()

        viewModel.handleScan(qr("a1"))
        advanceUntilIdle()
        viewModel.handleScan(qr("a2"))
        advanceUntilIdle()
        viewModel.updateSearch("maria")
        advanceUntilIdle()

        assertEquals(listOf("Maria"), viewModel.filteredRecords.value.map { it.displayName })
    }

    // region builders

    private fun qr(attendeeId: String) =
        CheckInQrPayload(campingId = "camp-1", attendeeId = attendeeId, userId = attendeeId).encoded()

    private fun campingService(
        attendees: List<CampingAttendee>,
        createdByUid: String? = null,
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

    private fun admin() = user(role = UserRole.Admin, uid = "admin-1")

    private fun user(role: UserRole, uid: String) = AuthenticatedUser(
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

    // endregion
}
