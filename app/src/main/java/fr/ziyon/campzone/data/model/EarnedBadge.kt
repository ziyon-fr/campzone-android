package fr.ziyon.campzone.data.model

import java.util.Date

/**
 * `users/{uid}/badges/{achievementId}` (`02-firestore-schema.md` §2.5). Doc ID
 * == [id] == catalog achievement id. Backend-written for objective badges;
 * clients are readers (RBAC blocks self-award). `campingID` and `note` are
 * written as **explicit Firestore null** when absent (not omitted, not deleted).
 */
data class EarnedBadge(
    val id: String,
    val userId: String,
    val earnedAt: Date? = null,
    val campingId: String? = null,
    val note: String? = null,
)

internal fun Map<String, Any?>.toEarnedBadgeOrNull(documentId: String): EarnedBadge =
    EarnedBadge(
        id = stringValue("id") ?: documentId,
        userId = stringValue("userID").orEmpty(),
        earnedAt = dateValue("earnedAt"),
        campingId = stringValue("campingID"),
        note = stringValue("note"),
    )

internal object EarnedBadgePayload {
    /** Manual award (gate `canAwardAchievements`). `campingID`/`note` are explicit null when absent. */
    fun awardPayload(
        badge: EarnedBadge,
        serverTimestamp: Any,
    ): Map<String, Any?> =
        linkedMapOf(
            "id" to badge.id,
            "userID" to badge.userId,
            "earnedAt" to serverTimestamp,
            "campingID" to badge.campingId?.trim()?.takeUnless { it.isBlank() }, // explicit null when absent
            "note" to badge.note?.trim()?.takeUnless { it.isBlank() }, // explicit null when empty
        )
}
