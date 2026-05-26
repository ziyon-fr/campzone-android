package fr.ziyon.campzone.data.model

import fr.ziyon.campzone.core.permissions.UserRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AnnouncementAudienceScope(val rawValue: String) {
    App("app"),
    Camping("camping");

    companion object {
        fun fromWire(value: String?): AnnouncementAudienceScope =
            entries.firstOrNull { it.rawValue == value } ?: App
    }
}

/**
 * `announcements/{announcementId}` (`02-firestore-schema.md` §6.2). Top-level,
 * read ordered `createdAt` desc, limit 100. `notificationTargetRoleRawValue` is
 * written as `""` when none (not omitted); `authorPhotoURL` is omit-when-nil.
 */
data class Announcement(
    val id: String,
    val title: String = "",
    val body: String = "",
    val audienceScopeRawValue: String = AnnouncementAudienceScope.App.rawValue,
    val campingId: String? = null,
    val campingTitle: String? = null,
    val notificationTargetRole: UserRole? = null,
    val authorId: String = "",
    val authorName: String = DEFAULT_AUTHOR,
    val authorPhotoUrl: String? = null,
    val attachments: List<AnnouncementAttachment> = emptyList(),
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
) {
    val audienceScope: AnnouncementAudienceScope
        get() = AnnouncementAudienceScope.fromWire(audienceScopeRawValue)

    val targetCampingId: String?
        get() {
            if (audienceScope != AnnouncementAudienceScope.Camping) return null
            return campingId?.trim()?.takeUnless { it.isBlank() }
        }

    val targetCampingTitle: String?
        get() = campingTitle?.trim()?.takeUnless { it.isBlank() }

    val audienceText: String
        get() = when (audienceScope) {
            AnnouncementAudienceScope.App ->
                notificationTargetRole?.displayName ?: "All app users"
            AnnouncementAudienceScope.Camping -> {
                val camping = targetCampingTitle ?: "Camping"
                notificationTargetRole?.let { "$camping · ${it.displayName}" } ?: camping
            }
        }

    val summary: String
        get() {
            val stripped = body
                .lines()
                .joinToString(" ") { line ->
                    val patterns = listOf(
                        Regex("^#{1,6}\\s+"),
                        Regex("^>\\s*"),
                        Regex("^[-*+]\\s+"),
                        Regex("^\\d+\\.\\s+"),
                    )
                    patterns.firstNotNullOfOrNull { it.find(line)?.let { m -> line.substring(m.range.last + 1) } }
                        ?: line
                }
                .replace(Regex("!\\[([^\\]]*)]\\([^)]+\\)"), "$1")
                .replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
                .replace(Regex("(\\*{1,3}|_{1,3})(.+?)\\1"), "$2")
                .replace(Regex("`([^`]+)`"), "$1")
                .replace(Regex("\\s{2,}"), " ")
                .trim()
            return if (stripped.length > 120) "${stripped.take(117)}…" else stripped
        }

    val wasEdited: Boolean
        get() {
            val c = createdAt ?: return false
            val u = updatedAt ?: return false
            return (u.time - c.time) > 60_000
        }

    val createdDateText: String
        get() = createdAt?.let(dateFormatter::format) ?: ""

    val updatedDateText: String
        get() = updatedAt?.let(dateFormatter::format) ?: ""

    fun isVisible(
        userRoleRawValue: String?,
        visibleCampingIds: Set<String>,
        canViewAll: Boolean,
    ): Boolean {
        if (canViewAll) return true
        val targetRole = notificationTargetRole?.rawValue?.trim()
        if (!targetRole.isNullOrBlank() && targetRole != userRoleRawValue) return false
        if (audienceScope != AnnouncementAudienceScope.Camping) return true
        val camping = targetCampingId ?: return true
        return visibleCampingIds.contains(camping)
    }

    companion object {
        const val DEFAULT_AUTHOR = "Campzone Team"
        private val dateFormatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
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

/** In-progress attachment not yet uploaded to Cloudinary. */
data class PendingAnnouncementAttachment(
    val id: String,
    val kind: AnnouncementAttachmentKind,
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?) = other is PendingAnnouncementAttachment && id == other.id
    override fun hashCode() = id.hashCode()
}

/** Wire-ready draft passed to [AnnouncementService.saveAnnouncement]. */
data class AnnouncementDraft(
    val id: String,
    val title: String,
    val body: String,
    val audienceScopeRawValue: String,
    val campingId: String?,
    val campingTitle: String?,
    val notificationTargetRoleRawValue: String?,
    val authorId: String,
    val authorName: String,
    val authorPhotoUrl: String?,
    val existingAttachments: List<AnnouncementAttachment>,
    val pendingAttachments: List<PendingAnnouncementAttachment>,
)

internal fun Map<String, Any?>.toAnnouncement(documentId: String): Announcement {
    val roleRaw = rawStringValue("notificationTargetRoleRawValue")?.takeUnless { it.isBlank() }
    return Announcement(
        id = stringValue("id") ?: documentId,
        title = rawStringValue("title").orEmpty(),
        body = rawStringValue("body") ?: rawStringValue("description").orEmpty(),
        audienceScopeRawValue = rawStringValue("audienceScopeRawValue")
            ?: AnnouncementAudienceScope.App.rawValue,
        campingId = stringValue("campingID"),
        campingTitle = stringValue("campingTitle"),
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

    fun draftPayload(
        draft: AnnouncementDraft,
        serverTimestamp: Any,
        includeCreatedAt: Boolean,
        attachments: List<AnnouncementAttachment>,
    ): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "title" to draft.title,
            "body" to draft.body,
            "audienceScopeRawValue" to draft.audienceScopeRawValue,
            "campingID" to (draft.campingId ?: ""),
            "campingTitle" to (draft.campingTitle ?: ""),
            "notificationTargetRoleRawValue" to (draft.notificationTargetRoleRawValue ?: ""),
            "authorID" to draft.authorId,
            "authorName" to draft.authorName,
            "attachments" to attachments.map(::attachmentMap),
            "updatedAt" to serverTimestamp,
        )
        draft.authorPhotoUrl?.trim()?.takeUnless { it.isBlank() }
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
