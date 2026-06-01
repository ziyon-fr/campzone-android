package fr.ziyon.campzone.data.lodging

import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.LodgingGenderPolicy
import fr.ziyon.campzone.data.model.LodgingUnit
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus

/**
 * Pure-data tent/cabin allocator — a deterministic, side-effect-free pass the UI
 * can call safely (same spirit as [fr.ziyon.campzone.data.teams.TeamBalancer],
 * port of the iOS `LodgingAllocator`). It is a constrained bin-pack:
 *
 * Hard rules (never violated):
 *  1. A unit never exceeds its capacity.
 *  2. `male`/`female` units only take attendees of that gender.
 *  3. A family (a guardian + the children they registered) stays in one unit;
 *     it is only split when no single unit can hold it.
 *
 * Soft preference: tighter packing first (smallest leftover) and age-coherent
 * units, so the result feels intentional.
 */
class LodgingAllocator {

    data class Result(
        /** Attendees assigned per unit id (only units that received people). */
        val assignmentsByUnitId: Map<String, List<CampingAttendee>>,
        /** Approved attendees that did not fit anywhere (capacity / policy). */
        val unplaced: List<CampingAttendee>,
        /** Lower is better; 0 means everyone placed with no policy strain. */
        val score: Double,
    )

    /**
     * Allocates the **approved** [attendees] into [units]. Pre-existing
     * `occupantIds` are ignored — this is a full re-plan of the approved set so
     * the caller can persist a clean state.
     */
    fun allocate(attendees: List<CampingAttendee>, units: List<LodgingUnit>): Result {
        if (units.isEmpty()) return Result(emptyMap(), emptyList(), 0.0)
        val approved = attendees.filter { it.registrationStatus == RegistrationApprovalStatus.Approved }
        if (approved.isEmpty()) return Result(emptyMap(), emptyList(), 0.0)

        val remaining = units.associate { it.id to it.capacity }.toMutableMap()
        val assignments = units.associate { it.id to mutableListOf<CampingAttendee>() }.toMutableMap()
        val unplaced = mutableListOf<CampingAttendee>()

        // Largest / family groups first — they are the hardest to seat.
        for (group in familyGroups(approved)) {
            if (!place(group, units, remaining, assignments)) {
                unplaced.addAll(group)
            }
        }

        val score = unplaced.size.toDouble() + ageSpread(assignments)
        val nonEmpty = assignments.filterValues { it.isNotEmpty() }.mapValues { it.value.toList() }
        return Result(nonEmpty, unplaced, score)
    }

    /**
     * Groups a guardian and the children they registered together; a solo
     * adult/self registration is its own singleton group. Multi-person families
     * are sorted before individuals (hardest to seat first).
     */
    private fun familyGroups(attendees: List<CampingAttendee>): List<List<CampingAttendee>> =
        attendees
            .groupBy { it.guardianId ?: it.userId }
            .values
            .map { group -> group.sortedBy { it.displayName.lowercase() } }
            .sortedWith(
                compareByDescending<List<CampingAttendee>> { it.size }
                    .thenBy { it.firstOrNull()?.id ?: "" },
            )

    private fun place(
        group: List<CampingAttendee>,
        units: List<LodgingUnit>,
        remaining: MutableMap<String, Int>,
        assignments: MutableMap<String, MutableList<CampingAttendee>>,
    ): Boolean {
        val isFamily = group.size > 1

        fun eligible(unit: LodgingUnit, count: Int): Boolean {
            if ((remaining[unit.id] ?: 0) < count) return false
            return when (unit.genderPolicy) {
                LodgingGenderPolicy.Any, LodgingGenderPolicy.Family -> true
                LodgingGenderPolicy.Male, LodgingGenderPolicy.Female ->
                    group.all { unit.genderPolicy.accepts(it.gender) }
            }
        }

        // Prefer a single unit that fits the whole group: family units first for
        // families, then the tightest fit (smallest leftover) so large units
        // aren't fragmented; deterministic id tie-break.
        val whole = units
            .filter { eligible(it, group.size) }
            .sortedWith(
                Comparator { a, b ->
                    val aFam = a.genderPolicy == LodgingGenderPolicy.Family
                    val bFam = b.genderPolicy == LodgingGenderPolicy.Family
                    if (isFamily && aFam != bFam) return@Comparator if (aFam) -1 else 1
                    val aLeft = (remaining[a.id] ?: 0) - group.size
                    val bLeft = (remaining[b.id] ?: 0) - group.size
                    if (aLeft != bLeft) aLeft - bLeft else a.id.compareTo(b.id)
                },
            )

        whole.firstOrNull()?.let { unit ->
            assignments.getValue(unit.id).addAll(group)
            remaining[unit.id] = (remaining[unit.id] ?: 0) - group.size
            return true
        }

        // No single unit fits the whole group — split it, still honouring
        // capacity + gender, packing into the unit with the most room left
        // (smallest id tie-break).
        var anyPlaced = false
        for (member in group) {
            val target = units
                .filter { eligible(it, 1) }
                .maxWithOrNull(
                    compareBy<LodgingUnit> { remaining[it.id] ?: 0 }.thenByDescending { it.id },
                ) ?: continue
            assignments.getValue(target.id).add(member)
            remaining[target.id] = (remaining[target.id] ?: 0) - 1
            anyPlaced = true
        }
        return anyPlaced
    }

    /** Soft penalty: distinct age groups mixed inside a unit, summed (×0.5). */
    private fun ageSpread(assignments: Map<String, List<CampingAttendee>>): Double {
        var total = 0.0
        for (members in assignments.values) {
            if (members.isEmpty()) continue
            val distinct = members.map { it.ageGroup.name }.toSet().size
            total += maxOf(0, distinct - 1) * 0.5
        }
        return total
    }
}
