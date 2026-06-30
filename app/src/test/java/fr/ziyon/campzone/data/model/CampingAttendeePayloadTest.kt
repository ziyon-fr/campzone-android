package fr.ziyon.campzone.data.model

import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.family.FamilyRelationship
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampingAttendeePayloadTest {

    @Test
    fun selfRegistrationWritesUserIdAndUidButNotPaymentStatus() {
        val payload = CampingAttendeePayload.registrationPayload(
            attendee = selfAttendee(),
            serverTimestamp = TS,
            includeCreatedAt = true,
        )

        assertEquals("uid-1", payload["id"])
        assertEquals("uid-1", payload["userID"])
        assertEquals("uid-1", payload["uid"]) // duplicate of userID
        assertEquals("youth", payload["ageGroup"]) // derived from age
        assertEquals("own_car", payload["transportationChoice"])
        assertEquals("pending", payload["registrationStatus"])
        assertEquals(listOf("peanuts", "Kiwi"), payload["allergies"])
        assertEquals(TS, payload["createdAt"])
        // backend settles payment - never written by the client on create
        assertFalse(payload.containsKey("paymentStatus"))
        // omit-when-nil optionals absent
        assertFalse(payload.containsKey("gender"))
        assertFalse(payload.containsKey("guardianID"))
        assertFalse(payload.containsKey("transportationBookingID"))
    }

    @Test
    fun childRegistrationWritesGuardianAndBusBooking() {
        val payload = CampingAttendeePayload.registrationPayload(
            attendee = selfAttendee().copy(
                id = "child-1",
                userId = "child-1",
                participantKind = RegistrationParticipantKind.Child,
                guardianId = "uid-1",
                gender = UserGender.Female,
                transportationChoice = TransportationChoice.ProvidedBus,
                transportationBookingId = "child-1-bus",
                transportationOptionId = "t1",
                transportationOptionName = "Bus A",
                relationship = FamilyRelationship.Other,
                customRelationshipLabel = "Godchild",
            ),
            serverTimestamp = TS,
            includeCreatedAt = true,
        )

        assertEquals("child", payload["participantKind"])
        assertEquals("uid-1", payload["guardianID"])
        assertEquals("female", payload["gender"])
        assertEquals("provided_bus", payload["transportationChoice"])
        assertEquals("child-1-bus", payload["transportationBookingID"])
        assertEquals("Bus A", payload["transportationOptionName"])
        assertEquals("other", payload["relationship"])
        assertEquals("Godchild", payload["customRelationshipLabel"])
    }

    @Test
    fun statusUpdateWritesOnlyStatusAndTimestamp() {
        val payload = CampingAttendeePayload.statusUpdatePayload(RegistrationApprovalStatus.Approved, TS)
        assertEquals("approved", payload["registrationStatus"])
        assertEquals(setOf("registrationStatus", "updatedAt"), payload.keys)
    }

    @Test
    fun roundTripsThroughDecoder() {
        val paymentUpdatedAt = Date(12)
        val approvedAt = Date(13)
        val original = selfAttendee().copy(
            gender = UserGender.Male,
            relationship = FamilyRelationship.LegalGuardian,
        )
        val payload = CampingAttendeePayload.registrationPayload(original, Date(7), includeCreatedAt = false)
            .toMutableMap()
            .apply {
                put("registrationStatus", "approved")
                put("paymentStatus", "paid")
                put("paymentReference", "pi_123")
                put("paymentUpdatedAt", paymentUpdatedAt)
                put("approvedVia", "payment")
                put("approvedAt", approvedAt)
            }
        val decoded = payload.toCampingAttendeeOrNull(documentId = "uid-1")!!

        assertEquals("uid-1", decoded.id)
        assertEquals("uid-1", decoded.userId)
        assertEquals(original.displayName, decoded.displayName)
        assertEquals(original.church, decoded.church)
        assertEquals(original.age, decoded.age)
        assertEquals(UserGender.Male, decoded.gender)
        assertEquals(listOf("en"), decoded.languages)
        assertEquals(listOf("peanuts", "Kiwi"), decoded.allergies)
        assertEquals(FamilyRelationship.LegalGuardian, decoded.relationship)
        assertEquals(RegistrationApprovalStatus.Approved, decoded.registrationStatus)
        assertEquals(TransportationPaymentStatus.Paid, decoded.paymentStatus)
        assertEquals("pi_123", decoded.paymentReference)
        assertEquals(paymentUpdatedAt, decoded.paymentUpdatedAt)
        assertEquals("payment", decoded.approvedVia)
        assertEquals(approvedAt, decoded.approvedAt)
        assertTrue(decoded.languages.isNotEmpty())
    }

    private companion object {
        const val TS = "serverTimestamp"

        fun selfAttendee() = CampingAttendee(
            id = "uid-1",
            userId = "uid-1",
            displayName = "Maria",
            church = "Paris Central SDA",
            age = 20,
            languages = listOf("en"),
            allergies = listOf("peanuts", "Kiwi"),
            registrationStatus = RegistrationApprovalStatus.Pending,
            preferredLanguage = "en",
        )
    }
}
