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

    private companion object {
        const val TS = "serverTimestamp"
    }
}
