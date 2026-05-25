package fr.ziyon.campzone.data.model

import java.util.Date

/**
 * `campings/{id}/activities/{activityId}` (`02-firestore-schema.md` §5.5) — an
 * immutable audit record. Written full-set (`merge:false`); `update` is
 * forbidden by RBAC. Create requires `campingID == path` and
 * `createdBy == auth.uid`. `points` is signed (negative = penalty/correction).
 */
data class Activity(
    val id: String,
    val campingId: String,
    val gameId: String,
    val name: String,
    val points: Int,
    val previousScore: Int,
    val newScore: Int,
    val createdBy: String,
    val createdByName: String,
    val createdAt: Date,
    val reason: String = "",
    val pointRuleId: String? = null,
    val targetTeamId: String? = null,
    val targetTeamName: String? = null,
    val targetUserId: String? = null,
    val targetUserName: String? = null,
    val visibility: PointRuleVisibility = PointRuleVisibility.Immediate,
)

internal fun Map<String, Any?>.toActivityOrNull(documentId: String): Activity? {
    val campingId = stringValue("campingID") ?: return null
    val gameId = stringValue("gameID") ?: return null
    val createdAt = dateValue("createdAt") ?: return null
    val points = intValue("points") ?: 0
    val previousScore = intValue("previousScore") ?: 0
    return Activity(
        id = stringValue("id") ?: documentId,
        campingId = campingId,
        gameId = gameId,
        name = rawStringValue("name").orEmpty(),
        points = points,
        previousScore = previousScore,
        newScore = intValue("newScore") ?: (previousScore + points),
        createdBy = stringValue("createdBy").orEmpty(),
        createdByName = rawStringValue("createdByName").orEmpty(),
        createdAt = createdAt,
        reason = rawStringValue("reason").orEmpty(),
        pointRuleId = stringValue("pointRuleID"),
        targetTeamId = stringValue("targetTeamID"),
        targetTeamName = stringValue("targetTeamName"),
        targetUserId = stringValue("targetUserID"),
        targetUserName = stringValue("targetUserName"),
        visibility = PointRuleVisibility.fromWire(stringValue("visibility")),
    )
}

internal object ActivityPayload {
    fun activityPayload(activity: Activity): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "id" to activity.id,
            "campingID" to activity.campingId,
            "gameID" to activity.gameId,
            "name" to activity.name.trim(),
            "points" to activity.points,
            "reason" to activity.reason,
            "visibility" to activity.visibility.wireValue,
            "previousScore" to activity.previousScore,
            "newScore" to activity.newScore,
            "createdBy" to activity.createdBy,
            "createdByName" to activity.createdByName,
            "createdAt" to activity.createdAt,
        )
        activity.pointRuleId?.trim()?.takeUnless { it.isBlank() }?.let { payload["pointRuleID"] = it }
        activity.targetTeamId?.trim()?.takeUnless { it.isBlank() }?.let { payload["targetTeamID"] = it }
        activity.targetTeamName?.trim()?.takeUnless { it.isBlank() }?.let { payload["targetTeamName"] = it }
        activity.targetUserId?.trim()?.takeUnless { it.isBlank() }?.let { payload["targetUserID"] = it }
        activity.targetUserName?.trim()?.takeUnless { it.isBlank() }?.let { payload["targetUserName"] = it }
        return payload
    }
}
