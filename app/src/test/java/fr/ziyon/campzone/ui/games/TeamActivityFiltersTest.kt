package fr.ziyon.campzone.ui.games

import fr.ziyon.campzone.data.model.Activity
import fr.ziyon.campzone.data.model.Team
import fr.ziyon.campzone.data.model.TeamMember
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamActivityFiltersTest {

    @Test
    fun teamEarnedActivitiesIncludesPositiveMemberAwards() {
        val team = team()
        val activities = listOf(
            activity(id = "team-award", points = 12, targetTeamId = team.id, createdAtMs = 1_000),
            activity(id = "member-award", points = 7, targetUserId = "member-1", createdAtMs = 2_000),
            activity(id = "other-member-award", points = 9, targetUserId = "other-user"),
            activity(id = "member-deduction", points = -3, targetUserId = "member-1"),
        )

        val earnedIds = activities.teamEarnedActivities(team).map { it.id }

        assertEquals(listOf("member-award", "team-award"), earnedIds)
    }

    @Test
    fun teamMemberDeductionActivitiesIncludesOnlyNegativeMemberRows() {
        val team = team()
        val activities = listOf(
            activity(id = "team-penalty-ledger", points = -10, targetTeamId = team.id),
            activity(id = "member-deduction", points = -4, targetUserId = "member-1", createdAtMs = 2_000),
            activity(id = "other-deduction", points = -5, targetUserId = "other-user"),
            activity(id = "member-award", points = 6, targetUserId = "member-1"),
        )

        val deductionIds = activities.teamMemberDeductionActivities(team).map { it.id }

        assertEquals(listOf("member-deduction"), deductionIds)
    }

    @Test
    fun matchesTeamOrMemberAcceptsTeamTargetOrCurrentMember() {
        val memberIds = setOf("member-1", "member-2")

        assertTrue(activity(id = "team", targetTeamId = "team-1").matchesTeamOrMember("team-1", memberIds))
        assertTrue(activity(id = "member", targetUserId = "member-1").matchesTeamOrMember("team-1", memberIds))
        assertFalse(activity(id = "other", targetUserId = "other-user").matchesTeamOrMember("team-1", memberIds))
    }

    private fun team() = Team(
        id = "team-1",
        campingId = "camp-1",
        members = listOf(
            TeamMember(id = "member-1", userId = "member-1", displayName = "Ana", church = "Central"),
            TeamMember(id = "member-2", userId = "member-2", displayName = "Bruno", church = "Central"),
        ),
    )

    private fun activity(
        id: String,
        points: Int = 1,
        targetTeamId: String? = null,
        targetUserId: String? = null,
        createdAtMs: Long = 0,
    ) = Activity(
        id = id,
        campingId = "camp-1",
        name = id,
        points = points,
        previousScore = 0,
        newScore = points,
        createdBy = "admin",
        createdByName = "Admin",
        createdAt = Date(createdAtMs),
        targetTeamId = targetTeamId,
        targetUserId = targetUserId,
    )
}
