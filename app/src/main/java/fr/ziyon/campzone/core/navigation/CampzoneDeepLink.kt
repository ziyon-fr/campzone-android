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

    data class RegistrationReview(val campingId: String) : CampzoneDeepLink

    fun canonicalShareUrlOrNull(): String? = when (this) {
        is Announcement -> "https://${Companion.WebHost}/announcements/${id.asUrlSegment()}"
        is Camping -> "https://${Companion.WebHost}/campings/${id.asUrlSegment()}"
        is CampingChat,
        is Poll,
        is RegistrationReview,
        is TeamChat,
        is TeamPoints,
        is TeamUpdate,
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

                "registration", "registration-review" -> {
                    val campingId = pathId
                        ?: query.firstValue("id", "c", "campingID")
                        ?: return null
                    RegistrationReview(campingId)
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
            val announcementId = payload.firstValue("announcementID")
            val event = payload.firstValue("event")?.lowercase(Locale.ROOT)

            return when (type) {
                "announcement" -> announcementId?.let(::Announcement)
                "chat_message", "chatmessage", "chat_mention", "chatmention" -> campingId?.let {
                    if (teamId.isNullOrBlank()) CampingChat(it) else TeamChat(it, teamId)
                }

                "poll" -> campingId?.let { Poll(it, pollId) }
                "registration", "registration_request" -> campingId?.let(::RegistrationReview)
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
                    announcementId = announcementId,
                    messageId = payload.firstValue("messageID"),
                )
            }
        }

        private fun inferFromPayload(
            campingId: String?,
            teamId: String?,
            pollId: String?,
            announcementId: String?,
            messageId: String?,
        ): CampzoneDeepLink? = when {
            announcementId != null -> Announcement(announcementId)
            messageId != null && campingId != null -> {
                if (teamId.isNullOrBlank()) CampingChat(campingId) else TeamChat(campingId, teamId)
            }

            pollId != null && campingId != null -> Poll(campingId, pollId)
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
