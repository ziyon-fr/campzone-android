package fr.ziyon.campzone.data.camping

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingPayload
import fr.ziyon.campzone.data.model.toCampingAttendeeOrNull
import fr.ziyon.campzone.data.model.toCampingOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await

/**
 * Read/write access to `campings/{id}` and its `registrations` subcollection
 * (`02-firestore-schema.md` §3). Mirrors the iOS `CampingServicing` contract.
 * All (de)serialization goes through the hand-mapped [Camping] / [CampingAttendee]
 * helpers — no POJO auto-mapping.
 */
interface CampingService {
    /** Live `campings` collection ordered by `startDate` (camping docs only, no attendees). */
    fun observeCampings(): Flow<List<Camping>>

    /** One-shot fetch of a single camping document. */
    suspend fun fetchCamping(id: String): Camping

    /**
     * Loads the `registrations` subcollection. Falls back to the caller's own
     * registration + the children they registered when the blanket list query
     * is denied by RBAC (mirrors the iOS fallback).
     */
    suspend fun loadAttendees(campingId: String): List<CampingAttendee>

    /** Create/update via the hand-built payload; stamps creator signature on first create. */
    suspend fun saveCamping(camping: Camping): Camping

    /** Writes only `{ registrationStatus: "cancelled", updatedAt }`. */
    suspend fun cancelCamping(id: String): Camping

    /** Hard delete (RBAC: creator or privileged manager). */
    suspend fun deleteCamping(id: String)

    /** Guidelines update path — writes only `{ guidelines, updatedAt }`. */
    suspend fun updateGuidelines(campingId: String, body: String): Camping
}

@Singleton
class FirebaseCampingService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : CampingService {

    override fun observeCampings(): Flow<List<Camping>> = callbackFlow {
        val registration = campings()
            .orderBy("startDate")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val campings = snapshot?.documents
                    ?.mapNotNull { document -> document.data?.toCampingOrNull(document.id) }
                    .orEmpty()
                trySend(campings)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun fetchCamping(id: String): Camping {
        val snapshot = campings().document(id).get().await()
        return snapshot.data?.toCampingOrNull(snapshot.id)
            ?: error("Camping could not be found.")
    }

    override suspend fun loadAttendees(campingId: String): List<CampingAttendee> {
        val registrations = campings().document(campingId).collection(Registrations)
        return try {
            registrations
                .orderBy("displayName")
                .get()
                .await()
                .documents
                .mapNotNull { it.data?.toCampingAttendeeOrNull(it.id) }
        } catch (denied: FirebaseFirestoreException) {
            // Blanket list denied for non-leadership: fetch only what RBAC allows
            // (own self-registration + children registered by this guardian).
            val uid = auth.currentUser?.uid ?: return emptyList()
            val byId = linkedMapOf<String, CampingAttendee>()

            runCatching { registrations.document(uid).get().await() }
                .getOrNull()
                ?.let { it.data?.toCampingAttendeeOrNull(it.id) }
                ?.let { byId[it.id] = it }

            runCatching { registrations.whereEqualTo("userID", uid).get().await() }
                .getOrNull()
                ?.documents
                ?.mapNotNull { it.data?.toCampingAttendeeOrNull(it.id) }
                ?.forEach { byId[it.id] = it }

            runCatching { registrations.whereEqualTo("guardianID", uid).get().await() }
                .getOrNull()
                ?.documents
                ?.mapNotNull { it.data?.toCampingAttendeeOrNull(it.id) }
                ?.forEach { byId[it.id] = it }

            byId.values.sortedBy { it.displayName.lowercase() }
        }
    }

    override suspend fun saveCamping(camping: Camping): Camping {
        val document = campings().document(camping.id)
        val exists = document.get().await().exists()
        val stamped = if (!exists && camping.createdByUid.isNullOrBlank()) {
            camping.copy(
                createdByUid = auth.currentUser?.uid,
                createdByName = auth.currentUser?.displayName?.takeUnless { it.isBlank() },
            )
        } else {
            camping
        }
        document
            .set(
                CampingPayload.campingPayload(
                    camping = stamped,
                    serverTimestamp = FieldValue.serverTimestamp(),
                    deleteField = FieldValue.delete(),
                    includeCreatedAt = !exists,
                ),
                SetOptions.merge(),
            )
            .await()
        return fetchCamping(camping.id)
    }

    override suspend fun cancelCamping(id: String): Camping {
        campings().document(id)
            .set(CampingPayload.cancelPayload(FieldValue.serverTimestamp()), SetOptions.merge())
            .await()
        return fetchCamping(id)
    }

    override suspend fun deleteCamping(id: String) {
        campings().document(id).delete().await()
    }

    override suspend fun updateGuidelines(campingId: String, body: String): Camping {
        campings().document(campingId)
            .set(
                CampingPayload.guidelinesPayload(body, FieldValue.serverTimestamp()),
                SetOptions.merge(),
            )
            .await()
        return fetchCamping(campingId)
    }

    private fun campings() = firestore.collection(Campings)

    private companion object {
        const val Campings = "campings"
        const val Registrations = "registrations"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CampingBindings {
    @Binds
    abstract fun bindCampingService(service: FirebaseCampingService): CampingService
}
