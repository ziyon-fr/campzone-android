package fr.ziyon.campzone.data.feedback

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.model.CampFeedback
import fr.ziyon.campzone.data.model.CampFeedbackPayload
import fr.ziyon.campzone.data.model.toCampFeedbackOrNull
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Persistence for post-camp survey responses at
 * `campings/{campingID}/feedback/{uid}` (`02-firestore-schema.md` §7.5). One
 * doc per participant (doc ID == submitting uid). Mirrors the iOS
 * `FeedbackServicing` (interface + Firestore + fake), with a hand-built
 * `Map<String, Any?>` payload so `submittedAt`/`updatedAt` stay
 * `serverTimestamp()` and the `overallRating` 1–5 RBAC constraint is honored.
 *
 * Reads are gated by the rules to the own doc OR `canViewParticipantProfiles`;
 * the survey loads only the caller's own doc, and the admin results view loads
 * the whole collection (call site gated `canManageAnyCamping`).
 */
interface FeedbackService {
    suspend fun loadMyFeedback(userId: String, campingId: String): CampFeedback?
    suspend fun submitFeedback(feedback: CampFeedback)
    suspend fun loadAllFeedback(campingId: String): List<CampFeedback>
}

class FirestoreFeedbackService @Inject constructor(
    private val db: FirebaseFirestore,
) : FeedbackService {

    override suspend fun loadMyFeedback(userId: String, campingId: String): CampFeedback? {
        val document = feedbackCollection(campingId).document(userId).get().await()
        if (!document.exists()) return null
        return document.data?.toCampFeedbackOrNull(document.id)
    }

    override suspend fun submitFeedback(feedback: CampFeedback) {
        val payload = CampFeedbackPayload.feedbackPayload(
            feedback = feedback,
            serverTimestamp = FieldValue.serverTimestamp(),
        )
        feedbackCollection(feedback.campingId)
            .document(feedback.id)
            .set(payload, SetOptions.merge())
            .await()
    }

    override suspend fun loadAllFeedback(campingId: String): List<CampFeedback> {
        val snapshot = feedbackCollection(campingId)
            .orderBy("submittedAt", Query.Direction.DESCENDING)
            .limit(MAX_RESPONSES)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.data?.toCampFeedbackOrNull(it.id) }
    }

    private fun feedbackCollection(campingId: String) =
        db.collection("campings").document(campingId).collection("feedback")

    private companion object {
        const val MAX_RESPONSES = 500L
    }
}

/** In-memory fake for previews and tests; mirrors the iOS `MockFeedbackService`. */
class FakeFeedbackService(
    initial: List<CampFeedback> = emptyList(),
    private var shouldFail: Boolean = false,
) : FeedbackService {

    private val store: MutableMap<String, CampFeedback> =
        initial.associateBy { it.id }.toMutableMap()

    /** Synchronous inspection hook for tests/previews (no coroutine needed). */
    fun peek(id: String): CampFeedback? = store[id]

    override suspend fun loadMyFeedback(userId: String, campingId: String): CampFeedback? {
        failIfNeeded()
        return store[userId]?.takeIf { it.campingId == campingId }
    }

    override suspend fun submitFeedback(feedback: CampFeedback) {
        failIfNeeded()
        store[feedback.id] = feedback.copy(submittedAt = Date(), updatedAt = Date())
    }

    override suspend fun loadAllFeedback(campingId: String): List<CampFeedback> {
        failIfNeeded()
        return store.values
            .filter { it.campingId == campingId }
            .sortedByDescending { it.submittedAt ?: Date(0) }
    }

    private fun failIfNeeded() {
        if (shouldFail) throw IllegalStateException("The fake feedback service was configured to fail.")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FeedbackBindings {
    @Binds
    @Singleton
    abstract fun bindFeedbackService(impl: FirestoreFeedbackService): FeedbackService
}
