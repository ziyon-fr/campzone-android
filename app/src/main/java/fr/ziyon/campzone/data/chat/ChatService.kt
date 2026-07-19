package fr.ziyon.campzone.data.chat

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.model.BlockedUser
import fr.ziyon.campzone.data.model.BlockedUserPayload
import fr.ziyon.campzone.data.model.ChatMention
import fr.ziyon.campzone.data.model.ChatMessage
import fr.ziyon.campzone.data.model.ChatReplyReference
import fr.ziyon.campzone.data.model.ChatMessagePayload
import fr.ziyon.campzone.data.model.ContentReport
import fr.ziyon.campzone.data.model.ContentReportPayload
import fr.ziyon.campzone.data.model.toBlockedUser
import fr.ziyon.campzone.data.model.toChatMessageOrNull
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

interface ChatService {
    fun observeMessages(campingId: String, teamId: String? = null, staffRoleId: String? = null): Flow<List<ChatMessage>>
    suspend fun loadMessages(campingId: String, teamId: String? = null, staffRoleId: String? = null): List<ChatMessage>
    suspend fun sendMessage(message: ChatMessage, teamId: String? = null, staffRoleId: String? = null): ChatMessage
    suspend fun editMessage(
        messageId: String,
        campingId: String,
        teamId: String?,
        newText: String,
        mentions: List<ChatMention>,
        staffRoleId: String? = null,
    )
    suspend fun setPinned(messageId: String, campingId: String, teamId: String?, pinned: Boolean, staffRoleId: String? = null)
    suspend fun setReaction(messageId: String, campingId: String, teamId: String?, userId: String, emoji: String, staffRoleId: String? = null)
    suspend fun removeReaction(messageId: String, campingId: String, teamId: String?, userId: String, staffRoleId: String? = null)
    suspend fun softDelete(messageId: String, campingId: String, teamId: String?, deletedById: String, staffRoleId: String? = null)
    suspend fun submitContentReport(report: ContentReport): ContentReport
    suspend fun loadBlockedUsers(userId: String): List<BlockedUser>
    suspend fun setBlocked(
        blocked: Boolean,
        currentUserId: String,
        blockedUserId: String,
        displayName: String,
    )
}

@Singleton
class FirestoreChatService @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : ChatService {

