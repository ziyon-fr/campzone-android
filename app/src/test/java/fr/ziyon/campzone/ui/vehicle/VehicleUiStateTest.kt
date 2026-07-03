package fr.ziyon.campzone.ui.vehicle

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
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

class VehicleUiStateTest {
    @Test
    fun actionSubjectAttendeeUsesFamilyInvitationWithoutReplacingSelfFlow() {
        val user = AuthenticatedUser(
            uid = "guardian-1",
            email = "guardian@example.com",
            displayName = "Ana",
            photoUrl = null,
            role = UserRole.Adult,
            church = "Paris Central",
            age = 36,
            preferredLanguage = "en",
            gender = null,
            onboardingCompleted = true,
        )
        val state = VehicleUiState(camping = camping())

        assertEquals(
            "guardian-1",
            state.actionSubjectAttendee(
                user = user,
                initialDecisionKind = null,
                initialRegistrationId = null,
            )?.id,
        )
        assertEquals(
            "child-emma",
            state.actionSubjectAttendee(
                user = user,
                initialDecisionKind = "invitation",
                initialRegistrationId = "child-emma",
            )?.id,
        )
        assertEquals(
            "guardian-1",
            state.actionSubjectAttendee(
                user = user,
                initialDecisionKind = "request",
                initialRegistrationId = "child-emma",
            )?.id,
        )
    }

    private fun camping(): Camping =
        Camping(
            id = "camp-1",
            title = "Camp",
            description = "",
            startDate = Date(1_800_000_000_000L),
            endDate = Date(1_800_086_400_000L),
            organizerLevel = OrganizerLevel(OrganizerType.Church, "Paris Central"),
            location = "Paris",
            registrationStatus = CampingRegistrationStatus.Open,
            attendees = listOf(
                attendee(
                    id = "guardian-1",
                    userId = "guardian-1",
                    name = "Ana",
                    kind = RegistrationParticipantKind.SelfParticipant,
                ),
                attendee(
                    id = "child-emma",
                    userId = "child-emma",
                    name = "Emma",
                    kind = RegistrationParticipantKind.Child,
                    guardianId = "guardian-1",
                ),
            ),
        )

    private fun attendee(
        id: String,
        userId: String,
        name: String,
        kind: RegistrationParticipantKind,
        guardianId: String? = null,
    ): CampingAttendee =
        CampingAttendee(
            id = id,
            userId = userId,
            displayName = name,
            church = "Paris Central",
            age = 12,
            languages = emptyList(),
            registrationStatus = RegistrationApprovalStatus.Approved,
            participantKind = kind,
            guardianId = guardianId,
        )
}
