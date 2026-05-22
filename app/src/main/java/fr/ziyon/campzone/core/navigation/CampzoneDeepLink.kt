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

    data class Poll(
        val campingId: String,
        val pollId: String?,
    ) : CampzoneDeepLink

    data class RegistrationReview(val campingId: String) : CampzoneDeepLink

    fun canonicalShareUrlOrNull(): String? = when (this) {
        is Announcement -> "campzone://announcement/${id.asUrlSegment()}"
        is Camping -> "campzone://camping/${id.asUrlSegment()}"
        is CampingChat,
        is Poll,
        is RegistrationReview,
        is TeamChat,
        -> null
    }

    companion object {
        fun fromCampzoneUrl(url: String?): CampzoneDeepLink? {
            if (url.isNullOrBlank()) return null
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            if (!uri.scheme.equals("campzone", ignoreCase = true)) return null

            val host = uri.host?.lowercase(Locale.ROOT) ?: return null
            val query = parseQuery(uri.rawQuery)
            val firstPathComponent = uri.rawPath
                ?.split("/")
                ?.firstOrNull { it.isNotBlank() }
                ?.decodeUrlComponentOrNull()
                ?.takeUnless { it.isBlank() }

            return when (host) {
                "camping", "campings" -> {
                    val id = firstPathComponent
                        ?: query.firstValue("id", "c", "campingID")
                        ?: return null
                    Camping(id)
                }

                "announcement", "announcements" -> {
                    val id = firstPathComponent
                        ?: query.firstValue("id", "announcementID")
                        ?: return null
                    Announcement(id)
                }

                else -> null
            }
        }

        fun fromPayload(payload: Map<String, String?>): CampzoneDeepLink? {
            val type = payload.firstValue("type", "kind")?.lowercase(Locale.ROOT)
            val campingId = payload.firstValue("campingID")
            val teamId = payload.firstValue("teamID")
            val pollId = payload.firstValue("pollID")
            val announcementId = payload.firstValue("announcementID")

            return when (type) {
                "announcement" -> announcementId?.let(::Announcement)
                "chat_message", "chatmessage" -> campingId?.let {
                    if (teamId.isNullOrBlank()) CampingChat(it) else TeamChat(it, teamId)
                }

                "poll" -> campingId?.let { Poll(it, pollId) }
                "registration", "registration_request" -> campingId?.let(::RegistrationReview)
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
