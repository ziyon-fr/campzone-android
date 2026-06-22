package fr.ziyon.campzone.data.notifications

import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.BuildConfig
import fr.ziyon.campzone.data.model.CampingSchedule
import fr.ziyon.campzone.data.model.NotificationSettings
import fr.ziyon.campzone.data.model.Program
import fr.ziyon.campzone.data.model.ScheduleReminderTiming
import java.text.SimpleDateFormat
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Calls the Vercel notification backend (`04-backend-api.md` §3). These calls
 * are what actually subscribe/unsubscribe the device's FCM token to topics -
 * without them the device receives no pushes even though the Firestore copies
 * exist. Both endpoints require the caller's Firebase ID token.
 */
interface NotificationApi {
    /** `POST /notifications/devices` - register/refresh the FCM token + topics. */
    suspend fun registerDevice(
        token: String,
        roleRawValue: String,
        localeIdentifier: String,
        appVersion: String,
    )

    /** `POST /notifications/settings` - persist settings + re-sync topics. */
    suspend fun syncSettings(settings: NotificationSettings, userId: String)

    /** `POST /notifications/reminders` with `action=replaceCamping`. */
    suspend fun replaceCampingReminders(campingId: String, reminders: List<ProgramReminderPlan>)

    /** `POST /notifications/reminders` with `action=replacePrograms`. */
    suspend fun replaceProgramReminders(
        campingId: String,
        programIds: List<String>,
        reminders: List<ProgramReminderPlan>,
    )

    suspend fun dispatchTransportation(notification: TransportationNotification) = Unit
}

data class TransportationNotification(
    val event: String,
    val campingId: String,
    val vehicleId: String,
    val registrationId: String,
    val participantName: String,
    val driverName: String,
)

data class ProgramReminderPlan(
    val appId: String = "campzone",
    val id: String,
    val campingId: String,
    val programId: String,
    val title: String,
    val body: String,
    val fireDate: Date,
    val targetTopic: String,
)

object ProgramReminderPlanner {
    fun plans(schedule: CampingSchedule, now: Date = Date()): List<ProgramReminderPlan> =
        schedule.allPrograms.mapNotNull { program ->
            plan(program = program, timing = schedule.reminderTiming, now = now)
        }

    fun plan(
        program: Program,
        timing: ScheduleReminderTiming,
        now: Date = Date(),
    ): ProgramReminderPlan? {
        val minutesBeforeStart = timing.minutesBeforeStart ?: return null
        val fireDate = Date(program.startDate.time - minutesBeforeStart * 60_000L)
        if (!fireDate.after(now)) return null
        return ProgramReminderPlan(
            id = "${program.campingId}-${program.id}",
            campingId = program.campingId,
            programId = program.id,
            title = program.title,
            body = reminderBody(program, minutesBeforeStart),
            fireDate = fireDate,
            targetTopic = "campzone_camping_reminders_${program.campingId}",
        )
    }

    private val ScheduleReminderTiming.minutesBeforeStart: Long?
        get() = when (this) {
            ScheduleReminderTiming.None -> null
            ScheduleReminderTiming.AtStart -> 0L
            ScheduleReminderTiming.FiveMinutes -> 5L
            ScheduleReminderTiming.FifteenMinutes -> 15L
            ScheduleReminderTiming.ThirtyMinutes -> 30L
            ScheduleReminderTiming.OneHour -> 60L
        }

    private fun reminderBody(program: Program, minutesBeforeStart: Long): String =
        if (minutesBeforeStart <= 0L) {
            "${program.title} starts now at ${program.location}."
        } else {
            "${program.title} starts in $minutesBeforeStart minutes at ${program.location}."
        }
}

object NoOpNotificationApi : NotificationApi {
    override suspend fun registerDevice(
        token: String,
        roleRawValue: String,
        localeIdentifier: String,
        appVersion: String,
    ) = Unit

    override suspend fun syncSettings(settings: NotificationSettings, userId: String) = Unit

    override suspend fun replaceCampingReminders(
        campingId: String,
        reminders: List<ProgramReminderPlan>,
    ) = Unit

    override suspend fun replaceProgramReminders(
        campingId: String,
        programIds: List<String>,
        reminders: List<ProgramReminderPlan>,
    ) = Unit

    override suspend fun dispatchTransportation(notification: TransportationNotification) = Unit
}

