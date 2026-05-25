package fr.ziyon.campzone.data.model

import fr.ziyon.campzone.data.auth.UserGender
import java.util.Date

/**
 * `campings/{id}/chat/{messageId}` and the team variant
 * (`02-firestore-schema.md` §6.1). Doc ID is a client UUID; `senderID ==
 * auth.uid`. Send is a full `set` (no merge); pin / soft-delete are
 * `updateData`. `teamID` is written only in team chat. Decode drops the message
 * if `campingID`/`senderID`/`senderName`/`text` is missing.
 */
data class ChatMessage(
    val id: String,
    val campingId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val teamId: String? = null,
    val senderChurch: String = "",
    val senderPreferredLanguage: String = "",
    val senderGender: UserGender? = null,
    val senderPhotoUrl: String? = null,
    val createdAt: Date? = null,
    val pinned: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedById: String? = null,
    val deletedAt: Date? = null,
) {
    companion object {
        const val CLIENT_TEXT_CAP = 500
    }
}

internal fun Map<String, Any?>.toChatMessageOrNull(documentId: String): ChatMessage? {
    val campingId = stringValue("campingID") ?: return null
    val senderId = stringValue("senderID") ?: return null
    val senderName = stringValue("senderName") ?: return null
    val text = rawStringValue("text")?.takeUnless { it.isBlank() } ?: return null
    return ChatMessage(
        id = documentId,
        campingId = campingId,
        senderId = senderId,
        senderName = senderName,
        text = text,
        teamId = stringValue("teamID"),
        senderChurch = rawStringValue("senderChurch").orEmpty(),
        senderPreferredLanguage = rawStringValue("senderPreferredLanguage").orEmpty(),
        senderGender = UserGender.fromWire(stringValue("senderGender")),
        senderPhotoUrl = stringValue("senderPhotoURL"),
        createdAt = dateValue("createdAt"),
        pinned = boolValue("pinned") ?: false,
        isDeleted = boolValue("isDeleted") ?: false,
        deletedById = stringValue("deletedByID"),
        deletedAt = dateValue("deletedAt"),
    )
}

internal object ChatMessagePayload {

    /** Full-set send. `teamID` included only for team chat. */
    fun sendPayload(
        message: ChatMessage,
        serverTimestamp: Any,
        isTeamChat: Boolean,
    ): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "campingID" to message.campingId,
            "senderID" to message.senderId,
            "senderName" to message.senderName,
            "senderChurch" to message.senderChurch,
            "senderPreferredLanguage" to message.senderPreferredLanguage,
            "text" to message.text.take(ChatMessage.CLIENT_TEXT_CAP),
            "createdAt" to serverTimestamp,
            "pinned" to false,
            "isDeleted" to false,
        )
        if (isTeamChat) {
            message.teamId?.trim()?.takeUnless { it.isBlank() }?.let { payload["teamID"] = it }
        }
        message.senderGender?.let { payload["senderGender"] = it.wireValue }
        message.senderPhotoUrl?.trim()?.takeUnless { it.isBlank() }?.let { payload["senderPhotoURL"] = it }
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
}
