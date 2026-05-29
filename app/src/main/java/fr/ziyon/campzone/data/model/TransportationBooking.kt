package fr.ziyon.campzone.data.model

import com.google.firebase.firestore.FieldValue
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.UUID

/**
 * `campings/{id}/transportationBookings/{bookingId}` (`02-firestore-schema.md`
 * §7.2). Reads merge `userID==uid` + `guardianID==uid`. RBAC checks literal
 * `paymentStatus == "unpaid"` and `boardingStatus == "not_boarded"` on create.
 * `validFrom`/`validUntil` are raw `Date` → Timestamp (not serverTimestamp).
 *
 * The schema is extended beyond docs §7.2 to match the shipped iOS app
 * (iOS authoritative): `coversReturn` + `scanHistory` model a round-trip pass
 * whose every marshal scan is appended to an audit log, plus `isActive`,
 * `arrivedBy/At` and `canceledBy/At/cancelReason`. Legacy bookings without a
 * history are back-filled from the old outbound `boardedAt`/`arrivedAt` pair.
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
    val isActive: Boolean = true,
    val coversReturn: Boolean = true,
    val guardianId: String? = null,
    val boardedBy: String? = null,
    val boardedAt: Date? = null,
    val arrivedBy: String? = null,
    val arrivedAt: Date? = null,
    val canceledBy: String? = null,
    val canceledAt: Date? = null,
    val cancelReason: String? = null,
    val paymentUpdatedBy: String? = null,
    val paymentUpdatedAt: Date? = null,
    val paymentReference: String? = null,
    val scanHistory: List<TransportationScanEvent> = emptyList(),
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
) {
    /** A ticket may board only while active, fare-settled, and not yet boarded. */
    val canBoard: Boolean
        get() = isActive &&
            paymentStatus.allowsBoarding &&
            boardingStatus == TransportationBoardingStatus.NotBoarded

    val hasArrived: Boolean
        get() = arrivedAt != null

    /** First scan recorded for the given `(leg, checkpoint)` pair. */
    fun scanEvent(
        leg: TransportationLeg,
        checkpoint: TransportationCheckpoint,
    ): TransportationScanEvent? =
        scanHistory.firstOrNull { it.leg == leg && it.checkpoint == checkpoint }

    fun didScan(leg: TransportationLeg, checkpoint: TransportationCheckpoint): Boolean =
        scanEvent(leg, checkpoint) != null

    /**
     * Where a leg currently stands. The return leg stays [NotStarted] until a
     * return-departure scan lands, so it never appears completed by accident
     * off legacy outbound data.
     */
    fun progress(leg: TransportationLeg): TransportationLegProgress = when {
        didScan(leg, TransportationCheckpoint.Arrival) -> TransportationLegProgress.Arrived
        didScan(leg, TransportationCheckpoint.Departure) -> TransportationLegProgress.InTransit
        else -> TransportationLegProgress.NotStarted
    }

    /** `true` when every leg the ticket covers has been scanned at both checkpoints. */
    val isTripComplete: Boolean
        get() {
            val outboundDone = progress(TransportationLeg.Outbound) == TransportationLegProgress.Arrived
            val returnDone = !coversReturn ||
                progress(TransportationLeg.Return) == TransportationLegProgress.Arrived
            return outboundDone && returnDone
        }

    /** Which leg the scanner should default to: outbound until it fully arrives, then return. */
    val nextLeg: TransportationLeg
        get() = if (progress(TransportationLeg.Outbound) == TransportationLegProgress.Arrived) {
            TransportationLeg.Return
        } else {
            TransportationLeg.Outbound
        }

    companion object {
        /**
         * Synthesises an outbound timeline from the pre-history `boardedAt` /
         * `arrivedAt` mirror fields so older bookings still surface a usable
         * scan history (matches iOS `legacyScanHistory`).
         */
        fun legacyScanHistory(
            boardedAt: Date?,
            boardedBy: String?,
            arrivedAt: Date?,
            arrivedBy: String?,
        ): List<TransportationScanEvent> = buildList {
            if (boardedAt != null && boardedBy != null) {
                add(
                    TransportationScanEvent(
                        leg = TransportationLeg.Outbound,
                        checkpoint = TransportationCheckpoint.Departure,
                        at = boardedAt,
                        by = boardedBy,
                    ),
                )
            }
            if (arrivedAt != null && arrivedBy != null) {
                add(
                    TransportationScanEvent(
                        leg = TransportationLeg.Outbound,
                        checkpoint = TransportationCheckpoint.Arrival,
                        at = arrivedAt,
                        by = arrivedBy,
                    ),
                )
            }
        }
    }
}

