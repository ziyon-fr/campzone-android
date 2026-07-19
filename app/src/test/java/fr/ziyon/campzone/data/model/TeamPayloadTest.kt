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

    @Test
    fun staffRolePayloadDerivesMemberUserIdsAndCapabilities() {
        val role = sampleStaffRole()
        val payload = StaffRolePayload.staffRolePayload(role, TS, includeCreatedAt = true)

        assertEquals("staff-1", payload["id"])
        assertEquals("camp-1", payload["campingID"])
        assertEquals("Worship Team", payload["name"])
        assertEquals("worship", payload["kind"])
        assertEquals(listOf("u1", "u2"), payload["memberUserIDs"])
        assertEquals(listOf("manageSchedule", "manageAnnouncements"), payload["capabilities"])
        assertEquals(true, payload["chatEnabled"])
        assertEquals(TS, payload["createdAt"])
    }

    @Test
    fun staffRoleRoundTripsThroughDecoder() {
        val original = sampleStaffRole()
        val payload = StaffRolePayload.staffRolePayload(original, Date(9), includeCreatedAt = false)
        val decoded = payload.toStaffRoleOrNull(documentId = "staff-1")!!

        assertEquals("staff-1", decoded.id)
        assertEquals(StaffRoleKind.Worship, decoded.kind)
        assertEquals(listOf("u1", "u2"), decoded.memberUserIds)
        assertEquals("Lead", decoded.members.first().title)
        assertEquals(
            listOf(StaffCapability.ManageSchedule, StaffCapability.ManageAnnouncements),
            decoded.capabilities,
        )
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

        fun sampleStaffRole() = CampingStaffRole(
            id = "staff-1",
            campingId = "camp-1",
            name = "Worship Team",
            kind = StaffRoleKind.Worship,
            description = "Music and worship moments",
            symbolName = "music.mic",
            colorHex = "#6A4C93",
            members = listOf(
                StaffRoleMember(id = "u1", userId = "u1", displayName = "Ana", church = "Paris", title = "Lead"),
                StaffRoleMember(id = "u2", userId = "u2", displayName = "Marc", church = "Lyon"),
            ),
            capabilities = listOf(StaffCapability.ManageSchedule, StaffCapability.ManageAnnouncements),
            chatEnabled = true,
            createdByUid = "creator",
        )
    }
}
