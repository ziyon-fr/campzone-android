package fr.ziyon.campzone.ui.chat

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.chat.FakeChatNotificationDispatcher
import fr.ziyon.campzone.data.chat.FakeChatService
import fr.ziyon.campzone.data.model.ChatMessage
import fr.ziyon.campzone.data.model.ContentReportReason
import fr.ziyon.campzone.data.model.ContentReportTarget
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
class ChatViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun sendCapsTextWritesTeamScopeAndDispatchesNotification() = runTest {
        val service = FakeChatService(initialMessages = emptyList())
        val dispatcher = FakeChatNotificationDispatcher()
        val viewModel = ChatViewModel(service, dispatcher)

        viewModel.updateDraft("x".repeat(600))
        viewModel.send("camp-1", "team-1", user())
        advanceUntilIdle()

        val state = viewModel.uiState.value as ChatUiState.Loaded
        val sent = state.messages.single()
        assertEquals(ChatMessage.CLIENT_TEXT_CAP, sent.text.length)
        assertEquals("team-1", sent.teamId)
        assertEquals("camp-1", dispatcher.dispatched.single().campingId)
        assertEquals(sent.id, dispatcher.dispatched.single().messageId)
        assertEquals("team-1", dispatcher.dispatched.single().teamId)
    }

    @Test
    fun blockUserFiltersVisibleMessagesAndUnblockRestoresThem() = runTest {
        val service = FakeChatService(
            initialMessages = listOf(
                message(id = "m1", senderId = "blocked", senderName = "Blocked Sender"),
                message(id = "m2", senderId = "friend", senderName = "Friend"),
            ),
        )
        val viewModel = ChatViewModel(service, FakeChatNotificationDispatcher())

        viewModel.start("camp-1", null, "me")
        advanceUntilIdle()
        assertEquals(2, (viewModel.uiState.value as ChatUiState.Loaded).visibleMessages.size)

        viewModel.setBlocked(true, "me", "blocked", "Blocked Sender")
        advanceUntilIdle()
        assertEquals(1, (viewModel.uiState.value as ChatUiState.Loaded).visibleMessages.size)

        viewModel.setBlocked(false, "me", "blocked", "Blocked Sender")
        advanceUntilIdle()
        assertEquals(2, (viewModel.uiState.value as ChatUiState.Loaded).visibleMessages.size)
    }

    @Test
    fun reportMessageCreatesChatMessageContentReport() = runTest {
        val service = FakeChatService(initialMessages = emptyList())
        val viewModel = ChatViewModel(service, FakeChatNotificationDispatcher())

        viewModel.reportMessage(
            message = message(id = "msg-9"),
            reporterId = "me",
            reason = ContentReportReason.Spam,
            note = "Repeated links",
        )
        advanceUntilIdle()

        val report = service.reports.single()
        assertEquals(ContentReportTarget.ChatMessage, report.target)
        assertEquals("msg-9", report.contentId)
        assertEquals("me", report.reporterId)
        assertEquals("Repeated links", report.note)
    }

    @Test
    fun softDeleteKeepsMessageAndMarksItDeleted() = runTest {
        val service = FakeChatService(initialMessages = listOf(message(id = "m1", senderId = "me")))
        val viewModel = ChatViewModel(service, FakeChatNotificationDispatcher())
        viewModel.start("camp-1", null, "me")
        advanceUntilIdle()

        val original = (viewModel.uiState.value as ChatUiState.Loaded).messages.single()
        viewModel.softDelete(original, "me")
        advanceUntilIdle()

        val deleted = (viewModel.uiState.value as ChatUiState.Loaded).messages.single()
        assertEquals("m1", deleted.id)
        assertTrue(deleted.isDeleted)
        assertEquals("me", deleted.deletedById)
        assertFalse((viewModel.uiState.value as ChatUiState.Loaded).visibleMessages.isEmpty())
    }

    private fun message(
        id: String,
        senderId: String = "sender",
        senderName: String = "Sender",
    ) = ChatMessage(
        id = id,
        campingId = "camp-1",
        senderId = senderId,
        senderName = senderName,
        senderChurch = "Central SDA",
        senderPreferredLanguage = "en",
        text = "Hello",
        createdAt = Date(1),
    )

    private fun user() = AuthenticatedUser(
        uid = "me",
        email = "me@campzone.local",
        displayName = "Me Camper",
        photoUrl = null,
        role = UserRole.User,
        church = "Central SDA",
        age = 24,
        preferredLanguage = "en",
        gender = UserGender.Female,
        onboardingCompleted = true,
    )
}
