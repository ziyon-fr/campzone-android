package fr.ziyon.campzone.data.model

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun gamePayloadWritesVenueLinksAndLegacyDocsDefaultHidden() {
        val now = Date(1_700_000_000_000L)
        val game = Game(
            id = "g1",
            campingId = "camp-1",
            name = "Trail Race",
            createdBy = "admin-1",
            venuePointIds = listOf(" pin-1 ", "pin-2", "pin-1", ""),
            locationVisibleToAll = true,
        )
        val payload = GamePayload.gamePayload(game, now, includeCreatedAt = false)

        assertEquals(listOf("pin-1", "pin-2"), payload["venuePointIDs"])
        assertEquals(true, payload["locationVisibleToAll"])

        val decoded = payload.toGameOrNull("g1")!!
        assertEquals(listOf("pin-1", "pin-2"), decoded.venuePointIds)
        assertTrue(decoded.locationVisibleToAll)

        val defaultHiddenPayload = GamePayload.gamePayload(
            game.copy(venuePointIds = emptyList(), locationVisibleToAll = false),
            now,
            includeCreatedAt = false,
        )
        assertFalse(defaultHiddenPayload.containsKey("venuePointIDs"))
        assertFalse(defaultHiddenPayload.containsKey("locationVisibleToAll"))

        val legacy = mapOf(
            "campingID" to "camp-1",
            "name" to "Legacy game",
        ).toGameOrNull("legacy")!!
        assertTrue(legacy.venuePointIds.isEmpty())
        assertFalse(legacy.locationVisibleToAll)
    }

    @Test
    fun leadershipOnlyVenuePointIdsHideUnpublishedGameLocationsUnlessRepublished() {
        val hiddenGame = Game(
            id = "g-hidden",
            campingId = "camp-1",
            name = "Secret clue",
            venuePointIds = listOf("pin-hidden", "pin-shared"),
            locationVisibleToAll = false,
        )
        val publicGame = Game(
            id = "g-public",
            campingId = "camp-1",
            name = "Public race",
            venuePointIds = listOf("pin-shared", "pin-public"),
            locationVisibleToAll = true,
        )
        val map = VenueMap(
            campingId = "camp-1",
            points = listOf(
                VenuePoint(id = "pin-hidden", name = "Hidden", category = VenueCategory.Program),
                VenuePoint(id = "pin-shared", name = "Shared", category = VenueCategory.Program),
                VenuePoint(id = "pin-public", name = "Public", category = VenueCategory.Program),
            ),
        )

        assertEquals(setOf("pin-hidden"), leadershipOnlyVenuePointIds(listOf(hiddenGame, publicGame)))
        assertEquals(
            listOf("pin-shared", "pin-public"),
            map.visibleForGameLocationRules(listOf(hiddenGame, publicGame), canSeeHiddenGameLocations = false)
                .points
                .map { it.id },
        )
        assertEquals(3, map.visibleForGameLocationRules(listOf(hiddenGame), canSeeHiddenGameLocations = true).points.size)
    }

    @Test
    fun gameInstructionsImagesOmitKindAndDecodeLegacyAsImage() {
        val instructions = GameInstructions(
            title = "Leader setup",
            description = "Bring flags.",
            images = listOf(
                GameInstructionAttachment(
                    id = "img-1",
                    url = "https://example.com/flag.jpg",
                    publicId = "campzone/flag",
                ),
            ),
        )

        val payload = GameInstructionsPayload.instructionsPayload(instructions)
        @Suppress("UNCHECKED_CAST")
        val image = (payload["images"] as List<Map<String, Any?>>).single()
        assertEquals("img-1", image["id"])
        assertEquals("campzone/flag", image["publicID"])
        assertFalse(image.containsKey("kind"))

        val decoded = payload.toGameInstructions()
        assertEquals(GameInstructionAttachmentKind.Image, decoded.images.single().kind)
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
