package fr.ziyon.campzone.data.lodging

import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.LodgingGenderPolicy
import fr.ziyon.campzone.data.model.LodgingKind
import fr.ziyon.campzone.data.model.LodgingUnit
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LodgingAllocatorTest {

    private fun unit(
        id: String,
        capacity: Int,
        policy: LodgingGenderPolicy = LodgingGenderPolicy.Any,
    ) = LodgingUnit(
        id = id,
        campingId = "camp-1",
        name = id,
        kind = LodgingKind.Tent,
        capacity = capacity,
        genderPolicy = policy,
    )

    private fun attendee(
        id: String,
        gender: UserGender? = null,
        guardianId: String? = null,
        status: RegistrationApprovalStatus = RegistrationApprovalStatus.Approved,
    ) = CampingAttendee(
        id = id,
        userId = id,
        displayName = id,
        church = "Central",
        age = if (guardianId != null) 8 else 30,
        languages = listOf("en"),
        registrationStatus = status,
        gender = gender,
        guardianId = guardianId,
        participantKind = if (guardianId != null) {
            RegistrationParticipantKind.Child
        } else {
            RegistrationParticipantKind.SelfParticipant
        },
    )

    @Test
    fun respectsCapacityAndLeavesOverflowUnplaced() {
        val result = LodgingAllocator().allocate(
            attendees = listOf(attendee("a"), attendee("b"), attendee("c")),
            units = listOf(unit("t1", capacity = 2)),
        )

        assertEquals(2, result.assignmentsByUnitId["t1"]?.size)
        assertEquals(1, result.unplaced.size)
    }

    @Test
    fun honoursGenderPolicy() {
        val result = LodgingAllocator().allocate(
            attendees = listOf(
                attendee("m1", gender = UserGender.Male),
                attendee("f1", gender = UserGender.Female),
            ),
            units = listOf(
                unit("male", capacity = 4, policy = LodgingGenderPolicy.Male),
                unit("female", capacity = 4, policy = LodgingGenderPolicy.Female),
            ),
        )

        assertEquals(listOf("m1"), result.assignmentsByUnitId["male"]?.map { it.id })
        assertEquals(listOf("f1"), result.assignmentsByUnitId["female"]?.map { it.id })
        assertTrue(result.unplaced.isEmpty())
    }

    @Test
    fun keepsFamilyTogetherInOneUnit() {
        val guardian = attendee("g1")
        val child1 = attendee("c1", guardianId = "g1")
        val child2 = attendee("c2", guardianId = "g1")

        val result = LodgingAllocator().allocate(
            attendees = listOf(guardian, child1, child2, attendee("solo")),
            units = listOf(
                // A 2-bed unit cannot hold the 3-person family, forcing the
                // allocator to seat them together in the larger unit.
                unit("small", capacity = 2),
                unit("family", capacity = 4, policy = LodgingGenderPolicy.Family),
            ),
        )

        // The whole family lands in a single unit (it may also pick up the solo
        // adult via tight-packing — what matters is they are not split apart).
        val familyUnit = result.assignmentsByUnitId.values.firstOrNull { members ->
            members.any { it.id == "g1" }
        }?.map { it.id } ?: emptyList()
        assertTrue(familyUnit.containsAll(listOf("g1", "c1", "c2")))
        assertTrue(result.unplaced.isEmpty())
    }

    @Test
    fun ignoresNonApprovedAttendees() {
        val result = LodgingAllocator().allocate(
            attendees = listOf(
                attendee("pending", status = RegistrationApprovalStatus.Pending),
            ),
            units = listOf(unit("t1", capacity = 4)),
        )

        assertTrue(result.assignmentsByUnitId.isEmpty())
        assertTrue(result.unplaced.isEmpty())
    }

    @Test
    fun emptyUnitsPlaceNobody() {
        val result = LodgingAllocator().allocate(
            attendees = listOf(attendee("a")),
            units = emptyList(),
        )
        assertTrue(result.assignmentsByUnitId.isEmpty())
        assertTrue(result.unplaced.isEmpty())
    }
}
