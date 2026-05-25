package fr.ziyon.campzone.data.model

import java.util.Date

/**
 * `campings/{id}/feedback/{uid}` (`02-firestore-schema.md` §7.5). Doc ID ==
 * submitting uid (RBAC enforces `overallRating` 1–5 and `feedbackId ==
 * auth.uid`). `submittedAt` is overridden to serverTimestamp on write;
 * `isAnonymous` hides `displayName` in the UI but it is still stored.
 */
data class CampFeedback(
    val id: String,
    val campingId: String,
    val userId: String,
    val displayName: String,
    val overallRating: Int,
    val wouldReturn: Boolean,
    val isAnonymous: Boolean,
    val programFeedback: List<ProgramFeedback> = emptyList(),
    val highlights: String = "",
    val improvements: String = "",
    val submittedAt: Date? = null,
    val updatedAt: Date? = null,
)

data class ProgramFeedback(
    val id: String,
    val programTitle: String,
    val rating: Int,
    val comment: String = "",
)

internal fun Map<String, Any?>.toCampFeedbackOrNull(documentId: String): CampFeedback? {
    val campingId = stringValue("campingID") ?: return null
    val rating = intValue("overallRating") ?: return null
    return CampFeedback(
        id = stringValue("id") ?: documentId,
        campingId = campingId,
        userId = stringValue("userID") ?: documentId,
        displayName = rawStringValue("displayName").orEmpty(),
        overallRating = rating.coerceIn(1, 5),
        wouldReturn = boolValue("wouldReturn") ?: false,
        isAnonymous = boolValue("isAnonymous") ?: false,
        programFeedback = mapListValue("programFeedback").mapNotNull { it.toProgramFeedbackOrNull() },
        highlights = rawStringValue("highlights").orEmpty(),
        improvements = rawStringValue("improvements").orEmpty(),
        submittedAt = dateValue("submittedAt"),
        updatedAt = dateValue("updatedAt"),
    )
}

internal fun Map<String, Any?>.toProgramFeedbackOrNull(): ProgramFeedback? {
    val id = stringValue("id") ?: return null
    return ProgramFeedback(
        id = id,
        programTitle = rawStringValue("programTitle").orEmpty(),
        rating = intValue("rating") ?: 0,
        comment = rawStringValue("comment").orEmpty(),
    )
}

internal object CampFeedbackPayload {
    fun feedbackPayload(
        feedback: CampFeedback,
        serverTimestamp: Any,
    ): Map<String, Any?> =
        linkedMapOf(
            "id" to feedback.id,
            "campingID" to feedback.campingId,
            "userID" to feedback.userId,
            "displayName" to feedback.displayName,
            "overallRating" to feedback.overallRating.coerceIn(1, 5),
            "programFeedback" to feedback.programFeedback.map(::programFeedbackMap),
            "highlights" to feedback.highlights,
            "improvements" to feedback.improvements,
            "wouldReturn" to feedback.wouldReturn,
            "isAnonymous" to feedback.isAnonymous,
            "submittedAt" to serverTimestamp,
            "updatedAt" to serverTimestamp,
        )

    fun programFeedbackMap(item: ProgramFeedback): Map<String, Any?> =
        linkedMapOf(
            "id" to item.id,
            "programTitle" to item.programTitle,
            "rating" to item.rating,
            "comment" to item.comment,
        )
}
