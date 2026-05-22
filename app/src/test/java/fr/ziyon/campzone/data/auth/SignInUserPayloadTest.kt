package fr.ziyon.campzone.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInUserPayloadTest {
    @Test
    fun firstSignInCreatesGuestUserDocumentPayload() {
        val payload = SignInUserPayload.mergePayload(
            identity = identity(),
            existing = null,
            serverTimestamp = Timestamp,
        )

        assertEquals("uid-1", payload["id"])
        assertEquals("uid-1", payload["uid"])
        assertEquals("guest", payload["role"])
        assertEquals(false, payload["onboardingCompleted"])
        assertEquals(Timestamp, payload["createdAt"])
        assertEquals(Timestamp, payload["updatedAt"])
        assertEquals("user@example.com", payload["email"])
        assertEquals("Ada Camp", payload["displayName"])
        assertEquals("https://example.com/photo.jpg", payload["photoURL"])
        assertEquals(listOf("google.com"), payload["providerIDs"])
        assertEquals("google", payload["lastAuthProvider"])
    }

    @Test
    fun existingProfileFieldsAreFirstWriteWins() {
        val payload = SignInUserPayload.mergePayload(
            identity = identity(),
            existing = mapOf(
                "email" to "edited@example.com",
                "displayName" to "Edited Name",
                "photoURL" to "https://example.com/edited.jpg",
                "role" to "adult",
                "onboardingCompleted" to true,
                "createdAt" to "existing-created-at",
            ),
            serverTimestamp = Timestamp,
        )

        assertFalse(payload.containsKey("email"))
        assertFalse(payload.containsKey("displayName"))
        assertFalse(payload.containsKey("photoURL"))
        assertFalse(payload.containsKey("role"))
        assertFalse(payload.containsKey("onboardingCompleted"))
        assertFalse(payload.containsKey("createdAt"))
        assertEquals(Timestamp, payload["updatedAt"])
        assertEquals("uid-1", payload["id"])
        assertEquals("uid-1", payload["uid"])
    }

    @Test
    fun blankExistingAuthFieldsCanBeFilledFromProviderProfile() {
        val payload = SignInUserPayload.mergePayload(
            identity = identity(),
            existing = mapOf(
                "email" to "",
                "displayName" to " ",
                "photoURL" to null,
            ),
            serverTimestamp = Timestamp,
        )

        assertEquals("user@example.com", payload["email"])
        assertEquals("Ada Camp", payload["displayName"])
        assertEquals("https://example.com/photo.jpg", payload["photoURL"])
        assertEquals("guest", payload["role"])
        assertTrue(payload.containsKey("createdAt"))
        assertEquals(false, payload["onboardingCompleted"])
    }

    private fun identity(): SignInIdentity =
        SignInIdentity(
            uid = "uid-1",
            email = " user@example.com ",
            displayName = " Ada Camp ",
            photoUrl = " https://example.com/photo.jpg ",
            providerIds = listOf("google.com", "google.com"),
            lastAuthProvider = "google",
        )

    private companion object {
        const val Timestamp = "serverTimestamp"
    }
}
