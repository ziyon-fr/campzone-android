package fr.ziyon.campzone.data.model

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportationBookingTest {

    @Test
    fun createPayloadWritesRoundTripDefaults() {
        val payload = TransportationBookingPayload.createPayload(bookingBase(), TS)

        assertEquals(TransportationPaymentStatus.Unpaid.wireValue, payload["paymentStatus"])
        assertEquals(TransportationBoardingStatus.NotBoarded.wireValue, payload["boardingStatus"])
        assertEquals(true, payload["isActive"])
        assertEquals(true, payload["coversReturn"])
        assertEquals(emptyList<Map<String, Any?>>(), payload["scanHistory"])
        assertEquals(TS, payload["createdAt"])
        assertEquals(TS, payload["updatedAt"])
    }

    @Test
    fun decodesScanHistoryAndRoundTripFields() {
        val events = listOf(
            TransportationScanEvent(
                id = "e1",
                leg = TransportationLeg.Outbound,
                checkpoint = TransportationCheckpoint.Departure,
                at = Date(1_000),
                by = "marshal-1",
                byName = "Leon",
                location = "Paris-Bercy",
            ),
            TransportationScanEvent(
                id = "e2",
                leg = TransportationLeg.Outbound,
                checkpoint = TransportationCheckpoint.Arrival,
                at = Date(2_000),
                by = "marshal-1",
            ),
        )
        val data = wireMap(coversReturn = false).toMutableMap().apply {
            put("scanHistory", events.map { TransportationBookingPayload.scanEventMap(it) })
        }

        val booking = data.toTransportationBookingOrNull("doc-1")!!

        assertFalse(booking.coversReturn)
        assertTrue(booking.isActive)
        assertEquals(2, booking.scanHistory.size)
        assertEquals("Paris-Bercy", booking.scanHistory.first().location)
        assertEquals(TransportationLegProgress.Arrived, booking.progress(TransportationLeg.Outbound))
    }

    @Test
    fun backfillsLegacyOutboundTimeline() {
        val data = wireMap().toMutableMap().apply {
            put("boardedBy", "marshal-1")
            put("boardedAt", Date(1_000))
            put("arrivedBy", "marshal-1")
            put("arrivedAt", Date(2_000))
            // no scanHistory key
        }

        val booking = data.toTransportationBookingOrNull("doc-1")!!

        assertEquals(2, booking.scanHistory.size)
        assertTrue(booking.didScan(TransportationLeg.Outbound, TransportationCheckpoint.Departure))
        assertTrue(booking.didScan(TransportationLeg.Outbound, TransportationCheckpoint.Arrival))
        assertEquals(TransportationLegProgress.Arrived, booking.progress(TransportationLeg.Outbound))
    }

    @Test
    fun progressAndTripCompletionDeriveFromHistory() {
        val outbound = bookingBase().copy(
            coversReturn = true,
            scanHistory = listOf(
                TransportationScanEvent(leg = TransportationLeg.Outbound, checkpoint = TransportationCheckpoint.Departure, at = Date(1), by = "m"),
                TransportationScanEvent(leg = TransportationLeg.Outbound, checkpoint = TransportationCheckpoint.Arrival, at = Date(2), by = "m"),
            ),
        )

        assertEquals(TransportationLegProgress.Arrived, outbound.progress(TransportationLeg.Outbound))
        assertEquals(TransportationLegProgress.NotStarted, outbound.progress(TransportationLeg.Return))
        assertFalse(outbound.isTripComplete)
        assertEquals(TransportationLeg.Return, outbound.nextLeg)

        val complete = outbound.copy(
            scanHistory = outbound.scanHistory + listOf(
                TransportationScanEvent(leg = TransportationLeg.Return, checkpoint = TransportationCheckpoint.Departure, at = Date(3), by = "m"),
                TransportationScanEvent(leg = TransportationLeg.Return, checkpoint = TransportationCheckpoint.Arrival, at = Date(4), by = "m"),
            ),
        )
        assertTrue(complete.isTripComplete)
    }

    @Test
    fun oneWayTripCompletesOnOutboundArrival() {
        val booking = bookingBase().copy(
            coversReturn = false,
            scanHistory = listOf(
                TransportationScanEvent(leg = TransportationLeg.Outbound, checkpoint = TransportationCheckpoint.Departure, at = Date(1), by = "m"),
                TransportationScanEvent(leg = TransportationLeg.Outbound, checkpoint = TransportationCheckpoint.Arrival, at = Date(2), by = "m"),
            ),
        )
        assertTrue(booking.isTripComplete)
    }

    @Test
    fun canBoardRequiresActivePaidAndNotBoarded() {
        val ready = bookingBase().copy(
            paymentStatus = TransportationPaymentStatus.Paid,
            boardingStatus = TransportationBoardingStatus.NotBoarded,
            isActive = true,
        )
        assertTrue(ready.canBoard)
        assertFalse(ready.copy(isActive = false).canBoard)
        assertFalse(ready.copy(paymentStatus = TransportationPaymentStatus.Unpaid).canBoard)
        assertFalse(ready.copy(boardingStatus = TransportationBoardingStatus.Boarded).canBoard)
    }

    @Test
    fun scanEventMapRoundTrips() {
        val event = TransportationScanEvent(
            id = "evt",
            leg = TransportationLeg.Return,
            checkpoint = TransportationCheckpoint.Arrival,
            at = Date(9_000),
            by = "marshal-9",
            byName = "Marshal Nine",
            location = "Camp gate",
        )

        val decoded = TransportationBookingPayload.scanEventMap(event).toTransportationScanEventOrNull()!!

        assertEquals(event.id, decoded.id)
        assertEquals(event.leg, decoded.leg)
        assertEquals(event.checkpoint, decoded.checkpoint)
        assertEquals(event.at, decoded.at)
        assertEquals(event.by, decoded.by)
        assertEquals(event.byName, decoded.byName)
        assertEquals(event.location, decoded.location)
    }

    @Test
    fun cancelPayloadDeletesBlankReasonAndKeepsRealReason() {
        val blank = TransportationBookingPayload.cancelPayload(
            reviewerId = "marshal-1",
            reason = "   ",
            serverTimestamp = TS,
            deleteField = DEL,
        )
        assertEquals(false, blank["isActive"])
        assertEquals(DEL, blank["cancelReason"])

        val real = TransportationBookingPayload.cancelPayload(
            reviewerId = "marshal-1",
            reason = "  No longer travelling ",
            serverTimestamp = TS,
            deleteField = DEL,
        )
        assertEquals("No longer travelling", real["cancelReason"])
    }

    @Test
    fun decoderDropsBookingMissingRequiredField() {
        val data = wireMap().toMutableMap().apply { remove("ticketToken") }
        assertNull(data.toTransportationBookingOrNull("doc-1"))
    }

    private fun wireMap(coversReturn: Boolean = true): Map<String, Any?> = mapOf(
        "id" to "participant-1-bus",
        "campingID" to "camp-1",
        "registrationID" to "participant-1",
        "participantID" to "participant-1",
        "participantKind" to RegistrationParticipantKind.SelfParticipant.wireValue,
        "participantName" to "Maria",
        "userID" to "participant-1",
        "ticketToken" to "token-1",
        "validFrom" to Date(1_000),
        "validUntil" to Date(5_000),
        "paymentStatus" to TransportationPaymentStatus.Paid.wireValue,
        "boardingStatus" to TransportationBoardingStatus.NotBoarded.wireValue,
        "isActive" to true,
        "coversReturn" to coversReturn,
    )

    private fun bookingBase() = TransportationBooking(
        id = "participant-1-bus",
        campingId = "camp-1",
        registrationId = "participant-1",
        participantId = "participant-1",
        participantKind = RegistrationParticipantKind.SelfParticipant,
        participantName = "Maria",
        userId = "participant-1",
        ticketToken = "token-1",
        validFrom = Date(1_000),
        validUntil = Date(5_000),
    )

    private companion object {
        const val TS = "serverTimestamp"
        const val DEL = "delete"
    }
}
