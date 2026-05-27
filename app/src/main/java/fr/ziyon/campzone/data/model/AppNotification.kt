package fr.ziyon.campzone.data.model

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * `ziyon_notifications/{id}` (`02-firestore-schema.md` §6.5) - the in-app feed.
 * **Backend-written, client read-only** (no payload). `appID` must be
 * `"campzone"` or the doc is ignored. `sentAt`/`createdAt` are ISO-8601 strings
 * (also accepts Timestamp/Date). Tolerant decoder.
 */
data class AppNotification(
    val id: String,
    val appId: String,
    val kind: AppNotificationKind,
    val title: String,
    val body: String,
    val topic: String,
    val sentAt: Date,
    val createdAt: Date? = null,
    val announcementId: String? = null,
    val campingId: String? = null,
    val pollId: String? = null,
    val teamId: String? = null,
    val role: String? = null,
    val senderId: String? = null,
    val messageId: String? = null,
) {
    companion object {
        const val APP_ID = "campzone"
        val DISTANT_PAST = Date(0)
    }
}

/** Returns null for docs whose `appID` is not `"campzone"` (filtered client-side). */
internal fun Map<String, Any?>.toAppNotificationOrNull(documentId: String): AppNotification? {
    val appId = stringValue("appID") ?: AppNotification.APP_ID
    if (appId != AppNotification.APP_ID) return null

    val announcementId = stringValue("announcementID")
    val campingId = stringValue("campingID")
    val pollId = stringValue("pollID")
    val createdAt = isoOrTimestamp("createdAt")
    val explicitKind = AppNotificationKind.fromWire(stringValue("kind"))
        ?: AppNotificationKind.fromWire(stringValue("type"))
    val kind = explicitKind ?: when {
        announcementId != null -> AppNotificationKind.Announcement
        pollId != null -> AppNotificationKind.Poll
        campingId != null -> AppNotificationKind.ChatMessage
        else -> AppNotificationKind.Unknown
    }

    return AppNotification(
        id = stringValue("id") ?: documentId,
        appId = appId,
        kind = kind,
        title = stringValue("title") ?: "Notification",
        body = rawStringValue("body").orEmpty(),
        topic = rawStringValue("topic").orEmpty(),
        sentAt = isoOrTimestamp("sentAt") ?: createdAt ?: AppNotification.DISTANT_PAST,
        createdAt = createdAt,
        announcementId = announcementId,
        campingId = campingId,
        pollId = pollId,
        teamId = stringValue("teamID"),
        role = stringValue("role"),
        senderId = stringValue("senderId"),
        messageId = stringValue("messageId"),
    )
}

private fun Map<String, Any?>.isoOrTimestamp(key: String): Date? =
    when (val value = this[key]) {
        is Timestamp -> value.toDate()
        is Date -> value
        is String -> parseIso8601(value)
        else -> null
    }

private fun parseIso8601(raw: String): Date? {
    val text = raw.trim().ifBlank { return null }
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
    )
    for (pattern in patterns) {
        runCatching {
            val format = SimpleDateFormat(pattern, Locale.US)
            if (pattern.endsWith("'Z'")) format.timeZone = TimeZone.getTimeZone("UTC")
            return format.parse(text)
        }
    }
    return null
}
