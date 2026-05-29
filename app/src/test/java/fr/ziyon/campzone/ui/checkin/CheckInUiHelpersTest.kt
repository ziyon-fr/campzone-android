package fr.ziyon.campzone.ui.checkin

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

class CheckInUiHelpersTest {

    @Test
    fun managedAttendeesSortSelfFirstThenChildrenByName() {
        val camping = camping(
            attendees = listOf(
                attendee("child-z", "Zoe", guardianId = "parent-1", kind = RegistrationParticipantKind.Child),
                attendee("parent-1", "Maya", userId = "parent-1"),
                attendee("child-a", "Ana", guardianId = "parent-1", kind = RegistrationParticipantKind.Child),
                attendee("other", "Other", userId = "other"),
            ),
        )

        val names = managedCheckInAttendees(
            camping = camping,
            userId = "parent-1",
            status = RegistrationApprovalStatus.Approved,
        ).map { it.displayName }

        assertEquals(listOf("Maya", "Ana", "Zoe"), names)
    }

    @Test
    fun managedAttendeesIncludesPendingOnlyForRequestedStatus() {
        val camping = camping(
            attendees = listOf(
                attendee("approved", "Approved", guardianId = "parent-1", kind = RegistrationParticipantKind.Child),
                attendee(
                    "pending",
                    "Pending",
                    guardianId = "parent-1",
                    kind = RegistrationParticipantKind.Child,
                    status = RegistrationApprovalStatus.Pending,
                ),
            ),
        )

        val pending = managedCheckInAttendees(
            camping = camping,
            userId = "parent-1",
            status = RegistrationApprovalStatus.Pending,
        )

        assertEquals(listOf("Pending"), pending.map { it.displayName })
    }

    @Test
    fun pendingFilterMatchesNameOrChurch() {
        val attendees = listOf(
            attendee("a", "Maria Silva", church = "Paris Central SDA"),
            attendee("b", "Joao", church = "Lyon SDA"),
        )

        assertEquals(listOf("Maria Silva"), filterPendingAttendees(attendees, "maria").map { it.displayName })
        assertEquals(listOf("Joao"), filterPendingAttendees(attendees, "lyon").map { it.displayName })
    }

    private fun camping(attendees: List<CampingAttendee>) = Camping(
        id = "camp-1",
        title = "Family Summer Camp",
        description = "Preview camp",
        startDate = Date(1_000),
        endDate = Date(2_000),
        organizerLevel = OrganizerLevel(OrganizerType.Church, "Paris Central SDA"),
        location = "Lake Annecy",
        registrationStatus = CampingRegistrationStatus.Open,
        attendees = attendees,
    )

    private fun attendee(
        id: String,
        name: String,
        userId: String = id,
        guardianId: String? = null,
        church: String = "Paris Central SDA",
        kind: RegistrationParticipantKind = RegistrationParticipantKind.SelfParticipant,
        status: RegistrationApprovalStatus = RegistrationApprovalStatus.Approved,
    ) = CampingAttendee(
        id = id,
        userId = userId,
        displayName = name,
        church = church,
        age = 20,
        languages = listOf("fr"),
        participantKind = kind,
        guardianId = guardianId,
        registrationStatus = status,
    )
}