/**
 * One marshal scan recorded against a [TransportationBooking]. Append-only; the
 * scanner writes a new event each time a marshal taps the QR. `(leg,
 * checkpoint)` fully describes the event - e.g. outbound+departure = boarding
 * for the trip to camp, return+arrival = arriving back home.
 */
data class TransportationScanEvent(
    val id: String = UUID.randomUUID().toString(),
    val leg: TransportationLeg,
    val checkpoint: TransportationCheckpoint,
    val at: Date,
    val by: String,
    val byName: String? = null,
    val location: String? = null,
)

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
    data class ArrivalSuccess(val booking: TransportationBooking) : TransportationScanResult
    data class AlreadyBoarded(val booking: TransportationBooking) : TransportationScanResult
    data class AlreadyArrived(val booking: TransportationBooking) : TransportationScanResult
    data class NotBoardedForArrival(val booking: TransportationBooking) : TransportationScanResult
    data class Inactive(val booking: TransportationBooking) : TransportationScanResult
    data class Unpaid(val booking: TransportationBooking) : TransportationScanResult
    data object WrongCamping : TransportationScanResult
    data object UnknownBooking : TransportationScanResult
    data object TokenMismatch : TransportationScanResult
    data object RegistrationNotApproved : TransportationScanResult
    data object Expired : TransportationScanResult
    data object Malformed : TransportationScanResult

    val isSuccess: Boolean
        get() = this is Success || this is ArrivalSuccess
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

    val boardedBy = stringValue("boardedBy")
    val boardedAt = dateValue("boardedAt")
    val arrivedBy = stringValue("arrivedBy")
    val arrivedAt = dateValue("arrivedAt")
    val scanHistory = mapListValue("scanHistory")
        .mapNotNull { it.toTransportationScanEventOrNull() }
        .ifEmpty {
            TransportationBooking.legacyScanHistory(
                boardedAt = boardedAt,
                boardedBy = boardedBy,
                arrivedAt = arrivedAt,
                arrivedBy = arrivedBy,
            )
        }

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
        isActive = boolValue("isActive") ?: true,
        coversReturn = boolValue("coversReturn") ?: true,
        guardianId = stringValue("guardianID"),
        boardedBy = boardedBy,
        boardedAt = boardedAt,
        arrivedBy = arrivedBy,
        arrivedAt = arrivedAt,
        canceledBy = stringValue("canceledBy"),
        canceledAt = dateValue("canceledAt"),
        cancelReason = stringValue("cancelReason"),
        paymentUpdatedBy = stringValue("paymentUpdatedBy"),
        paymentUpdatedAt = dateValue("paymentUpdatedAt"),
        paymentReference = stringValue("paymentReference"),
        scanHistory = scanHistory,
        createdAt = dateValue("createdAt"),
        updatedAt = dateValue("updatedAt"),
    )
}

