package fr.ziyon.campzone.ui.notifications

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.notifications.FakeNotificationSettingsService
import fr.ziyon.campzone.data.notifications.NotificationChannelsLoader
import fr.ziyon.campzone.data.notifications.PersonalTeamChannel
import fr.ziyon.campzone.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeNotificationChannelsLoader(
    private val campings: List<Camping> = emptyList(),
    private val teams: List<PersonalTeamChannel> = emptyList(),
) : NotificationChannelsLoader {
    override suspend fun attendedCampings(uid: String): List<Camping> = campings
    override suspend fun personalTeams(uid: String): List<PersonalTeamChannel> = teams
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        service: FakeNotificationSettingsService = FakeNotificationSettingsService(),
        loader: NotificationChannelsLoader = FakeNotificationChannelsLoader(),
    ) = NotificationSettingsViewModel(service, loader)

    @Test
    fun loadEmitsLoadedWithRoleOptions() = runTest {
        val vm = viewModel()
        vm.load("u1", UserRole.Admin)
        advanceUntilIdle()

        val state = vm.uiState.value as NotificationSettingsUiState.Loaded
        assertEquals(UserRole.allWireRoles.size, state.roleOptions.size)
        assertTrue(state.settings.isEnabled)
    }

    @Test
    fun toggleCategoryPersistsAndReportsSaved() = runTest {
        val service = FakeNotificationSettingsService()
        val vm = viewModel(service = service)
        vm.load("u1", UserRole.User)
        advanceUntilIdle()

        vm.setCategory(NotificationCategory.Chat, false)
        advanceUntilIdle()

        val state = vm.uiState.value as NotificationSettingsUiState.Loaded
        assertFalse(state.settings.chatMessagesEnabled)
        assertFalse(service.stored!!.chatMessagesEnabled)
        assertEquals(NotificationOpMessage.Saved, vm.operationMessage.value)
    }

    @Test
    fun saveFailureRevertsAndReportsError() = runTest {
        val service = FakeNotificationSettingsService()
        val vm = viewModel(service = service)
        vm.load("u1", UserRole.User)
        advanceUntilIdle()

        service.shouldFail = true
        vm.setCategory(NotificationCategory.Announcements, false)
        advanceUntilIdle()

        val state = vm.uiState.value as NotificationSettingsUiState.Loaded
        // Reverted to last known-good (announcements still enabled).
        assertTrue(state.settings.announcementsEnabled)
        assertEquals(NotificationOpMessage.SaveFailed, vm.operationMessage.value)
    }

    @Test
    fun adminCanSubscribeToAdditionalRole() = runTest {
        val service = FakeNotificationSettingsService()
        val vm = viewModel(service = service)
        vm.load("u1", UserRole.Admin)
        advanceUntilIdle()

        vm.toggleRole(UserRole.Pastor, true)
        advanceUntilIdle()

        val state = vm.uiState.value as NotificationSettingsUiState.Loaded
        assertTrue(state.settings.subscribedRoles.contains(UserRole.Pastor))
    }
}
