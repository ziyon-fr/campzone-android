package fr.ziyon.campzone.data.notifications

import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class BadgeNotificationRequest(
    val recipientUserId: String,
    val recipientDisplayName: String?,
    val recipientPhotoUrl: String?,
    val achievementId: String,
    val achievementTitle: String,
    val campingId: String?,
    val awardedByUserId: String?,
)

interface BadgeNotificationDispatcher {
    suspend fun dispatchBadgeAward(request: BadgeNotificationRequest)
}

@Singleton
class BackendBadgeNotificationDispatcher @Inject constructor(
    private val auth: FirebaseAuth,
) : BadgeNotificationDispatcher {

    override suspend fun dispatchBadgeAward(request: BadgeNotificationRequest) {
        val token = auth.currentUser?.getIdToken(false)?.await()?.token ?: return

        val body = JSONObject()
            .put("appID", CampzoneAppId)
            .put("recipientUserID", request.recipientUserId)
            .put("achievementID", request.achievementId)
            .put("achievementTitle", request.achievementTitle)
        request.recipientDisplayName?.trim()?.takeUnless { it.isBlank() }
            ?.let { body.put("recipientDisplayName", it) }
        request.recipientPhotoUrl?.trim()?.takeUnless { it.isBlank() }
            ?.let { body.put("recipientPhotoURLString", it) }
        request.campingId?.trim()?.takeUnless { it.isBlank() }
            ?.let { body.put("campingID", it) }
        request.awardedByUserId?.trim()?.takeUnless { it.isBlank() }
            ?.let { body.put("awardedByUserID", it) }

        withContext(Dispatchers.IO) {
            val connection = (
                URL("${BuildConfig.BACKEND_BASE_URL}/notifications/dispatch/badge")
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
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException(response.ifBlank { "Badge notification dispatch failed." })
            }
        }
    }

    private companion object {
        const val CampzoneAppId = "campzone"
    }
}

class FakeBadgeNotificationDispatcher : BadgeNotificationDispatcher {
    val dispatched = mutableListOf<BadgeNotificationRequest>()
    var shouldFail = false

    override suspend fun dispatchBadgeAward(request: BadgeNotificationRequest) {
        if (shouldFail) throw IllegalStateException("Fake badge dispatcher configured to fail.")
        dispatched.add(request)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BadgeNotificationBindings {
    @Binds
    @Singleton
    abstract fun bindBadgeNotificationDispatcher(
        impl: BackendBadgeNotificationDispatcher,
    ): BadgeNotificationDispatcher
}
