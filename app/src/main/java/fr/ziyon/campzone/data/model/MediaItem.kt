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
    val source: MediaSource = MediaSource.Cloudinary,
    val secureUrl: String,
    val externalUrl: String? = null,
    val publicId: String?,
    val uploaderId: String,
    val uploaderName: String,
    val caption: String = "",
    val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationSeconds: Double? = null,
    val uploadedAt: Date? = null,
) {
    val playbackUrl: String
        get() = externalUrl ?: secureUrl

    val opensExternally: Boolean
        get() = source == MediaSource.ExternalVideo

    val displayThumbnailUrl: String?
        get() = thumbnailUrl
            ?: if (source == MediaSource.ExternalVideo) {
                null
            } else if (kind == MediaKind.Photo) {
                CloudinaryMediaUrl.thumbnailUrl(secureUrl)
            } else {
                secureUrl
            }
}

enum class MediaSource(val wireValue: String) {
    Cloudinary("cloudinary"),
    ExternalVideo("externalVideo");

    companion object {
        fun fromWire(value: String?): MediaSource? = entries.firstOrNull { it.wireValue == value }
    }
}

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
    val source = MediaSource.fromWire(stringValue("source"))
        ?: if (stringValue("externalURL") != null) MediaSource.ExternalVideo else MediaSource.Cloudinary
    val secureUrl = stringValue("secureURL")
    val externalUrl = stringValue("externalURL")
    val publicId = stringValue("publicID")?.trim()?.takeUnless { it.isBlank() }
    val resolvedSecureUrl = when (source) {
        MediaSource.Cloudinary -> {
            if (publicId == null) return null
            secureUrl ?: return null
        }
        MediaSource.ExternalVideo -> externalUrl ?: secureUrl ?: return null
    }
    val uploaderId = stringValue("uploaderID") ?: return null
    val uploaderName = stringValue("uploaderName") ?: return null
    return MediaItem(
        id = stringValue("id") ?: documentId,
        campingId = stringValue("campingID").orEmpty(),
        kind = kind,
        source = source,
        secureUrl = resolvedSecureUrl,
        externalUrl = if (source == MediaSource.ExternalVideo) externalUrl ?: resolvedSecureUrl else null,
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
            "source" to media.source.wireValue,
            "secureURL" to media.secureUrl,
            "uploaderID" to media.uploaderId,
            "uploaderName" to media.uploaderName,
            "caption" to media.caption,
            "uploadedAt" to serverTimestamp,
        )
        media.externalUrl?.trim()?.takeUnless { it.isBlank() }?.let { payload["externalURL"] = it }
        media.publicId?.trim()?.takeUnless { it.isBlank() }?.let { payload["publicID"] = it }
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

private object CloudinaryMediaUrl {
    fun thumbnailUrl(secureUrl: String, maxWidth: Int = 600): String {
        val transformed = transformedUrl(secureUrl, "f_auto,q_auto,w_$maxWidth,c_limit")
        return transformed ?: secureUrl
    }

    private fun transformedUrl(secureUrl: String, transform: String): String? {
        val url = runCatching { java.net.URL(secureUrl) }.getOrNull() ?: return null
        val parts = url.path.split("/").filter { it.isNotBlank() }.toMutableList()
        val uploadIndex = parts.indexOf("upload")
        if (uploadIndex < 0) return null
        parts.add(uploadIndex + 1, transform)
        return "${url.protocol}://${url.host}/${parts.joinToString("/")}"
    }
}
