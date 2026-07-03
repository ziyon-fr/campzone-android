package fr.ziyon.campzone.data.model

import fr.ziyon.campzone.core.navigation.CampzoneDeepLink
import fr.ziyon.campzone.core.permissions.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNotificationDeepLinkTest {

    private fun notification(
        kind: AppNotificationKind,
        topic: String = "campzone_announcements",
        announcementId: String? = null,
        campingId: String? = null,
        programId: String? = null,
        pollId: String? = null,
        teamId: String? = null,
        event: String? = null,
        role: String? = null,
        recipientUserId: String? = null,
        achievementId: String? = null,
        vehicleId: String? = null,
        registrationId: String? = null,
        shareId: String? = null,
        deepLinkUrl: String? = null,
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
        programId = programId,
        pollId = pollId,
        teamId = teamId,
        role = role,
        event = event,
        recipientUserId = recipientUserId,
        achievementId = achievementId,
        vehicleId = vehicleId,
        registrationId = registrationId,
        shareId = shareId,
        deepLinkUrl = deepLinkUrl,
        mentionedUserIds = mentionedUserIds,
    )

    @Test
    fun extendedKindsParseFromWire() {
        assertEquals(AppNotificationKind.TeamUpdate, AppNotificationKind.fromWire("team_update"))
        assertEquals(AppNotificationKind.TeamUpdate, AppNotificationKind.fromWire("teamupdate"))
        assertEquals(AppNotificationKind.Registration, AppNotificationKind.fromWire("registration_request"))
        assertEquals(AppNotificationKind.ChatMention, AppNotificationKind.fromWire("chatmention"))
        assertEquals(AppNotificationKind.Badge, AppNotificationKind.fromWire("achievement_badge"))
        assertEquals(AppNotificationKind.Transportation, AppNotificationKind.fromWire("transportation"))
        assertEquals(AppNotificationKind.Checklist, AppNotificationKind.fromWire("packing_share"))
    }

    @Test
    fun badgeDeepLinkOpensExactAchievement() {
        val link = notification(
            AppNotificationKind.Badge,
            recipientUserId = "u1",
            achievementId = "badge-1",
        ).deepLink()
        assertEquals(CampzoneDeepLink.Achievement("u1", null, null, null, "badge-1"), link)
    }

    @Test
    fun transportationInvitationOpensDecisionSheet() {
        val link = notification(
            AppNotificationKind.Transportation,
            campingId = "camp-1",
            event = "invitation",
            vehicleId = "car-1",
            registrationId = "reg-1",
        ).deepLink()
        assertEquals(CampzoneDeepLink.TransportationInvitation("camp-1", "car-1", "reg-1"), link)
    }

    @Test
    fun checklistDeepLinkPreservesActionSubjectRegistration() {
        val link = notification(
            AppNotificationKind.Checklist,
            topic = "campzone_user_guardian-1",
            campingId = "camp-1",
            recipientUserId = "guardian-1",
            registrationId = "child-emma",
            shareId = "share-1",
        ).deepLink()

        assertEquals(CampzoneDeepLink.PackingShare("camp-1", "share-1", "child-emma"), link)
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
    fun approvedRegistrationDeepLinkGoesToCampingDetail() {
        val link = notification(
            AppNotificationKind.Registration,
            campingId = "c1",
            event = "approved",
        ).deepLink()
        assertEquals(CampzoneDeepLink.Camping("c1"), link)
    }

    @Test
    fun badgeDeepLinkGoesToAchievements() {
        val link = notification(
            AppNotificationKind.Badge,
            topic = "campzone_user_u1",
            campingId = "camp-1",
            recipientUserId = "u1",
        ).deepLink()

        assertEquals(
            CampzoneDeepLink.Achievements(
                userId = "u1",
                displayName = null,
                photoUrl = null,
                campingId = "camp-1",
            ),
            link,
        )
    }

    @Test
    fun explicitRegistrationDeepLinkOverridesApprovedEvent() {
        val link = notification(
            AppNotificationKind.Registration,
            campingId = "c1",
            event = "approved",
            deepLinkUrl = "campzone://registration-review/c1",
        ).deepLink()
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
    fun scheduleReminderDeepLinkOpensProgramWhenProgramIdIsPresent() {
        val link = notification(
            AppNotificationKind.ScheduleReminder,
            campingId = "c1",
            programId = "program-1",
        ).deepLink()

        assertEquals(CampzoneDeepLink.ScheduleProgram("c1", "program-1"), link)
    }

    @Test
    fun scheduleReminderWithoutProgramFallsBackToCampingDetail() {
        val link = notification(AppNotificationKind.ScheduleReminder, campingId = "c1").deepLink()
        assertEquals(CampzoneDeepLink.Camping("c1"), link)
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

    @Test
    fun directUserRowIsScopedToRecipientUser() {
        val n = notification(
            AppNotificationKind.Registration,
            topic = "campzone_user_u2",
            campingId = "c1",
            recipientUserId = "u2",
        )
        val topics = setOf("campzone_user_u2")
        assertTrue(n.concerns("u2", UserRole.User, topics))
        assertFalse(n.concerns("u1", UserRole.User, topics))
    }
}
