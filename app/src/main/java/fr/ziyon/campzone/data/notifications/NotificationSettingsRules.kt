package fr.ziyon.campzone.data.notifications

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.model.NotificationSettings

/**
 * Pure (testable) settings rules ported from iOS `NotificationSettings`
 * (`Model/NotificationModels.swift`): defaults, role-audience scoping,
 * normalization, and write sanitization. No I/O.
 */
object NotificationSettingsRules {

    /** Admins may subscribe to every role; everyone else only their own. */
    fun roleAudienceOptions(role: UserRole): List<UserRole> =
        if (role.isAdmin) UserRole.allWireRoles.toList() else listOf(role)

    /** Default settings (everything enabled, own role subscribed), normalized. */
    fun defaultSettings(role: UserRole): NotificationSettings =
        NotificationSettings(subscribedRoles = listOf(role)).normalizedFor(role)

    /**
     * Clamps `subscribedRoleRawValues` to the roles this user is allowed to
     * pick, and re-adds the user's own role when role messages are on but the
     * list ended up empty. Mirrors iOS `normalized(for:)`.
     */
    fun NotificationSettings.normalizedFor(role: UserRole): NotificationSettings {
        val allowed = roleAudienceOptions(role).toSet()
        var roles = subscribedRoles.filter { it in allowed }.distinct().sortedBy { it.rawValue }
        if (roleMessagesEnabled && roles.isEmpty() && allowed.contains(role)) {
            roles = listOf(role)
        }
        return copy(subscribedRoles = roles)
    }

    /** Trims/dedupes/sorts ids and drops any unknown role raws before a write. */
    fun NotificationSettings.sanitized(): NotificationSettings = copy(
        subscribedCampingIds = cleanedIds(subscribedCampingIds),
        subscribedRoles = subscribedRoles.distinct().sortedBy { it.rawValue },
        subscribedTeamIds = cleanedIds(subscribedTeamIds),
    )

    private fun cleanedIds(values: List<String>): List<String> =
        values.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
}
