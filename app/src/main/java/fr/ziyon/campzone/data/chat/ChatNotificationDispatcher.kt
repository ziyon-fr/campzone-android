package fr.ziyon.campzone.data.chat

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.BuildConfig
import fr.ziyon.campzone.R
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Normal chat push: `POST /notifications/dispatch/chat`. */
data class ChatNotificationRequest(
    val campingId: String,
    val messageId: String,
    val senderId: String,
    val senderName: String,
    val body: String,
    val teamId: String? = null,
)

/**
 * Targeted @mention push: `POST /notifications/dispatch/chatMention`.
 * [mentionedUserIds] is already expanded from `@everyone` into concrete
 * recipients and excludes the sender.
 */
data class ChatMentionRequest(
    val campingId: String,
    val messageId: String,
    val senderId: String,
    val senderName: String,
    val body: String,
    val mentionedUserIds: List<String>,
    val isEveryoneMention: Boolean,
    val teamId: String? = null,
)

interface ChatNotificationDispatcher {
    suspend fun dispatchChatMessage(request: ChatNotificationRequest)
    suspend fun dispatchChatMention(request: ChatMentionRequest)
}

@Singleton
class BackendChatNotificationDispatcher @Inject constructor(
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
) : ChatNotificationDispatcher {

    override suspend fun dispatchChatMessage(request: ChatNotificationRequest) {
        val body = JSONObject()
            .put("appID", CampzoneAppId)
            .put("campingID", request.campingId)
            .put("messageID", request.messageId)
            .put("senderID", request.senderId)
            .put("senderName", request.senderName)
            .put("body", request.body)
        request.teamId?.trim()?.takeUnless { it.isBlank() }?.let { body.put("teamID", it) }
        post("dispatch/chat", body)
    }

    override suspend fun dispatchChatMention(request: ChatMentionRequest) {
        val title = if (request.isEveryoneMention) {
            context.getString(R.string.chat_mention_title_everyone, request.senderName)
        } else {
            context.getString(R.string.chat_mention_title_you, request.senderName)
        }
        val body = JSONObject()
            .put("appID", CampzoneAppId)
            .put("campingID", request.campingId)
            .put("messageID", request.messageId)
            .put("senderID", request.senderId)
            .put("senderName", request.senderName)
            .put("title", title)
            .put("body", request.body)
            .put("mentionedUserIDs", JSONArray(request.mentionedUserIds))
            .put("isEveryoneMention", request.isEveryoneMention)
        request.teamId?.trim()?.takeUnless { it.isBlank() }?.let { body.put("teamID", it) }
        post("dispatch/chatMention", body)
    }

    private suspend fun post(path: String, body: JSONObject) {
        val token = auth.currentUser
            ?.getIdToken(false)
            ?.await()
            ?.token
            ?: return

        withContext(Dispatchers.IO) {
            val connection = (
                URL("${BuildConfig.BACKEND_BASE_URL}/$path")
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
    val mentionDispatched = mutableListOf<ChatMentionRequest>()
    var shouldFail = false

    override suspend fun dispatchChatMessage(request: ChatNotificationRequest) {
        if (shouldFail) throw IllegalStateException("Fake dispatcher configured to fail.")
        dispatched.add(request)
    }

    override suspend fun dispatchChatMention(request: ChatMentionRequest) {
        if (shouldFail) throw IllegalStateException("Fake dispatcher configured to fail.")
        mentionDispatched.add(request)
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
