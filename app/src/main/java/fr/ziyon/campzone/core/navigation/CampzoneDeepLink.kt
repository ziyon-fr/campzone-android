package fr.ziyon.campzone.core.navigation

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

sealed interface CampzoneDeepLink {
    data class Announcement(val id: String) : CampzoneDeepLink
    data class Camping(val id: String) : CampzoneDeepLink
    data class CampingChat(val campingId: String) : CampzoneDeepLink

    data class TeamChat(
        val campingId: String,
        val teamId: String,
    ) : CampzoneDeepLink

    data class TeamUpdate(
        val campingId: String,
        val teamId: String,
    ) : CampzoneDeepLink

    data class TeamPoints(
        val campingId: String,
        val teamId: String?,
    ) : CampzoneDeepLink

    data class Poll(
        val campingId: String,
        val pollId: String?,
    ) : CampzoneDeepLink

    data class ScheduleProgram(
        val campingId: String,
        val programId: String,
    ) : CampzoneDeepLink

    data class RegistrationReview(val campingId: String) : CampzoneDeepLink

    data class Achievements(
        val userId: String,
        val displayName: String?,
        val photoUrl: String?,
        val campingId: String?,
    ) : CampzoneDeepLink

    data class Achievement(
        val userId: String,
        val displayName: String?,
        val photoUrl: String?,
        val campingId: String?,
        val achievementId: String,
    ) : CampzoneDeepLink

    data class Transportation(val campingId: String) : CampzoneDeepLink
    data class TransportationJoin(val campingId: String, val invitationCode: String) : CampzoneDeepLink
    data class TransportationInvitation(
        val campingId: String,
        val vehicleId: String,
        val registrationId: String,
    ) : CampzoneDeepLink
    data class TransportationRequest(
        val campingId: String,
        val vehicleId: String,
        val registrationId: String,
    ) : CampzoneDeepLink

    fun canonicalShareUrlOrNull(): String? = when (this) {
        is Announcement -> "https://${Companion.WebHost}/announcements/${id.asUrlSegment()}"
        is Camping -> "https://${Companion.WebHost}/campings/${id.asUrlSegment()}"
        is Achievements -> "https://${Companion.WebHost}/badges/${userId.asUrlSegment()}"
        is Achievement -> "https://${Companion.WebHost}/badges/${userId.asUrlSegment()}?achievementID=${achievementId.asUrlSegment()}"
        is TransportationJoin -> "https://${Companion.WebHost}/transportation-join/${campingId.asUrlSegment()}?code=${invitationCode.asUrlSegment()}"
        is CampingChat,
        is Poll,
        is RegistrationReview,
        is ScheduleProgram,
        is TeamChat,
        is TeamPoints,
        is TeamUpdate,
        is Transportation,
        is TransportationInvitation,
        is TransportationRequest,
        -> null
    }

