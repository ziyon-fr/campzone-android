package fr.ziyon.campzone.data.notifications

import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.BuildConfig
import fr.ziyon.campzone.data.model.NotificationSettings
import java.net.HttpURLConnection
import java.net.URL
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

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationApiBindings {
    @Binds
    abstract fun bindNotificationApi(impl: BackendNotificationApi): NotificationApi
}
