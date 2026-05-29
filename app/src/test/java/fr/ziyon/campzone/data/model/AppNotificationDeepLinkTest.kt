package fr.ziyon.campzone.data.model

import fr.ziyon.campzone.core.navigation.CampzoneDeepLink
import fr.ziyon.campzone.core.permissions.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNotificationDeepLinkTest {

    private fun notification(
        kind: AppNotificationKind,
        topic: String = "campzone_announcements",
        announcementId: String? = null,
        campingId: String? = null,
        pollId: String? = null,
        teamId: String? = null,
        event: String? = null,
        role: String? = null,
        mentionedUserIds: List<String> = emptyList(),
    ) = AppNotification(
        id = "n1",
        appId = AppNotification.APP_ID,
        kind = kind,
        title = "t",
        body = "b",
        topic = topic,
        sentAt = java.util.Date(),
        announcementId = announcementId,
        campingId = campingId,
        pollId = pollId,
        teamId = teamId,
        role = role,
        event = event,
        mentionedUserIds = mentionedUserIds,
    )

    @Test
    fun extendedKindsParseFromWire() {
        assertEquals(AppNotificationKind.TeamUpdate, AppNotificationKind.fromWire("team_update"))
        assertEquals(AppNotificationKind.TeamUpdate, AppNotificationKind.fromWire("teamupdate"))
        assertEquals(AppNotificationKind.Registration, AppNotificationKind.fromWire("registration_request"))
        assertEquals(AppNotificationKind.ChatMention, AppNotificationKind.fromWire("chatmention"))
    }

    @Test
    fun announcementDeepLink() {
        val link = notification(AppNotificationKind.Announcement, announcementId = "a1").deepLink()
        assertEquals(CampzoneDeepLink.Announcement("a1"), link)
    }

    @Test
    fun chatDeepLinkPrefersTeamWhenTeamIdPresent() {
        val team = notification(AppNotificationKind.ChatMessage, campingId = "c1", teamId = "t1").deepLink()
        assertEquals(CampzoneDeepLink.TeamChat("c1", "t1"), team)
        val camp = notification(AppNotificationKind.ChatMessage, campingId = "c1").deepLink()
        assertEquals(CampzoneDeepLink.CampingChat("c1"), camp)
    }

    @Test
    fun pollDeepLinkCarriesOptionalPollId() {
        val link = notification(AppNotificationKind.Poll, campingId = "c1", pollId = "p1").deepLink()
        assertEquals(CampzoneDeepLink.Poll("c1", "p1"), link)
    }

    @Test
    fun registrationDeepLinkGoesToReview() {
        val link = notification(AppNotificationKind.Registration, campingId = "c1").deepLink()
        assertEquals(CampzoneDeepLink.RegistrationReview("c1"), link)
    }

    @Test
    fun teamUpdatePointEventGoesToPoints() {
        val link = notification(
            AppNotificationKind.TeamUpdate,
            campingId = "c1",
            teamId = "t1",
            event = "scoreChanged",
        ).deepLink()
        assertEquals(CampzoneDeepLink.TeamPoints("c1", "t1"), link)
    }

    @Test
    fun teamUpdateNonPointEventGoesToTeam() {
        val link = notification(
            AppNotificationKind.TeamUpdate,
            campingId = "c1",
            teamId = "t1",
            event = "memberAssigned",
        ).deepLink()
        assertEquals(CampzoneDeepLink.TeamUpdate("c1", "t1"), link)
    }

    @Test
    fun scheduleReminderHasNoDeepLink() {
        assertNull(notification(AppNotificationKind.ScheduleReminder).deepLink())
    }

    @Test
    fun concernsRequiresVisibleTopic() {
        val n = notification(AppNotificationKind.Announcement, topic = "campzone_announcements")
        assertTrue(n.concerns("u1", UserRole.User, setOf("campzone_announcements")))
        assertFalse(n.concerns("u1", UserRole.User, setOf("campzone_role_user")))
    }

    @Test
    fun roleScopedRowGatedToMatchingRole() {
        val n = notification(
            AppNotificationKind.Announcement,
            topic = "campzone_role_leader",
            role = "leader",
        )
        val topics = setOf("campzone_role_leader")
        assertTrue(n.concerns("u1", UserRole.Leader, topics))
        assertFalse(n.concerns("u1", UserRole.User, topics))
        assertTrue(n.concerns("admin1", UserRole.Admin, topics))
    }

    @Test
    fun mentionRowScopedToMentionedUser() {
        val n = notification(
            AppNotificationKind.ChatMention,
            topic = "campzone_camping_chat_c1",
            campingId = "c1",
            mentionedUserIds = listOf("u2"),
        )
        val topics = setOf("campzone_camping_chat_c1")
        assertTrue(n.concerns("u2", UserRole.User, topics))
        assertFalse(n.concerns("u1", UserRole.User, topics))
    }
}
