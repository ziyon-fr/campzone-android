package fr.ziyon.campzone.data.model

import java.util.Date

/**
 * `campings/{id}/polls/{pollId}` (`02-firestore-schema.md` §6.3). `campingID` is
 * NOT stored (from path) — [campingId] is injected by the service after decode.
 * `createdAt`/`closesAt` are a **client `Date()`** (not serverTimestamp);
 * `closesAt` is written as explicit Firestore **null** when absent.
 * `showsResultsBeforeClose` defaults **true**.
 */
data class Poll(
    val id: String,
    val campingId: String = "",
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
) {
    val totalVotes: Int get() = options.sumOf { it.voteCount }

    /** True once a close date has been reached. */
    val isExpired: Boolean get() = closesAt?.let { !Date().before(it) } ?: false

    /** A poll accepts votes only while open AND not past its close date. */
    val resolvedIsOpen: Boolean get() = isOpen && !isExpired

    fun percentage(optionId: String): Double {
        if (totalVotes == 0) return 0.0
        val count = options.firstOrNull { it.id == optionId }?.voteCount ?: return 0.0
        return count.toDouble() / totalVotes
    }
}

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

/** Validation outcomes for the admin poll composer. Mapped to localized copy in the UI. */
enum class PollFormError {
    QuestionRequired,
    NotEnoughOptions,
    DuplicateOptions,
    InvalidCloseDate,
}

/**
 * Editable poll draft for the admin composer (mirrors the iOS `PollForm`). Pure
 * value type so validation can be unit-tested without a service.
 */
data class PollForm(
    val question: String = "",
    val description: String = "",
    val optionLabels: List<String> = listOf("", ""),
    val allowsMultiple: Boolean = false,
    val showsResultsBeforeClose: Boolean = true,
    val isOpen: Boolean = true,
    val hasCloseDate: Boolean = false,
    val closesAt: Date = defaultCloseDate(),
) {
    val validOptionLabels: List<String>
        get() = optionLabels.map { it.trim() }.filter { it.isNotEmpty() }

    val validationError: PollFormError?
        get() {
            if (question.trim().isEmpty()) return PollFormError.QuestionRequired
            val labels = validOptionLabels
            if (labels.size < 2) return PollFormError.NotEnoughOptions
            if (labels.map { it.lowercase() }.toSet().size != labels.size) {
                return PollFormError.DuplicateOptions
            }
            if (hasCloseDate && !closesAt.after(Date())) return PollFormError.InvalidCloseDate
            return null
        }

    val isValid: Boolean get() = validationError == null

    companion object {
        const val MIN_OPTIONS = 2
        const val MAX_OPTIONS = 8

        fun from(poll: Poll): PollForm {
            val labels = poll.options.map { it.label }
                .let { if (it.size < MIN_OPTIONS) it + List(MIN_OPTIONS - it.size) { "" } else it }
            return PollForm(
                question = poll.question,
                description = poll.description,
                optionLabels = labels,
                allowsMultiple = poll.allowsMultiple,
                showsResultsBeforeClose = poll.showsResultsBeforeClose,
                isOpen = poll.isOpen,
                hasCloseDate = poll.closesAt != null,
                closesAt = poll.closesAt ?: defaultCloseDate(),
            )
        }

        private fun defaultCloseDate(): Date = Date(System.currentTimeMillis() + 6L * 60 * 60 * 1000)
    }
}

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
