package fr.ziyon.campzone.data.teams

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

enum class TeamNotificationEvent(val wireValue: String) {
    Created("created"),
    Updated("updated"),
    MemberAssigned("memberAssigned"),
    MemberRemoved("memberRemoved"),
    MemberRoleUpdated("memberRoleUpdated"),
    ScoreChanged("scoreChanged"),
    MemberScoreChanged("memberScoreChanged"),
    PenaltyApplied("penaltyApplied"),
}

data class TeamNotificationRequest(
    val campingId: String,
    val teamId: String,
    val teamName: String,
    val event: TeamNotificationEvent,
    val body: String,
    val title: String = event.defaultTitle,
    val memberId: String? = null,
    val memberName: String? = null,
    val pointsDelta: Int? = null,
    val reason: String? = null,
)

interface TeamNotificationDispatcher {
    suspend fun dispatchTeamUpdate(request: TeamNotificationRequest)
}

@Singleton
class BackendTeamNotificationDispatcher @Inject constructor(
    private val auth: FirebaseAuth,
) : TeamNotificationDispatcher {

    override suspend fun dispatchTeamUpdate(request: TeamNotificationRequest) {
        val token = auth.currentUser?.getIdToken(false)?.await()?.token ?: return

        val body = JSONObject()
            .put("appID", CampzoneAppId)
            .put("campingID", request.campingId)
            .put("teamID", request.teamId)
            .put("teamName", request.teamName)
            .put("event", request.event.wireValue)
            .put("title", request.title)
            .put("body", request.body)
        request.memberId?.trim()?.takeUnless { it.isBlank() }?.let { body.put("memberID", it) }
        request.memberName?.trim()?.takeUnless { it.isBlank() }?.let { body.put("memberName", it) }
        request.pointsDelta?.let { body.put("pointsDelta", it) }
        request.reason?.trim()?.takeUnless { it.isBlank() }?.let { body.put("reason", it) }

        withContext(Dispatchers.IO) {
            val connection = (
                URL("${BuildConfig.BACKEND_BASE_URL}/notifications/dispatch/team")
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
                throw IllegalStateException(response.ifBlank { "Team notification dispatch failed." })
            }
        }
    }

    private companion object {
        const val CampzoneAppId = "campzone"
    }
}

class FakeTeamNotificationDispatcher : TeamNotificationDispatcher {
    val dispatched = mutableListOf<TeamNotificationRequest>()
    var shouldFail = false

    override suspend fun dispatchTeamUpdate(request: TeamNotificationRequest) {
        if (shouldFail) throw IllegalStateException("Fake team dispatcher configured to fail.")
        dispatched.add(request)
    }
}

private val TeamNotificationEvent.defaultTitle: String
    get() = when (this) {
        TeamNotificationEvent.Created -> "Team created"
        TeamNotificationEvent.Updated -> "Team updated"
        TeamNotificationEvent.MemberAssigned -> "New team member"
        TeamNotificationEvent.MemberRemoved -> "Team member removed"
        TeamNotificationEvent.MemberRoleUpdated -> "Team role updated"
        TeamNotificationEvent.ScoreChanged -> "Team score updated"
        TeamNotificationEvent.MemberScoreChanged -> "Personal score updated"
        TeamNotificationEvent.PenaltyApplied -> "Team penalty applied"
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class TeamNotificationBindings {
    @Binds
    @Singleton
    abstract fun bindTeamNotificationDispatcher(
        impl: BackendTeamNotificationDispatcher,
    ): TeamNotificationDispatcher
}
