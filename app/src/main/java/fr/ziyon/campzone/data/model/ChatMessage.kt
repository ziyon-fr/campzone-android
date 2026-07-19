package fr.ziyon.campzone.data.model

import fr.ziyon.campzone.data.auth.UserGender
import java.util.Date

/**
 * An @mention attached to a chat message. Extracted from the composer at send
 * time, persisted alongside the message, and used to render highlighted ranges
 * and to fan out "mentioned you" notifications. [userId] carries
 * [EVERYONE_TOKEN] for the `@everyone` broadcast. [displayName] is denormalized
 * so historical messages still render even after a member leaves.
 */
data class ChatMention(
    val userId: String,
    val displayName: String,
    /** Zero-based UTF-16 offset of the `@` within [ChatMessage.text]. */
    val offset: Int,
    /** Length (UTF-16 code units) of the token, incl. the leading `@`. */
    val length: Int,
) {
    val isEveryone: Boolean get() = userId == EVERYONE_TOKEN
    val endOffset: Int get() = offset + length

    companion object {
        const val EVERYONE_TOKEN = "__everyone__"
    }
}

/** The media kind carried by a chat message attachment. */
enum class ChatAttachmentKind(val wireValue: String) {
    Image("image"),
    Audio("audio"),
    ;

    companion object {
        fun fromWire(value: String?): ChatAttachmentKind? =
            entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * A media attachment on a chat message. Uploaded to Cloudinary first; the
 * message then persists the `secure_url` + `public_id` alongside lightweight
 * layout/duration metadata. Text and attachment are independent: an image can
 * carry a caption; a voice note carries no text.
 */
data class ChatAttachment(
    val kind: ChatAttachmentKind,
    val url: String,
    /** Cloudinary public id for backend cleanup. Null for legacy/preview data. */
    val publicId: String? = null,
    /** Voice-note length in seconds (audio only). */
    val durationSeconds: Double? = null,
    /** Pixel dimensions for aspect-ratio layout without a load (image only). */
    val width: Int? = null,
    val height: Int? = null,
)

/** A denormalized snapshot of the message this one quotes as a reply. */
data class ChatReplyReference(
    val messageId: String,
    val senderId: String,
    val senderName: String,
    val textPreview: String? = null,
    val mediaType: ChatAttachmentKind? = null,
    val mediaUrl: String? = null,
) {
    companion object {
        const val PREVIEW_LIMIT = 140

        fun from(message: ChatMessage): ChatReplyReference? {
            if (message.isDeleted) return null
            val trimmed = message.text.trim()
            return ChatReplyReference(
                messageId = message.id,
                senderId = message.senderId,
                senderName = message.senderName,
                textPreview = trimmed.takeIf { it.isNotEmpty() }?.take(PREVIEW_LIMIT),
                mediaType = message.attachment?.kind,
                mediaUrl = message.attachment?.url,
            )
        }
    }
}

/** Canonical one-row reaction set, ordered to match iOS. */
object ChatReaction {
    val available = listOf("\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDC4D", "\uD83D\uDE4F", "\uD83D\uDD25", "\uD83D\uDC40")
}

data class ChatReactionSummary(
    val emoji: String,
    val count: Int,
    val reactedByCurrentUser: Boolean,
)

/**
 * `campings/{id}/chat/{messageId}` and the team variant
 * (`02-firestore-schema.md` §6.1, extended to match the shipped iOS chat:
 * @mentions, image/voice attachments, inline edits). Doc ID is a client UUID;
 * `senderID == auth.uid`. Send is a full `set` (no merge); pin / soft-delete /
 * edit are `updateData`. `teamID` is written only in team chat and
 * `staffRoleID` only in staff-role chat. Decode drops the message if
 * `campingID`/`senderID`/`senderName`/`text` is missing — `text` may be `""`
 * (a voice note carries no text).
 */
data class ChatMessage(
    val id: String,
    val campingId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val teamId: String? = null,
    val staffRoleId: String? = null,
    val senderChurch: String = "",
    val senderPreferredLanguage: String = "",
    val senderGender: UserGender? = null,
    val senderPhotoUrl: String? = null,
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
    val pinned: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedById: String? = null,
    val deletedAt: Date? = null,
    val mentions: List<ChatMention> = emptyList(),
    val attachment: ChatAttachment? = null,
    val editedAt: Date? = null,
    val replyTo: ChatReplyReference? = null,
    val reactions: Map<String, String> = emptyMap(),
) {
    val isEdited: Boolean get() = editedAt != null
    val hasAttachment: Boolean get() = attachment != null
    val hasText: Boolean get() = text.trim().isNotEmpty()
    val isReply: Boolean get() = replyTo != null
    val hasReactions: Boolean get() = reactions.isNotEmpty()

    fun reaction(userId: String?): String? = userId?.let(reactions::get)

    fun reactionSummaries(currentUserId: String?): List<ChatReactionSummary> {
        if (reactions.isEmpty()) return emptyList()
        val mine = reaction(currentUserId)
        return reactions.values
            .groupingBy { it }
            .eachCount()
            .map { (emoji, count) ->
                ChatReactionSummary(
                    emoji = emoji,
                    count = count,
                    reactedByCurrentUser = emoji == mine,
                )
            }
            .sortedWith(
                compareByDescending<ChatReactionSummary> { it.count }
                    .thenBy { summary ->
                        ChatReaction.available.indexOf(summary.emoji).takeIf { it >= 0 } ?: Int.MAX_VALUE
                    }
                    .thenBy { it.emoji },
            )
    }

    /**
     * Whether [userId] may edit this message right now. Only the author, only
     * plain-text messages (no attachment), only while not deleted, and only
     * within [EDIT_WINDOW_MS] of sending.
     */
    fun isEditable(userId: String?, now: Date = Date()): Boolean {
        if (userId == null || senderId != userId || isDeleted || attachment != null) return false
        val created = createdAt ?: return false
        return now.time - created.time <= EDIT_WINDOW_MS
    }

    /**
     * True when this message should notify [userId] (a direct mention or
     * `@everyone`). Deleted messages never notify.
     */
    fun notifies(userId: String): Boolean {
        if (isDeleted) return false
        return mentions.any { it.userId == userId || it.isEveryone }
    }

    companion object {
        const val CLIENT_TEXT_CAP = 500

        /** Author edit window (15 minutes), matching iOS `ChatMessage.editWindow`. */
        const val EDIT_WINDOW_MS = 15L * 60 * 1000
    }
}

internal fun Map<String, Any?>.toChatMessageOrNull(documentId: String): ChatMessage? {
    val campingId = stringValue("campingID") ?: return null
    val senderId = stringValue("senderID") ?: return null
    val senderName = stringValue("senderName") ?: return null
    // `text` is required as a String but may be "" — voice notes carry no text.
    val text = rawStringValue("text") ?: return null
    return ChatMessage(
        id = documentId,
        campingId = campingId,
        senderId = senderId,
        senderName = senderName,
        text = text,
        teamId = stringValue("teamID"),
        staffRoleId = stringValue("staffRoleID"),
        senderChurch = rawStringValue("senderChurch").orEmpty(),
        senderPreferredLanguage = rawStringValue("senderPreferredLanguage").orEmpty(),
        senderGender = UserGender.fromWire(stringValue("senderGender")),
        senderPhotoUrl = stringValue("senderPhotoURL"),
        createdAt = dateValue("createdAt"),
        updatedAt = dateValue("updatedAt"),
        pinned = boolValue("pinned") ?: false,
        isDeleted = boolValue("isDeleted") ?: false,
        deletedById = stringValue("deletedByID"),
        deletedAt = dateValue("deletedAt"),
        mentions = mapListValue("mentions").mapNotNull { it.toChatMentionOrNull() },
        attachment = toChatAttachmentOrNull(),
        editedAt = dateValue("editedAt"),
        replyTo = toChatReplyReferenceOrNull(),
        reactions = mapValue("reactions")
            .orEmpty()
            .mapNotNull { (userId, emoji) -> (emoji as? String)?.takeIf { it.isNotBlank() }?.let { userId to it } }
            .toMap(),
    )
}

internal fun Map<String, Any?>.toChatMentionOrNull(): ChatMention? {
    val userId = stringValue("userID") ?: return null
    val displayName = rawStringValue("displayName") ?: return null
    val offset = intValue("offset") ?: return null
    val length = intValue("length") ?: return null
    return ChatMention(userId = userId, displayName = displayName, offset = offset, length = length)
}

private fun Map<String, Any?>.toChatAttachmentOrNull(): ChatAttachment? {
    val kind = ChatAttachmentKind.fromWire(stringValue("attachmentKind")) ?: return null
    val url = stringValue("attachmentURL") ?: return null
    return ChatAttachment(
        kind = kind,
        url = url,
        publicId = stringValue("attachmentPublicID"),
        durationSeconds = doubleValue("attachmentDuration"),
        width = intValue("attachmentWidth"),
        height = intValue("attachmentHeight"),
    )
}

private fun Map<String, Any?>.toChatReplyReferenceOrNull(): ChatReplyReference? {
    val raw = mapValue("replyTo") ?: return null
    val messageId = raw.stringValue("messageID") ?: return null
    val senderId = raw.stringValue("senderID") ?: return null
    val senderName = raw.rawStringValue("senderName") ?: return null
    return ChatReplyReference(
        messageId = messageId,
        senderId = senderId,
        senderName = senderName,
        textPreview = raw.rawStringValue("textPreview")?.takeIf { it.isNotBlank() },
        mediaType = ChatAttachmentKind.fromWire(raw.stringValue("mediaType")),
        mediaUrl = raw.stringValue("mediaURL"),
    )
}

internal object ChatMessagePayload {

    /**
     * Full-set send. `teamID` is included only for team chat and `staffRoleID`
     * only for staff-role chat. `text` is written unmodified so persisted
     * @mention offsets stay aligned (the composer caps input at
     * [ChatMessage.CLIENT_TEXT_CAP]). Mentions/attachment are written only when
     * present; mentions also write the flat `mentionedUserIDs` list (required
     * alongside `mentions` by the security rules).
     */
    fun sendPayload(
        message: ChatMessage,
        serverTimestamp: Any,
        isTeamChat: Boolean,
        isStaffRoleChat: Boolean = false,
    ): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "campingID" to message.campingId,
            "senderID" to message.senderId,
            "senderName" to message.senderName,
            "senderChurch" to message.senderChurch,
            "senderPreferredLanguage" to message.senderPreferredLanguage,
            "text" to message.text,
            "createdAt" to serverTimestamp,
            "pinned" to message.pinned,
            "isDeleted" to false,
        )
        if (isTeamChat) {
            message.teamId?.trim()?.takeUnless { it.isBlank() }?.let { payload["teamID"] = it }
        }
        if (isStaffRoleChat) {
            message.staffRoleId?.trim()?.takeUnless { it.isBlank() }?.let { payload["staffRoleID"] = it }
        }
        message.senderGender?.let { payload["senderGender"] = it.wireValue }
        message.senderPhotoUrl?.trim()?.takeUnless { it.isBlank() }?.let { payload["senderPhotoURL"] = it }
        applyMentions(payload, message.mentions)
        applyAttachment(payload, message.attachment)
        message.replyTo?.let { payload["replyTo"] = replyMap(it) }
        return payload
    }

