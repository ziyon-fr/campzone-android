package fr.ziyon.campzone.ui.games

import fr.ziyon.campzone.data.model.Activity
import fr.ziyon.campzone.data.model.Team

internal fun Activity.matchesTeamOrMember(teamId: String, memberUserIds: Set<String>): Boolean {
    if (targetTeamId == teamId) return true
    return targetUserId?.let { it in memberUserIds } == true
}

internal fun List<Activity>.teamEarnedActivities(team: Team): List<Activity> {
    val memberUserIds = team.memberUserIds.toSet()
    return filter { activity ->
        activity.points > 0 && activity.matchesTeamOrMember(team.id, memberUserIds)
    }.sortedByDescending { it.createdAt }
}

internal fun List<Activity>.teamMemberDeductionActivities(team: Team): List<Activity> {
    val memberUserIds = team.memberUserIds.toSet()
    return filter { activity ->
        activity.points < 0 && activity.targetUserId?.let { it in memberUserIds } == true
    }.sortedByDescending { it.createdAt }
}
