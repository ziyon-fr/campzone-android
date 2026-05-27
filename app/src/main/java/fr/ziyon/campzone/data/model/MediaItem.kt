package fr.ziyon.campzone.data.model

import fr.ziyon.campzone.core.permissions.UserRole
import java.util.Date

/**
 * `campings/{id}/media/{mediaId}` (`02-firestore-schema.md` §7.6). Full `set`,
 * ordered `uploadedAt` desc. Decode drops the doc if any required field is
 * missing.
 */
data class MediaItem(
    val id: String,
    val campingId: String,
    val kind: MediaKind,
    val secureUrl: String,
    val publicId: String,
    val uploaderId: String,
    val uploaderName: String,
    val caption: String = "",
    val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationSeconds: Double? = null,
    val uploadedAt: Date? = null,
)

/** `campings/{id}/albumSettings/default` - single doc. */
data class AlbumSettings(
    val allowedUploadRoles: List<UserRole> = DEFAULT_ROLES,
) {
    fun allows(role: UserRole): Boolean = allowedUploadRoles.contains(role)

    companion object {
        val DEFAULT_ROLES: List<UserRole> = listOf(
            UserRole.Admin,
            UserRole.Leader,
            UserRole.Pastor,
            UserRole.Photographer,
            UserRole.YouthDirector,
        )
    }
}

internal fun Map<String, Any?>.toMediaItemOrNull(documentId: String): MediaItem? {
    val kind = MediaKind.fromWire(stringValue("kind")) ?: return null
    val secureUrl = stringValue("secureURL") ?: return null
    val publicId = stringValue("publicID") ?: return null
    val uploaderId = stringValue("uploaderID") ?: return null
    val uploaderName = stringValue("uploaderName") ?: return null
    return MediaItem(
        id = stringValue("id") ?: documentId,
        campingId = stringValue("campingID").orEmpty(),
        kind = kind,
        secureUrl = secureUrl,
        publicId = publicId,
        uploaderId = uploaderId,
        uploaderName = uploaderName,
        caption = rawStringValue("caption").orEmpty(),
        thumbnailUrl = stringValue("thumbnailURL"),
        width = intValue("width"),
        height = intValue("height"),
        durationSeconds = doubleValue("durationSeconds"),
        uploadedAt = dateValue("uploadedAt"),
    )
}

internal fun Map<String, Any?>.toAlbumSettings(): AlbumSettings {
    val roles = rawStringListValue("allowedUploadRoles").map(UserRole::fromWire)
    return AlbumSettings(
        allowedUploadRoles = roles.ifEmpty { AlbumSettings.DEFAULT_ROLES },
    )
}

internal object MediaPayload {
    fun mediaPayload(
        media: MediaItem,
        serverTimestamp: Any,
    ): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "id" to media.id,
            "campingID" to media.campingId,
            "kind" to media.kind.wireValue,
            "secureURL" to media.secureUrl,
            "publicID" to media.publicId,
            "uploaderID" to media.uploaderId,
            "uploaderName" to media.uploaderName,
            "caption" to media.caption,
            "uploadedAt" to serverTimestamp,
        )
        media.thumbnailUrl?.trim()?.takeUnless { it.isBlank() }?.let { payload["thumbnailURL"] = it }
        media.width?.let { payload["width"] = it }
        media.height?.let { payload["height"] = it }
        media.durationSeconds?.let { payload["durationSeconds"] = it }
        return payload
    }

    fun albumSettingsPayload(settings: AlbumSettings): Map<String, Any?> =
        linkedMapOf(
            "allowedUploadRoles" to settings.allowedUploadRoles
                .map { it.rawValue }
                .distinct()
                .sorted(),
        )
}
