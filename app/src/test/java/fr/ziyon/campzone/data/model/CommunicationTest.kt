package fr.ziyon.campzone.data.model

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.UserGender
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunicationTest {

    // --- ChatMessage ---

    @Test
    fun chatSendIncludesTeamIdOnlyForTeamChat() {
        val message = ChatMessage(
            id = "m1",
            campingId = "camp-1",
            senderId = "u1",
            senderName = "Maria",
            text = "Hello",
            teamId = "team-1",
            senderGender = UserGender.Female,
        )
        val campingChat = ChatMessagePayload.sendPayload(message, TS, isTeamChat = false)
        assertFalse(campingChat.containsKey("teamID"))
        assertEquals("female", campingChat["senderGender"])

        val teamChat = ChatMessagePayload.sendPayload(message, TS, isTeamChat = true)
        assertEquals("team-1", teamChat["teamID"])
    }

    @Test
    fun chatReplyAndReactionsRoundTrip() {
        val reply = ChatReplyReference(
            messageId = "orig",
            senderId = "u2",
            senderName = "Lea",
            textPreview = "Original message",
            mediaType = ChatAttachmentKind.Image,
            mediaUrl = "https://img/orig.jpg",
        )
        val payload = ChatMessagePayload.sendPayload(
            ChatMessage(
                id = "m1",
                campingId = "camp-1",
                senderId = "u1",
                senderName = "Maria",
                text = "Replying",
                replyTo = reply,
            ),
            TS,
            isTeamChat = false,
        ).toMutableMap().apply {
            put("reactions", mapOf("u2" to "\u2764\uFE0F", "u3" to "\u2764\uFE0F", "u4" to ""))
        }

        @Suppress("UNCHECKED_CAST")
        val replyMap = payload["replyTo"] as Map<String, Any?>
        assertEquals("orig", replyMap["messageID"])
        assertEquals("u2", replyMap["senderID"])
        assertEquals("image", replyMap["mediaType"])

        val decoded = payload.toChatMessageOrNull("m1")
        assertEquals("orig", decoded?.replyTo?.messageId)
        assertEquals("https://img/orig.jpg", decoded?.replyTo?.mediaUrl)
        assertEquals(mapOf("u2" to "\u2764\uFE0F", "u3" to "\u2764\uFE0F"), decoded?.reactions)
        assertEquals(1, decoded?.reactionSummaries("u2")?.size)
        assertTrue(decoded?.reactionSummaries("u2")?.first()?.reactedByCurrentUser == true)
    }

    @Test
    fun chatPinAndSoftDelete() {
        assertEquals(mapOf("pinned" to true), ChatMessagePayload.pinPayload(true))

        val soft = ChatMessagePayload.softDeletePayload("mod-1", TS)
        assertEquals(true, soft["isDeleted"])
        assertEquals("mod-1", soft["deletedByID"])
        assertEquals(TS, soft["deletedAt"])
    }

    @Test
    fun chatDecodeDropsMissingRequiredAndRoundTrips() {
        val updatedAt = Date(11)
        val payload = ChatMessagePayload.sendPayload(
            ChatMessage(id = "m1", campingId = "camp-1", senderId = "u1", senderName = "Maria", text = "Hi"),
            TS, isTeamChat = false,
        ).toMutableMap().apply { put("updatedAt", updatedAt) }
        val decoded = payload.toChatMessageOrNull("m1")
        assertEquals("Hi", decoded?.text)
        assertEquals(updatedAt, decoded?.updatedAt)

        val broken = payload.toMutableMap().apply { remove("senderName") }
        assertNull(broken.toChatMessageOrNull("m1"))
    }

    // --- Announcement ---

    @Test
    fun announcementEmptyStringEncodings() {
        val payload = AnnouncementPayload.draftPayload(
            announcementDraft(title = "Welcome", body = "**Hi**", role = null),
            TS, includeCreatedAt = true, attachments = emptyList(),
        )
        // written as "" (not omitted) when no target role
        assertEquals("", payload["notificationTargetRoleRawValue"])
        assertFalse(payload.containsKey("authorPhotoURL")) // omit-when-nil
        assertEquals(TS, payload["createdAt"])

        val targeted = AnnouncementPayload.draftPayload(
            announcementDraft(title = "T", role = UserRole.Leader.rawValue),
            TS, includeCreatedAt = false, attachments = emptyList(),
        )
        assertEquals("leader", targeted["notificationTargetRoleRawValue"])
    }

    @Test
    fun announcementAttachmentWritesEmptyStringsNotOmitted() {
        val payload = AnnouncementPayload.draftPayload(
            announcementDraft(title = "T", role = null),
            TS, includeCreatedAt = false,
            attachments = listOf(
                AnnouncementAttachment(
                    id = "att1",
                    kind = AnnouncementAttachmentKind.Pdf,
                    fileName = "rules.pdf",
                    contentType = "application/pdf",
                    storagePath = "",
                    downloadUrl = "",
                ),
            ),
        )
        @Suppress("UNCHECKED_CAST")
        val attachment = (payload["attachments"] as List<Map<String, Any?>>).first()
        assertEquals("pdf", attachment["kind"])
        assertEquals("", attachment["storagePath"])
        assertEquals("", attachment["downloadURL"])
    }

    private fun announcementDraft(
        title: String,
        body: String = "",
        role: String? = null,
        authorPhotoUrl: String? = null,
    ) = AnnouncementDraft(
        id = "a1",
        title = title,
        body = body,
        audienceScopeRawValue = AnnouncementAudienceScope.App.rawValue,
        campingId = null,
        campingTitle = null,
        notificationTargetRoleRawValue = role,
        authorId = "",
        authorName = "",
        authorPhotoUrl = authorPhotoUrl,
        existingAttachments = emptyList(),
        pendingAttachments = emptyList(),
    )

    @Test
    fun announcementReadsLegacyDescriptionBody() {
        val decoded = mapOf("title" to "T", "description" to "legacy body").toAnnouncement("a1")
        assertEquals("legacy body", decoded.body)
    }

    // --- Poll ---

    @Test
    fun pollClosesAtExplicitNullAndClientDate() {
        val now = Date(1_700_000_000_000L)
        val updatedAt = Date(1_700_000_100_000L)
        val open = PollPayload.pollPayload(
            Poll(id = "p1", question = "Fav?", options = listOf(PollOption("o1", "A")), closesAt = null),
            now, includeCreatedAt = true,
        ).toMutableMap().apply { put("updatedAt", updatedAt) }
        assertTrue(open.containsKey("closesAt")) // present…
        assertNull(open["closesAt"]) // …as explicit null
        assertEquals(now, open["createdAt"]) // client Date()
        assertEquals(true, open["showsResultsBeforeClose"]) // defaults true
        assertEquals(updatedAt, open.toPoll("p1").updatedAt)

        val closing = PollPayload.pollPayload(
            Poll(id = "p1", question = "Q", closesAt = Date(1_700_000_500_000L)),
            now, includeCreatedAt = false,
        )
        assertEquals(Date(1_700_000_500_000L), closing["closesAt"])
    }

    @Test
    fun pollVoteDocKeyedByVoter() {
        val payload = PollPayload.votePayload("voter-1", listOf("o1", "o2"), TS)
        assertEquals("voter-1", payload["voterID"])
        assertEquals(listOf("o1", "o2"), payload["selectedOptionIDs"])
        assertEquals(TS, payload["votedAt"])

        val decoded = payload.toPollVote("voter-1")
        assertEquals("voter-1", decoded.voterId)
        assertEquals(listOf("o1", "o2"), decoded.selectedOptionIds)
    }

    @Test
    fun chatMentionsAndImageAttachmentRoundTrip() {
        val message = ChatMessage(
            id = "m1",
            campingId = "camp-1",
            senderId = "u1",
            senderName = "Maria",
            text = "Hi @Lea and @everyone",
            mentions = listOf(
                ChatMention("u-lea", "Lea", offset = 3, length = 4),
                ChatMention(ChatMention.EVERYONE_TOKEN, "Everyone", offset = 12, length = 9),
            ),
            attachment = ChatAttachment(
                kind = ChatAttachmentKind.Image,
                url = "https://img/secure.jpg",
                publicId = "campzone/chat/camp-1/m1",
                width = 600,
                height = 800,
            ),
        )
        val payload = ChatMessagePayload.sendPayload(message, TS, isTeamChat = false)
        // flat mentionedUserIDs written alongside the full mentions list (RBAC needs both)
        assertEquals(listOf("u-lea", ChatMention.EVERYONE_TOKEN), payload["mentionedUserIDs"])
        assertEquals("image", payload["attachmentKind"])
        assertEquals("https://img/secure.jpg", payload["attachmentURL"])
        assertEquals(600, payload["attachmentWidth"])
        assertFalse(payload.containsKey("attachmentDuration")) // omit-when-nil

        val decoded = payload.toChatMessageOrNull("m1")!!
        assertEquals(2, decoded.mentions.size)
        assertEquals("Lea", decoded.mentions[0].displayName)
        assertEquals(4, decoded.mentions[0].length)
        assertTrue(decoded.mentions[1].isEveryone)
        assertEquals(ChatAttachmentKind.Image, decoded.attachment?.kind)
        assertEquals(800, decoded.attachment?.height)
        assertTrue(decoded.notifies("u-lea"))
    }

    @Test
    fun chatVoiceNoteKeepsEmptyTextAndDuration() {
        val message = ChatMessage(
            id = "v1", campingId = "camp-1", senderId = "u1", senderName = "David",
            text = "",
            attachment = ChatAttachment(
                kind = ChatAttachmentKind.Audio,
                url = "https://aud/voice.m4a",
                publicId = "pid",
                durationSeconds = 14.0,
            ),
        )
        val payload = ChatMessagePayload.sendPayload(message, TS, isTeamChat = false)
        assertEquals("", payload["text"])
        assertEquals("audio", payload["attachmentKind"])

        // a blank-text voice note must NOT be dropped on decode
        val decoded = payload.toChatMessageOrNull("v1")!!
        assertEquals("", decoded.text)
        assertFalse(decoded.hasText)
        assertTrue(decoded.hasAttachment)
        assertEquals(ChatAttachmentKind.Audio, decoded.attachment?.kind)
        assertEquals(14.0, decoded.attachment?.durationSeconds ?: 0.0, 0.0001)
    }

    @Test
    fun chatEditPayloadDeletesMentionsWhenEmpty() {
        val withMentions = ChatMessagePayload.editPayload(
            "Updated @Lea", listOf(ChatMention("u-lea", "Lea", 8, 4)), TS, deleteValue = DELETE,
        )
        assertEquals("Updated @Lea", withMentions["text"])
        assertEquals(TS, withMentions["editedAt"])
        assertEquals(listOf("u-lea"), withMentions["mentionedUserIDs"])

        val cleared = ChatMessagePayload.editPayload("Updated", emptyList(), TS, deleteValue = DELETE)
        assertEquals(DELETE, cleared["mentions"])
        assertEquals(DELETE, cleared["mentionedUserIDs"])
    }

    private companion object {
        const val TS = "serverTimestamp"
        const val DELETE = "deleteField"
    }
}
