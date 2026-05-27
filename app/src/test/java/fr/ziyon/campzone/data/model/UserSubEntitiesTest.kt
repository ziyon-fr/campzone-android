package fr.ziyon.campzone.data.model

import com.google.firebase.Timestamp
import fr.ziyon.campzone.core.permissions.UserRole
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserSubEntitiesTest {

    // --- EarnedBadge ---

    @Test
    fun badgeAwardWritesExplicitNullForAbsentCampingAndNote() {
        val payload = EarnedBadgePayload.awardPayload(
            EarnedBadge(id = "first_camp", userId = "u1", campingId = null, note = null),
            TS,
        )
        assertEquals("first_camp", payload["id"])
        assertEquals(TS, payload["earnedAt"])
        // explicit null (key present, value null) - not omitted, not deleted
        assertTrue(payload.containsKey("campingID"))
        assertNull(payload["campingID"])
        assertTrue(payload.containsKey("note"))
        assertNull(payload["note"])

        val populated = EarnedBadgePayload.awardPayload(
            EarnedBadge(id = "first_camp", userId = "u1", campingId = "camp-1", note = "Great job"),
            TS,
        )
        assertEquals("camp-1", populated["campingID"])
        assertEquals("Great job", populated["note"])
    }

    @Test
    fun badgeRoundTrips() {
        val decoded = mapOf(
            "id" to "first_camp",
            "userID" to "u1",
            "campingID" to "camp-1",
            "note" to "Auto-awarded",
        ).toEarnedBadgeOrNull("first_camp")
        assertEquals("camp-1", decoded.campingId)
        assertEquals("Auto-awarded", decoded.note)
    }

    // --- NotificationToken ---

    @Test
    fun tokenPayloadHasNoAppIdAndRoundTrips() {
        val token = NotificationToken(
            token = "fcm-abc",
            role = UserRole.Leader,
            localeIdentifier = "fr_FR",
            appVersion = "1.0.0",
        )
        val payload = NotificationPrefsPayload.tokenPayload(token, TS, includeCreatedAt = true)
        assertEquals("android", payload["platform"])
        assertEquals("fcm", payload["provider"])
        assertEquals("leader", payload["role"])
        assertFalse(payload.containsKey("appID")) // lives only in the backend payload
        assertEquals(TS, payload["createdAt"])

        val decoded = payload.toNotificationTokenOrNull()!!
        assertEquals("fcm-abc", decoded.token)
        assertEquals(UserRole.Leader, decoded.role)
    }

    // --- NotificationSettings ---

    @Test
    fun settingsStoresSubscribedRoleRawValuesSortedAndDeduped() {
        val settings = NotificationSettings(
            subscribedRoles = listOf(UserRole.Leader, UserRole.Admin, UserRole.Leader),
            subscribedCampingIds = listOf("c2", "c1", "c2"),
        )
        val payload = NotificationPrefsPayload.settingsPayload(settings, TS)
        assertEquals(listOf("admin", "leader"), payload["subscribedRoleRawValues"]) // deduped + sorted
        assertEquals(listOf("c1", "c2"), payload["subscribedCampingIDs"])
        assertFalse(payload.containsKey("createdAt")) // no client createdAt

        val decoded = payload.toNotificationSettings()
        assertEquals(listOf(UserRole.Admin, UserRole.Leader), decoded.subscribedRoles)
        assertTrue(decoded.isEnabled)
    }

    // --- BlockedUser ---

    @Test
    fun blockedAtIsTimestampOnly() {
        val withTimestamp = mapOf(
            "blockedUserID" to "blocked-1",
            "displayName" to "Spammer",
            "blockedAt" to Timestamp(Date(1_000_000)),
        ).toBlockedUser("blocked-1")
        assertEquals(Date(1_000_000), withTimestamp.blockedAt)

        // a raw Date must NOT be accepted (no Date fallback on read)
        val withRawDate = mapOf(
            "blockedUserID" to "blocked-1",
            "blockedAt" to Date(1_000_000),
        ).toBlockedUser("blocked-1")
        assertNull(withRawDate.blockedAt)
        assertEquals("blocked-1", withRawDate.displayName) // falls back to doc ID
    }

    private companion object {
        const val TS = "serverTimestamp"
    }
}
