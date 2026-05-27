package fr.ziyon.campzone.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.chat.ChatNotificationDispatcher
import fr.ziyon.campzone.data.chat.ChatNotificationRequest
import fr.ziyon.campzone.data.chat.ChatService
import fr.ziyon.campzone.data.model.BlockedUser
import fr.ziyon.campzone.data.model.ChatMessage
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
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

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

    fun updateDraft(value: String) {
        _draft.value = value.take(ChatMessage.CLIENT_TEXT_CAP)
    }

    fun clearOperationError() {
        _operationError.value = null
    }

    fun clearOperationMessage() {
        _operationMessage.value = null
    }

    fun send(campingId: String, teamId: String?, sender: AuthenticatedUser) {
        val text = _draft.value.trim().take(ChatMessage.CLIENT_TEXT_CAP)
        if (text.isBlank() || _isSending.value) return

        viewModelScope.launch {
            _isSending.value = true
            _operationError.value = null
            try {
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
                    text = text,
                    createdAt = Date(),
                )
                val saved = service.sendMessage(message, teamId)
                messages = (messages.filterNot { it.id == saved.id } + saved)
                    .sortedBy { it.createdAt?.time ?: Long.MAX_VALUE }
                _draft.value = ""
                publishLoaded()
                dispatchBestEffort(saved)
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Could not send message."
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

    fun softDelete(message: ChatMessage, reviewerId: String) {
        if (reviewerId.isBlank()) return
        viewModelScope.launch {
            _operationError.value = null
            try {
                service.softDelete(message.id, message.campingId, message.teamId, reviewerId)
                replaceMessage(
                    message.copy(
                        isDeleted = true,
                        deletedById = reviewerId,
                        deletedAt = Date(),
                    ),
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
                service.setBlocked(
                    blocked = blocked,
                    currentUserId = currentUserId,
                    blockedUserId = blockedUserId,
                    displayName = displayName,
                )
                blockedUsers = if (blocked) {
                    (blockedUsers.filterNot { it.blockedUserId == blockedUserId } +
                        BlockedUser(blockedUserId, displayName.ifBlank { blockedUserId }, Date()))
                        .sortedBy { it.displayName.lowercase() }
                } else {
                    blockedUsers.filterNot { it.blockedUserId == blockedUserId }
                }
                publishLoaded()
                _operationMessage.value = if (blocked) {
                    "User blocked."
                } else {
                    "User unblocked."
                }
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Could not update blocked users."
            }
        }
    }

    override fun onCleared() {
        observeJob?.cancel()
        super.onCleared()
    }

    private fun dispatchBestEffort(message: ChatMessage) {
        viewModelScope.launch {
            runCatching {
                notificationDispatcher.dispatchChatMessage(
                    ChatNotificationRequest(
                        campingId = message.campingId,
                        messageId = message.id,
                        teamId = message.teamId,
                    ),
                )
            }
        }
    }

    private fun replaceMessage(message: ChatMessage) {
        messages = messages.map { if (it.id == message.id) message else it }
        publishLoaded()
    }

    private fun publishLoaded() {
        _uiState.update { ChatUiState.Loaded(messages = messages, blockedUsers = blockedUsers) }
    }

    private data class ScopeKey(
        val campingId: String,
        val teamId: String?,
        val currentUserId: String,
    )
}
