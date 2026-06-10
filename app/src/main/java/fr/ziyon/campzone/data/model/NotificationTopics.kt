package fr.ziyon.campzone.data.model

import fr.ziyon.campzone.core.permissions.UserRole

/**
 * FCM topic naming + the set of topics a given user can see in the in-app
 * feed. Ported verbatim from the iOS `AppNotification` topic helpers
 * (`04-backend-api.md` §3.5) - the feed filters `ziyon_notifications` by
 * `appID == "campzone"` and these visible topics.
 *
 * `_topic(appID, scope, value)` joins the parts with `_`, each part sanitized
 * so any character outside `[A-Za-z0-9-_.~%]` becomes `_`.
 */
object NotificationTopics {
    const val AppId = AppNotification.APP_ID

    fun topic(scope: String, value: String? = null): String =
        listOfNotNull(AppId, scope, value)
            .joinToString(separator = "_") { sanitizePart(it) }

    val globalAnnouncement: String get() = topic(scope = "announcements")

    fun roleTopic(roleRawValue: String): String = topic(scope = "role", value = roleRawValue)

    fun userTopic(userId: String): String = topic(scope = "user", value = userId)

    fun campingAnnouncement(campingId: String): String =
        topic(scope = "camping", value = campingId)

    fun campingRoleAnnouncement(campingId: String, roleRawValue: String): String =
        topic(scope = "camping_role", value = "${campingId}_$roleRawValue")

    fun campingRegistrationRole(campingId: String, roleRawValue: String): String =
        topic(scope = "camping_registration_role", value = "${campingId}_$roleRawValue")

    fun campingChat(campingId: String): String =
        topic(scope = "camping_chat", value = campingId)

    fun campingReminders(campingId: String): String =
        topic(scope = "camping_reminders", value = campingId)

    fun teamUpdate(teamId: String): String = topic(scope = "team", value = teamId)

    fun teamChat(teamId: String): String = topic(scope = "team_chat", value = teamId)

    /** Reads `campzone_role_<role>` back out of a topic string, else null. */
    fun roleFromTopic(topic: String): String? {
        val prefix = "${AppId}_role_"
        return if (topic.startsWith(prefix)) topic.removePrefix(prefix) else null
    }

    /**
     * The audience the leadership role evaluator uses: admins see every role
     * topic, everyone else only their own.
     */
    fun roleAudience(role: UserRole): List<UserRole> =
        if (role.isAdmin) UserRole.allWireRoles else listOf(role)

    /**
     * The complete set of topics a user's feed should read, honouring their
     * saved settings. When `settings` is null, defaults (everything enabled)
     * apply. Mirrors iOS `AppNotification.visibleTopics(for:settings:)`.
     */
    fun visibleTopics(
        role: UserRole,
        settings: NotificationSettings? = null,
        userId: String? = null,
    ): Set<String> {
        if (settings?.isEnabled == false) return emptySet()

        val announcementsEnabled = settings?.announcementsEnabled ?: true
        val chatMessagesEnabled = settings?.chatMessagesEnabled ?: true
        val scheduleRemindersEnabled = settings?.scheduleRemindersEnabled ?: true
        val roleMessagesEnabled = settings?.roleMessagesEnabled ?: true
        val teamUpdatesEnabled = settings?.teamUpdatesEnabled ?: true

        val roles: List<UserRole> = when {
            !roleMessagesEnabled -> emptyList()
            settings != null && settings.subscribedRoles.isNotEmpty() -> settings.subscribedRoles
            else -> roleAudience(role)
        }
        val roleTopics = roles.map { roleTopic(it.rawValue) }

        val campingIds = settings?.subscribedCampingIds ?: emptyList()
        val campingTopics = campingIds.flatMap { campingId ->
            buildList {
                if (announcementsEnabled) {
                    add(campingAnnouncement(campingId))
                    addAll(roles.map { campingRoleAnnouncement(campingId, it.rawValue) })
                }
                if (roleMessagesEnabled) {
                    addAll(roles.map { campingRegistrationRole(campingId, it.rawValue) })
                }
                if (chatMessagesEnabled) add(campingChat(campingId))
                if (scheduleRemindersEnabled) add(campingReminders(campingId))
            }
        }

        val teamTopics = (settings?.subscribedTeamIds ?: emptyList()).flatMap { teamId ->
            buildList {
                if (teamUpdatesEnabled) add(teamUpdate(teamId))
                if (chatMessagesEnabled) add(teamChat(teamId))
            }
        }

        val globalTopics = if (announcementsEnabled) listOf(globalAnnouncement) else emptyList()
        val directUserTopics = userId
            ?.trim()
            ?.takeUnless { it.isBlank() }
            ?.let { listOf(userTopic(it)) }
            ?: emptyList()
        return (directUserTopics + globalTopics + roleTopics + campingTopics + teamTopics).toSet()
    }

    private fun sanitizePart(value: String): String =
        value.map { ch ->
            val allowed = ch.isLetterOrDigit() && ch.code < 128 || ch in "-_.~%"
            if (allowed) ch else '_'
        }.joinToString("")
}
