package fr.ziyon.campzone.data.model

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementPayloadTest {
    @Test
    fun catalogMatchesShippedIosAchievementIds() {
        assertEquals(45, AchievementCatalog.all.size)
        assertEquals(45, AchievementCatalog.all.map { it.id }.distinct().size)
        assertEquals(7, AchievementCatalog.all.count { it.awardKind == AchievementAwardKind.Automatic })
        assertEquals(38, AchievementCatalog.manual.size)
        assertEquals(10, AchievementCatalog.all.count { it.rarity == AchievementRarity.Common })
        assertEquals(10, AchievementCatalog.all.count { it.rarity == AchievementRarity.Uncommon })
        assertEquals(10, AchievementCatalog.all.count { it.rarity == AchievementRarity.Rare })
        assertEquals(10, AchievementCatalog.all.count { it.rarity == AchievementRarity.Epic })
        assertEquals(5, AchievementCatalog.all.count { it.rarity == AchievementRarity.Legendary })
        assertTrue(AchievementCatalog.achievement("first-adventure") != null)
        assertTrue(AchievementCatalog.achievement("campfire-legend") != null)
    }

    @Test
    fun manualAwardPayloadKeepsExplicitNullKeys() {
        val payload = EarnedBadgePayload.awardPayload(
            badge = EarnedBadge(
                id = "tent-ready",
                userId = "user-1",
                earnedAt = Date(),
                campingId = null,
                note = "   ",
            ),
            serverTimestamp = TS,
        )

        assertEquals("tent-ready", payload["id"])
        assertEquals("user-1", payload["userID"])
        assertEquals(TS, payload["earnedAt"])
        assertTrue(payload.containsKey("campingID"))
        assertTrue(payload.containsKey("note"))
        assertNull(payload["campingID"])
        assertNull(payload["note"])
    }

    @Test
    fun decoderFallsBackToDocumentIdAndFiltersByCatalogAtServiceLayer() {
        val decoded = mapOf(
            "userID" to "user-1",
            "earnedAt" to TS,
            "campingID" to null,
            "note" to null,
        ).toEarnedBadgeOrNull("team-captain")

        assertEquals("team-captain", decoded.id)
        assertEquals("user-1", decoded.userId)
        assertEquals(TS, decoded.earnedAt)
    }

    private companion object {
        val TS = Date(42)
    }
}