    companion object {
        private const val CustomScheme = "campzone"
        const val WebHost = "campzone-web.vercel.app"
        private val PointEvents = setOf("scorechanged", "memberscorechanged", "penaltyapplied")
        private val ExplicitDeepLinkKeys = arrayOf(
            "deepLink",
            "deeplink",
            "deep_link",
            "deepLinkURL",
            "deepLinkUrl",
            "url",
            "link",
        )

        fun fromCampzoneUrl(url: String?): CampzoneDeepLink? {
            if (url.isNullOrBlank()) return null
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null

            val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
            val pathComponents = uri.rawPath
                ?.split("/")
                ?.mapNotNull { component ->
                    component.takeIf { it.isNotBlank() }?.decodeUrlComponentOrNull()
                }
                .orEmpty()
            val query = parseQuery(uri.rawQuery)

            val route: String
            val pathId: String?
            when {
                scheme == CustomScheme -> {
                    route = host
                    pathId = pathComponents.firstValue()
                }

                scheme == "https" && host == WebHost -> {
                    route = pathComponents.getOrNull(0)?.lowercase(Locale.ROOT).orEmpty()
                    pathId = pathComponents.getOrNull(1)?.takeUnless { it.isBlank() }
                }

                else -> return null
            }

            return when (route) {
                "camping", "campings" -> {
                    val id = pathId
                        ?: query.firstValue("id", "c", "campingID")
                        ?: return null
                    Camping(id)
                }

                "announcement", "announcements" -> {
                    val id = pathId
                        ?: query.firstValue("id", "announcementID")
                        ?: return null
                    Announcement(id)
                }

                "chat", "camping-chat" -> {
                    val campingId = pathId
                        ?: query.firstValue("id", "c", "campingID")
                        ?: return null
                    CampingChat(campingId)
                }

                "team-chat" -> {
                    val campingId = query.firstValue("c", "campingID") ?: return null
                    val teamId = pathId
                        ?: query.firstValue("teamID", "t")
                        ?: return null
                    TeamChat(campingId = campingId, teamId = teamId)
                }

                "team", "teams" -> {
                    val campingId = query.firstValue("c", "campingID") ?: return null
                    val teamId = pathId
                        ?: query.firstValue("teamID", "t")
                        ?: return null
                    TeamUpdate(campingId = campingId, teamId = teamId)
                }

                "points", "point-history" -> {
                    val campingId = pathId
                        ?: query.firstValue("id", "c", "campingID")
                        ?: return null
                    TeamPoints(
                        campingId = campingId,
                        teamId = query.firstValue("teamID", "t"),
                    )
                }

                "poll", "polls" -> {
                    val campingId = query.firstValue("c", "campingID")
                        ?: pathId
                        ?: return null
                    Poll(
                        campingId = campingId,
                        pollId = query.firstValue("pollID", "p"),
                    )
                }

                "schedule-program", "program", "programs" -> {
                    val programId = pathId
                        ?: query.firstValue("programID", "p")
                        ?: return null
                    val campingId = query.firstValue("c", "campingID") ?: return null
                    ScheduleProgram(campingId = campingId, programId = programId)
                }

                "registration", "registration-review" -> {
                    val campingId = pathId
                        ?: query.firstValue("id", "c", "campingID")
                        ?: return null
                    RegistrationReview(campingId)
                }

                "achievement", "achievements", "badge", "badges" -> {
                    val userId = pathId
                        ?: query.firstValue("id", "userID", "uid")
                        ?: return null
                    val achievementId = query.firstValue("achievementID", "badgeID", "a")
                    if (achievementId == null) {
                        Achievements(
                            userId = userId,
                            displayName = query.firstValue("displayName", "name"),
                            photoUrl = query.firstValue("photoURLString", "photoURL"),
                            campingId = query.firstValue("campingID", "c"),
                        )
                    } else {
                        Achievement(
                            userId = userId,
                            displayName = query.firstValue("displayName", "name"),
                            photoUrl = query.firstValue("photoURLString", "photoURL"),
                            campingId = query.firstValue("campingID", "c"),
                            achievementId = achievementId,
                        )
                    }
                }

                "transportation", "transport" -> {
                    val campingId = pathId ?: query.firstValue("campingID", "c") ?: return null
                    Transportation(campingId)
                }

                "transportation-join" -> {
                    val campingId = pathId ?: query.firstValue("campingID", "c") ?: return null
                    val code = query.firstValue("code", "invitationCode", "i")
                        ?.trimEnd('.', ',', ';', ':', '!', '?')
                        ?.takeUnless { it.isBlank() }
                        ?: return null
                    TransportationJoin(campingId, code)
                }

                "transportation-invitation", "transportation-request" -> {
                    val vehicleId = pathId ?: query.firstValue("vehicleID", "v") ?: return null
                    val campingId = query.firstValue("campingID", "c") ?: return null
                    val registrationId = query.firstValue("registrationID", "r") ?: return null
                    if (route == "transportation-invitation") {
                        TransportationInvitation(campingId, vehicleId, registrationId)
                    } else {
                        TransportationRequest(campingId, vehicleId, registrationId)
                    }
                }

                else -> null
            }
        }

        fun fromPayload(payload: Map<String, String?>): CampzoneDeepLink? {
            payload.firstExplicitDeepLink()?.let { return it }

            val type = payload.firstValue("type", "kind")?.lowercase(Locale.ROOT)
            val campingId = payload.firstValue("campingID")
            val teamId = payload.firstValue("teamID")
            val pollId = payload.firstValue("pollID")
            val programId = payload.firstValue("programID")
            val achievementId = payload.firstValue("achievementID", "badgeID")
            val vehicleId = payload.firstValue("vehicleID")
            val registrationId = payload.firstValue("registrationID")
            val announcementId = payload.firstValue("announcementID")
            val event = payload.firstValue("event")?.lowercase(Locale.ROOT)
            val recipientUserId = payload.firstValue("recipientUserID", "userID", "uid")
            val recipientDisplayName = payload.firstValue("recipientDisplayName", "displayName", "name")
            val recipientPhotoUrl = payload.firstValue(
                "recipientPhotoURLString",
                "recipientPhotoURL",
                "photoURLString",
                "photoURL",
            )

            return when (type) {
                "announcement" -> announcementId?.let(::Announcement)
                "chat_message", "chatmessage", "chat_mention", "chatmention" -> campingId?.let {
                    if (teamId.isNullOrBlank()) CampingChat(it) else TeamChat(it, teamId)
                }

                "poll" -> campingId?.let { Poll(it, pollId) }
                "schedule_reminder", "schedulereminder" -> campingId?.let {
                    if (programId.isNullOrBlank()) Camping(it) else ScheduleProgram(it, programId)
                }
                "registration", "registration_request" -> campingId?.let {
                    if (event == "approved") Camping(it) else RegistrationReview(it)
                }
                "badge", "achievement", "achievement_badge" -> recipientUserId?.let { userId ->
                    if (achievementId == null) {
                        Achievements(userId, recipientDisplayName, recipientPhotoUrl, campingId)
                    } else {
                        Achievement(userId, recipientDisplayName, recipientPhotoUrl, campingId, achievementId)
                    }
                }
                "transportation", "transportation_invitation", "transportation_request" -> campingId?.let {
                    when (event) {
                        "invitation", "invited" -> if (vehicleId != null && registrationId != null) {
                            TransportationInvitation(it, vehicleId, registrationId)
                        } else null
                        "join_request", "request" -> if (vehicleId != null && registrationId != null) {
                            TransportationRequest(it, vehicleId, registrationId)
                        } else null
                        else -> Transportation(it)
                    }
                }
                "team_update", "teamupdate" -> campingId?.let {
                    when {
                        event.isPointEvent() -> TeamPoints(campingId = it, teamId = teamId)
                        !teamId.isNullOrBlank() -> TeamUpdate(campingId = it, teamId = teamId)
                        else -> Camping(it)
                    }
                }

                else -> inferFromPayload(
                    campingId = campingId,
                    teamId = teamId,
                    pollId = pollId,
                    programId = programId,
                    announcementId = announcementId,
                    messageId = payload.firstValue("messageID"),
                )
            }
        }

        private fun inferFromPayload(
            campingId: String?,
            teamId: String?,
            pollId: String?,
            programId: String?,
            announcementId: String?,
            messageId: String?,
        ): CampzoneDeepLink? = when {
            announcementId != null -> Announcement(announcementId)
            messageId != null && campingId != null -> {
                if (teamId.isNullOrBlank()) CampingChat(campingId) else TeamChat(campingId, teamId)
            }

            pollId != null && campingId != null -> Poll(campingId, pollId)
            programId != null && campingId != null -> ScheduleProgram(campingId, programId)
            campingId != null -> Camping(campingId)
            else -> null
        }

        private fun String?.isPointEvent(): Boolean = this in PointEvents

        private fun Map<String, String?>.firstExplicitDeepLink(): CampzoneDeepLink? =
            ExplicitDeepLinkKeys.firstNotNullOfOrNull { key ->
                firstValue(key)?.let(::fromCampzoneUrl)
            }
    }
}

private fun parseQuery(rawQuery: String?): Map<String, String> {
    if (rawQuery.isNullOrBlank()) return emptyMap()

    return rawQuery
        .split("&")
        .mapNotNull { part ->
            val pieces = part.split("=", limit = 2)
            val key = pieces.getOrNull(0)?.decodeUrlComponentOrNull()?.takeUnless { it.isBlank() }
            val value = pieces.getOrNull(1)?.decodeUrlComponentOrNull()?.takeUnless { it.isBlank() }
            if (key != null && value != null) key to value else null
        }
        .toMap()
}

private fun Map<String, String?>.firstValue(vararg keys: String): String? =
    entries.firstNotNullOfOrNull { (entryKey, entryValue) ->
        if (keys.any { it.equals(entryKey, ignoreCase = true) }) {
            entryValue?.trim()?.takeUnless { it.isBlank() }
        } else {
            null
        }
    }

private fun String.decodeUrlComponentOrNull(): String? =
    runCatching { URLDecoder.decode(this, Charsets.UTF_8.name()) }.getOrNull()

private fun String.asUrlSegment(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

private fun List<String>.firstValue(): String? =
    firstOrNull { it.isNotBlank() }?.trim()?.takeUnless { it.isBlank() }
