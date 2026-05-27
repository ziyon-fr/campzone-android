package fr.ziyon.campzone.data.chat

import fr.ziyon.campzone.data.model.ChatMention

/**
 * A user (or the `@everyone` sentinel) suggested while typing an @mention.
 * Built by the chat UI from the scope's participants; the synthetic everyone
 * row carries [isEveryone] and a localized [displayName] label while the token
 * written into the message is always the constant `"everyone"` ([tokenName]).
 */
data class MentionCandidate(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val photoUrl: String? = null,
    val isEveryone: Boolean = false,
) {
    /** The token written after the `@`. Constant for everyone so cross-locale
     *  mentions resolve identically; the picker still shows [displayName]. */
    val tokenName: String get() = if (isEveryone) "everyone" else displayName
}

/**
 * Draft the composer builds for a new (or edited) chat message: text plus the
 * @mentions the user committed by picking from the suggestion list. Mentions
 * are re-validated against the current [text] before sending so a half-deleted
 * token never ships a stale mention.
 */
data class ChatMessageDraft(
    val text: String = "",
    val mentions: List<ChatMention> = emptyList(),
) {
    val isValid: Boolean
        get() {
            val trimmed = text.trim()
            return trimmed.isNotEmpty() && trimmed.length <= MAX_LENGTH
        }

    /** Mentions whose offset/length still resolve to their original token. */
    val resolvedMentions: List<ChatMention>
        get() = ChatMentionScanner.resolve(mentions, text)

    companion object {
        const val MAX_LENGTH = 500
    }
}

/**
 * Pure helpers for working with @mention tokens inside the composer text.
 * Kotlin `Char`/`String` indices are UTF-16 code units, matching the offsets
 * iOS persists, so a [ChatMention.offset]/[ChatMention.length] crosses
 * platforms unchanged. Ported from the iOS `ChatMentionScanner`.
 */
object ChatMentionScanner {

    data class ActiveQuery(val query: String, val atIndex: Int)

    /**
     * The `@`-triggered query that should drive the suggestion picker, or null
     * when [caret] is not inside an active mention token. The `@` must start the
     * string or follow whitespace (so `name@host` does not trigger); the trigger
     * ends at the first whitespace.
     */
    fun activeQuery(text: String, caret: Int): ActiveQuery? {
        if (caret < 0 || caret > text.length) return null
        var cursor = caret
        while (cursor > 0) {
            val previous = cursor - 1
            val character = text[previous]
            if (character == '@') {
                val triggers = previous == 0 || text[previous - 1].isWhitespace()
                if (triggers) {
                    return ActiveQuery(query = text.substring(previous + 1, caret), atIndex = previous)
                }
                return null
            }
            if (character.isWhitespace()) return null
            cursor = previous
        }
        return null
    }

    /**
     * Returns only the mentions whose offset/length still resolves to the exact
     * `@<token>` substring in [text]. Mentions the user edited become plain text
     * and are dropped before send / notification dispatch.
     */
    fun resolve(mentions: List<ChatMention>, text: String): List<ChatMention> {
        val count = text.length
        return mentions.filter { mention ->
            if (mention.offset < 0 || mention.length <= 0 || mention.endOffset > count) {
                return@filter false
            }
            val substring = text.substring(mention.offset, mention.endOffset)
            val expected = "@" + if (mention.isEveryone) "everyone" else mention.displayName
            substring == expected
        }
    }

    data class Insertion(val text: String, val mention: ChatMention, val caret: Int)

    /**
     * Replaces the in-progress `@query` at [atIndex] with the canonical
     * `@<tokenName>` token plus a trailing space, returning the new text, the
     * [ChatMention] metadata, and the new caret offset.
     */
    fun insertion(
        candidate: MentionCandidate,
        text: String,
        atIndex: Int,
        queryLength: Int,
    ): Insertion? {
        if (atIndex < 0 || atIndex > text.length) return null
        // +1 covers the leading `@`; clamp, then swallow one trailing whitespace.
        var queryEnd = (atIndex + queryLength + 1).coerceIn(0, text.length)
        if (queryEnd < text.length && text[queryEnd].isWhitespace()) {
            queryEnd += 1
        }
        val display = candidate.tokenName
        val replacement = "@$display "
        val updated = text.substring(0, atIndex) + replacement + text.substring(queryEnd)
        val mention = ChatMention(
            userId = candidate.id,
            displayName = candidate.displayName,
            offset = atIndex,
            length = "@$display".length,
        )
        val caret = (atIndex + replacement.length).coerceIn(0, updated.length)
        return Insertion(text = updated, mention = mention, caret = caret)
    }
}
