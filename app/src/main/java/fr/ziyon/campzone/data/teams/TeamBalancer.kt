package fr.ziyon.campzone.data.teams

import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import kotlin.math.abs

data class TeamBalanceResult(
    val assignmentsByTeamId: Map<String, List<CampingAttendee>>,
    val balanceScore: Double,
)

class TeamBalancer {

    fun balance(
        attendees: List<CampingAttendee>,
        teamIds: List<String>,
    ): TeamBalanceResult {
        val cleanedTeamIds = teamIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleanedTeamIds.isEmpty()) return TeamBalanceResult(emptyMap(), 0.0)

        val approved = attendees
            .filter { it.registrationStatus == RegistrationApprovalStatus.Approved }
        if (approved.isEmpty()) {
            return TeamBalanceResult(
                assignmentsByTeamId = cleanedTeamIds.associateWith { emptyList() },
                balanceScore = 0.0,
            )
        }

        val assignments = cleanedTeamIds.associateWith { mutableListOf<CampingAttendee>() }.toMutableMap()
        var forward = true
        for (bucket in makeBuckets(approved)) {
            val ordered = if (forward) cleanedTeamIds else cleanedTeamIds.asReversed()
            bucket.forEachIndexed { index, attendee ->
                assignments.getValue(ordered[index % ordered.size]).add(attendee)
            }
            forward = !forward
        }

        refine(assignments, cleanedTeamIds)
        return TeamBalanceResult(
            assignmentsByTeamId = assignments.mapValues { it.value.toList() },
            balanceScore = balanceScore(assignments),
        )
    }

    private fun makeBuckets(attendees: List<CampingAttendee>): List<List<CampingAttendee>> =
        attendees.groupBy { attendee ->
            listOf(
                attendee.church.lowercase(),
                attendee.preferredLanguage.lowercase(),
                attendee.ageGroup.wireValue,
                attendee.gender?.wireValue ?: "unknown",
            ).joinToString("|")
        }.values
            .sortedWith(
                compareByDescending<List<CampingAttendee>> { it.size }
                    .thenBy { it.firstOrNull()?.id.orEmpty() },
            )
            .map { bucket -> bucket.sortedBy { it.displayName.lowercase() } }

    private fun refine(
        assignments: MutableMap<String, MutableList<CampingAttendee>>,
        teamIds: List<String>,
    ) {
        val baseScore = balanceScore(assignments)
        if (baseScore <= 0.0) return

        for (sourceId in teamIds) {
            for (targetId in teamIds) {
                if (sourceId == targetId) continue
                val sourceMembers = assignments[sourceId].orEmpty()
                val targetMembers = assignments[targetId].orEmpty()
                if (sourceMembers.isEmpty() || targetMembers.isEmpty()) continue

                var bestImprovement = 0.0
                var bestSwap: Pair<Int, Int>? = null
                for (sourceIndex in sourceMembers.indices) {
                    for (targetIndex in targetMembers.indices) {
                        val candidate = assignments.mapValues { it.value.toMutableList() }.toMutableMap()
                        val sourceAttendee = candidate.getValue(sourceId)[sourceIndex]
                        val targetAttendee = candidate.getValue(targetId)[targetIndex]
                        candidate.getValue(sourceId)[sourceIndex] = targetAttendee
                        candidate.getValue(targetId)[targetIndex] = sourceAttendee
                        val improvement = baseScore - balanceScore(candidate)
                        if (improvement > bestImprovement) {
                            bestImprovement = improvement
                            bestSwap = sourceIndex to targetIndex
                        }
                    }
                }

                val swap = bestSwap ?: continue
                val sourceAttendee = assignments.getValue(sourceId)[swap.first]
                val targetAttendee = assignments.getValue(targetId)[swap.second]
                assignments.getValue(sourceId)[swap.first] = targetAttendee
                assignments.getValue(targetId)[swap.second] = sourceAttendee
                return
            }
        }
    }

    private fun balanceScore(assignments: Map<String, List<CampingAttendee>>): Double {
        val teams = assignments.values.toList()
        if (teams.isEmpty() || teams.sumOf { it.size } == 0) return 0.0
        return deviationSum(teams) { it.church.lowercase() } +
            deviationSum(teams) { it.preferredLanguage.lowercase() } +
            deviationSum(teams) { it.ageGroup.wireValue } +
            deviationSum(teams) { it.gender?.wireValue ?: "unknown" }
    }

    private fun deviationSum(
        teams: List<List<CampingAttendee>>,
        key: (CampingAttendee) -> String,
    ): Double {
        val totals = mutableMapOf<String, Int>()
        teams.forEach { team -> team.forEach { totals[key(it)] = (totals[key(it)] ?: 0) + 1 } }
        return totals.entries.sumOf { (value, totalCount) ->
            val mean = totalCount.toDouble() / teams.size.toDouble()
            teams.sumOf { team ->
                abs(team.count { key(it) == value }.toDouble() - mean)
            }
        }
    }
}