internal fun Map<String, Any?>.toTransportationScanEventOrNull(): TransportationScanEvent? {
    val legRaw = stringValue("leg") ?: return null
    val checkpointRaw = stringValue("checkpoint") ?: return null
    val at = dateValue("at") ?: return null
    val by = stringValue("by") ?: return null
    return TransportationScanEvent(
        id = stringValue("id") ?: UUID.randomUUID().toString(),
        leg = TransportationLeg.fromWire(legRaw),
        checkpoint = TransportationCheckpoint.fromWire(checkpointRaw),
        at = at,
        by = by,
        byName = stringValue("byName"),
        location = stringValue("location"),
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
            "isActive" to true,
            "coversReturn" to booking.coversReturn,
            "scanHistory" to emptyList<Map<String, Any?>>(),
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

    /** Plain JSON-compatible map for `FieldValue.arrayUnion` / `scanHistory`. */
    fun scanEventMap(event: TransportationScanEvent): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>(
            "id" to event.id,
            "leg" to event.leg.wireValue,
            "checkpoint" to event.checkpoint.wireValue,
            "at" to event.at,
            "by" to event.by,
        )
        event.byName?.trim()?.takeUnless { it.isBlank() }?.let { map["byName"] = it }
        event.location?.trim()?.takeUnless { it.isBlank() }?.let { map["location"] = it }
        return map
    }

    /**
     * Appends a departure scan for [leg]. Mirrors the legacy outbound
     * `boardingStatus`/`boardedBy`/`boardedAt` fields so consumers reading them
     * (dashboard, history back-fill) stay accurate.
     */
    fun markBoardedPayload(
        leg: TransportationLeg,
        reviewerId: String,
        reviewerName: String?,
        location: String?,
        now: Date,
        serverTimestamp: Any,
    ): Map<String, Any?> {
        val event = TransportationScanEvent(
            leg = leg,
            checkpoint = TransportationCheckpoint.Departure,
            at = now,
            by = reviewerId,
            byName = reviewerName,
            location = location,
        )
        val payload = linkedMapOf<String, Any?>(
            "scanHistory" to FieldValue.arrayUnion(scanEventMap(event)),
            "updatedAt" to serverTimestamp,
        )
        if (leg == TransportationLeg.Outbound) {
            payload["boardingStatus"] = TransportationBoardingStatus.Boarded.wireValue
            payload["boardedBy"] = reviewerId
            payload["boardedAt"] = serverTimestamp
        }
        return payload
    }

    /** Appends an arrival scan for [leg]; mirrors legacy `arrivedBy`/`arrivedAt`. */
    fun markArrivedPayload(
        leg: TransportationLeg,
        reviewerId: String,
        reviewerName: String?,
        location: String?,
        now: Date,
        serverTimestamp: Any,
    ): Map<String, Any?> {
        val event = TransportationScanEvent(
            leg = leg,
            checkpoint = TransportationCheckpoint.Arrival,
            at = now,
            by = reviewerId,
            byName = reviewerName,
            location = location,
        )
        val payload = linkedMapOf<String, Any?>(
            "scanHistory" to FieldValue.arrayUnion(scanEventMap(event)),
            "updatedAt" to serverTimestamp,
        )
        if (leg == TransportationLeg.Outbound) {
            payload["arrivedBy"] = reviewerId
            payload["arrivedAt"] = serverTimestamp
        }
        return payload
    }

    fun updatePaymentStatusPayload(
        status: TransportationPaymentStatus,
        reviewerId: String,
        serverTimestamp: Any,
    ): Map<String, Any?> =
        linkedMapOf(
            "paymentStatus" to status.wireValue,
            "paymentUpdatedBy" to reviewerId,
            "paymentUpdatedAt" to serverTimestamp,
            "updatedAt" to serverTimestamp,
        )

    /** Cancel keeps the doc; `cancelReason` is delete-when-empty. */
    fun cancelPayload(
        reviewerId: String,
        reason: String?,
        serverTimestamp: Any,
        deleteField: Any,
    ): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "isActive" to false,
            "canceledBy" to reviewerId,
            "canceledAt" to serverTimestamp,
            "updatedAt" to serverTimestamp,
        )
        val trimmed = reason?.trim().orEmpty()
        payload["cancelReason"] = if (trimmed.isEmpty()) deleteField else trimmed
        return payload
    }
}
