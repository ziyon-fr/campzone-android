package fr.ziyon.campzone.data.notifications

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.model.NotificationSettings
import fr.ziyon.campzone.data.notifications.NotificationSettingsRules.normalizedFor
import fr.ziyon.campzone.data.notifications.NotificationSettingsRules.sanitized
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSettingsRulesTest {

    @Test
    fun defaultSettingsSubscribeToOwnRole() {
        val settings = NotificationSettingsRules.defaultSettings(UserRole.Leader)
        assertEquals(listOf(UserRole.Leader), settings.subscribedRoles)
        assertTrue(settings.isEnabled)
        assertTrue(settings.announcementsEnabled)
    }

    @Test
    fun adminMayKeepMultipleRoleAudiences() {
        val settings = NotificationSettings(subscribedRoles = listOf(UserRole.Leader, UserRole.Pastor))
            .normalizedFor(UserRole.Admin)
        assertEquals(listOf(UserRole.Leader, UserRole.Pastor), settings.subscribedRoles.sortedBy { it.rawValue })
    }

    @Test
    fun nonAdminRoleAudienceClampedToOwnRole() {
        val settings = NotificationSettings(subscribedRoles = listOf(UserRole.Leader, UserRole.Admin))
            .normalizedFor(UserRole.Leader)
        assertEquals(listOf(UserRole.Leader), settings.subscribedRoles)
    }

    @Test
    fun emptyRolesRefilledWithOwnRoleWhenRoleMessagesOn() {
        val settings = NotificationSettings(subscribedRoles = emptyList(), roleMessagesEnabled = true)
            .normalizedFor(UserRole.User)
        assertEquals(listOf(UserRole.User), settings.subscribedRoles)
    }

    @Test
    fun emptyRolesStayEmptyWhenRoleMessagesOff() {
        val settings = NotificationSettings(subscribedRoles = emptyList(), roleMessagesEnabled = false)
            .normalizedFor(UserRole.User)
        assertTrue(settings.subscribedRoles.isEmpty())
    }

    @Test
    fun sanitizeTrimsDedupesAndSortsIds() {
        val settings = NotificationSettings(
            subscribedCampingIds = listOf(" camp-b ", "camp-a", "camp-a", "  "),
            subscribedTeamIds = listOf("t2", "t1", "t1"),
            subscribedStaffRoleIds = listOf(" staff-worship ", "staff-games", "staff-worship"),
        ).sanitized()
        assertEquals(listOf("camp-a", "camp-b"), settings.subscribedCampingIds)
        assertEquals(listOf("t1", "t2"), settings.subscribedTeamIds)
        assertEquals(listOf("staff-games", "staff-worship"), settings.subscribedStaffRoleIds)
    }
}
