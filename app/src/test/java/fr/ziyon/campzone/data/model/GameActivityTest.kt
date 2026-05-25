package fr.ziyon.campzone.data.model

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GameActivityTest {

    @Test
    fun gameUsesClientClockAndOmitsNilPointRuleFields() {
        val now = Date(1_700_000_000_000L)
        val game = Game(
            id = "g1",
            campingId = "camp-1",
            name = "Treasure Hunt",
            createdBy = "admin-1",
            pointRules = listOf(
                PointRule(id = "r1", name = "Win", points = 10),
                PointRule(
                    id = "r2",
                    name = "Foul",
                    points = -5,
                    ruleBrokenPenalty = 2,
                    maxUses = 3,
                    category = "conduct",
                    appliesTo = PointRuleTarget.Team,
                    visibility = PointRuleVisibility.AfterReveal,
                ),
            ),
        )
        val payload = GamePayload.gamePayload(game, now, includeCreatedAt = true)

        assertEquals(now, payload["updatedAt"]) // client Date(), not serverTimestamp
        assertEquals(now, payload["createdAt"])

        @Suppress("UNCHECKED_CAST")
        val rules = payload["pointRules"] as List<Map<String, Any?>>
        assertFalse(rules[0].containsKey("ruleBrokenPenalty")) // omit-when-nil
        assertFalse(rules[0].containsKey("maxUses"))
        assertFalse(rules[0].containsKey("category"))
        assertEquals(-5, rules[1]["points"]) // negative allowed
        assertEquals("afterReveal", rules[1]["visibility"])

        val decoded = payload.toGameOrNull("g1")!!
        assertEquals(2, decoded.pointRules.size)
        assertEquals(PointRuleTarget.Team, decoded.pointRules[1].appliesTo)
    }

    @Test
    fun activityFullSetSignedPointsAndOmitTargets() {
        val created = Date(1_700_000_100_000L)
        val activity = Activity(
            id = "act-1",
            campingId = "camp-1",
            gameId = "g1",
            name = "Penalty",
            points = -5,
            previousScore = 50,
            newScore = 45,
            createdBy = "admin-1",
            createdByName = "Admin",
            createdAt = created,
            targetTeamId = "team-1",
            targetTeamName = "Eagles",
        )
        val payload = ActivityPayload.activityPayload(activity)

        assertEquals(-5, payload["points"])
        assertEquals(45, payload["newScore"])
        assertEquals(created, payload["createdAt"]) // client Date()
        assertEquals("team-1", payload["targetTeamID"])
        assertFalse(payload.containsKey("targetUserID")) // omit-when-nil
        assertFalse(payload.containsKey("pointRuleID"))

        val decoded = payload.toActivityOrNull("act-1")!!
        assertEquals(-5, decoded.points)
        assertEquals(45, decoded.newScore)
        assertEquals("Eagles", decoded.targetTeamName)
    }
}
