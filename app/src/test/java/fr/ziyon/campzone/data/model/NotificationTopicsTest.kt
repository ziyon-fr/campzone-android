package fr.ziyon.campzone.data.model

import fr.ziyon.campzone.core.permissions.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTopicsTest {

    @Test
    fun globalAnnouncementTopicIsStable() {
        assertEquals("campzone_announcements", NotificationTopics.globalAnnouncement)
    }

    @Test
    fun topicSanitizesDisallowedCharacters() {
        assertEquals("campzone_camping_camp_a_b", NotificationTopics.campingAnnouncement("camp a/b"))
    }

    @Test
    fun roleTopicRoundTrips() {
        val topic = NotificationTopics.roleTopic(UserRole.Leader.rawValue)
        assertEquals("campzone_role_leader", topic)
        assertEquals("leader", NotificationTopics.roleFromTopic(topic))
    }

    @Test
    fun disabledMasterYieldsNoTopics() {
        val settings = NotificationSettings(isEnabled = false)
        assertTrue(NotificationTopics.visibleTopics(UserRole.User, settings).isEmpty())
    }

    @Test
    fun nonAdminSeesOnlyOwnRoleTopic() {
        val topics = NotificationTopics.visibleTopics(UserRole.Leader, settings = null, userId = "leader-1")
        assertTrue(topics.contains("campzone_role_leader"))
        assertFalse(topics.contains("campzone_role_admin"))
        assertTrue(topics.contains(NotificationTopics.globalAnnouncement))
        assertTrue(topics.contains("campzone_user_leader-1"))
    }

    @Test
    fun adminSeesEveryRoleTopic() {
        val topics = NotificationTopics.visibleTopics(UserRole.Admin, settings = null)
        assertTrue(topics.contains("campzone_role_admin"))
        assertTrue(topics.contains("campzone_role_leader"))
    }

    @Test
    fun disablingAnnouncementsRemovesGlobalTopic() {
        val settings = NotificationSettings(announcementsEnabled = false, subscribedRoles = listOf(UserRole.User))
        val topics = NotificationTopics.visibleTopics(UserRole.User, settings)
        assertFalse(topics.contains(NotificationTopics.globalAnnouncement))
    }

    @Test
    fun subscribedCampingAddsChatAndReminderTopics() {
        val settings = NotificationSettings(
            subscribedCampingIds = listOf("c1"),
            subscribedRoles = listOf(UserRole.User),
        )
        val topics = NotificationTopics.visibleTopics(UserRole.User, settings)
        assertTrue(topics.contains("campzone_camping_c1"))
        assertTrue(topics.contains("campzone_camping_chat_c1"))
        assertTrue(topics.contains("campzone_camping_reminders_c1"))
    }

    @Test
    fun subscribedTeamAddsTeamTopics() {
        val settings = NotificationSettings(
            subscribedTeamIds = listOf("t1"),
            subscribedRoles = listOf(UserRole.User),
        )
        val topics = NotificationTopics.visibleTopics(UserRole.User, settings)
        assertTrue(topics.contains("campzone_team_t1"))
        assertTrue(topics.contains("campzone_team_chat_t1"))
    }

    @Test
    fun directUserTopicIsSuppressedWhenNotificationsAreDisabled() {
        val settings = NotificationSettings(isEnabled = false)
        val topics = NotificationTopics.visibleTopics(UserRole.User, settings, userId = "u1")
        assertFalse(topics.contains("campzone_user_u1"))
    }

    @Test
    fun scopedSubscriptionsCarryEveryRulesPredicate() {
        val settings = NotificationSettings(
            subscribedCampingIds = listOf("c1"),
            subscribedTeamIds = listOf("t1"),
            subscribedRoles = listOf(UserRole.Adult),
        )

        val subscriptions = NotificationTopics.visibleTopicSubscriptions(
            role = UserRole.Adult,
            settings = settings,
            userId = "adult-1",
            teamCampingIds = mapOf("t1" to "c1"),
        )

        assertTrue(subscriptions.contains(
            NotificationTopicSubscription(
                topic = "campzone_camping_c1",
                campingId = "c1",
            ),
        ))
        assertTrue(subscriptions.contains(
            NotificationTopicSubscription(
                topic = "campzone_camping_role_c1_adult",
                campingId = "c1",
                role = "adult",
            ),
        ))
        assertTrue(subscriptions.contains(
            NotificationTopicSubscription(
                topic = "campzone_team_t1",
                campingId = "c1",
                teamId = "t1",
            ),
        ))
    }
}
