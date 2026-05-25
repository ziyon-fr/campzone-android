package fr.ziyon.campzone.data.model

import com.google.firebase.Timestamp
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AppNotificationTest {

    @Test
    fun ignoresNonCampzoneApps() {
        val doc = mapOf("appID" to "otherapp", "title" to "X", "topic" to "t")
        assertNull(doc.toAppNotificationOrNull("n1"))
    }

    @Test
    fun parsesIso8601SentAt() {
        val doc = mapOf(
            "appID" to "campzone",
            "kind" to "announcement",
            "title" to "News",
            "sentAt" to "2026-05-16T09:00:00.000Z",
        )
        val decoded = doc.toAppNotificationOrNull("n1")!!
        assertNotNull(decoded.sentAt)
        // 2026-05-16T09:00:00Z is well after the distant-past floor
        assert(decoded.sentAt.after(AppNotification.DISTANT_PAST))
    }

    @Test
    fun acceptsTimestampSentAt() {
        val ts = Timestamp(Date(1_700_000_000_000L))
        val doc = mapOf("appID" to "campzone", "kind" to "poll", "sentAt" to ts)
        assertEquals(ts.toDate(), doc.toAppNotificationOrNull("n1")!!.sentAt)
    }

    @Test
    fun infersKindFromIdFieldsWhenNoExplicitKind() {
        assertEquals(
            AppNotificationKind.Announcement,
            mapOf("appID" to "campzone", "announcementID" to "a1").toAppNotificationOrNull("n1")!!.kind,
        )
        assertEquals(
            AppNotificationKind.Poll,
            mapOf("appID" to "campzone", "pollID" to "p1").toAppNotificationOrNull("n1")!!.kind,
        )
        assertEquals(
            AppNotificationKind.ChatMessage,
            mapOf("appID" to "campzone", "campingID" to "c1").toAppNotificationOrNull("n1")!!.kind,
        )
        assertEquals(
            AppNotificationKind.Unknown,
            mapOf("appID" to "campzone").toAppNotificationOrNull("n1")!!.kind,
        )
    }

    @Test
    fun explicitKindWinsOverInference() {
        val doc = mapOf("appID" to "campzone", "kind" to "schedule_reminder", "announcementID" to "a1")
        assertEquals(AppNotificationKind.ScheduleReminder, doc.toAppNotificationOrNull("n1")!!.kind)
    }
}
