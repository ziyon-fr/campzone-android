package fr.ziyon.campzone.data.chat

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

data class ChatNotificationRequest(
    val campingId: String,
    val messageId: String,
    val teamId: String? = null,
)

interface ChatNotificationDispatcher {
    suspend fun dispatchChatMessage(request: ChatNotificationRequest)
}

@Singleton
class BackendChatNotificationDispatcher @Inject constructor(
    private val auth: FirebaseAuth,
) : ChatNotificationDispatcher {

    override suspend fun dispatchChatMessage(request: ChatNotificationRequest) {
        val token = auth.currentUser
            ?.getIdToken(false)
            ?.await()
            ?.token
            ?: return

        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("appID", CampzoneAppId)
                .put("campingID", request.campingId)
                .put("messageID", request.messageId)
            request.teamId?.trim()?.takeUnless { it.isBlank() }?.let {
                body.put("teamID", it)
            }

            val connection = (
                URL("${BuildConfig.BACKEND_BASE_URL}/notifications/dispatch/chat")
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
                throw IllegalStateException(response.ifBlank { "Chat notification dispatch failed." })
            }
        }
    }

    private companion object {
        const val CampzoneAppId = "campzone"
    }
}

class FakeChatNotificationDispatcher : ChatNotificationDispatcher {
    val dispatched = mutableListOf<ChatNotificationRequest>()
    var shouldFail = false

    override suspend fun dispatchChatMessage(request: ChatNotificationRequest) {
        if (shouldFail) throw IllegalStateException("Fake dispatcher configured to fail.")
        dispatched.add(request)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatNotificationBindings {
    @Binds
    @Singleton
    abstract fun bindChatNotificationDispatcher(
        impl: BackendChatNotificationDispatcher,
    ): ChatNotificationDispatcher
}
