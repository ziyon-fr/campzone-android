package fr.ziyon.campzone.data.model

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Date

/**
 * `campings/{id}/transportationBookings/{bookingId}` (`02-firestore-schema.md`
 * §7.2). Reads merge `userID==uid` + `guardianID==uid`. RBAC checks literal
 * `paymentStatus == "unpaid"` and `boardingStatus == "not_boarded"` on create.
 * `validFrom`/`validUntil` are raw `Date` → Timestamp (not serverTimestamp).
 */
data class TransportationBooking(
    val id: String,
    val campingId: String,
    val registrationId: String,
    val participantId: String,
    val participantKind: RegistrationParticipantKind,
    val participantName: String,
    val userId: String,
    val ticketToken: String,
    val validFrom: Date,
    val validUntil: Date,
    val transportationOptionId: String? = null,
    val transportationOptionName: String? = null,
    val paymentStatus: TransportationPaymentStatus = TransportationPaymentStatus.Unpaid,
    val boardingStatus: TransportationBoardingStatus = TransportationBoardingStatus.NotBoarded,
    val guardianId: String? = null,
    val boardedBy: String? = null,
    val boardedAt: Date? = null,
    val paymentUpdatedBy: String? = null,
    val paymentUpdatedAt: Date? = null,
    val paymentReference: String? = null,
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
) {
    val canBoard: Boolean
        get() = paymentStatus in setOf(
            TransportationPaymentStatus.Paid,
            TransportationPaymentStatus.Waived,
        ) && boardingStatus == TransportationBoardingStatus.NotBoarded
}

data class TransportationTicketPayload(
    val version: Int = CURRENT_VERSION,
    val campingId: String,
    val bookingId: String,
    val registrationId: String,
    val participantId: String,
    val token: String,
) {
    fun encoded(): String {
        val query = buildString {
            append("v=").append(version)
            append("&c=").append(encode(campingId))
            append("&b=").append(encode(bookingId))
            append("&r=").append(encode(registrationId))
            append("&p=").append(encode(participantId))
            append("&t=").append(encode(token))
        }
        return "$SCHEME://$HOST?$query"
    }

    companion object {
        const val SCHEME = "campzone"
        const val HOST = "transport"
        const val CURRENT_VERSION = 1

        fun fromBooking(booking: TransportationBooking): TransportationTicketPayload =
            TransportationTicketPayload(
                campingId = booking.campingId,
                bookingId = booking.id,
                registrationId = booking.registrationId,
                participantId = booking.participantId,
                token = booking.ticketToken,
            )

        fun decode(scannedValue: String): TransportationTicketPayload? {
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
            return TransportationTicketPayload(
                version = version,
                campingId = map["c"]?.takeUnless { it.isBlank() } ?: return null,
                bookingId = map["b"]?.takeUnless { it.isBlank() } ?: return null,
                registrationId = map["r"]?.takeUnless { it.isBlank() } ?: return null,
                participantId = map["p"]?.takeUnless { it.isBlank() } ?: return null,
                token = map["t"]?.takeUnless { it.isBlank() } ?: return null,
            )
        }

        private fun encode(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8.name())

        private fun decodeComponent(value: String): String =
            runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }
                .getOrDefault(value)
    }
}

sealed interface TransportationScanResult {
    data class Success(val booking: TransportationBooking) : TransportationScanResult
    data class AlreadyBoarded(val booking: TransportationBooking) : TransportationScanResult
    data class Unpaid(val booking: TransportationBooking) : TransportationScanResult
    data object WrongCamping : TransportationScanResult
    data object UnknownBooking : TransportationScanResult
    data object TokenMismatch : TransportationScanResult
    data object RegistrationNotApproved : TransportationScanResult
    data object Expired : TransportationScanResult
    data object Malformed : TransportationScanResult

    val isSuccess: Boolean
        get() = this is Success
}

internal fun Map<String, Any?>.toTransportationBookingOrNull(documentId: String): TransportationBooking? {
    val campingId = stringValue("campingID") ?: return null
    val registrationId = stringValue("registrationID") ?: return null
    val participantId = stringValue("participantID") ?: return null
    val participantName = stringValue("participantName") ?: return null
    val userId = stringValue("userID") ?: return null
    val ticketToken = stringValue("ticketToken") ?: return null
    val paymentStatus = stringValue("paymentStatus") ?: return null
    val boardingStatus = stringValue("boardingStatus") ?: return null
    return TransportationBooking(
        id = stringValue("id") ?: documentId,
        campingId = campingId,
        registrationId = registrationId,
        participantId = participantId,
        participantKind = RegistrationParticipantKind.fromWire(stringValue("participantKind")),
        participantName = participantName,
        userId = userId,
        ticketToken = ticketToken,
        validFrom = dateValue("validFrom") ?: Date(),
        validUntil = dateValue("validUntil") ?: Date(),
        transportationOptionId = stringValue("transportationOptionID"),
        transportationOptionName = stringValue("transportationOptionName"),
        paymentStatus = TransportationPaymentStatus.fromWire(paymentStatus),
        boardingStatus = TransportationBoardingStatus.fromWire(boardingStatus),
        guardianId = stringValue("guardianID"),
        boardedBy = stringValue("boardedBy"),
        boardedAt = dateValue("boardedAt"),
        paymentUpdatedBy = stringValue("paymentUpdatedBy"),
        paymentUpdatedAt = dateValue("paymentUpdatedAt"),
        paymentReference = stringValue("paymentReference"),
        createdAt = dateValue("createdAt"),
        updatedAt = dateValue("updatedAt"),
    )
}

internal object TransportationBookingPayload {

    /** Create - RBAC requires the `unpaid` / `not_boarded` literals. */
    fun createPayload(
        booking: TransportationBooking,
        serverTimestamp: Any,
    ): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "id" to booking.id,
            "campingID" to booking.campingId,
            "registrationID" to booking.registrationId,
            "participantID" to booking.participantId,
            "participantKind" to booking.participantKind.wireValue,
            "participantName" to booking.participantName,
            "userID" to booking.userId,
            "ticketToken" to booking.ticketToken,
            "validFrom" to booking.validFrom,
            "validUntil" to booking.validUntil,
            "paymentStatus" to TransportationPaymentStatus.Unpaid.wireValue,
            "boardingStatus" to TransportationBoardingStatus.NotBoarded.wireValue,
            "createdAt" to serverTimestamp,
            "updatedAt" to serverTimestamp,
        )
        booking.guardianId?.trim()?.takeUnless { it.isBlank() }?.let { payload["guardianID"] = it }
        booking.transportationOptionId?.trim()?.takeUnless { it.isBlank() }
            ?.let { payload["transportationOptionID"] = it }
        booking.transportationOptionName?.trim()?.takeUnless { it.isBlank() }
            ?.let { payload["transportationOptionName"] = it }
        return payload
    }

    fun markBoardedPayload(
        boardedBy: String,
        serverTimestamp: Any,
    ): Map<String, Any?> =
        linkedMapOf(
            "boardingStatus" to TransportationBoardingStatus.Boarded.wireValue,
            "boardedBy" to boardedBy,
            "boardedAt" to serverTimestamp,
            "updatedAt" to serverTimestamp,
        )
}
