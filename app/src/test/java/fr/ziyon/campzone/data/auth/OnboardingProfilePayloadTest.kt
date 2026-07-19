package fr.ziyon.campzone.data.auth

import fr.ziyon.campzone.core.permissions.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OnboardingProfilePayloadTest {
    @Test
    fun userMergePayloadCompletesOnboardingWithoutChangingRole() {
        val payload = OnboardingProfilePayload.userMergePayload(
            profile = OnboardingProfile(
                age = 17,
                church = " Église Adventiste de Paris-Central ",
                preferredLanguage = "pt",
                gender = UserGender.Female,
            ),
            serverTimestamp = Timestamp,
            deleteField = Delete,
        )

        assertEquals(17, payload["age"])
        assertEquals("youth", payload["ageGroup"])
        assertEquals("Église Adventiste de Paris-Central", payload["church"])
        assertEquals(listOf("pt"), payload["languages"])
        assertEquals("pt", payload["preferredLanguage"])
        assertEquals("female", payload["gender"])
        assertFalse(payload.containsKey("role"))
        assertEquals(true, payload["onboardingCompleted"])
        assertEquals(Timestamp, payload["updatedAt"])
        assertFalse(payload.containsKey("uid"))
        assertFalse(payload.containsKey("id"))
    }

    @Test
    fun nilOptionalProfileFieldsUseDeleteField() {
        val payload = OnboardingProfilePayload.userMergePayload(
            profile = OnboardingProfile(
                age = null,
                church = "Central SDA",
                preferredLanguage = "fr",
                gender = null,
            ),
            serverTimestamp = Timestamp,
            deleteField = Delete,
        )

        assertEquals(Delete, payload["age"])
        assertEquals(Delete, payload["ageGroup"])
        assertEquals(Delete, payload["gender"])
    }

    @Test
    fun ageGroupDerivationMatchesIosRanges() {
        assertEquals(CampingAgeGroup.Kids, CampingAgeGroup.fromAge(12))
        assertEquals(CampingAgeGroup.Youth, CampingAgeGroup.fromAge(13))
        assertEquals(CampingAgeGroup.Youth, CampingAgeGroup.fromAge(35))
        assertEquals(CampingAgeGroup.Adult, CampingAgeGroup.fromAge(36))
    }

    @Test
    fun snapshotPayloadsMatchIosNarrowOnboardingSync() {
        val user = AuthenticatedUser(
            uid = "uid-1",
            email = "camper@example.com",
            displayName = "",
            photoUrl = null,
            role = UserRole.User,
            church = "",
            age = null,
            preferredLanguage = "",
            gender = null,
            onboardingCompleted = false,
        )
        val profile = OnboardingProfile(
            age = 36,
            church = "Central SDA",
            preferredLanguage = "fr",
            gender = UserGender.PreferNotToSay,
        )

        val registration = OnboardingProfilePayload.registrationSnapshotPayload(
            user = user,
            profile = profile,
            serverTimestamp = Timestamp,
        )
        val checkIn = OnboardingProfilePayload.checkInSnapshotPayload(
            user = user,
            profile = profile,
            serverTimestamp = Timestamp,
        )

        assertEquals("camper@example.com", registration["displayName"])
        assertEquals(36, registration["age"])
        assertEquals("adult", registration["ageGroup"])
        assertEquals("prefer_not_to_say", registration["gender"])
        assertEquals(listOf("fr"), registration["languages"])
        assertFalse(checkIn.containsKey("age"))
        assertFalse(checkIn.containsKey("languages"))
        assertEquals("adult", checkIn["ageGroup"])
        assertEquals("fr", checkIn["preferredLanguage"])
    }

    private companion object {
        const val Timestamp = "serverTimestamp"
        const val Delete = "delete"
    }
}
