package fr.ziyon.campzone.data.model

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CheckInQrTest {

    @Test
    fun encodeDecodeRoundTrip() {
        val original = CheckInQrPayload(
            campingId = "11111111-2222-3333-4444-555555555555",
            attendeeId = "att-abc-123",
            userId = "uid-xyz-789",
            issuedAt = Date(1_700_000_000_000),
        )
        val decoded = CheckInQrPayload.decode(original.encoded())!!

        assertEquals(CheckInQrPayload.CURRENT_VERSION, decoded.version)
        assertEquals(original.campingId, decoded.campingId)
        assertEquals(original.attendeeId, decoded.attendeeId)
        assertEquals(original.userId, decoded.userId)
        // iat is encoded at second precision.
        assertEquals(original.issuedAt.time / 1000, decoded.issuedAt.time / 1000)
    }

    @Test
    fun decodesCanonicalIosUrl() {
        val decoded =
            CheckInQrPayload.decode("campzone://checkin?v=1&c=camp1&a=att1&u=user1&iat=1700000000")!!
        assertEquals("camp1", decoded.campingId)
        assertEquals("att1", decoded.attendeeId)
        assertEquals("user1", decoded.userId)
        assertEquals(1_700_000_000L, decoded.issuedAt.time / 1000)
    }

    @Test
    fun schemeAndHostAreCaseInsensitive() {
        assertNotNull(CheckInQrPayload.decode("CAMPZONE://CHECKIN?v=1&c=a&a=b&u=c"))
    }

    @Test
    fun rejectsForeignSchemeOrHost() {
        assertNull(CheckInQrPayload.decode("https://checkin?v=1&c=a&a=b&u=c"))
        assertNull(CheckInQrPayload.decode("campzone://other?v=1&c=a&a=b&u=c"))
    }

    @Test
    fun rejectsWrongVersion() {
        assertNull(CheckInQrPayload.decode("campzone://checkin?v=2&c=a&a=b&u=c"))
        assertNull(CheckInQrPayload.decode("campzone://checkin?v=x&c=a&a=b&u=c"))
    }

    @Test
    fun rejectsMissingOrEmptyRequiredFields() {
        assertNull(CheckInQrPayload.decode("campzone://checkin?c=a&a=b&u=c")) // no version
        assertNull(CheckInQrPayload.decode("campzone://checkin?v=1&a=b&u=c")) // no campingId
        assertNull(CheckInQrPayload.decode("campzone://checkin?v=1&c=a&u=c")) // no attendeeId
        assertNull(CheckInQrPayload.decode("campzone://checkin?v=1&c=a&a=b")) // no userId
        assertNull(CheckInQrPayload.decode("campzone://checkin?v=1&c=&a=b&u=c")) // empty campingId
    }

    @Test
    fun rejectsGarbage() {
        assertNull(CheckInQrPayload.decode(""))
        assertNull(CheckInQrPayload.decode("not a url"))
        assertNull(CheckInQrPayload.decode("hello-world"))
    }

    @Test
    fun defaultsIssuedAtWhenAbsent() {
        val decoded = CheckInQrPayload.decode("campzone://checkin?v=1&c=a&a=b&u=c")!!
        assertNotNull(decoded.issuedAt)
    }
}
