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

data class RegistrationNotificationRequest(
    val campingId: String,
    val campingTitle: String,
    val participantName: String,
    val requestedByName: String,
    val participantCount: Int,
) {
    val title: String = "New registration request"

    val body: String
        get() = if (participantCount > 1) {
            "$requestedByName requested to register $participantCount participants for $campingTitle. Review and approve."
        } else {
            "$requestedByName requested to register $participantName for $campingTitle. Review and approve."
        }
}

interface RegistrationNotificationDispatcher {
    suspend fun dispatchRegistrationRequest(request: RegistrationNotificationRequest)
}

@Singleton
class BackendRegistrationNotificationDispatcher @Inject constructor(
    private val auth: FirebaseAuth,
) : RegistrationNotificationDispatcher {

    override suspend fun dispatchRegistrationRequest(request: RegistrationNotificationRequest) {
        val token = auth.currentUser
            ?.getIdToken(false)
            ?.await()
            ?.token
            ?: return

        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("appID", CampzoneAppId)
                .put("campingID", request.campingId)
                .put("title", request.title)
                .put("body", request.body)
                .put("participantName", request.participantName)
                .put("requestedByName", request.requestedByName)
                .put("participantCount", request.participantCount)

            val connection = (
                URL("${BuildConfig.BACKEND_BASE_URL}/notifications/dispatch/registration")
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
                throw IllegalStateException(response.ifBlank { "Registration notification failed." })
            }
        }
    }

    private companion object {
        const val CampzoneAppId = "campzone"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RegistrationNotificationBindings {
    @Binds
    abstract fun bindRegistrationNotificationDispatcher(
        dispatcher: BackendRegistrationNotificationDispatcher,
    ): RegistrationNotificationDispatcher
}
