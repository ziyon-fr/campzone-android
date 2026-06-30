package fr.ziyon.campzone.data.teams

import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

data class TeamBalanceResult(
    val assignmentsByTeamId: Map<String, List<CampingAttendee>>,
    val balanceScore: Double,
)

class TeamBalancer {

    fun balance(
        attendees: List<CampingAttendee>,
        teamIds: List<String>,
        shouldCancel: () -> Boolean = { false },
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
        for (attendee in makeBuckets(approved).flatten()) {
            if (shouldCancel()) {
                return TeamBalanceResult(
                    assignmentsByTeamId = assignments.mapValues { it.value.toList() },
                    balanceScore = balanceScore(assignments),
                )
            }
            val teamId = bestTeamId(attendee, assignments, cleanedTeamIds) ?: continue
            assignments.getValue(teamId).add(attendee)
        }

        refine(assignments, cleanedTeamIds, shouldCancel)
        return TeamBalanceResult(
            assignmentsByTeamId = assignments.mapValues { it.value.toList() },
            balanceScore = balanceScore(assignments),
        )
    }

    private fun makeBuckets(attendees: List<CampingAttendee>): List<List<CampingAttendee>> =
        attendees.groupBy { attendee ->
            listOf(
                churchKey(attendee),
                preferredLanguageKey(attendee),
                ageGroupKey(attendee),
                genderKey(attendee),
            ).joinToString("|")
        }.values
            .sortedWith(
                compareByDescending<List<CampingAttendee>> { it.size }
                    .thenBy { it.firstOrNull()?.id.orEmpty() },
            )
            .map { bucket -> bucket.sortedBy { normalizedKey(it.displayName) } }

    private fun bestTeamId(
        attendee: CampingAttendee,
        assignments: Map<String, List<CampingAttendee>>,
        teamIds: List<String>,
    ): String? {
        val smallestTeamSize = teamIds.minOfOrNull { assignments[it].orEmpty().size } ?: return null
        return teamIds
            .filter { assignments[it].orEmpty().size == smallestTeamSize }
            .minWithOrNull(
                compareBy<String> { balanceScoreAfterAdding(attendee, it, assignments) }
                    .thenBy { teamIds.indexOf(it) },
            )
    }

    private fun balanceScoreAfterAdding(
        attendee: CampingAttendee,
        teamId: String,
        assignments: Map<String, List<CampingAttendee>>,
    ): Double {
        val candidate = assignments.mapValues { it.value.toMutableList() }.toMutableMap()
        candidate.getOrPut(teamId) { mutableListOf() }.add(attendee)
        return balanceScore(candidate)
    }

    private fun refine(
        assignments: MutableMap<String, MutableList<CampingAttendee>>,
        teamIds: List<String>,
        shouldCancel: () -> Boolean,
    ) {
        if (teamIds.size <= 1) return

        val maxPasses = maxOf(1, minOf(12, teamIds.size * 2))
        repeat(maxPasses) {
            if (shouldCancel()) return
            val baseScore = balanceScore(assignments)
            if (baseScore <= 0.0) return

            var bestScore = baseScore
            var bestSwap: BestSwap? = null

            for (sourceId in teamIds) {
                if (shouldCancel()) return
                for (targetId in teamIds) {
                    if (sourceId == targetId) continue
                    val sourceMembers = assignments[sourceId].orEmpty()
                    val targetMembers = assignments[targetId].orEmpty()
                    if (sourceMembers.isEmpty() || targetMembers.isEmpty()) continue

                    for (sourceIndex in sourceMembers.indices) {
                        if (shouldCancel()) return
                        val sourceAttendee = sourceMembers[sourceIndex]
                        for (targetIndex in targetMembers.indices) {
                            val targetAttendee = targetMembers[targetIndex]
                            assignments.getValue(sourceId)[sourceIndex] = targetAttendee
                            assignments.getValue(targetId)[targetIndex] = sourceAttendee
                            val next = balanceScore(assignments)
                            assignments.getValue(sourceId)[sourceIndex] = sourceAttendee
                            assignments.getValue(targetId)[targetIndex] = targetAttendee

                            if (next < bestScore) {
                                bestScore = next
                                bestSwap = BestSwap(sourceId, targetId, sourceIndex, targetIndex)
                            }
                        }
                    }
                }
            }

            val swap = bestSwap ?: return
            val sourceAttendee = assignments.getValue(swap.sourceId)[swap.sourceIndex]
            val targetAttendee = assignments.getValue(swap.targetId)[swap.targetIndex]
            assignments.getValue(swap.sourceId)[swap.sourceIndex] = targetAttendee
            assignments.getValue(swap.targetId)[swap.targetIndex] = sourceAttendee
        }
    }

