package fr.ziyon.campzone.ui.chat

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.chat.FakeChatNotificationDispatcher
import fr.ziyon.campzone.data.chat.FakeChatService
import fr.ziyon.campzone.data.media.AudioUploader
import fr.ziyon.campzone.data.media.CloudinaryUploadResult
import fr.ziyon.campzone.data.media.ImageUploader
import fr.ziyon.campzone.data.model.ChatMention
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

    private fun viewModel(service: FakeChatService, dispatcher: FakeChatNotificationDispatcher) =
        ChatViewModel(service, dispatcher, FakeUploader, FakeUploader)

    @Test
    fun sendWritesTeamScopeAndUsesChatDispatchWhenNoMentions() = runTest {
        val service = FakeChatService(initialMessages = emptyList())
        val dispatcher = FakeChatNotificationDispatcher()
        val vm = viewModel(service, dispatcher)

        vm.updateDraft("Hello team", emptyList())
        vm.send("camp-1", "team-1", user(), mentionableUserIds = emptyList())
        advanceUntilIdle()

        val state = vm.uiState.value as ChatUiState.Loaded
        val sent = state.messages.single()
        assertEquals("Hello team", sent.text)
        assertEquals("team-1", sent.teamId)
        // No mentions -> broadcast chat dispatch only, never the mention dispatch.
        assertEquals("camp-1", dispatcher.dispatched.single().campingId)
        assertEquals(sent.id, dispatcher.dispatched.single().messageId)
        assertEquals("team-1", dispatcher.dispatched.single().teamId)
        assertTrue(dispatcher.mentionDispatched.isEmpty())
    }

    @Test
    fun overLongDraftIsNotSent() = runTest {
        val service = FakeChatService(initialMessages = emptyList())
        val vm = viewModel(service, FakeChatNotificationDispatcher())

        vm.updateDraft("x".repeat(600), emptyList())
        vm.send("camp-1", null, user(), emptyList())
        advanceUntilIdle()

        // The composer caps at 500; an over-limit draft fails isValid and is dropped.
        assertTrue(vm.uiState.value is ChatUiState.Loading)
    }

    @Test
    fun mentionSendsOnlyTheMentionDispatch() = runTest {
        val service = FakeChatService(initialMessages = emptyList())
        val dispatcher = FakeChatNotificationDispatcher()
        val vm = viewModel(service, dispatcher)

        vm.updateDraft("Hi @Lea", listOf(ChatMention("u-lea", "Lea", offset = 3, length = 4)))
        vm.send("camp-1", null, user(), mentionableUserIds = listOf("u-lea"))
        advanceUntilIdle()

        assertTrue(dispatcher.dispatched.isEmpty())
        val mention = dispatcher.mentionDispatched.single()
        assertEquals(listOf("u-lea"), mention.mentionedUserIds)
        assertFalse(mention.isEveryoneMention)
    }

    @Test
    fun blockUserFiltersVisibleMessagesAndUnblockRestoresThem() = runTest {
        val service = FakeChatService(
            initialMessages = listOf(
                message(id = "m1", senderId = "blocked", senderName = "Blocked Sender"),
                message(id = "m2", senderId = "friend", senderName = "Friend"),
            ),
        )
        val vm = viewModel(service, FakeChatNotificationDispatcher())

        vm.start("camp-1", null, "me")
        advanceUntilIdle()
        assertEquals(2, (vm.uiState.value as ChatUiState.Loaded).visibleMessages.size)

        vm.setBlocked(true, "me", "blocked", "Blocked Sender")
        advanceUntilIdle()
        assertEquals(1, (vm.uiState.value as ChatUiState.Loaded).visibleMessages.size)

        vm.setBlocked(false, "me", "blocked", "Blocked Sender")
        advanceUntilIdle()
        assertEquals(2, (vm.uiState.value as ChatUiState.Loaded).visibleMessages.size)
    }

    @Test
    fun reportMessageCreatesChatMessageContentReport() = runTest {
        val service = FakeChatService(initialMessages = emptyList())
        val vm = viewModel(service, FakeChatNotificationDispatcher())

        vm.reportMessage(
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
        val vm = viewModel(service, FakeChatNotificationDispatcher())
        vm.start("camp-1", null, "me")
        advanceUntilIdle()

        val original = (vm.uiState.value as ChatUiState.Loaded).messages.single()
        vm.softDelete(original, "me")
        advanceUntilIdle()

        val deleted = (vm.uiState.value as ChatUiState.Loaded).messages.single()
        assertEquals("m1", deleted.id)
        assertTrue(deleted.isDeleted)
        assertEquals("me", deleted.deletedById)
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

    private object FakeUploader : ImageUploader, AudioUploader {
        override suspend fun uploadImage(
            assetIdPrefix: String,
            folder: String,
            tags: List<String>,
            bytes: ByteArray,
            mimeType: String,
            fileExtension: String,
        ) = CloudinaryUploadResult("https://img/$assetIdPrefix.jpg", "pid", width = 100, height = 100)

        override suspend fun uploadAudio(
            assetIdPrefix: String,
            folder: String,
            tags: List<String>,
            bytes: ByteArray,
            mimeType: String,
            fileExtension: String,
        ) = CloudinaryUploadResult("https://aud/$assetIdPrefix.m4a", "pid", duration = 3.0)
    }
}
