package fr.ziyon.campzone.ui.notifications

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.model.AppNotification
import fr.ziyon.campzone.data.model.AppNotificationKind
import fr.ziyon.campzone.data.model.NotificationTopics
import fr.ziyon.campzone.data.notifications.FakeAppNotificationFeedService
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
class AppNotificationFeedViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun notification(id: String, sentAt: Date) = AppNotification(
        id = id,
        appId = AppNotification.APP_ID,
        kind = AppNotificationKind.Announcement,
        title = "t",
        body = "b",
        topic = NotificationTopics.globalAnnouncement,
        sentAt = sentAt,
        announcementId = id,
    )

    @Test
    fun loadEmitsLoadedNewestFirst() = runTest {
        val service = FakeAppNotificationFeedService(
            notifications = listOf(
                notification("old", Date(1_000)),
                notification("new", Date(3_000)),
            ),
        )
        val vm = AppNotificationFeedViewModel(service)
        vm.load("u1", UserRole.User)
        advanceUntilIdle()

        val state = vm.uiState.value as AppNotificationFeedUiState.Loaded
        assertEquals(listOf("new", "old"), state.notifications.map { it.id })
    }

    @Test
    fun emptyStreamEmitsEmpty() = runTest {
        val vm = AppNotificationFeedViewModel(FakeAppNotificationFeedService(notifications = emptyList()))
        vm.load("u1", UserRole.User)
        advanceUntilIdle()

        assertTrue(vm.uiState.value is AppNotificationFeedUiState.Empty)
    }

    @Test
    fun failureEmitsError() = runTest {
        val vm = AppNotificationFeedViewModel(FakeAppNotificationFeedService(shouldFail = true))
        vm.load("u1", UserRole.User)
        advanceUntilIdle()

        assertTrue(vm.uiState.value is AppNotificationFeedUiState.Error)
    }
}