    private fun balanceScore(assignments: Map<String, List<CampingAttendee>>): Double {
        val teams = assignments.values.toList()
        if (teams.isEmpty() || teams.sumOf { it.size } == 0) return 0.0
        return (TEAM_SIZE_WEIGHT * teamSizeDeviationSum(teams)) +
            deviationSum(teams) { listOf(churchKey(it)) } +
            deviationSum(teams) { listOf(preferredLanguageKey(it)) } +
            (SPOKEN_LANGUAGE_WEIGHT * deviationSum(teams, ::spokenLanguageKeys)) +
            deviationSum(teams) { listOf(ageGroupKey(it)) } +
            deviationSum(teams) { listOf(genderKey(it)) }
    }

    private fun teamSizeDeviationSum(teams: List<List<CampingAttendee>>): Double {
        val mean = teams.sumOf { it.size }.toDouble() / teams.size.toDouble()
        return teams.sumOf { abs(it.size.toDouble() - mean) }
    }

    private fun deviationSum(
        teams: List<List<CampingAttendee>>,
        keysForAttendee: (CampingAttendee) -> List<String>,
    ): Double {
        val totals = mutableMapOf<String, Int>()
        val teamCounts = List(teams.size) { mutableMapOf<String, Int>() }
        teams.forEachIndexed { index, team ->
            team.forEach { attendee ->
                keysForAttendee(attendee).forEach { key ->
                    totals[key] = (totals[key] ?: 0) + 1
                    teamCounts[index][key] = (teamCounts[index][key] ?: 0) + 1
                }
            }
        }
        return totals.entries.sumOf { (key, totalCount) ->
            val mean = totalCount.toDouble() / teams.size.toDouble()
            teams.indices.sumOf { index ->
                abs((teamCounts[index][key] ?: 0).toDouble() - mean)
            }
        }
    }

    private fun churchKey(attendee: CampingAttendee): String = normalizedKey(attendee.church)

    private fun preferredLanguageKey(attendee: CampingAttendee): String {
        val preferred = normalizedKey(attendee.preferredLanguage)
        return if (preferred != UNKNOWN_KEY) preferred else normalizedKey(attendee.languages.firstOrNull().orEmpty())
    }

    private fun spokenLanguageKeys(attendee: CampingAttendee): List<String> {
        val keys = attendee.languages
            .map(::normalizedKey)
            .filter { it != UNKNOWN_KEY }
            .distinct()
            .sorted()
        return keys.ifEmpty { listOf(preferredLanguageKey(attendee)) }
    }

    private fun ageGroupKey(attendee: CampingAttendee): String = attendee.ageGroup.wireValue

    private fun genderKey(attendee: CampingAttendee): String = attendee.gender?.wireValue ?: UNKNOWN_KEY

    private fun normalizedKey(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return UNKNOWN_KEY
        val folded = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")
        return folded.lowercase(Locale.ROOT)
    }

    private data class BestSwap(
        val sourceId: String,
        val targetId: String,
        val sourceIndex: Int,
        val targetIndex: Int,
    )

    private companion object {
        const val TEAM_SIZE_WEIGHT = 4.0
        const val SPOKEN_LANGUAGE_WEIGHT = 0.5
        const val UNKNOWN_KEY = "unknown"
        val DIACRITICS_REGEX = "\\p{Mn}+".toRegex()
    }
}
