package fr.ziyon.campzone.data.model

import fr.ziyon.campzone.data.auth.UserGender
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TeamPayloadTest {

    @Test
    fun fullRewriteDerivesMemberUserIdsAndNormalizesCaptaincy() {
        val team = sampleTeam().copy(
            members = listOf(
                member("u1", TeamMemberRole.Captain),
                member("u2", TeamMemberRole.Captain), // duplicate captain → demoted
                member("u3", TeamMemberRole.ViceCaptain),
                member("u4", TeamMemberRole.Member),
            ),
        )
        val payload = TeamPayload.teamPayload(team, TS, DEL, includeCreatedAt = true)

        assertEquals(listOf("u1", "u2", "u3", "u4"), payload["memberUserIDs"])

        @Suppress("UNCHECKED_CAST")
        val members = payload["members"] as List<Map<String, Any?>>
        assertEquals("captain", members[0]["role"])
        assertEquals("member", members[1]["role"]) // second captain demoted
        assertEquals("viceCaptain", members[2]["role"])
        assertEquals(TS, payload["createdAt"])
    }

    @Test
    fun penaltyStoredPositiveWithRawDate() {
        val created = Date(123_456)
        val team = sampleTeam().copy(
            penalties = listOf(TeamPenalty(id = "pen-1", reason = "Late", points = 5, createdAt = created)),
        )
        val payload = TeamPayload.teamPayload(team, TS, DEL, includeCreatedAt = false)

        @Suppress("UNCHECKED_CAST")
        val penalty = (payload["penalties"] as List<Map<String, Any?>>).first()
        assertEquals(5, penalty["points"]) // positive magnitude
        assertEquals(created, penalty["createdAt"]) // raw Date, not serverTimestamp
    }

    @Test
    fun photoDeleteWhenEmpty() {
        val payload = TeamPayload.teamPayload(
            sampleTeam().copy(photoUrl = null, photoPublicId = null),
            TS, DEL, includeCreatedAt = false,
        )
        assertEquals(DEL, payload["photoURL"])
        assertEquals(DEL, payload["photoPublicID"])
        assertFalse(payload.containsKey("createdAt"))
    }

    @Test
    fun totalScoreComputedNotStored() {
        val team = sampleTeam().copy(
            points = 100,
            members = listOf(
                member("u1", TeamMemberRole.Captain).copy(personalScore = 20),
                member("u2", TeamMemberRole.Member).copy(personalScore = 5),
            ),
            penalties = listOf(TeamPenalty("pen-1", "Late", 10, Date(1))),
        )
        // 100 + (20 + 5) - 10 = 115
        assertEquals(115, team.totalScore)

        val payload = TeamPayload.teamPayload(team, TS, DEL, includeCreatedAt = false)
        assertFalse(payload.containsKey("totalScore"))
    }

    @Test
    fun roundTripsThroughDecoder() {
        val original = sampleTeam().copy(
            members = listOf(
                member("u1", TeamMemberRole.Captain).copy(
                    age = 22,
                    gender = UserGender.Male,
                    personalScore = 7,
                ),
            ),
        )
        val payload = TeamPayload.teamPayload(original, Date(9), DEL, includeCreatedAt = false)
        val decoded = payload.toTeamOrNull(documentId = "team-1")!!

        assertEquals("team-1", decoded.id)
        assertEquals(original.name, decoded.name)
        assertEquals(original.colorHex, decoded.colorHex)
        assertEquals(listOf("u1"), decoded.memberUserIds)
        assertEquals(TeamMemberRole.Captain, decoded.members.first().role)
        assertEquals(22, decoded.members.first().age)
        assertEquals(UserGender.Male, decoded.members.first().gender)
        assertEquals(7, decoded.members.first().personalScore)
    }

    private companion object {
        const val TS = "serverTimestamp"
        const val DEL = "delete"

        fun sampleTeam() = Team(id = "team-1", campingId = "camp-1", name = "Eagles")

        fun member(uid: String, role: TeamMemberRole) = TeamMember(
            id = uid,
            userId = uid,
            displayName = "Member $uid",
            church = "Paris Central SDA",
            role = role,
        )
    }
}
