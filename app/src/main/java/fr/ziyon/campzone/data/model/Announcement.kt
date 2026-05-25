package fr.ziyon.campzone.data.model

import fr.ziyon.campzone.core.permissions.UserRole
import java.util.Date

/**
 * `announcements/{announcementId}` (`02-firestore-schema.md` §6.2). Top-level,
 * read ordered `createdAt` desc, limit 100. `notificationTargetRoleRawValue` is
 * written as `""` when none (not omitted); `authorPhotoURL` is omit-when-nil.
 */
data class Announcement(
    val id: String,
    val title: String = "",
    val body: String = "",
    val notificationTargetRole: UserRole? = null,
    val authorId: String = "",
    val authorName: String = DEFAULT_AUTHOR,
    val authorPhotoUrl: String? = null,
    val attachments: List<AnnouncementAttachment> = emptyList(),
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
) {
    companion object {
        const val DEFAULT_AUTHOR = "Campzone Team"
    }
}

data class AnnouncementAttachment(
    val id: String,
    val kind: AnnouncementAttachmentKind,
    val fileName: String,
    val contentType: String,
    val storagePath: String,
    val downloadUrl: String,
)

internal fun Map<String, Any?>.toAnnouncement(documentId: String): Announcement {
    val roleRaw = rawStringValue("notificationTargetRoleRawValue")?.takeUnless { it.isBlank() }
    return Announcement(
        id = stringValue("id") ?: documentId,
        title = rawStringValue("title").orEmpty(),
        body = rawStringValue("body") ?: rawStringValue("description").orEmpty(),
        notificationTargetRole = roleRaw?.let(UserRole::fromWire),
        authorId = stringValue("authorID").orEmpty(),
        authorName = stringValue("authorName") ?: Announcement.DEFAULT_AUTHOR,
        authorPhotoUrl = stringValue("authorPhotoURL"),
        attachments = mapListValue("attachments").mapNotNull { it.toAnnouncementAttachmentOrNull() },
        createdAt = dateValue("createdAt"),
        updatedAt = dateValue("updatedAt"),
    )
}

internal fun Map<String, Any?>.toAnnouncementAttachmentOrNull(): AnnouncementAttachment? {
    val id = stringValue("id") ?: return null
    return AnnouncementAttachment(
        id = id,
        kind = AnnouncementAttachmentKind.fromWire(stringValue("kind")),
        fileName = rawStringValue("fileName").orEmpty(),
        contentType = rawStringValue("contentType").orEmpty(),
        storagePath = rawStringValue("storagePath").orEmpty(),
        downloadUrl = rawStringValue("downloadURL").orEmpty(),
    )
}

internal object AnnouncementPayload {

    fun announcementPayload(
        announcement: Announcement,
        serverTimestamp: Any,
        includeCreatedAt: Boolean,
    ): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "title" to announcement.title.trim(),
            "body" to announcement.body,
            "notificationTargetRoleRawValue" to (announcement.notificationTargetRole?.rawValue ?: ""),
            "authorID" to announcement.authorId,
            "authorName" to announcement.authorName,
            "attachments" to announcement.attachments.map(::attachmentMap),
            "updatedAt" to serverTimestamp,
        )
        announcement.authorPhotoUrl?.trim()?.takeUnless { it.isBlank() }
            ?.let { payload["authorPhotoURL"] = it }
        if (includeCreatedAt) payload["createdAt"] = serverTimestamp
        return payload
    }

    fun attachmentMap(attachment: AnnouncementAttachment): Map<String, Any?> =
        linkedMapOf(
            "id" to attachment.id,
            "kind" to attachment.kind.wireValue,
            "fileName" to attachment.fileName,
            "contentType" to attachment.contentType,
            "storagePath" to attachment.storagePath,
            "downloadURL" to attachment.downloadUrl,
        )
}