    fun pinPayload(pinned: Boolean): Map<String, Any?> =
        linkedMapOf("pinned" to pinned)

    fun softDeletePayload(
        deletedById: String,
        serverTimestamp: Any,
    ): Map<String, Any?> =
        linkedMapOf(
            "isDeleted" to true,
            "deletedByID" to deletedById,
            "deletedAt" to serverTimestamp,
        )

    /**
     * Edit a text message: new text + re-resolved mentions + `editedAt`. When no
     * mentions remain, both `mentions` and `mentionedUserIDs` are removed via
     * [deleteValue] (`FieldValue.delete()`) so a stale set never lingers.
     */
    fun editPayload(
        newText: String,
        mentions: List<ChatMention>,
        serverTimestamp: Any,
        deleteValue: Any,
    ): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "text" to newText,
            "editedAt" to serverTimestamp,
        )
        if (mentions.isEmpty()) {
            payload["mentions"] = deleteValue
            payload["mentionedUserIDs"] = deleteValue
        } else {
            payload["mentions"] = mentions.map(::mentionMap)
            payload["mentionedUserIDs"] = mentions.map { it.userId }
        }
        return payload
    }

    private fun applyMentions(payload: MutableMap<String, Any?>, mentions: List<ChatMention>) {
        if (mentions.isEmpty()) return
        payload["mentions"] = mentions.map(::mentionMap)
        payload["mentionedUserIDs"] = mentions.map { it.userId }
    }

    private fun applyAttachment(payload: MutableMap<String, Any?>, attachment: ChatAttachment?) {
        attachment ?: return
        payload["attachmentKind"] = attachment.kind.wireValue
        payload["attachmentURL"] = attachment.url
        attachment.publicId?.let { payload["attachmentPublicID"] = it }
        attachment.durationSeconds?.let { payload["attachmentDuration"] = it }
        attachment.width?.let { payload["attachmentWidth"] = it }
        attachment.height?.let { payload["attachmentHeight"] = it }
    }

    private fun replyMap(reply: ChatReplyReference): Map<String, Any?> =
        linkedMapOf<String, Any?>(
            "messageID" to reply.messageId,
            "senderID" to reply.senderId,
            "senderName" to reply.senderName,
        ).apply {
            reply.textPreview?.let { put("textPreview", it) }
            reply.mediaType?.let { put("mediaType", it.wireValue) }
            reply.mediaUrl?.let { put("mediaURL", it) }
        }

    private fun mentionMap(mention: ChatMention): Map<String, Any?> =
        linkedMapOf(
            "userID" to mention.userId,
            "displayName" to mention.displayName,
            "offset" to mention.offset,
            "length" to mention.length,
        )
}
