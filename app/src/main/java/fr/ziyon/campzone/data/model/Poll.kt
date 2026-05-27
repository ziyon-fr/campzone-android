package fr.ziyon.campzone.data.model

import java.util.Date

/**
 * `campings/{id}/polls/{pollId}` (`02-firestore-schema.md` §6.3). `campingID` is
 * NOT stored (from path). `createdAt`/`closesAt` are a **client `Date()`** (not
 * serverTimestamp); `closesAt` is written as explicit Firestore **null** when
 * absent. `showsResultsBeforeClose` defaults **true**.
 */
data class Poll(
    val id: String,
    val question: String = "",
    val description: String = "",
    val options: List<PollOption> = emptyList(),
    val allowsMultiple: Boolean = false,
    val showsResultsBeforeClose: Boolean = true,
    val isOpen: Boolean = false,
    val createdById: String = "",
    val createdByName: String = "",
    val createdAt: Date? = null,
    val closesAt: Date? = null,
    val updatedAt: Date? = null,
)

data class PollOption(
    val id: String,
    val label: String,
    val voteCount: Int = 0,
)

/** `.../votes/{voterId}` - doc ID == voter uid (one per voter; re-vote overwrites). */
data class PollVote(
    val voterId: String,
    val selectedOptionIds: List<String> = emptyList(),
    val votedAt: Date? = null,
)

internal fun Map<String, Any?>.toPoll(documentId: String): Poll =
    Poll(
        id = documentId,
        question = rawStringValue("question").orEmpty(),
        description = rawStringValue("description").orEmpty(),
        options = mapListValue("options").mapNotNull { it.toPollOptionOrNull() },
        allowsMultiple = boolValue("allowsMultiple") ?: false,
        showsResultsBeforeClose = boolValue("showsResultsBeforeClose") ?: true,
        isOpen = boolValue("isOpen") ?: false,
        createdById = stringValue("createdByID").orEmpty(),
        createdByName = rawStringValue("createdByName").orEmpty(),
        createdAt = dateValue("createdAt"),
        closesAt = dateValue("closesAt"),
        updatedAt = dateValue("updatedAt"),
    )

internal fun Map<String, Any?>.toPollOptionOrNull(): PollOption? {
    val id = stringValue("id") ?: return null
    return PollOption(
        id = id,
        label = rawStringValue("label").orEmpty(),
        voteCount = intValue("voteCount") ?: 0,
    )
}

internal fun Map<String, Any?>.toPollVote(documentId: String): PollVote =
    PollVote(
        voterId = stringValue("voterID") ?: documentId,
        selectedOptionIds = stringListValue("selectedOptionIDs"),
        votedAt = dateValue("votedAt"),
    )

internal object PollPayload {

    /** `createdAt`/`closesAt` are client `Date()`; `closesAt` is explicit null when absent. */
    fun pollPayload(poll: Poll, now: Date, includeCreatedAt: Boolean): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "question" to poll.question.trim(),
            "description" to poll.description,
            "options" to poll.options.map(::optionMap),
            "allowsMultiple" to poll.allowsMultiple,
            "showsResultsBeforeClose" to poll.showsResultsBeforeClose,
            "isOpen" to poll.isOpen,
            "createdByID" to poll.createdById,
            "createdByName" to poll.createdByName,
            "closesAt" to poll.closesAt, // explicit null when none
        )
        if (includeCreatedAt) payload["createdAt"] = poll.createdAt ?: now
        return payload
    }

    fun optionMap(option: PollOption): Map<String, Any?> =
        linkedMapOf(
            "id" to option.id,
            "label" to option.label,
            "voteCount" to option.voteCount.coerceAtLeast(0),
        )

    fun votePayload(
        voterId: String,
        selectedOptionIds: List<String>,
        serverTimestamp: Any,
    ): Map<String, Any?> =
        linkedMapOf(
            "voterID" to voterId,
            "selectedOptionIDs" to selectedOptionIds,
            "votedAt" to serverTimestamp,
        )
}