    override fun observeMessages(campingId: String, teamId: String?, staffRoleId: String?): Flow<List<ChatMessage>> =
        callbackFlow {
            val listener = chatCollection(campingId, teamId, staffRoleId)
                .orderBy(Field.CreatedAt, Query.Direction.ASCENDING)
                .limitToLast(RecentMessageLimit)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val messages = snapshot?.documents
                        ?.mapNotNull { document ->
                            @Suppress("UNCHECKED_CAST")
                            (document.data as? Map<String, Any?>)?.toChatMessageOrNull(document.id)
                        }
                        ?.sortedBy { it.createdAt?.time ?: Long.MAX_VALUE }
                        .orEmpty()
                    trySend(messages)
                }
            awaitClose { listener.remove() }
        }

    override suspend fun loadMessages(campingId: String, teamId: String?, staffRoleId: String?): List<ChatMessage> {
        val snapshot = chatCollection(campingId, teamId, staffRoleId)
            .orderBy(Field.CreatedAt, Query.Direction.ASCENDING)
            .limitToLast(RecentMessageLimit)
            .get()
            .await()
        return snapshot.documents.mapNotNull { document ->
            @Suppress("UNCHECKED_CAST")
            (document.data as? Map<String, Any?>)?.toChatMessageOrNull(document.id)
        }.sortedBy { it.createdAt?.time ?: Long.MAX_VALUE }
    }

    override suspend fun sendMessage(message: ChatMessage, teamId: String?, staffRoleId: String?): ChatMessage {
        val currentUserId = auth.currentUser?.uid
        require(currentUserId == message.senderId) { "You can only send chat messages as yourself." }
        require(message.campingId.isNotBlank()) { "Camping is required." }
        require(message.text.isNotBlank() || message.attachment != null) {
            "A message needs text or an attachment."
        }

        // Persist the text unmodified so stored @mention offsets stay aligned;
        // the composer already caps draft input at CLIENT_TEXT_CAP.
        val scopedMessage = message.copy(
            teamId = teamId?.trim()?.takeUnless { it.isBlank() },
            staffRoleId = staffRoleId?.trim()?.takeUnless { it.isBlank() },
            pinned = false,
            isDeleted = false,
        )
        val payload = ChatMessagePayload.sendPayload(
            message = scopedMessage,
            serverTimestamp = FieldValue.serverTimestamp(),
            isTeamChat = scopedMessage.teamId != null,
            isStaffRoleChat = scopedMessage.staffRoleId != null,
        )

        chatCollection(scopedMessage.campingId, scopedMessage.teamId, scopedMessage.staffRoleId)
            .document(scopedMessage.id)
            .set(payload)
            .await()

        return scopedMessage.copy(createdAt = scopedMessage.createdAt ?: Date())
    }

    override suspend fun editMessage(
        messageId: String,
        campingId: String,
        teamId: String?,
        newText: String,
        mentions: List<ChatMention>,
        staffRoleId: String?,
    ) {
        require(auth.currentUser != null) { "You must be signed in to edit a message." }
        chatCollection(campingId, teamId, staffRoleId)
            .document(messageId)
            .update(
                ChatMessagePayload.editPayload(
                    newText = newText,
                    mentions = mentions,
                    serverTimestamp = FieldValue.serverTimestamp(),
                    deleteValue = FieldValue.delete(),
                ),
            )
            .await()
    }

    override suspend fun setPinned(
        messageId: String,
        campingId: String,
        teamId: String?,
        pinned: Boolean,
        staffRoleId: String?,
    ) {
        chatCollection(campingId, teamId, staffRoleId)
            .document(messageId)
            .update(ChatMessagePayload.pinPayload(pinned))
            .await()
    }

    override suspend fun setReaction(
        messageId: String,
        campingId: String,
        teamId: String?,
        userId: String,
        emoji: String,
        staffRoleId: String?,
    ) {
        chatCollection(campingId, teamId, staffRoleId)
            .document(messageId)
            .update("reactions.$userId", emoji)
            .await()
    }

    override suspend fun removeReaction(
        messageId: String,
        campingId: String,
        teamId: String?,
        userId: String,
        staffRoleId: String?,
    ) {
        chatCollection(campingId, teamId, staffRoleId)
            .document(messageId)
            .update("reactions.$userId", FieldValue.delete())
            .await()
    }

    override suspend fun softDelete(
        messageId: String,
        campingId: String,
        teamId: String?,
        deletedById: String,
        staffRoleId: String?,
    ) {
        chatCollection(campingId, teamId, staffRoleId)
            .document(messageId)
            .update(ChatMessagePayload.softDeletePayload(deletedById, FieldValue.serverTimestamp()))
            .await()
    }

    override suspend fun submitContentReport(report: ContentReport): ContentReport {
        db.collection(Collection.ContentReports)
            .document(report.id)
            .set(ContentReportPayload.reportPayload(report, FieldValue.serverTimestamp()))
            .await()
        return report.copy(createdAt = report.createdAt)
    }

    override suspend fun loadBlockedUsers(userId: String): List<BlockedUser> {
        val snapshot = blockedUsersCollection(userId).get().await()
        return snapshot.documents.map { document ->
            @Suppress("UNCHECKED_CAST")
            (document.data as? Map<String, Any?>).orEmpty().toBlockedUser(document.id)
        }.sortedBy { it.displayName.lowercase() }
    }

    override suspend fun setBlocked(
        blocked: Boolean,
        currentUserId: String,
        blockedUserId: String,
        displayName: String,
    ) {
        require(currentUserId.isNotBlank()) { "Current user is required." }
        require(blockedUserId.isNotBlank()) { "Blocked user is required." }
        require(currentUserId != blockedUserId) { "You cannot block yourself." }

        val document = blockedUsersCollection(currentUserId).document(blockedUserId)
        if (blocked) {
            document
                .set(
                    BlockedUserPayload.blockPayload(
                        blockedUser = BlockedUser(
                            blockedUserId = blockedUserId,
                            displayName = displayName.ifBlank { blockedUserId },
                        ),
                        serverTimestamp = FieldValue.serverTimestamp(),
                    ),
                    SetOptions.merge(),
                )
                .await()
        } else {
            document.delete().await()
        }
    }

    private fun chatCollection(campingId: String, teamId: String?, staffRoleId: String?): CollectionReference {
        require(teamId.isNullOrBlank() || staffRoleId.isNullOrBlank()) {
            "A chat scope can target either a team or a staff role, not both."
        }
        val campingDocument = db.collection(Collection.Campings).document(campingId)
        return when {
            !staffRoleId.isNullOrBlank() -> campingDocument
                .collection(Collection.StaffRoles)
                .document(staffRoleId)
                .collection(Collection.Chat)
            !teamId.isNullOrBlank() -> campingDocument
                .collection(Collection.Teams)
                .document(teamId)
                .collection(Collection.Chat)
            else -> campingDocument.collection(Collection.Chat)
        }
    }

    private fun blockedUsersCollection(userId: String) =
        db.collection(Collection.Users)
            .document(userId)
            .collection(Collection.BlockedUsers)

    private object Collection {
        const val Campings = "campings"
        const val Teams = "teams"
        const val StaffRoles = "staffRoles"
        const val Chat = "chat"
        const val Users = "users"
        const val BlockedUsers = "blockedUsers"
        const val ContentReports = "contentReports"
    }

    private object Field {
        const val CreatedAt = "createdAt"
    }

    private companion object {
        const val RecentMessageLimit = 200L
    }
}

