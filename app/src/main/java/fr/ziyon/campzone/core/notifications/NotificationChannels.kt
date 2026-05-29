package fr.ziyon.campzone.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import fr.ziyon.campzone.R

/**
 * Android notification channels, keyed by the backend `data.type` so each
 * push category (announcement, chat, poll, schedule reminder, team update,
 * registration) is independently mutable in system settings. Created once on
 * app start. Channel ids are stable wire-ish strings; do not rename.
 */
object NotificationChannels {
    const val Announcement = "announcement"
    const val ChatMessage = "chat_message"
    const val Poll = "poll"
    const val ScheduleReminder = "schedule_reminder"
    const val TeamUpdate = "team_update"
    const val Registration = "registration"
    const val General = "general"

    private data class ChannelSpec(
        val id: String,
        @StringRes val nameRes: Int,
        @StringRes val descriptionRes: Int,
    )

    private val specs = listOf(
        ChannelSpec(Announcement, R.string.notif_channel_announcement, R.string.notif_channel_announcement_desc),
        ChannelSpec(ChatMessage, R.string.notif_channel_chat, R.string.notif_channel_chat_desc),
        ChannelSpec(Poll, R.string.notif_channel_poll, R.string.notif_channel_poll_desc),
        ChannelSpec(ScheduleReminder, R.string.notif_channel_reminder, R.string.notif_channel_reminder_desc),
        ChannelSpec(TeamUpdate, R.string.notif_channel_team, R.string.notif_channel_team_desc),
        ChannelSpec(Registration, R.string.notif_channel_registration, R.string.notif_channel_registration_desc),
        ChannelSpec(General, R.string.notif_channel_general, R.string.notif_channel_general_desc),
    )

    /** Maps a backend `type`/`kind` string to a channel id (tolerant casing). */
    fun channelIdFor(type: String?): String = when (type?.trim()?.lowercase()) {
        "announcement" -> Announcement
        "chat_message", "chatmessage", "chat_mention", "chatmention" -> ChatMessage
        "poll" -> Poll
        "schedule_reminder", "schedulereminder" -> ScheduleReminder
        "team_update", "teamupdate" -> TeamUpdate
        "registration", "registration_request" -> Registration
        else -> General
    }

    /** Registers every channel. No-op below Android O. Idempotent. */
    fun registerAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        specs.forEach { spec ->
            val channel = NotificationChannel(
                spec.id,
                context.getString(spec.nameRes),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(spec.descriptionRes)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
