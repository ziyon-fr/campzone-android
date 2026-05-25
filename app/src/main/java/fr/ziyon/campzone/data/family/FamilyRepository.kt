package fr.ziyon.campzone.data.family

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A participant already on file matching the new entry by normalized name + age,
 * surfaced before saving so the guardian can confirm. Mirrors iOS
 * `FamilyParticipantDuplicateMatch`.
 */
data class FamilyParticipantDuplicateMatch(
    val existing: ChildParticipant,
    val guardianDisplayName: String,
)

interface FamilyRepository {
    suspend fun loadChildren(userId: String): List<ChildParticipant>
    suspend fun saveChild(child: ChildParticipant, userId: String): ChildParticipant
    suspend fun deleteChild(id: String, userId: String)

    /**
     * Best-effort lookup for the same participant under a *different* guardian
     * (collection-group read; needs the `children (displayName, age)` index and
     * adult/admin rules). Callers treat a thrown error as "no match".
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

        val snapshot = firestore
            .collectionGroup(ChildrenCollection)
            .whereEqualTo("displayName", trimmed)
            .whereEqualTo("age", age)
            .limit(10)
            .get()
            .await()

        for (document in snapshot.documents) {
            val participant = document.data?.toChildParticipantOrNull(documentId = document.id)
                ?: continue
            if (participant.guardianId == excludingGuardianId) continue

            return FamilyParticipantDuplicateMatch(
                existing = participant,
                guardianDisplayName = guardianDisplayName(participant.guardianId),
            )
        }
        return null
    }

    private suspend fun guardianDisplayName(uid: String): String {
        val name = runCatching {
            firestore.collection(UsersCollection).document(uid).get().await()
                .getString("displayName")
                ?.trim()
        }.getOrNull()
        return name?.takeUnless { it.isBlank() } ?: AnotherGuardianFallback
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
