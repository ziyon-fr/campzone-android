package fr.ziyon.campzone.data.profile

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

data class UserDataExportResult(
    val file: File,
    val generatedAt: Date,
    val recordCount: Int,
    val failureCount: Int,
)

private data class UserDataExportRecord(
    val path: String,
    val data: Map<String, Any?>,
)

@Singleton
class UserDataExportRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @param:ApplicationContext private val context: Context,
) {
    suspend fun exportData(user: AuthenticatedUser): UserDataExportResult {
        val recordsByPath = linkedMapOf<String, UserDataExportRecord>()
        val failures = mutableListOf<String>()

        suspend fun collectDocument(label: String, reference: DocumentReference) {
            runCatching {
                val snapshot = reference.get().await()
                val data = snapshot.data ?: return
                recordsByPath[reference.path] = UserDataExportRecord(reference.path, data)
            }.onFailure { error ->
                failures += "$label: ${error.message.orEmpty()}"
            }
        }

        suspend fun collectCollection(label: String, reference: CollectionReference) {
            runCatching {
                reference.get().await().documents.forEach { document ->
                    val data = document.data ?: return@forEach
                    recordsByPath[document.reference.path] = UserDataExportRecord(
                        path = document.reference.path,
                        data = data,
                    )
                }
            }.onFailure { error ->
                failures += "$label: ${error.message.orEmpty()}"
            }
        }

        suspend fun collectQuery(label: String, query: Query) {
            runCatching {
                query.get().await().documents.forEach { document ->
                    val data = document.data ?: return@forEach
                    recordsByPath[document.reference.path] = UserDataExportRecord(
                        path = document.reference.path,
                        data = data,
                    )
                }
            }.onFailure { error ->
                failures += "$label: ${error.message.orEmpty()}"
            }
        }

        suspend fun collectCollectionGroup(
            label: String,
            collectionId: String,
            field: String,
            value: String,
        ) {
            collectQuery(
                label = label,
                query = firestore
                    .collectionGroup(collectionId)
                    .whereEqualTo(field, value),
            )
        }

        val userDocument = firestore.collection(UsersCollection).document(user.uid)
        collectDocument("profile", userDocument)
        collectCollection("notification settings", userDocument.collection(NotificationSettingsCollection))
        collectCollection("notification tokens", userDocument.collection(NotificationTokensCollection))
        collectCollection("children", userDocument.collection(ChildrenCollection))
        collectCollection("badges", userDocument.collection(BadgesCollection))
        collectCollection("blocked users", userDocument.collection(BlockedUsersCollection))

        collectCollectionGroup("registrations by userID", RegistrationsCollection, Field.UserId, user.uid)
        collectCollectionGroup("registrations by uid", RegistrationsCollection, Field.Uid, user.uid)
        collectCollectionGroup("family registrations", RegistrationsCollection, Field.GuardianId, user.uid)
        collectCollectionGroup("check-ins", CheckInsCollection, Field.UserId, user.uid)
        collectCollectionGroup("transportation bookings", TransportationBookingsCollection, Field.UserId, user.uid)
        collectCollectionGroup("family transportation bookings", TransportationBookingsCollection, Field.GuardianId, user.uid)
        collectCollectionGroup("chat messages", ChatCollection, Field.SenderId, user.uid)
        collectCollectionGroup("poll votes", VotesCollection, Field.VoterId, user.uid)
        collectCollectionGroup("feedback", FeedbackCollection, Field.UserId, user.uid)
        collectCollectionGroup("album media", MediaCollection, Field.UploaderId, user.uid)
        collectCollectionGroup("activities", ActivitiesCollection, Field.CreatedBy, user.uid)
        collectCollectionGroup("polls authored", PollsCollection, Field.CreatedById, user.uid)
        collectQuery(
            "announcements authored",
            firestore.collection(AnnouncementsCollection).whereEqualTo(Field.AuthorId, user.uid),
        )
        collectQuery(
            "content reports",
            firestore.collection(ContentReportsCollection).whereEqualTo(Field.ReporterId, user.uid),
        )

        val generatedAt = Date()
        val records = recordsByPath.values.sortedBy { it.path }
        val payload = makePayload(
            user = user,
            records = records,
            failures = failures.sorted(),
            generatedAt = generatedAt,
        )
        val file = writePayload(
            payload = payload,
            userId = user.uid,
            generatedAt = generatedAt,
        )

        return UserDataExportResult(
            file = file,
            generatedAt = generatedAt,
            recordCount = records.size,
            failureCount = failures.size,
        )
    }

    private fun makePayload(
        user: AuthenticatedUser,
        records: List<UserDataExportRecord>,
        failures: List<String>,
        generatedAt: Date,
    ): JSONObject =
        JSONObject()
            .put("format", "campzone-user-data-export-v1")
            .put("generatedAt", isoString(generatedAt))
            .put(
                "user",
                JSONObject()
                    .put("uid", user.uid)
                    .put("email", user.email)
                    .put("displayName", user.displayName)
                    .put("role", user.role.rawValue),
            )
            .put("recordCount", records.size)
            .put(
                "records",
                JSONArray().apply {
                    records.forEach { record ->
                        put(
                            JSONObject()
                                .put("path", record.path)
                                .put("data", sanitize(record.data)),
                        )
                    }
                },
            )
            .put(
                "failures",
                JSONArray().apply {
                    failures.forEach(::put)
                },
            )

    private fun writePayload(
        payload: JSONObject,
        userId: String,
        generatedAt: Date,
    ): File {
        val safeUserId = userId.map { character ->
            if (character.isLetterOrDigit() || character == '-' || character == '_') {
                character
            } else {
                '-'
            }
        }.joinToString("")
        val dateStamp = fileDateFormatter().format(generatedAt)
        val file = File(context.cacheDir, "campzone-data-export-$safeUserId-$dateStamp.json")
        file.writeText(payload.toString(2))
        return file
    }

    private fun sanitize(value: Any?): Any =
        when (value) {
            null -> JSONObject.NULL
            is Timestamp -> isoString(value.toDate())
            is Date -> isoString(value)
            is DocumentReference -> value.path
            is GeoPoint -> JSONObject()
                .put("latitude", value.latitude)
                .put("longitude", value.longitude)
            is Map<*, *> -> JSONObject().apply {
                value.forEach { (key, mapValue) ->
                    if (key is String) put(key, sanitize(mapValue))
                }
            }
            is Iterable<*> -> JSONArray().apply {
                value.forEach { put(sanitize(it)) }
            }
            is Array<*> -> JSONArray().apply {
                value.forEach { put(sanitize(it)) }
            }
            is String,
            is Boolean,
            is Number,
            -> value
            else -> value.toString()
        }

    private fun isoString(date: Date): String = isoDateFormatter().format(date)

    private fun isoDateFormatter(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    private fun fileDateFormatter(): SimpleDateFormat =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    private object Field {
        const val Uid = "uid"
        const val UserId = "userID"
        const val GuardianId = "guardianID"
        const val SenderId = "senderID"
        const val AuthorId = "authorID"
        const val UploaderId = "uploaderID"
        const val CreatedBy = "createdBy"
        const val CreatedById = "createdByID"
        const val ReporterId = "reporterID"
        const val VoterId = "voterID"
    }

    private companion object {
        const val UsersCollection = "users"
        const val NotificationSettingsCollection = "notificationSettings"
        const val NotificationTokensCollection = "notificationTokens"
        const val ChildrenCollection = "children"
        const val BadgesCollection = "badges"
        const val BlockedUsersCollection = "blockedUsers"
        const val RegistrationsCollection = "registrations"
        const val CheckInsCollection = "checkIns"
        const val TransportationBookingsCollection = "transportationBookings"
        const val ChatCollection = "chat"
        const val VotesCollection = "votes"
        const val FeedbackCollection = "feedback"
        const val MediaCollection = "media"
        const val ActivitiesCollection = "activities"
        const val PollsCollection = "polls"
        const val AnnouncementsCollection = "announcements"
        const val ContentReportsCollection = "contentReports"
    }
}