class FakeChatService(
    initialMessages: List<ChatMessage> = previewChatMessages(),
    initialBlockedUsers: List<BlockedUser> = emptyList(),
    var shouldFail: Boolean = false,
) : ChatService {
    private val messagesByScope = initialMessages
        .groupBy { scopeKey(it.campingId, it.teamId, it.staffRoleId) }
        .mapValues { it.value.toMutableList() }
        .toMutableMap()
    private val blockedByUser = mutableMapOf<String, MutableList<BlockedUser>>()
    val reports = mutableListOf<ContentReport>()

    init {
        if (initialBlockedUsers.isNotEmpty()) {
            blockedByUser["preview-user"] = initialBlockedUsers.toMutableList()
        }
    }

    override fun observeMessages(campingId: String, teamId: String?, staffRoleId: String?): Flow<List<ChatMessage>> = flow {
        failIfNeeded()
        emit(messages(campingId, teamId, staffRoleId))
    }

    override suspend fun loadMessages(campingId: String, teamId: String?, staffRoleId: String?): List<ChatMessage> {
        failIfNeeded()
        return messages(campingId, teamId, staffRoleId)
    }

    override suspend fun sendMessage(message: ChatMessage, teamId: String?, staffRoleId: String?): ChatMessage {
        failIfNeeded()
        val scoped = message.copy(
            teamId = teamId?.takeUnless { it.isBlank() },
            staffRoleId = staffRoleId?.takeUnless { it.isBlank() },
            createdAt = message.createdAt ?: Date(),
            pinned = false,
            isDeleted = false,
        )
        val key = scopeKey(scoped.campingId, scoped.teamId, scoped.staffRoleId)
        messagesByScope.getOrPut(key) { mutableListOf() }.add(scoped)
        return scoped
    }

    override suspend fun editMessage(
        messageId: String,
        campingId: String,
        teamId: String?,
        newText: String,
        mentions: List<ChatMention>,
        staffRoleId: String?,
    ) {
        failIfNeeded()
        mutateMessage(messageId, campingId, teamId, staffRoleId) {
            it.copy(text = newText, mentions = mentions, editedAt = Date())
        }
    }

    override suspend fun setPinned(
        messageId: String,
        campingId: String,
        teamId: String?,
        pinned: Boolean,
        staffRoleId: String?,
    ) {
        failIfNeeded()
        mutateMessage(messageId, campingId, teamId, staffRoleId) { it.copy(pinned = pinned) }
    }

    override suspend fun setReaction(
        messageId: String,
        campingId: String,
        teamId: String?,
        userId: String,
        emoji: String,
        staffRoleId: String?,
    ) {
        failIfNeeded()
        mutateMessage(messageId, campingId, teamId, staffRoleId) { it.copy(reactions = it.reactions + (userId to emoji)) }
    }

    override suspend fun removeReaction(
        messageId: String,
        campingId: String,
        teamId: String?,
        userId: String,
        staffRoleId: String?,
    ) {
        failIfNeeded()
        mutateMessage(messageId, campingId, teamId, staffRoleId) { it.copy(reactions = it.reactions - userId) }
    }

    override suspend fun softDelete(
        messageId: String,
        campingId: String,
        teamId: String?,
        deletedById: String,
        staffRoleId: String?,
    ) {
        failIfNeeded()
        mutateMessage(messageId, campingId, teamId, staffRoleId) {
            it.copy(isDeleted = true, deletedById = deletedById, deletedAt = Date())
        }
    }

    override suspend fun submitContentReport(report: ContentReport): ContentReport {
        failIfNeeded()
        reports.add(report)
        return report
    }

    override suspend fun loadBlockedUsers(userId: String): List<BlockedUser> {
        failIfNeeded()
        return blockedByUser[userId].orEmpty().sortedBy { it.displayName.lowercase() }
    }

    override suspend fun setBlocked(
        blocked: Boolean,
        currentUserId: String,
        blockedUserId: String,
        displayName: String,
    ) {
        failIfNeeded()
        val list = blockedByUser.getOrPut(currentUserId) { mutableListOf() }
        list.removeAll { it.blockedUserId == blockedUserId }
        if (blocked) {
            list.add(
                BlockedUser(
                    blockedUserId = blockedUserId,
                    displayName = displayName.ifBlank { blockedUserId },
                    blockedAt = Date(),
                ),
            )
        }
    }

    private fun messages(campingId: String, teamId: String?, staffRoleId: String?): List<ChatMessage> =
        messagesByScope[scopeKey(campingId, teamId, staffRoleId)]
            .orEmpty()
            .sortedBy { it.createdAt?.time ?: Long.MAX_VALUE }

    private fun mutateMessage(
        messageId: String,
        campingId: String,
        teamId: String?,
        staffRoleId: String?,
        update: (ChatMessage) -> ChatMessage,
    ) {
        val key = scopeKey(campingId, teamId, staffRoleId)
        val messages = messagesByScope[key] ?: return
        val index = messages.indexOfFirst { it.id == messageId }
        if (index >= 0) messages[index] = update(messages[index])
    }

    private fun failIfNeeded() {
        if (shouldFail) throw IllegalStateException("FakeChatService configured to fail.")
    }

    private companion object {
        fun scopeKey(campingId: String, teamId: String?, staffRoleId: String?): String = when {
            !staffRoleId.isNullOrBlank() -> "camping:$campingId|staffRole:$staffRoleId"
            !teamId.isNullOrBlank() -> "camping:$campingId|team:$teamId"
            else -> "camping:$campingId"
        }
    }
}

