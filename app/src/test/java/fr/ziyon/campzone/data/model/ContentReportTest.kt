package fr.ziyon.campzone.data.model

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ContentReportTest {

    @Test
    fun submitWritesFullSetWithCamelCaseTargetAndNote() {
        val payload = ContentReportPayload.reportPayload(sampleReport(), TS)
        assertEquals("r1", payload["id"])
        assertEquals("chatMessage", payload["target"]) // camelCase, not chat_message
        assertEquals("spam", payload["reason"])
        assertEquals("", payload["note"]) // written even when empty
        assertEquals("pending", payload["status"])
        assertEquals(TS, payload["createdAt"])
    }

    @Test
    fun statusUpdate() {
        val payload = ContentReportPayload.statusUpdatePayload(ContentReportStatus.Resolved, "admin-1", TS)
        assertEquals("resolved", payload["status"])
        assertEquals("admin-1", payload["reviewedByID"])
        assertEquals(TS, payload["reviewedAt"])
    }

    @Test
    fun statusUpdateRejectsPendingStatus() {
        assertThrows(IllegalArgumentException::class.java) {
            ContentReportPayload.statusUpdatePayload(ContentReportStatus.Pending, "admin-1", TS)
        }
    }

    @Test
    fun submitRejectsMissingRequiredIds() {
        assertThrows(IllegalArgumentException::class.java) {
            ContentReportPayload.reportPayload(sampleReport().copy(reporterId = ""), TS)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ContentReportPayload.reportPayload(sampleReport().copy(contentId = ""), TS)
        }
    }

    @Test
    fun roundTripsThroughBrittleDecoder() {
        val created = Date(1)
        val payload = ContentReportPayload.reportPayload(sampleReport(), created)
        val decoded = payload.toContentReport("r1")
        assertEquals(ContentReportTarget.ChatMessage, decoded.target)
        assertEquals(ContentReportReason.Spam, decoded.reason)
        assertEquals(ContentReportStatus.Pending, decoded.status)
        assertEquals(created, decoded.createdAt)
    }

    @Test
    fun decoderThrowsOnMissingRequiredField() {
        val payload = ContentReportPayload.reportPayload(sampleReport(), Date(1))
            .toMutableMap().apply { remove("contentID") }
        assertThrows(IllegalArgumentException::class.java) { payload.toContentReport("r1") }
    }

    @Test
    fun decoderThrowsOnUnknownEnum() {
        val payload = ContentReportPayload.reportPayload(sampleReport(), Date(1))
            .toMutableMap().apply { put("target", "bogusTarget") }
        assertThrows(IllegalArgumentException::class.java) { payload.toContentReport("r1") }
    }

    private companion object {
        const val TS = "serverTimestamp"

        fun sampleReport() = ContentReport(
            id = "r1",
            target = ContentReportTarget.ChatMessage,
            contentId = "msg-9",
            reporterId = "u1",
            reason = ContentReportReason.Spam,
            note = "",
            status = ContentReportStatus.Pending,
            createdAt = Date(0),
        )
    }
}
