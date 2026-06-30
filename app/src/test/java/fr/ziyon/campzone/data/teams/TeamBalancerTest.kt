package fr.ziyon.campzone.data.teams

import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamBalancerTest {

    @Test
    fun balanceUsesOnlyApprovedAttendeesAndAllSelectedTeams() {
        val result = TeamBalancer().balance(
            attendees = listOf(
                attendee("u1", "Ana", RegistrationApprovalStatus.Approved),
                attendee("u2", "Marc", RegistrationApprovalStatus.Pending),
                attendee("u3", "Joao", RegistrationApprovalStatus.Approved),
            ),
            teamIds = listOf("lions", "eagles"),
        )

        assertEquals(setOf("lions", "eagles"), result.assignmentsByTeamId.keys)
        val assigned = result.assignmentsByTeamId.values.flatten()
        assertEquals(listOf("u1", "u3"), assigned.map { it.id }.sorted())
        assertFalse(assigned.any { it.id == "u2" })
    }

    @Test
    fun handlesLargerCampWithoutDroppingAssignments() {
        val attendees = (0 until 130).map { index ->
            attendee(
                id = "large-$index",
                name = "Preview Attendee ${index + 1}",
                status = RegistrationApprovalStatus.Approved,
                church = listOf("Central", "North", "East", "West", "South")[index % 5],
                age = listOf(9, 12, 16, 19, 24, 37, 52)[index % 7],
                gender = if (index % 3 == 0) UserGender.Female else UserGender.Male,
                preferredLanguage = listOf("fr", "pt", "en")[index % 3],
                languages = if (index % 4 == 0) {
                    listOf("fr", "pt")
                } else {
                    listOf(listOf("fr"), listOf("pt"), listOf("en"))[index % 3]
                },
            )
        }
        val teamIds = (0 until 6).map { "team-$it" }

        val result = TeamBalancer().balance(attendees, teamIds)

        assertEquals(attendees.map { it.id }.toSet(), assignedIds(result))
        assertTrue(maxTeamSizeSpread(result) <= 1)
    }

    @Test
    fun cancellationSignalShortCircuitsBalanceWork() {
        val attendees = (0 until 30).map { index ->
            attendee("cancel-$index", "Cancel $index", RegistrationApprovalStatus.Approved)
        }

        val result = TeamBalancer().balance(
            attendees = attendees,
            teamIds = listOf("alpha", "bravo", "charlie"),
            shouldCancel = { true },
        )

        assertTrue(assignedIds(result).isEmpty())
    }

    private fun attendee(
        id: String,
        name: String,
        status: RegistrationApprovalStatus,
        church: String = "Paris Central SDA",
        age: Int = 16,
        gender: UserGender? = UserGender.PreferNotToSay,
        preferredLanguage: String = "fr",
        languages: List<String> = listOf("fr"),
    ) = CampingAttendee(
        id = id,
        userId = id,
        displayName = name,
        church = church,
        age = age,
        languages = languages,
        preferredLanguage = preferredLanguage,
        gender = gender,
        registrationStatus = status,
    )

    private fun assignedIds(result: TeamBalanceResult): Set<String> =
        result.assignmentsByTeamId.values.flatten().map { it.id }.toSet()

    private fun maxTeamSizeSpread(result: TeamBalanceResult): Int {
        val counts = result.assignmentsByTeamId.values.map { it.size }
        return (counts.maxOrNull() ?: 0) - (counts.minOrNull() ?: 0)
    }
}
