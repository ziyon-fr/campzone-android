package fr.ziyon.campzone.data.chat

import fr.ziyon.campzone.data.model.ChatMention
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatMentionScannerTest {

    @Test
    fun activeQueryTriggersAfterAtFollowingWhitespace() {
        val text = "Hi @le"
        val q = ChatMentionScanner.activeQuery(text, caret = text.length)
        assertEquals("le", q?.query)
        assertEquals(3, q?.atIndex)
    }

    @Test
    fun activeQueryTriggersAtStartOfString() {
        val q = ChatMentionScanner.activeQuery("@ev", caret = 3)
        assertEquals("ev", q?.query)
        assertEquals(0, q?.atIndex)
    }

    @Test
    fun activeQueryEmptyRightAfterAt() {
        val q = ChatMentionScanner.activeQuery("Hi @", caret = 4)
        assertEquals("", q?.query)
        assertEquals(3, q?.atIndex)
    }

    @Test
    fun activeQueryDoesNotTriggerInsideWord() {
        // '@' preceded by a non-space (email-style) must not trigger.
        assertNull(ChatMentionScanner.activeQuery("mail@host", caret = 9))
    }

    @Test
    fun activeQueryNullWhenWhitespaceBetweenAtAndCaret() {
        assertNull(ChatMentionScanner.activeQuery("Hi @lea now", caret = 11))
    }

    @Test
    fun resolveKeepsExactTokensAndDropsStaleOnes() {
        val text = "Hi @Lea and @everyone"
        val mentions = listOf(
            ChatMention("u-lea", "Lea", offset = 3, length = 4),
            ChatMention(ChatMention.EVERYONE_TOKEN, "Everyone", offset = 12, length = 9),
        )
        assertEquals(2, ChatMentionScanner.resolve(mentions, text).size)

        // stale offset (text shortened) is dropped
        assertEquals(0, ChatMentionScanner.resolve(mentions, "Hi").size)
        // token no longer matches the stored display name is dropped
        assertEquals(0, ChatMentionScanner.resolve(listOf(ChatMention("u", "Lea", 0, 4)), "Lea!").size)
    }

    @Test
    fun insertionReplacesQueryWithCanonicalToken() {
        val result = ChatMentionScanner.insertion(
            candidate = MentionCandidate(id = "u-lea", displayName = "Lea", subtitle = ""),
            text = "Hi @le",
            atIndex = 3,
            queryLength = 2,
        )!!
        assertEquals("Hi @Lea ", result.text)
        assertEquals(3, result.mention.offset)
        assertEquals(4, result.mention.length) // "@Lea"
        assertEquals(result.text.length, result.caret)
    }

    @Test
    fun insertionEveryoneUsesConstantToken() {
        val result = ChatMentionScanner.insertion(
            candidate = MentionCandidate(
                id = ChatMention.EVERYONE_TOKEN,
                displayName = "Everyone",
                subtitle = "",
                isEveryone = true,
            ),
            text = "Hi @ev",
            atIndex = 3,
            queryLength = 2,
        )!!
        assertEquals("Hi @everyone ", result.text)
        assertEquals(ChatMention.EVERYONE_TOKEN, result.mention.userId)
        assertEquals("@everyone".length, result.mention.length)
    }
}