fun previewChatMessages(campingId: String = "preview-camping"): List<ChatMessage> =
    listOf(
        ChatMessage(
            id = "welcome",
            campingId = campingId,
            senderId = "leader-1",
            senderName = "Camp Office",
            senderChurch = "Central SDA",
            senderPreferredLanguage = "en",
            text = "Welcome! Dinner starts near the dining hall at 18:30.",
            createdAt = Date(System.currentTimeMillis() - 900_000),
            pinned = true,
            reactions = mapOf("preview-user" to "\u2764\uFE0F", "member-1" to "\uD83D\uDE4F"),
        ),
        ChatMessage(
            id = "reply",
            campingId = campingId,
            senderId = "preview-user",
            senderName = "Preview Camper",
            senderChurch = "Central SDA",
            senderPreferredLanguage = "en",
            text = "Got it, thank you.",
            createdAt = Date(System.currentTimeMillis() - 300_000),
            replyTo = ChatReplyReference(
                messageId = "welcome",
                senderId = "leader-1",
                senderName = "Camp Office",
                textPreview = "Welcome! Dinner starts near the dining hall at 18:30.",
            ),
            reactions = mapOf("leader-1" to "\uD83D\uDC4D"),
        ),
        ChatMessage(
            id = "team-note",
            campingId = campingId,
            teamId = "lions",
            senderId = "member-1",
            senderName = "Ana Silva",
            senderChurch = "Central SDA",
            senderPreferredLanguage = "pt",
            text = "Team prayer circle in five minutes.",
            createdAt = Date(System.currentTimeMillis() - 120_000),
        ),
    )

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatBindings {
    @Binds
    @Singleton
    abstract fun bindChatService(impl: FirestoreChatService): ChatService
}
