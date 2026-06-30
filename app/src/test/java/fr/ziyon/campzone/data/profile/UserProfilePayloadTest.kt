package fr.ziyon.campzone.data.profile

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.UserGender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfilePayloadTest {
    @Test
    fun userMergePayloadMatchesIosProfileSave() {
        val payload = UserProfilePayload.userMergePayload(
            user = sampleProfile(),
            serverTimestamp = Timestamp,
            deleteField = Delete,
        )

        assertEquals("uid-1", payload["uid"])
        assertEquals("Lea Camper", payload["displayName"])
        assertEquals(22, payload["age"])
        assertEquals("youth", payload["ageGroup"])
        assertEquals("female", payload["gender"])
        assertEquals("Paris Central SDA", payload["church"])
        assertEquals(listOf("Singing", "First Aid"), payload["skills"])
        assertEquals(listOf("peanuts", "Kiwi"), payload["allergies"])
        assertEquals("Designer", payload["profession"])
        assertEquals("Bachelor", payload["education"])
        assertEquals("Guide", payload["pathfinderRank"])
        assertEquals("+33 6 00 00 00 00", payload["phone"])
        assertEquals("lea@example.com", payload["email"])
        assertEquals("fr", payload["preferredLanguage"])
        assertEquals(listOf("fr", "pt"), payload["languages"])
        assertEquals("adult", payload["role"])
        assertEquals("https://cdn.example/avatar.jpg", payload["photoURL"])
        assertEquals("campzone/avatars/uid-1", payload["photoPublicID"])
        assertEquals(true, payload["onboardingCompleted"])
        assertEquals(Timestamp, payload["updatedAt"])
        assertFalse(payload.containsKey("id"))
    }

    @Test
    fun nilOptionalProfileFieldsUseDeleteField() {
        val payload = UserProfilePayload.userMergePayload(
            user = sampleProfile().copy(
                age = null,
                gender = null,
                photoUrl = null,
                photoPublicId = null,
            ),
            serverTimestamp = Timestamp,
            deleteField = Delete,
        )

        assertEquals(Delete, payload["age"])
        assertEquals(Delete, payload["ageGroup"])
        assertEquals(Delete, payload["gender"])
        assertEquals(Delete, payload["photoURL"])
        assertEquals(Delete, payload["photoPublicID"])
    }

    @Test
    fun accountDeletionPayloadsMatchIosFlags() {
        val request = UserProfilePayload.accountDeletionPayload(
            uid = "uid-1",
            pendingDeletionAt = DeletionTimestamp,
            serverTimestamp = Timestamp,
        )
        val cancel = UserProfilePayload.cancelAccountDeletionPayload(
            deleteField = Delete,
            serverTimestamp = Timestamp,
        )

        assertEquals(DeletionTimestamp, request["pendingDeletionAt"])
        assertEquals("uid-1", request["deletionRequestedBy"])
        assertEquals(Timestamp, request["updatedAt"])
        assertEquals(Delete, cancel["pendingDeletionAt"])
        assertEquals(Delete, cancel["deletionRequestedBy"])
        assertEquals(Timestamp, cancel["updatedAt"])
    }

    @Test
    fun denormalizedPayloadsUseIosFieldNames() {
        val user = sampleProfile()

        val attendee = UserProfilePayload.attendeeProfilePayload(user, Delete)
        val participant = UserProfilePayload.participantProfilePayload(user, Delete)
        val chat = UserProfilePayload.chatProfilePayload(user, Delete)
        val announcement = UserProfilePayload.announcementProfilePayload(user, Delete)
        val poll = UserProfilePayload.pollProfilePayload(user)

        assertEquals("Lea Camper", attendee["displayName"])
        assertEquals(22, attendee["age"])
        assertEquals("youth", attendee["ageGroup"])
        assertEquals("female", attendee["gender"])
        assertEquals(listOf("fr", "pt"), attendee["languages"])
        assertEquals(listOf("peanuts", "Kiwi"), attendee["allergies"])
        assertEquals("https://cdn.example/avatar.jpg", participant["photoURL"])
        assertEquals("Lea Camper", chat["senderName"])
        assertEquals("female", chat["senderGender"])
        assertEquals("https://cdn.example/avatar.jpg", chat["senderPhotoURL"])
        assertEquals("Lea Camper", announcement["authorName"])
        assertEquals("https://cdn.example/avatar.jpg", announcement["authorPhotoURL"])
        assertEquals("Lea Camper", poll["createdByName"])
    }

    @Test
    fun teamMemberRewriteRemovesNilEmbeddedOptionals() {
        val rewrite = UserProfilePayload.rewriteTeamMembers(
            members = listOf(
                mapOf(
                    "userID" to "uid-1",
                    "displayName" to "Old Name",
                    "age" to 16,
                    "ageGroup" to "youth",
                    "gender" to "male",
                    "photoURL" to "https://old.example/avatar.jpg",
                    "role" to "member",
                ),
                mapOf("userID" to "uid-2", "displayName" to "Other"),
            ),
            user = sampleProfile().copy(
                age = null,
                gender = null,
                photoUrl = null,
            ),
        )

        val updated = rewrite.members.first()
        assertTrue(rewrite.didChange)
        assertEquals("Lea Camper", updated["displayName"])
        assertEquals("Paris Central SDA", updated["church"])
        assertEquals("fr", updated["preferredLanguage"])
        assertFalse(updated.containsKey("age"))
        assertFalse(updated.containsKey("ageGroup"))
        assertFalse(updated.containsKey("gender"))
        assertFalse(updated.containsKey("photoURL"))
        assertEquals("member", updated["role"])
        assertEquals("Other", rewrite.members.last()["displayName"])
    }

    private fun sampleProfile(): UserProfile =
        UserProfile(
            uid = "uid-1",
            displayName = " Lea Camper ",
            age = 22,
            gender = UserGender.Female,
            church = " Paris Central SDA ",
            skills = listOf(" Singing ", "", "First Aid"),
            allergies = listOf(" peanuts ", "Kiwi", "kiwi", ""),
            profession = " Designer ",
            education = " Bachelor ",
            pathfinderRank = " Guide ",
            phone = " +33 6 00 00 00 00 ",
            email = " lea@example.com ",
            preferredLanguage = "fr",
            languages = listOf("fr", "pt"),
            role = UserRole.Adult,
            photoUrl = "https://cdn.example/avatar.jpg",
            photoPublicId = " campzone/avatars/uid-1 ",
            onboardingCompleted = true,
        )

    private companion object {
        const val Timestamp = "serverTimestamp"
        const val Delete = "delete"
        const val DeletionTimestamp = "pendingDeletionAt"
    }
}
