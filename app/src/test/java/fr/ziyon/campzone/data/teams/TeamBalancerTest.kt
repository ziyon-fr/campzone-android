package fr.ziyon.campzone.data.teams

import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun attendee(
        id: String,
        name: String,
        status: RegistrationApprovalStatus,
    ) = CampingAttendee(
        id = id,
        userId = id,
        displayName = name,
        church = "Paris Central SDA",
        age = 16,
        languages = listOf("fr"),
        preferredLanguage = "fr",
        gender = UserGender.PreferNotToSay,
        registrationStatus = status,
    )
}
