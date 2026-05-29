package fr.ziyon.campzone.data.model

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Date

/**
 * Payload encoded into a participant's QR check-in code. Mirrors the iOS
 * `CheckInQRPayload`. QR contents are a URL of the form:
 *
 * ```
 * campzone://checkin?v=1&c=<campingID>&a=<attendeeID>&u=<userID>&iat=<unixSeconds>
 * ```
 *
 * The scanner verifies that the attendee exists in the camping's approved list
 * before recording a check-in. Forged codes for non-attendees are rejected
 * because the server-issued attendee + user IDs cannot be guessed.
 */
data class CheckInQrPayload(
    val version: Int = CURRENT_VERSION,
    val campingId: String,
    val attendeeId: String,
    val userId: String,
    val issuedAt: Date = Date(),
) {
    /** Encodes the payload into the canonical Campzone check-in URL string. */
    fun encoded(): String {
        val query = buildString {
            append("v=").append(version)
            append("&c=").append(encode(campingId))
            append("&a=").append(encode(attendeeId))
            append("&u=").append(encode(userId))
            append("&iat=").append(issuedAt.time / MILLIS_PER_SECOND)
        }
        return "$SCHEME://$HOST?$query"
    }

    companion object {
        const val SCHEME = "campzone"
        const val HOST = "checkin"
        const val CURRENT_VERSION = 1
        private const val MILLIS_PER_SECOND = 1000L

        /**
         * Attempts to decode a scanned string into a check-in payload. Returns
         * `null` for malformed input or non-Campzone codes.
         */
        fun decode(scannedValue: String): CheckInQrPayload? {
            val uri = runCatching { URI(scannedValue.trim()) }.getOrNull() ?: return null
            if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null
            val host = uri.host ?: uri.authority
            if (!host.equals(HOST, ignoreCase = true)) return null
            val query = uri.rawQuery ?: return null

            val map = query.split("&").mapNotNull { pair ->
                val separator = pair.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                pair.substring(0, separator) to decodeComponent(pair.substring(separator + 1))
            }.toMap()

            val version = map["v"]?.toIntOrNull() ?: return null
            if (version != CURRENT_VERSION) return null
            val campingId = map["c"]?.takeUnless { it.isBlank() } ?: return null
            val attendeeId = map["a"]?.takeUnless { it.isBlank() } ?: return null
            val userId = map["u"]?.takeUnless { it.isBlank() } ?: return null
            val issuedAt = map["iat"]?.toLongOrNull()
                ?.let { Date(it * MILLIS_PER_SECOND) }
                ?: Date()

            return CheckInQrPayload(
                version = version,
                campingId = campingId,
                attendeeId = attendeeId,
                userId = userId,
                issuedAt = issuedAt,
            )
        }

        private fun encode(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8.name())

        private fun decodeComponent(value: String): String =
            runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }
                .getOrDefault(value)
    }
}

/**
 * Outcome of processing a scanned (or manually entered) check-in. Mirrors the
 * iOS `CheckInScanResult`. The UI maps each case to localized copy; this layer
 * stays free of Android resources so it can be unit-tested.
 */
sealed interface CheckInScanResult {
    data class Success(val record: CheckInRecord) : CheckInScanResult
    data class AlreadyCheckedIn(val record: CheckInRecord) : CheckInScanResult
    data object UnknownAttendee : CheckInScanResult
    data object WrongCamping : CheckInScanResult
    data object NotApproved : CheckInScanResult
    data object Malformed : CheckInScanResult

    val isSuccess: Boolean
        get() = this is Success
}
