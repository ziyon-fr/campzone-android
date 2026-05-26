package fr.ziyon.campzone.data.announcements

import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.BuildConfig
import fr.ziyon.campzone.data.model.Announcement
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

interface AnnouncementNotificationDispatcher {
    suspend fun dispatchAnnouncement(announcement: Announcement)
}

@Singleton
class BackendAnnouncementNotificationDispatcher @Inject constructor(
    private val auth: FirebaseAuth,
) : AnnouncementNotificationDispatcher {

    override suspend fun dispatchAnnouncement(announcement: Announcement) {
        val token = auth.currentUser
            ?.getIdToken(false)
            ?.await()
            ?.token
            ?: return

        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("appID", CampzoneAppId)
                .put("announcementID", announcement.id)
                .put("title", announcement.title)
                .put("body", announcement.summary)
                .put("audienceScopeRawValue", announcement.audienceScopeRawValue)
                .put("campingID", announcement.campingId ?: "")
                .put("notificationTargetRoleRawValue", announcement.notificationTargetRole?.rawValue ?: "")

            val connection = (
                URL("${BuildConfig.BACKEND_BASE_URL}/notifications/dispatch/announcement")
                    .openConnection() as HttpURLConnection
                ).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
            }

            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val stream = if (connection.responseCode in 200..299) connection.inputStream
            else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException(response.ifBlank { "Announcement notification dispatch failed." })
            }
        }
    }

    private companion object {
        const val CampzoneAppId = "campzone"
    }
}

class FakeAnnouncementNotificationDispatcher : AnnouncementNotificationDispatcher {
    override suspend fun dispatchAnnouncement(announcement: Announcement) = Unit
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AnnouncementNotificationBindings {
    @Binds
    abstract fun bindAnnouncementNotificationDispatcher(
        impl: BackendAnnouncementNotificationDispatcher,
    ): AnnouncementNotificationDispatcher
}
