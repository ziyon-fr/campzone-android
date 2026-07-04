package fr.ziyon.campzone.ui.camping.template

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.camping.FakeCampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.CampingTemplateCloneValidationError
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.testing.FakeStringProvider
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CampingTemplateCloneViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadInitializesNextYearDraftForAdmin() = runTest {
        val viewModel = CampingTemplateCloneViewModel(service(), FakeStringProvider())

        viewModel.load("camp-2025", admin())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is CampingTemplateCloneUiState.Ready)
        assertEquals("Summer Camp 2026", viewModel.form.value.title)
        assertEquals(CampingRegistrationStatus.Closed, viewModel.form.value.registrationStatus)
        assertTrue(viewModel.availableCopies.value.canCopySchedule)
        assertTrue(viewModel.availableCopies.value.canCopyTeams)
        assertTrue(viewModel.availableCopies.value.canCopySongbook)
        assertTrue(viewModel.availableCopies.value.canCopyGuidelines)
    }

    @Test
    fun regularUserIsRestrictedFromCreatingTemplateClone() = runTest {
        val viewModel = CampingTemplateCloneViewModel(service(), FakeStringProvider())

        viewModel.load("camp-2025", user(UserRole.User, uid = "user-1", church = "Other"))
        advanceUntilIdle()

        assertEquals(CampingTemplateCloneUiState.Restricted, viewModel.uiState.value)
    }

    @Test
    fun cloneTemplateSendsRequestAndPublishesCreatedId() = runTest {
        val service = service()
        val viewModel = CampingTemplateCloneViewModel(service, FakeStringProvider())
        viewModel.load("camp-2025", admin())
        advanceUntilIdle()

        viewModel.cloneTemplate("camp-2025")
        advanceUntilIdle()

        val request = service.templateCloneRequests.single()
        assertEquals("camp-2025", request.sourceCampingId)
        assertEquals("Summer Camp 2026", request.title)
        assertEquals(CampingRegistrationStatus.Closed, request.registrationStatus)
        assertTrue(request.options.includeSchedule)
        assertTrue(request.options.includeTeams)
        assertTrue(request.options.includeSongbook)
        assertTrue(request.options.includeGuidelines)
        assertEquals(request.targetCampingId, viewModel.createdCampingId.value)
    }

    @Test
    fun cloneTemplateRequiresAtLeastOneSection() = runTest {
        val service = service()
        val viewModel = CampingTemplateCloneViewModel(service, FakeStringProvider())
        viewModel.load("camp-2025", admin())
        advanceUntilIdle()

        viewModel.toggleSchedule(false)
        viewModel.toggleTeams(false)
        viewModel.toggleSongbook(false)
        viewModel.toggleGuidelines(false)
        viewModel.cloneTemplate("camp-2025")
        advanceUntilIdle()

        assertEquals(
            listOf(CampingTemplateCloneValidationError.ContentRequired),
            viewModel.validationErrors.value,
        )
        assertTrue(service.templateCloneRequests.isEmpty())
        assertNull(viewModel.createdCampingId.value)
    }

    private fun service() = FakeCampingService(initial = listOf(camping()))

    private fun camping() = Camping(
        id = "camp-2025",
        title = "Summer Camp 2025",
        description = "A week together",
        startDate = utcDate(2025, 7, 5),
        endDate = utcDate(2025, 7, 8),
        organizerLevel = OrganizerLevel(OrganizerType.Church, "Paris Central SDA"),
        location = "Lake Annecy",
        registrationStatus = CampingRegistrationStatus.Open,
        guidelines = "Pack warm layers.",
    )

    private fun admin() = user(UserRole.Admin, uid = "admin-1", church = "Paris Central SDA")

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

    private fun utcDate(year: Int, month: Int, day: Int): Date =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, 12, 0, 0)
        }.time
}
