package fr.ziyon.campzone.data.polls

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
import org.json.JSONObject

/** Poll lifecycle event for `POST /notifications/dispatch/poll`. */
enum class PollDispatchEvent(val wireValue: String) {
    Created("created"),
    Closed("closed"),
    Reopened("reopened"),
}

data class PollNotificationRequest(
    val campingId: String,
    val pollId: String,
    val question: String,
    val event: PollDispatchEvent,
)

interface PollNotificationDispatcher {
    suspend fun dispatchPoll(request: PollNotificationRequest)
}

@Singleton
class BackendPollNotificationDispatcher @Inject constructor(
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
) : PollNotificationDispatcher {

    override suspend fun dispatchPoll(request: PollNotificationRequest) {
        val token = auth.currentUser?.getIdToken(false)?.await()?.token ?: return

        val titleRes = when (request.event) {
            PollDispatchEvent.Created -> R.string.poll_dispatch_title_created
            PollDispatchEvent.Closed -> R.string.poll_dispatch_title_closed
            PollDispatchEvent.Reopened -> R.string.poll_dispatch_title_reopened
        }
        val bodyRes = when (request.event) {
            PollDispatchEvent.Created -> R.string.poll_dispatch_body_created
            PollDispatchEvent.Closed -> R.string.poll_dispatch_body_closed
            PollDispatchEvent.Reopened -> R.string.poll_dispatch_body_reopened
        }
        val body = JSONObject()
            .put("appID", CampzoneAppId)
            .put("campingID", request.campingId)
            .put("pollID", request.pollId)
            .put("event", request.event.wireValue)
            .put("title", context.getString(titleRes, request.question))
            .put("body", context.getString(bodyRes))

        withContext(Dispatchers.IO) {
            val connection = (
                URL("${BuildConfig.BACKEND_BASE_URL}/notifications/dispatch/poll")
                    .openConnection() as HttpURLConnection
                ).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException(response.ifBlank { "Poll notification dispatch failed." })
            }
        }
    }

    private companion object {
        const val CampzoneAppId = "campzone"
    }
}

class FakePollNotificationDispatcher : PollNotificationDispatcher {
    val dispatched = mutableListOf<PollNotificationRequest>()
    var shouldFail = false

    override suspend fun dispatchPoll(request: PollNotificationRequest) {
        if (shouldFail) throw IllegalStateException("Fake poll dispatcher configured to fail.")
        dispatched.add(request)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PollNotificationBindings {
    @Binds
    @Singleton
    abstract fun bindPollNotificationDispatcher(
        impl: BackendPollNotificationDispatcher,
    ): PollNotificationDispatcher
}
