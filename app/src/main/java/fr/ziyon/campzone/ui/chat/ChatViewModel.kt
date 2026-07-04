package fr.ziyon.campzone.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.chat.ChatMentionRequest
import fr.ziyon.campzone.data.chat.ChatMessageDraft
import fr.ziyon.campzone.data.chat.ChatNotificationDispatcher
import fr.ziyon.campzone.data.chat.ChatNotificationRequest
import fr.ziyon.campzone.data.chat.ChatService
import fr.ziyon.campzone.data.media.AudioUploader
import fr.ziyon.campzone.data.media.ImageUploader
import fr.ziyon.campzone.data.model.BlockedUser
import fr.ziyon.campzone.data.model.ChatAttachment
import fr.ziyon.campzone.data.model.ChatAttachmentKind
import fr.ziyon.campzone.data.model.ChatMention
import fr.ziyon.campzone.data.model.ChatMessage
import fr.ziyon.campzone.data.model.ChatReplyReference
import fr.ziyon.campzone.data.model.ContentReport
import fr.ziyon.campzone.data.model.ContentReportReason
import fr.ziyon.campzone.data.model.ContentReportStatus
import fr.ziyon.campzone.data.model.ContentReportTarget
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ChatUiState {
    data object Loading : ChatUiState
    data class Loaded(
        val messages: List<ChatMessage>,
        val blockedUsers: List<BlockedUser>,
    ) : ChatUiState {
        private val blockedIds: Set<String> = blockedUsers.map { it.blockedUserId }.toSet()

        val visibleMessages: List<ChatMessage>
            get() = messages.filterNot { it.senderId in blockedIds }

        val pinnedMessages: List<ChatMessage>
            get() = visibleMessages.filter { it.pinned && !it.isDeleted }
    }
    data class Error(val message: String) : ChatUiState
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val service: ChatService,
    private val notificationDispatcher: ChatNotificationDispatcher,
    private val imageUploader: ImageUploader,
    private val audioUploader: AudioUploader,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _draft = MutableStateFlow(ChatMessageDraft())
    val draft: StateFlow<ChatMessageDraft> = _draft.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _isUploadingAttachment = MutableStateFlow(false)
    val isUploadingAttachment: StateFlow<Boolean> = _isUploadingAttachment.asStateFlow()

    /** Id of the message currently being edited, or null when composing new. */
    private val _editingMessageId = MutableStateFlow<String?>(null)
    val editingMessageId: StateFlow<String?> = _editingMessageId.asStateFlow()

    private val _replyingTo = MutableStateFlow<ChatMessage?>(null)
    val replyingTo: StateFlow<ChatMessage?> = _replyingTo.asStateFlow()

    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    private var observeJob: Job? = null
    private var loadedScope: ScopeKey? = null
    private var messages: List<ChatMessage> = emptyList()
    private var blockedUsers: List<BlockedUser> = emptyList()

    fun start(campingId: String, teamId: String?, currentUserId: String) {
        val nextScope = ScopeKey(campingId, teamId?.takeUnless { it.isBlank() }, currentUserId)
        if (loadedScope == nextScope && observeJob?.isActive == true) return

        observeJob?.cancel()
        loadedScope = nextScope
        messages = emptyList()
        blockedUsers = emptyList()
        _uiState.value = ChatUiState.Loading
        _operationError.value = null

        observeJob = viewModelScope.launch {
            try {
                blockedUsers = service.loadBlockedUsers(currentUserId)
                service.observeMessages(campingId, nextScope.teamId).collect { latest ->
                    messages = latest
                    publishLoaded()
                }
            } catch (e: Exception) {
                _uiState.value = ChatUiState.Error(e.message ?: "Failed to load chat.")
            }
        }
    }

    fun retry(campingId: String, teamId: String?, currentUserId: String) {
        loadedScope = null
        start(campingId, teamId, currentUserId)
    }

    /** Replaces the composer draft (text + committed @mentions). */
    fun updateDraft(text: String, mentions: List<ChatMention>) {
        _draft.value = ChatMessageDraft(text = text, mentions = mentions)
    }

    fun clearOperationError() { _operationError.value = null }

    fun clearOperationMessage() { _operationMessage.value = null }

    fun send(
        campingId: String,
        teamId: String?,
        sender: AuthenticatedUser,
        mentionableUserIds: List<String>,
    ) {
        val current = _draft.value
        if (!current.isValid || _isSending.value) return
        val replyBeforeSend = _replyingTo.value
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            campingId = campingId,
            teamId = teamId?.takeUnless { it.isBlank() },
            senderId = sender.uid,
            senderName = sender.preferredDisplayName,
            senderChurch = sender.church,
            senderPreferredLanguage = sender.preferredLanguage,
            senderGender = sender.gender,
            senderPhotoUrl = sender.photoUrl,
            text = current.text,
            createdAt = Date(),
            mentions = current.resolvedMentions,
            replyTo = replyBeforeSend?.let(ChatReplyReference::from),
        )

        viewModelScope.launch {
            _isSending.value = true
            _operationError.value = null

            appendLocal(message)
            _draft.value = ChatMessageDraft()
            _replyingTo.value = null
            publishLoaded()
            _isSending.value = false

            try {
                val saved = service.sendMessage(message, teamId)
                appendLocal(saved)
                publishLoaded()
                finalizeDispatch(saved, teamId, mentionableUserIds)
            } catch (e: Exception) {
                rollbackFailedSend(message.id, current, replyBeforeSend)
                _operationError.value = e.message ?: "Could not send message."
            } finally {
                _isSending.value = false
            }
        }
    }

    fun sendImage(
        bytes: ByteArray,
        mimeType: String,
        fileExtension: String,
        caption: String,
        campingId: String,
        teamId: String?,
        sender: AuthenticatedUser,
    ) {
        viewModelScope.launch {
            _isUploadingAttachment.value = true
            _operationError.value = null
            try {
                val messageId = UUID.randomUUID().toString()
                val result = imageUploader.uploadImage(
                    assetIdPrefix = messageId,
                    folder = chatFolder(campingId),
                    tags = listOf("campzone", "chat", "camping:$campingId"),
                    bytes = bytes,
                    mimeType = mimeType,
                    fileExtension = fileExtension,
                )
                val attachment = ChatAttachment(
                    kind = ChatAttachmentKind.Image,
                    url = result.secureUrl,
                    publicId = result.publicId,
                    width = result.width,
                    height = result.height,
                )
                deliverMedia(messageId, caption.trim(), attachment, campingId, teamId, sender)
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Could not send photo."
            } finally {
                _isUploadingAttachment.value = false
            }
        }
    }

    fun sendVoice(
        bytes: ByteArray,
        durationSeconds: Double,
        campingId: String,
        teamId: String?,
        sender: AuthenticatedUser,
    ) {
        viewModelScope.launch {
            _isUploadingAttachment.value = true
            _operationError.value = null
            try {
                val messageId = UUID.randomUUID().toString()
                val result = audioUploader.uploadAudio(
                    assetIdPrefix = messageId,
                    folder = chatFolder(campingId),
                    tags = listOf("campzone", "chat", "voice", "camping:$campingId"),
                    bytes = bytes,
                    mimeType = "audio/m4a",
                    fileExtension = "m4a",
                )
                val attachment = ChatAttachment(
                    kind = ChatAttachmentKind.Audio,
                    url = result.secureUrl,
                    publicId = result.publicId,
                    durationSeconds = result.duration ?: durationSeconds,
                )
                deliverMedia(messageId, "", attachment, campingId, teamId, sender)
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Could not send voice message."
            } finally {
                _isUploadingAttachment.value = false
            }
        }
    }

    fun beginReply(message: ChatMessage) {
        if (message.isDeleted) return
        if (_editingMessageId.value != null) cancelEditing()
        _replyingTo.value = message
    }

    fun cancelReply() {
        _replyingTo.value = null
    }

    fun beginEditing(message: ChatMessage) {
        if (message.attachment != null || message.isDeleted) return
        _replyingTo.value = null
        _editingMessageId.value = message.id
        _draft.value = ChatMessageDraft(text = message.text, mentions = message.mentions)
    }

    fun cancelEditing() {
        _editingMessageId.value = null
        _draft.value = ChatMessageDraft()
    }

    fun commitEdit(campingId: String, teamId: String?) {
        val editingId = _editingMessageId.value ?: return
        val current = _draft.value
        if (!current.isValid || _isSending.value) return

        viewModelScope.launch {
            _isSending.value = true
            _operationError.value = null
            try {
                val resolved = current.resolvedMentions
                service.editMessage(editingId, campingId, teamId, current.text, resolved)
                messages = messages.map {
                    if (it.id == editingId) {
                        it.copy(text = current.text, mentions = resolved, editedAt = Date())
                    } else {
                        it
                    }
                }
                _editingMessageId.value = null
                _draft.value = ChatMessageDraft()
                publishLoaded()
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Could not edit the message."
            } finally {
                _isSending.value = false
            }
        }
    }

    fun togglePinned(message: ChatMessage) {
        viewModelScope.launch {
            _operationError.value = null
            try {
                val pinned = !message.pinned
                service.setPinned(message.id, message.campingId, message.teamId, pinned)
                replaceMessage(message.copy(pinned = pinned))
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Could not update the pin."
            }
        }
    }

    fun toggleReaction(message: ChatMessage, emoji: String, userId: String) {
        if (userId.isBlank() || message.isDeleted) return
        val current = messages.firstOrNull { it.id == message.id } ?: return
        val previous = current.reactions[userId]
        val removing = previous == emoji
        val nextReactions = if (removing) current.reactions - userId else current.reactions + (userId to emoji)
        replaceMessage(current.copy(reactions = nextReactions))

        viewModelScope.launch {
            _operationError.value = null
            try {
                if (removing) {
                    service.removeReaction(message.id, message.campingId, message.teamId, userId)
                } else {
                    service.setReaction(message.id, message.campingId, message.teamId, userId, emoji)
                }
            } catch (e: Exception) {
                replaceMessage(current)
                _operationError.value = e.message ?: "Could not update the reaction."
            }
        }
    }

    fun softDelete(message: ChatMessage, reviewerId: String) {
        if (reviewerId.isBlank()) return
        viewModelScope.launch {
            _operationError.value = null
            try {
                service.softDelete(message.id, message.campingId, message.teamId, reviewerId)
                replaceMessage(
                    message.copy(isDeleted = true, deletedById = reviewerId, deletedAt = Date()),
                )
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Could not remove the message."
            }
        }
    }

    fun reportMessage(
        message: ChatMessage,
        reporterId: String,
        reason: ContentReportReason,
        note: String,
    ) {
        if (reporterId.isBlank()) return
        viewModelScope.launch {
            _operationError.value = null
            try {
                service.submitContentReport(
                    ContentReport(
                        id = UUID.randomUUID().toString(),
                        target = ContentReportTarget.ChatMessage,
                        contentId = message.id,
                        reporterId = reporterId,
                        reason = reason,
                        note = note.trim(),
                        status = ContentReportStatus.Pending,
                        createdAt = Date(),
                    ),
                )
                _operationMessage.value = "Report submitted."
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Could not submit report."
            }
        }
    }

    fun setBlocked(blocked: Boolean, currentUserId: String, blockedUserId: String, displayName: String) {
        viewModelScope.launch {
            _operationError.value = null
            try {
                service.setBlocked(blocked, currentUserId, blockedUserId, displayName)
                blockedUsers = if (blocked) {
                    (blockedUsers.filterNot { it.blockedUserId == blockedUserId } +
                        BlockedUser(blockedUserId, displayName.ifBlank { blockedUserId }, Date()))
                        .sortedBy { it.displayName.lowercase() }
                } else {
                    blockedUsers.filterNot { it.blockedUserId == blockedUserId }
                }
                publishLoaded()
                _operationMessage.value = if (blocked) "User blocked." else "User unblocked."
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Could not update blocked users."
            }
        }
    }

    override fun onCleared() {
        observeJob?.cancel()
        super.onCleared()
    }

    // MARK: - Internals

    private suspend fun deliverMedia(
        messageId: String,
        text: String,
        attachment: ChatAttachment,
        campingId: String,
        teamId: String?,
        sender: AuthenticatedUser,
    ) {
        val message = ChatMessage(
            id = messageId,
            campingId = campingId,
            teamId = teamId?.takeUnless { it.isBlank() },
            senderId = sender.uid,
            senderName = sender.preferredDisplayName,
            senderChurch = sender.church,
            senderPreferredLanguage = sender.preferredLanguage,
            senderGender = sender.gender,
            senderPhotoUrl = sender.photoUrl,
            text = text,
            createdAt = Date(),
            attachment = attachment,
        )
        appendLocal(message)
        publishLoaded()
        try {
            val saved = service.sendMessage(message, teamId)
            appendLocal(saved)
            publishLoaded()
            // Media carries no mentions, so this always uses the broadcast dispatch.
            finalizeDispatch(saved, teamId, emptyList())
        } catch (e: Exception) {
            removeLocal(message.id)
            publishLoaded()
            throw e
        }
    }

    /**
     * Either/or notification dispatch (matches the iOS ChatObserver fix): when
     * the message resolves to concrete @mention recipients, send ONLY the
     * targeted mention push; otherwise send the broadcast chat push. Never both.
     */
    private fun finalizeDispatch(
        message: ChatMessage,
        teamId: String?,
        mentionableUserIds: List<String>,
    ) {
        viewModelScope.launch {
            runCatching {
                val recipients = mentionRecipients(message, mentionableUserIds)
                if (recipients.isNotEmpty()) {
                    notificationDispatcher.dispatchChatMention(
                        ChatMentionRequest(
                            campingId = message.campingId,
                            messageId = message.id,
                            senderId = message.senderId,
                            senderName = message.senderName,
                            body = message.text.take(MENTION_BODY_CAP),
                            mentionedUserIds = recipients,
                            isEveryoneMention = message.mentions.any { it.isEveryone },
                            teamId = teamId,
                        ),
                    )
                } else {
                    notificationDispatcher.dispatchChatMessage(
                        ChatNotificationRequest(
                            campingId = message.campingId,
                            messageId = message.id,
                            senderId = message.senderId,
                            senderName = message.senderName,
                            body = message.text,
                            teamId = teamId,
                            replyToMessageId = message.replyTo?.messageId,
                            replyToSenderId = message.replyTo?.senderId,
                            replyToSenderName = message.replyTo?.senderName,
                        ),
                    )
                }
            }
        }
    }

    /**
     * Resolves the user ids to notify for [message]: `@everyone` expands over
     * [mentionableUserIds]; direct mentions are kept only when they target a
     * member of that pool. The sender is always excluded.
     */
    private fun mentionRecipients(
        message: ChatMessage,
        mentionableUserIds: List<String>,
    ): List<String> {
        if (message.mentions.isEmpty()) return emptyList()
        val pool = mentionableUserIds.filter { it != message.senderId }.toSet()
        val recipients = linkedSetOf<String>()
        for (mention in message.mentions) {
            if (mention.isEveryone) {
                recipients.addAll(pool)
            } else if (mention.userId in pool) {
                recipients.add(mention.userId)
            }
        }
        return recipients.toList()
    }

    private fun appendLocal(message: ChatMessage) {
        messages = (messages.filterNot { it.id == message.id } + message)
            .sortedBy { it.createdAt?.time ?: Long.MAX_VALUE }
    }

    private fun rollbackFailedSend(
        messageId: String,
        draftBeforeSend: ChatMessageDraft,
        replyBeforeSend: ChatMessage?,
    ) {
        if (removeLocal(messageId) && _draft.value == ChatMessageDraft()) {
            _draft.value = draftBeforeSend
            if (_replyingTo.value == null) {
                _replyingTo.value = replyBeforeSend
            }
        }
        publishLoaded()
    }

    private fun removeLocal(messageId: String): Boolean {
        val next = messages.filterNot { it.id == messageId }
        if (next.size == messages.size) return false
        messages = next
        return true
    }

    private fun replaceMessage(message: ChatMessage) {
        messages = messages.map { if (it.id == message.id) message else it }
        publishLoaded()
    }

    private fun publishLoaded() {
        _uiState.update { ChatUiState.Loaded(messages = messages, blockedUsers = blockedUsers) }
    }

    private fun chatFolder(campingId: String): String = "campzone/chat/$campingId"

    private data class ScopeKey(
        val campingId: String,
        val teamId: String?,
        val currentUserId: String,
    )

    private companion object {
        const val MENTION_BODY_CAP = 140
    }
}
