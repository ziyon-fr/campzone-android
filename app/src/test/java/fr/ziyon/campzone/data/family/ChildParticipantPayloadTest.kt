package fr.ziyon.campzone.data.family

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ChildParticipantPayloadTest {
    @Test
    fun childPayloadMatchesIosShape() {
        val consent = Date()
        val payload = ChildParticipantPayload.childPayload(
            child = sampleChild().copy(
                guardianConsentAt = consent,
                photoUrl = "https://cdn.example/child.jpg",
                photoPublicId = "campzone/participants/child-1",
                medicalNotes = "None",
                allergies = listOf("peanuts", "Kiwi"),
                relationship = FamilyRelationship.Other,
                customRelationshipLabel = "Neighbor",
            ),
            serverTimestamp = Timestamp,
            deleteField = Delete,
            includeCreatedAt = true,
        )

        assertEquals("child-1", payload["id"])
        assertEquals("guardian-1", payload["guardianID"])
        assertEquals("Ana Santos", payload["displayName"])
        assertEquals(10, payload["age"])
        assertEquals("kids", payload["ageGroup"])
        assertEquals("female", payload["gender"])
        assertEquals("Paris Central SDA", payload["church"])
        assertEquals("fr", payload["preferredLanguage"])
        assertEquals(listOf("fr"), payload["languages"])
        assertEquals("Maria Santos", payload["emergencyContactName"])
        assertEquals("None", payload["medicalNotes"])
        assertEquals(listOf("peanuts", "Kiwi"), payload["allergies"])
        assertEquals("other", payload["relationship"])
        assertEquals("Neighbor", payload["customRelationshipLabel"])
        assertEquals(consent, payload["guardianConsentAt"])
        assertEquals("https://cdn.example/child.jpg", payload["photoURL"])
        assertEquals("campzone/participants/child-1", payload["photoPublicID"])
        assertEquals(Timestamp, payload["updatedAt"])
        assertEquals(Timestamp, payload["createdAt"])
    }

    @Test
    fun nilOptionalsUseDeleteFieldAndCreatedAtOmittedOnUpdate() {
        val payload = ChildParticipantPayload.childPayload(
            child = sampleChild().copy(guardianConsentAt = null, photoUrl = null, photoPublicId = null),
            serverTimestamp = Timestamp,
            deleteField = Delete,
            includeCreatedAt = false,
        )

        assertEquals(Delete, payload["guardianConsentAt"])
        assertEquals(Delete, payload["photoURL"])
        assertEquals(Delete, payload["photoPublicID"])
        assertFalse(payload.containsKey("createdAt"))
    }

    @Test
    fun payloadRoundTripsThroughDeserializer() {
        val original = sampleChild().copy(
            relationship = FamilyRelationship.Grandparent,
            medicalNotes = "Allergic to peanuts",
            allergies = listOf("peanuts", "Kiwi"),
        )
        val payload = ChildParticipantPayload.childPayload(
            child = original,
            serverTimestamp = Timestamp,
            deleteField = Delete,
            includeCreatedAt = false,
        )
        val decoded = payload.toChildParticipantOrNull(documentId = "child-1")

        assertEquals(original.id, decoded?.id)
        assertEquals(original.displayName, decoded?.displayName)
        assertEquals(original.age, decoded?.age)
        assertEquals(original.gender, decoded?.gender)
        assertEquals(original.church, decoded?.church)
        assertEquals(original.preferredLanguage, decoded?.preferredLanguage)
        assertEquals(original.emergencyContactName, decoded?.emergencyContactName)
        assertEquals(original.emergencyContactPhone, decoded?.emergencyContactPhone)
        assertEquals(original.relationship, decoded?.relationship)
        assertEquals(original.medicalNotes, decoded?.medicalNotes)
        assertEquals(original.allergies, decoded?.allergies)
        assertEquals(original.guardianConsentAt, decoded?.guardianConsentAt)
    }

    @Test
    fun deserializerDropsDocMissingRequiredField() {
        val payload = ChildParticipantPayload.childPayload(
            child = sampleChild(),
            serverTimestamp = Timestamp,
            deleteField = Delete,
            includeCreatedAt = false,
        ).toMutableMap().apply { remove("guardianID") }

        assertNull(payload.toChildParticipantOrNull(documentId = "child-1"))
    }

    private companion object {
        const val Timestamp = "serverTimestamp"
        const val Delete = "delete"
    }
}
