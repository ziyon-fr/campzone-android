package fr.ziyon.campzone.data.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RegistrationNotificationRequestTest {

    private fun request(
        participantName: String,
        requestedByName: String,
        participantCount: Int,
        selfRegistration: Boolean,
    ) = RegistrationNotificationRequest(
        campingId = "camp-1",
        campingTitle = "Summer Camp",
        participantName = participantName,
        requestedByName = requestedByName,
        participantCount = participantCount,
        selfRegistration = selfRegistration,
    )

    @Test
    fun selfRegistrationDoesNotRepeatOwnName() {
        val body = request(
            participantName = "Lea Muller",
            requestedByName = "Lea Muller",
            participantCount = 1,
            selfRegistration = true,
        ).body

        assertEquals("Lea Muller registered for Summer Camp. Review and approve.", body)
        // Never the redundant "X requested to register X".
        assertFalse(body.contains("requested to register"))
    }

    @Test
    fun singleFamilyMemberNamesTheParticipant() {
        val body = request(
            participantName = "Sophie Muller",
            requestedByName = "Lea Muller",
            participantCount = 1,
            selfRegistration = false,
        ).body

        assertEquals(
            "Lea Muller requested to register Sophie Muller for Summer Camp. Review and approve.",
            body,
        )
    }

    @Test
    fun multipleParticipantsUseTheCountBody() {
        val body = request(
            participantName = "Sophie Muller",
            requestedByName = "Lea Muller",
            participantCount = 3,
            selfRegistration = true,
        ).body

        assertEquals(
            "Lea Muller requested to register 3 participants for Summer Camp. Review and approve.",
            body,
        )
    }
}
