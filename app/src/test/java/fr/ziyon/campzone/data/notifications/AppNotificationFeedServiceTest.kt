package fr.ziyon.campzone.data.notifications

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.model.AppNotification
import fr.ziyon.campzone.data.model.AppNotificationKind
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppNotificationFeedServiceTest {

    @Test
    fun snapshotStoreWaitsForAllInitialTopicsAndSuppressesDuplicates() {
        val topics = setOf("campzone_announcements", "campzone_user_u1")
        val store = NotificationFeedSnapshotStore(
            userId = "u1",
            role = UserRole.User,
            visibleTopics = topics,
        )

        assertNull(
            store.update(
                "campzone_announcements",
                listOf(notification("global", "campzone_announcements", sentAt = 100)),
            ),
        )

        val first = store.update(
            "campzone_user_u1",
            listOf(notification("approved", "campzone_user_u1", sentAt = 200, recipientUserId = "u1")),
        )

        assertEquals(listOf("approved", "global"), first?.map { it.id })
        assertNull(
            store.update(
                "campzone_user_u1",
                listOf(notification("approved", "campzone_user_u1", sentAt = 200, recipientUserId = "u1")),
            ),
        )
    }

    @Test
    fun snapshotStoreRejectsDirectRowsForCampingsNoLongerVisible() {
        val topic = "campzone_user_u1"
        val store = NotificationFeedSnapshotStore(
            userId = "u1",
            role = UserRole.User,
            visibleTopics = setOf(topic),
            visibilityScope = NotificationVisibilityScope(
                visibleCampingIds = setOf("camp-current"),
            ),
        )

        val rows = store.update(
            topic,
            listOf(notification("stale", topic, 100, recipientUserId = "u1")),
        )

        assertNull(rows)
    }

    private fun notification(
        id: String,
        topic: String,
        sentAt: Long,
        recipientUserId: String? = null,
    ) = AppNotification(
        id = id,
        appId = AppNotification.APP_ID,
        kind = AppNotificationKind.Registration,
        title = id,
        body = "Body",
        topic = topic,
        sentAt = Date(sentAt),
        campingId = "camp-1",
        recipientUserId = recipientUserId,
    )
}
