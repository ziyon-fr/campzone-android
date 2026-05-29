package fr.ziyon.campzone.data.model

import fr.ziyon.campzone.data.auth.CampingAgeGroup
import fr.ziyon.campzone.data.auth.UserGender
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CheckInRecordPayloadTest {

    @Test
    fun writesRequiredFieldsAndOmitsNilOptionals() {
        val payload = CheckInRecordPayload.checkInPayload(minimalRecord(), serverTimestamp = TS)

        assertEquals("camp-1", payload["campingID"])
        assertEquals("att-1", payload["attendeeID"])
        assertEquals("user-1", payload["userID"])
        assertEquals("Maria", payload["displayName"])
        assertEquals("", payload["church"])
        assertEquals("", payload["preferredLanguage"])
        assertEquals("qr", payload["method"])
        assertEquals("leader-1", payload["checkedInBy"])
        assertEquals(TS, payload["checkedInAt"])
        // omit-when-nil optionals absent
        assertFalse(payload.containsKey("ageGroup"))
        assertFalse(payload.containsKey("gender"))
        assertFalse(payload.containsKey("photoURL"))
    }

    @Test
    fun writesOptionalsWhenPresent() {
        val payload = CheckInRecordPayload.checkInPayload(
            minimalRecord().copy(
                church = "Paris SDA",
                preferredLanguage = "fr",
                ageGroup = CampingAgeGroup.Youth,
                gender = UserGender.Female,
                photoUrl = "https://img/p.jpg",
                method = CheckInMethod.Manual,
            ),
            serverTimestamp = TS,
        )

        assertEquals("Paris SDA", payload["church"])
        assertEquals("fr", payload["preferredLanguage"])
        assertEquals("youth", payload["ageGroup"])
        assertEquals("female", payload["gender"])
        assertEquals("https://img/p.jpg", payload["photoURL"])
        assertEquals("manual", payload["method"])
    }

    @Test
    fun blankPhotoUrlIsOmitted() {
        val payload = CheckInRecordPayload.checkInPayload(
            minimalRecord().copy(photoUrl = "   "),
            serverTimestamp = TS,
        )
        assertFalse(payload.containsKey("photoURL"))
    }

    @Test
    fun roundTripsThroughDecoder() {
        val original = minimalRecord().copy(
            church = "Paris SDA",
            preferredLanguage = "fr",
            ageGroup = CampingAgeGroup.Adult,
            gender = UserGender.Male,
            photoUrl = "https://img/p.jpg",
        )
        val payload = CheckInRecordPayload.checkInPayload(original, serverTimestamp = TS)
        val decoded = payload.toCheckInRecordOrNull(documentId = "att-1")!!

        assertEquals("camp-1", decoded.campingId)
        assertEquals("att-1", decoded.attendeeId)
        assertEquals("user-1", decoded.userId)
        assertEquals("Maria", decoded.displayName)
        assertEquals(CheckInMethod.Qr, decoded.method)
        assertEquals("leader-1", decoded.checkedInBy)
        assertEquals("Paris SDA", decoded.church)
        assertEquals("fr", decoded.preferredLanguage)
        assertEquals(CampingAgeGroup.Adult, decoded.ageGroup)
        assertEquals(UserGender.Male, decoded.gender)
        assertEquals("https://img/p.jpg", decoded.photoUrl)
        assertEquals(TS, decoded.checkedInAt)
    }

    @Test
    fun decoderFallsBackToDocumentIdForAttendeeId() {
        val payload = CheckInRecordPayload.checkInPayload(minimalRecord(), serverTimestamp = TS)
            .toMutableMap()
            .apply { remove("attendeeID") }
        assertEquals("doc-xyz", payload.toCheckInRecordOrNull("doc-xyz")?.attendeeId)
    }

    @Test
    fun decoderDropsRecordWhenRequiredFieldMissing() {
        val payload = CheckInRecordPayload.checkInPayload(minimalRecord(), serverTimestamp = TS)
            .toMutableMap()
            .apply { remove("method") }
        assertNull(payload.toCheckInRecordOrNull("doc-xyz"))
    }

    private companion object {
        // A real Date stands in for the server timestamp so checkedInAt decodes.
        val TS = Date(42)

        fun minimalRecord() = CheckInRecord(
            campingId = "camp-1",
            attendeeId = "att-1",
            userId = "user-1",
            displayName = "Maria",
            method = CheckInMethod.Qr,
            checkedInBy = "leader-1",
        )
    }
}