@Singleton
class BackendNotificationApi @Inject constructor(
    private val auth: FirebaseAuth,
) : NotificationApi {

    override suspend fun registerDevice(
        token: String,
        roleRawValue: String,
        localeIdentifier: String,
        appVersion: String,
    ) {
        val body = JSONObject()
            .put("appID", CampzoneAppId)
            .put("token", token)
            .put("platform", AndroidPlatform)
            .put("provider", FcmProvider)
            .put("role", roleRawValue)
            .put("localeIdentifier", localeIdentifier)
            .put("appVersion", appVersion)
        post("devices", body)
    }

    override suspend fun syncSettings(settings: NotificationSettings, userId: String) {
        val roleRaws = JSONArray().apply {
            settings.subscribedRoles
                .map { it.rawValue }
                .distinct()
                .sorted()
                .forEach { put(it) }
        }
        val campingIds = JSONArray().apply {
            settings.subscribedCampingIds
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
                .forEach { put(it) }
        }
        val teamIds = JSONArray().apply {
            settings.subscribedTeamIds
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
                .forEach { put(it) }
        }

        val body = JSONObject()
            .put("appID", CampzoneAppId)
            .put("userID", userId)
            .put("isEnabled", settings.isEnabled)
            .put("authorizationState", settings.authorizationState.wireValue)
            .put("announcementsEnabled", settings.announcementsEnabled)
            .put("chatMessagesEnabled", settings.chatMessagesEnabled)
            .put("scheduleRemindersEnabled", settings.scheduleRemindersEnabled)
            .put("roleMessagesEnabled", settings.roleMessagesEnabled)
            .put("teamUpdatesEnabled", settings.teamUpdatesEnabled)
            .put("subscribedCampingIDs", campingIds)
            // Send both keys: the backend accepts either, and `subscribedRoleRawValues`
            // is the stored canonical form (`04-backend-api.md` §3.3).
            .put("subscribedRoleRawValues", roleRaws)
            .put("subscribedRoles", roleRaws)
            .put("subscribedTeamIDs", teamIds)

        post("settings", body)
    }

    override suspend fun replaceCampingReminders(
        campingId: String,
        reminders: List<ProgramReminderPlan>,
    ) {
        val body = JSONObject()
            .put("action", "replaceCamping")
            .put("appID", CampzoneAppId)
            .put("campingID", campingId)
            .put("reminders", reminders.toJsonArray())
        post("reminders", body)
    }

    override suspend fun replaceProgramReminders(
        campingId: String,
        programIds: List<String>,
        reminders: List<ProgramReminderPlan>,
    ) {
        val body = JSONObject()
            .put("action", "replacePrograms")
            .put("appID", CampzoneAppId)
            .put("campingID", campingId)
            .put(
                "programIDs",
                JSONArray().apply {
                    programIds
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .sorted()
                        .forEach { put(it) }
                },
            )
            .put("reminders", reminders.toJsonArray())
        post("reminders", body)
    }

    override suspend fun dispatchTransportation(notification: TransportationNotification) {
        post(
            "dispatch/transportation",
            JSONObject()
                .put("appID", CampzoneAppId)
                .put("event", notification.event)
                .put("campingID", notification.campingId)
                .put("vehicleID", notification.vehicleId)
                .put("registrationID", notification.registrationId)
                .put("participantName", notification.participantName)
                .put("driverName", notification.driverName),
        )
    }

    private suspend fun post(path: String, body: JSONObject) {
        val token = auth.currentUser
            ?.getIdToken(false)
            ?.await()
            ?.token
            ?: return

        withContext(Dispatchers.IO) {
            val connection = (
                URL("${BuildConfig.BACKEND_BASE_URL}/notifications/$path")
                    .openConnection() as HttpURLConnection
                ).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
            }

            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val isSuccess = connection.responseCode in 200..299
            val stream = if (isSuccess) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (!isSuccess) {
                throw IllegalStateException(response.ifBlank { "Notification request failed." })
            }
        }
    }

    private companion object {
        const val CampzoneAppId = "campzone"
        const val AndroidPlatform = "android"
        const val FcmProvider = "fcm"
    }
}

private fun List<ProgramReminderPlan>.toJsonArray(): JSONArray =
    JSONArray().apply {
        forEach { reminder ->
            put(
                JSONObject()
                    .put("appID", reminder.appId)
                    .put("id", reminder.id)
                    .put("campingID", reminder.campingId)
                    .put("programID", reminder.programId)
                    .put("title", reminder.title)
                    .put("body", reminder.body)
                    .put("fireDate", reminder.fireDate.toIso8601String())
                    .put("targetTopic", reminder.targetTopic),
            )
        }
    }

private fun Date.toIso8601String(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(this)

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationApiBindings {
    @Binds
    abstract fun bindNotificationApi(impl: BackendNotificationApi): NotificationApi
}
