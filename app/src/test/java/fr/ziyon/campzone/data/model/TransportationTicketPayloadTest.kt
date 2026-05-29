package fr.ziyon.campzone.data.model

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TransportationTicketPayloadTest {
    @Test
    fun encodeDecodeRoundTrip() {
        val payload = TransportationTicketPayload(
            campingId = "camp-1",
            bookingId = "booking-1",
            registrationId = "registration-1",
            participantId = "participant-1",
            token = "secret-token",
        )

        val encoded = payload.encoded()
        val decoded = TransportationTicketPayload.decode(encoded)!!

        assertEquals("campzone://transport?v=1&c=camp-1&b=booking-1&r=registration-1&p=participant-1&t=secret-token", encoded)
        assertEquals(payload, decoded)
    }

    @Test
    fun decodesCanonicalUrl() {
        val decoded = TransportationTicketPayload.decode(
            "campzone://transport?v=1&c=camp&b=booking&r=registration&p=participant&t=token",
        )!!

        assertEquals("camp", decoded.campingId)
        assertEquals("booking", decoded.bookingId)
        assertEquals("registration", decoded.registrationId)
        assertEquals("participant", decoded.participantId)
        assertEquals("token", decoded.token)
    }

    @Test
    fun decodeAcceptsCaseInsensitiveSchemeAndHost() {
        val decoded = TransportationTicketPayload.decode(
            "CAMPZONE://Transport?v=1&c=camp&b=booking&r=registration&p=participant&t=token",
        )

        assertNotNull(decoded)
    }

    @Test
    fun rejectsForeignOrIncompleteValues() {
        assertNull(TransportationTicketPayload.decode("campzone://checkin?v=1&c=a&b=b&r=r&p=p&t=t"))
        assertNull(TransportationTicketPayload.decode("https://transport?v=1&c=a&b=b&r=r&p=p&t=t"))
        assertNull(TransportationTicketPayload.decode("campzone://transport?v=2&c=a&b=b&r=r&p=p&t=t"))
        assertNull(TransportationTicketPayload.decode("campzone://transport?v=1&c=a&b=b&r=r&p=p"))
    }

    @Test
    fun createPayloadUsesRuleRequiredDefaultsAndRawDates() {
        val booking = TransportationBooking(
            id = "p1-bus",
            campingId = "camp-1",
            registrationId = "p1",
            participantId = "p1",
            participantKind = RegistrationParticipantKind.SelfParticipant,
            participantName = "Maria",
            userId = "p1",
            ticketToken = "token",
            validFrom = Date(1_000),
            validUntil = Date(2_000),
        )

        val payload = TransportationBookingPayload.createPayload(booking, serverTimestamp = "server")

        assertEquals("unpaid", payload["paymentStatus"])
        assertEquals("not_boarded", payload["boardingStatus"])
        assertEquals(Date(1_000), payload["validFrom"])
        assertEquals(Date(2_000), payload["validUntil"])
        assertEquals("server", payload["createdAt"])
        assertNotNull(TransportationTicketPayload.fromBooking(booking).encoded())
    }
}
