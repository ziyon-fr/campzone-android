package fr.ziyon.campzone.data.model

import fr.ziyon.campzone.core.permissions.UserRole
import java.util.Date

/**
 * `users/{uid}/notificationTokens/{sha256hex}` (`02-firestore-schema.md` §2.2).
 * Doc ID = lowercase hex SHA-256 of the raw FCM token. No `appID` field here
 * (that lives only in the backend `/notifications/devices` payload).
 */
data class NotificationToken(
    val token: String,
    val role: UserRole,
    val localeIdentifier: String,
    val appVersion: String = "unknown",
    val platform: String = "android",
    val provider: String = "fcm",
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
)

internal fun Map<String, Any?>.toNotificationTokenOrNull(): NotificationToken? {
    val token = stringValue("token") ?: return null
    return NotificationToken(
        token = token,
        role = UserRole.fromWire(stringValue("role")),
        localeIdentifier = rawStringValue("localeIdentifier").orEmpty(),
        appVersion = stringValue("appVersion") ?: "unknown",
        platform = stringValue("platform") ?: "android",
        provider = stringValue("provider") ?: "fcm",
        createdAt = dateValue("createdAt"),
        updatedAt = dateValue("updatedAt"),
    )
}

/**
 * `users/{uid}/notificationSettings/default` (`02-firestore-schema.md` §2.3) -
 * single doc. The stored field is **`subscribedRoleRawValues`** (Swift's
 * `subscribedRoles` is computed). No client `createdAt`.
 */
data class NotificationSettings(
    val isEnabled: Boolean = true,
    val authorizationState: NotificationAuthorizationState = NotificationAuthorizationState.NotDetermined,
    val announcementsEnabled: Boolean = true,
    val chatMessagesEnabled: Boolean = true,
    val scheduleRemindersEnabled: Boolean = true,
    val roleMessagesEnabled: Boolean = true,
    val teamUpdatesEnabled: Boolean = true,
    val subscribedCampingIds: List<String> = emptyList(),
    val subscribedRoles: List<UserRole> = emptyList(),
    val subscribedTeamIds: List<String> = emptyList(),
    val updatedAt: Date? = null,
)

internal fun Map<String, Any?>.toNotificationSettings(): NotificationSettings =
    NotificationSettings(
        isEnabled = boolValue("isEnabled") ?: true,
        authorizationState = NotificationAuthorizationState.fromWire(stringValue("authorizationState")),
        announcementsEnabled = boolValue("announcementsEnabled") ?: true,
        chatMessagesEnabled = boolValue("chatMessagesEnabled") ?: true,
        scheduleRemindersEnabled = boolValue("scheduleRemindersEnabled") ?: true,
        roleMessagesEnabled = boolValue("roleMessagesEnabled") ?: true,
        teamUpdatesEnabled = boolValue("teamUpdatesEnabled") ?: true,
        subscribedCampingIds = stringListValue("subscribedCampingIDs"),
        subscribedRoles = rawStringListValue("subscribedRoleRawValues").map(UserRole::fromWire),
        subscribedTeamIds = stringListValue("subscribedTeamIDs"),
        updatedAt = dateValue("updatedAt"),
    )

internal object NotificationPrefsPayload {

    fun tokenPayload(
        token: NotificationToken,
        serverTimestamp: Any,
        includeCreatedAt: Boolean,
    ): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "token" to token.token,
            "platform" to token.platform,
            "provider" to token.provider,
            "role" to token.role.rawValue,
            "localeIdentifier" to token.localeIdentifier,
            "appVersion" to token.appVersion,
            "updatedAt" to serverTimestamp,
        )
        if (includeCreatedAt) payload["createdAt"] = serverTimestamp
        return payload
    }

    fun settingsPayload(
        settings: NotificationSettings,
        serverTimestamp: Any,
    ): Map<String, Any?> =
        linkedMapOf(
            "isEnabled" to settings.isEnabled,
            "authorizationState" to settings.authorizationState.wireValue,
            "announcementsEnabled" to settings.announcementsEnabled,
            "chatMessagesEnabled" to settings.chatMessagesEnabled,
            "scheduleRemindersEnabled" to settings.scheduleRemindersEnabled,
            "roleMessagesEnabled" to settings.roleMessagesEnabled,
            "teamUpdatesEnabled" to settings.teamUpdatesEnabled,
            "subscribedCampingIDs" to settings.subscribedCampingIds
                .map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted(),
            "subscribedRoleRawValues" to settings.subscribedRoles
                .map { it.rawValue }.distinct().sorted(),
            "subscribedTeamIDs" to settings.subscribedTeamIds
                .map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted(),
            "updatedAt" to serverTimestamp,
        )
}
