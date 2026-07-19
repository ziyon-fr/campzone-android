package fr.ziyon.campzone.data.model

import com.google.firebase.Timestamp
import fr.ziyon.campzone.core.navigation.CampzoneDeepLink
import fr.ziyon.campzone.core.permissions.UserRole
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
    val programId: String? = null,
    val pollId: String? = null,
    val teamId: String? = null,
    val staffRoleId: String? = null,
    val role: String? = null,
    val senderId: String? = null,
    val messageId: String? = null,
    val event: String? = null,
    val recipientUserId: String? = null,
    val registrationId: String? = null,
    val achievementId: String? = null,
    val vehicleId: String? = null,
    val shareId: String? = null,
    val deepLinkUrl: String? = null,
    val mentionedUserIds: List<String> = emptyList(),
) {
    val feedDeduplicationKey: String
        get() = if (kind == AppNotificationKind.Announcement && !announcementId.isNullOrBlank()) {
            "$appId:announcement:$announcementId"
        } else {
            "$appId:document:$id"
        }

    /**
     * Whether this notification should appear in [role]'s feed, given the set
     * of topics that user is subscribed to. Mirrors iOS `concerns(user:visibleTopics:)`:
     * role-scoped rows are gated to the matching role topic and either the
     * matching role or admin; `@mention` rows are personal to `mentionedUserIds`.
     */
    fun concerns(userId: String, role: UserRole, visibleTopics: Set<String>): Boolean {
        if (appId != APP_ID) return false

        this.role?.let { roleRaw ->
            val roleTopic = NotificationTopics.roleTopic(roleRaw)
            if (!visibleTopics.contains(roleTopic)) return false
            if (!(role.isAdmin || roleRaw == role.rawValue)) return false
        }

        if (kind == AppNotificationKind.ChatMention && mentionedUserIds.isNotEmpty()) {
            if (!mentionedUserIds.contains(userId)) return false
        }

        recipientUserId?.let { recipient ->
            if (recipient != userId) return false
        }

        return visibleTopics.contains(topic)
    }

    /**
     * The deep-link destination for a tapped feed row, derived from the
     * notification's kind + ids. Mirrors iOS `AppNotification.deepLink`.
     */
    fun deepLink(): CampzoneDeepLink? {
        CampzoneDeepLink.fromCampzoneUrl(deepLinkUrl)?.let { return it }

        return when (kind) {
            AppNotificationKind.Announcement ->
                announcementId?.let(CampzoneDeepLink::Announcement)

            AppNotificationKind.Badge -> recipientUserId?.let { userId ->
                if (achievementId == null) {
                    CampzoneDeepLink.Achievements(userId, null, null, campingId)
                } else {
                    CampzoneDeepLink.Achievement(userId, null, null, campingId, achievementId)
                }
            }

            AppNotificationKind.ChatMessage, AppNotificationKind.ChatMention -> campingId?.let {
                when {
                    staffRoleId != null -> null
                    teamId != null -> CampzoneDeepLink.TeamChat(it, teamId)
                    else -> CampzoneDeepLink.CampingChat(it)
                }
            }

            AppNotificationKind.Checklist -> if (campingId != null && shareId != null) {
                CampzoneDeepLink.PackingShare(
                    campingId = campingId,
                    shareId = shareId,
                    registrationId = registrationId,
                )
            } else {
                null
            }

            AppNotificationKind.Poll ->
                campingId?.let { CampzoneDeepLink.Poll(campingId = it, pollId = pollId) }

            AppNotificationKind.Registration -> campingId?.let {
                if (event.equals("approved", ignoreCase = true)) {
                    CampzoneDeepLink.Camping(it)
                } else {
                    CampzoneDeepLink.RegistrationReview(it)
                }
            }

            AppNotificationKind.TeamUpdate -> campingId?.let {
                when {
                    isPointEvent(event) -> CampzoneDeepLink.TeamPoints(campingId = it, teamId = teamId)
                    teamId != null -> CampzoneDeepLink.TeamUpdate(campingId = it, teamId = teamId)
                    else -> CampzoneDeepLink.Camping(it)
                }
            }

            AppNotificationKind.ScheduleReminder -> campingId?.let {
                if (programId.isNullOrBlank()) CampzoneDeepLink.Camping(it) else CampzoneDeepLink.ScheduleProgram(it, programId)
            }

            AppNotificationKind.Transportation -> campingId?.let {
                when (event?.trim()?.lowercase()) {
                    "invitation", "invited" -> if (vehicleId != null && registrationId != null) {
                        CampzoneDeepLink.TransportationInvitation(it, vehicleId, registrationId)
                    } else CampzoneDeepLink.Transportation(it)
                    "join_request", "request" -> if (vehicleId != null && registrationId != null) {
                        CampzoneDeepLink.TransportationRequest(it, vehicleId, registrationId)
                    } else CampzoneDeepLink.Transportation(it)
                    else -> CampzoneDeepLink.Transportation(it)
                }
            }

            AppNotificationKind.Unknown -> null
        }
    }

    companion object {
        const val APP_ID = "campzone"
        val DISTANT_PAST = Date(0)

        private val POINT_EVENTS = setOf("scorechanged", "memberscorechanged", "penaltyapplied")

        private fun isPointEvent(event: String?): Boolean =
            event?.trim()?.lowercase() in POINT_EVENTS
    }
}

fun AppNotification.isPreferredFeedRepresentativeOver(existing: AppNotification): Boolean =
    when {
        sentAt != existing.sentAt -> sentAt.after(existing.sentAt)
        else -> id < existing.id
    }

/** Returns null for docs whose `appID` is not `"campzone"` (filtered client-side). */
internal fun Map<String, Any?>.toAppNotificationOrNull(documentId: String): AppNotification? {
    val appId = stringValue("appID") ?: AppNotification.APP_ID
    if (appId != AppNotification.APP_ID) return null

    val announcementId = stringValue("announcementID")
    val campingId = stringValue("campingID")
    val programId = stringValue("programID")
    val pollId = stringValue("pollID")
    val shareId = firstStringValue("shareID", "packingShareID")
    val registrationId = firstStringValue("actionSubjectRegistrationID", "registrationID")
    val createdAt = isoOrTimestamp("createdAt")
    val explicitKind = AppNotificationKind.fromWire(stringValue("kind"))
        ?: AppNotificationKind.fromWire(stringValue("type"))
    val kind = explicitKind ?: when {
        announcementId != null -> AppNotificationKind.Announcement
        shareId != null -> AppNotificationKind.Checklist
        programId != null -> AppNotificationKind.ScheduleReminder
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
        programId = programId,
        pollId = pollId,
        teamId = stringValue("teamID"),
        staffRoleId = stringValue("staffRoleID"),
        role = stringValue("role") ?: NotificationTopics.roleFromTopic(rawStringValue("topic").orEmpty()),
        senderId = stringValue("senderId"),
        messageId = stringValue("messageId"),
        event = stringValue("event"),
        recipientUserId = stringValue("recipientUserID"),
        registrationId = registrationId,
        achievementId = firstStringValue("achievementID", "badgeID"),
        vehicleId = stringValue("vehicleID"),
        shareId = shareId,
        deepLinkUrl = firstStringValue("deepLink", "deeplink", "deep_link", "deepLinkURL", "deepLinkUrl", "url", "link"),
        mentionedUserIds = stringListValue("mentionedUserIDs"),
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

private fun Map<String, Any?>.firstStringValue(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key -> stringValue(key) }
