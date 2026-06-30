package fr.ziyon.campzone.data.family

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fr.ziyon.campzone.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A participant already on file matching the new entry by normalized name + age,
 * surfaced before saving so the guardian can confirm. Mirrors iOS
 * `FamilyParticipantDuplicateMatch`.
 */
data class FamilyParticipantDuplicateMatch(
    val displayName: String,
    val age: Int,
    val guardianDisplayName: String,
)

interface FamilyRepository {
    suspend fun loadChildren(userId: String): List<ChildParticipant>
    suspend fun saveChild(child: ChildParticipant, userId: String): ChildParticipant
    suspend fun deleteChild(id: String, userId: String)

    /**
     * Privacy-preserving backend lookup for the same participant under a
     * different guardian. Only the fields required by the warning are returned.
     */
    suspend fun findSimilarParticipant(
        displayName: String,
        age: Int,
        excludingGuardianId: String,
    ): FamilyParticipantDuplicateMatch?
}

@Singleton
class FirebaseFamilyRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : FamilyRepository {
    override suspend fun loadChildren(userId: String): List<ChildParticipant> =
        childrenCollection(userId)
            .orderBy("displayName")
            .get()
            .await()
            .documents
            .mapNotNull { document ->
                document.data?.toChildParticipantOrNull(documentId = document.id)
            }

    override suspend fun saveChild(child: ChildParticipant, userId: String): ChildParticipant {
        val savedChild = child.copy(guardianId = userId)
        val document = childrenCollection(userId).document(savedChild.id)
        val snapshot = document.get().await()

        document
            .set(
                ChildParticipantPayload.childPayload(
                    child = savedChild,
                    serverTimestamp = FieldValue.serverTimestamp(),
                    deleteField = FieldValue.delete(),
                    includeCreatedAt = !snapshot.exists(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
            .await()

        return savedChild
    }

    override suspend fun deleteChild(id: String, userId: String) {
        childrenCollection(userId).document(id).delete().await()
    }

    override suspend fun findSimilarParticipant(
        displayName: String,
        age: Int,
        excludingGuardianId: String,
    ): FamilyParticipantDuplicateMatch? {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty()) return null

        val token = auth.currentUser?.getIdToken(false)?.await()?.token
            ?: error("Sign in again before checking family participants.")
        return withContext(Dispatchers.IO) {
            val connection = (
                URL("${BuildConfig.BACKEND_BASE_URL}/family/duplicate-check")
                    .openConnection() as HttpURLConnection
                ).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
            }
            val body = JSONObject()
                .put("displayName", trimmed)
                .put("age", age)
                .put("excludingGuardianID", excludingGuardianId)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val success = connection.responseCode in 200..299
            val stream = if (success) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (!success) error(
                familyBackendErrorMessage(
                    response = response,
                    fallback = "Could not check for an existing participant.",
                ),
            )

            val match = JSONObject(response).optJSONObject("data")?.optJSONObject("match")
                ?: return@withContext null
            FamilyParticipantDuplicateMatch(
                displayName = match.optString("displayName", trimmed),
                age = match.optInt("age", age),
                guardianDisplayName = match.optString("guardianDisplayName", AnotherGuardianFallback),
            )
        }
    }

    private fun childrenCollection(userId: String) =
        firestore
            .collection(UsersCollection)
            .document(userId)
            .collection(ChildrenCollection)

    private companion object {
        const val UsersCollection = "users"
        const val ChildrenCollection = "children"
        const val AnotherGuardianFallback = "another guardian"
    }
}

internal fun normalizeFamilyParticipantName(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .trim()
        .replace(Regex("\\s+"), " ")
        .lowercase()

internal fun familyBackendErrorMessage(response: String, fallback: String): String =
    BackendMessageRegex.find(response)
        ?.groupValues
        ?.getOrNull(1)
        ?.let(::decodeJsonStringContent)
        ?.takeUnless { it.isBlank() }
        ?: response.trim().takeUnless { it.isBlank() || it.startsWith("{") || it.startsWith("[") }
        ?: fallback

private val BackendMessageRegex = Regex(""""message"\s*:\s*"((?:\\.|[^"\\])*)"""")

private fun decodeJsonStringContent(value: String): String = buildString(value.length) {
    var index = 0
    while (index < value.length) {
        val character = value[index++]
        if (character != '\\' || index >= value.length) {
            append(character)
            continue
        }

        when (val escaped = value[index++]) {
            '"', '\\', '/' -> append(escaped)
            'b' -> append('\b')
            'f' -> append('\u000C')
            'n' -> append('\n')
            'r' -> append('\r')
            't' -> append('\t')
            'u' -> {
                val end = (index + 4).coerceAtMost(value.length)
                val codePoint = value.substring(index, end).takeIf { it.length == 4 }?.toIntOrNull(16)
                if (codePoint != null) {
                    append(codePoint.toChar())
                    index = end
                } else {
                    append("\\u")
                }
            }
            else -> append(escaped)
        }
    }
}
