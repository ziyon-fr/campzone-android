package fr.ziyon.campzone.data.model

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
