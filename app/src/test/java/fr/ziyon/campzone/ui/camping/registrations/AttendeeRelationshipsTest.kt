package fr.ziyon.campzone.ui.camping.registrations

import fr.ziyon.campzone.data.family.FamilyRelationship
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test

class AttendeeRelationshipsTest {
    @Test
    fun childGetsGuardianAndSiblingWithoutDuplicates() {
        val guardian = attendee("guardian", "guardian", RegistrationParticipantKind.SelfParticipant)
        val child = attendee("child", "child", RegistrationParticipantKind.Child, "guardian")
            .copy(relationship = FamilyRelationship.Parent)
        val sibling = attendee("sibling", "sibling", RegistrationParticipantKind.Child, "guardian")
        val camping = camping(listOf(guardian, child, sibling))

        val related = relatedAttendees(camping, child)

        assertEquals(listOf("guardian", "sibling"), related.map { it.attendee.id })
    }

    @Test
    fun guardianGetsEveryDependent() {
        val guardian = attendee("guardian", "guardian", RegistrationParticipantKind.SelfParticipant)
        val child = attendee("child", "child", RegistrationParticipantKind.Child, "guardian")
        val camping = camping(listOf(guardian, child))

        assertEquals(listOf("child"), relatedAttendees(camping, guardian).map { it.attendee.id })
    }

    private fun attendee(
        id: String,
        userId: String,
        kind: RegistrationParticipantKind,
        guardianId: String? = null,
    ) = CampingAttendee(
        id = id,
        userId = userId,
        displayName = id,
        church = "Church",
        age = 12,
        languages = listOf("en"),
        registrationStatus = RegistrationApprovalStatus.Approved,
        participantKind = kind,
        guardianId = guardianId,
    )

    private fun camping(attendees: List<CampingAttendee>) = Camping(
        id = "camp",
        title = "Camp",
        description = "",
        startDate = Date(0),
        endDate = Date(1),
        organizerLevel = OrganizerLevel(OrganizerType.International, "International"),
        location = "Place",
        registrationStatus = CampingRegistrationStatus.Open,
        attendees = attendees,
    )
}
