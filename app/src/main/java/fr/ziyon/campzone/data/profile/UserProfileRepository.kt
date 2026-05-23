package fr.ziyon.campzone.data.profile

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

interface UserProfileRepository {
    suspend fun fetchUser(uid: String, fallback: AuthenticatedUser): UserProfile
    suspend fun saveUser(user: UserProfile): UserProfile
    suspend fun uploadProfilePhoto(
        user: UserProfile,
        bytes: ByteArray,
        mimeType: String,
        fileExtension: String,
    ): UserProfile
    suspend fun requestAccountDeletion(uid: String): UserProfile
    suspend fun cancelAccountDeletion(uid: String): UserProfile
}

@Singleton
class FirebaseUserProfileRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val avatarUploader: CloudinaryAvatarUploader,
) : UserProfileRepository {
    override suspend fun fetchUser(uid: String, fallback: AuthenticatedUser): UserProfile {
        val snapshot = userDocument(uid).get().await()
        val data = snapshot.data ?: return UserProfile.empty(from = fallback)
        return data.toUserProfile(documentId = snapshot.id, fallback = fallback)
    }

    override suspend fun saveUser(user: UserProfile): UserProfile {
        val savedUser = UserProfilePayload.cleaned(user)
        userDocument(savedUser.uid)
            .set(
                UserProfilePayload.userMergePayload(
                    user = savedUser,
                    serverTimestamp = serverTimestamp(),
                    deleteField = deleteField(),
                ),
                SetOptions.merge(),
            )
            .await()

        propagateProfileReferences(savedUser)
        return savedUser
    }

    override suspend fun uploadProfilePhoto(
        user: UserProfile,
        bytes: ByteArray,
        mimeType: String,
        fileExtension: String,
    ): UserProfile {
        val upload = avatarUploader.uploadAvatar(
            uid = user.uid,
            bytes = bytes,
            mimeType = mimeType,
            fileExtension = fileExtension,
        )
        return saveUser(
            user.copy(
                photoUrl = upload.secureUrl,
                photoPublicId = upload.publicId,
                onboardingCompleted = true,
            ),
        )
    }

    override suspend fun requestAccountDeletion(uid: String): UserProfile {
        val now = Timestamp.now()
        userDocument(uid)
            .set(
                UserProfilePayload.accountDeletionPayload(
                    uid = uid,
                    pendingDeletionAt = now,
                    serverTimestamp = serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()

        return readUser(uid).copy(pendingDeletionAt = now.toDate())
    }

    override suspend fun cancelAccountDeletion(uid: String): UserProfile {
        userDocument(uid)
            .set(
                UserProfilePayload.cancelAccountDeletionPayload(
                    deleteField = deleteField(),
                    serverTimestamp = serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()

        return readUser(uid).copy(pendingDeletionAt = null)
    }

    private suspend fun readUser(uid: String): UserProfile {
        val snapshot = userDocument(uid).get().await()
        return snapshot.data.orEmpty().toUserProfile(documentId = snapshot.id, fallbackUid = uid)
    }

    private fun userDocument(uid: String) =
        firestore.collection(UsersCollection).document(uid)

    private suspend fun propagateProfileReferences(user: UserProfile) {
        runCatching { updateRegistrationReferences(user) }
        runCatching { updateTeamMemberReferences(user) }
        runCatching { updateCheckInReferences(user) }
        runCatching { updateChatReferences(user) }
        runCatching { updateAnnouncementReferences(user) }
        runCatching { updatePollReferences(user) }
    }

    private suspend fun updateRegistrationReferences(user: UserProfile) {
        val snapshot = firestore
            .collectionGroup(RegistrationsCollection)
            .whereEqualTo("userID", user.uid)
            .get()
            .await()

        val payload = UserProfilePayload.withUpdatedAt(
            payload = UserProfilePayload.attendeeProfilePayload(
                user = user,
                deleteField = deleteField(),
            ),
            serverTimestamp = serverTimestamp(),
        )

        var batch = firestore.batch()
        var writes = 0
        for (document in snapshot.documents) {
            batch.set(document.reference, payload, SetOptions.merge())
            writes += 1

            val campingDocument = document.reference.parent.parent
            if (campingDocument != null) {
                batch.set(
                    campingDocument,
                    mapOf("updatedAt" to serverTimestamp()),
                    SetOptions.merge(),
                )
                writes += 1
            }

            if (writes >= FirestoreBatchFlushSize) {
                batch.commit().await()
                batch = firestore.batch()
                writes = 0
            }
        }
        batch.commitIfNeeded(writes)
    }

    private suspend fun updateTeamMemberReferences(user: UserProfile) {
        val snapshot = firestore
            .collectionGroup(TeamsCollection)
            .get()
            .await()

        var batch = firestore.batch()
        var writes = 0
        for (document in snapshot.documents) {
            val members = document.data?.get("members").toStringMapList()
            if (members.isEmpty()) continue

            val rewrite = UserProfilePayload.rewriteTeamMembers(
                members = members,
                user = user,
            )
            if (!rewrite.didChange) continue

            batch.set(
                document.reference,
                mapOf(
                    "members" to rewrite.members,
                    "updatedAt" to serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            writes += 1

            if (writes >= FirestoreBatchFlushSize) {
                batch.commit().await()
                batch = firestore.batch()
                writes = 0
            }
        }
        batch.commitIfNeeded(writes)
    }

    private suspend fun updateCheckInReferences(user: UserProfile) {
        val snapshot = firestore
            .collectionGroup(CheckInsCollection)
            .whereEqualTo("userID", user.uid)
            .get()
            .await()

        updateProfileDocuments(
            documents = snapshot.documents.filterIsInstance<QueryDocumentSnapshot>(),
            payload = UserProfilePayload.participantProfilePayload(
                user = user,
                deleteField = deleteField(),
            ),
        )
    }

    private suspend fun updateChatReferences(user: UserProfile) {
        val snapshot = firestore
            .collectionGroup(ChatCollection)
            .whereEqualTo("senderID", user.uid)
            .get()
            .await()

        updateProfileDocuments(
            documents = snapshot.documents.filterIsInstance<QueryDocumentSnapshot>(),
            payload = UserProfilePayload.chatProfilePayload(
                user = user,
                deleteField = deleteField(),
            ),
        )
    }

    private suspend fun updateAnnouncementReferences(user: UserProfile) {
        val snapshot = firestore
            .collection(AnnouncementsCollection)
            .whereEqualTo("authorID", user.uid)
            .get()
            .await()

        updateProfileDocuments(
            documents = snapshot.documents.filterIsInstance<QueryDocumentSnapshot>(),
            payload = UserProfilePayload.announcementProfilePayload(
                user = user,
                deleteField = deleteField(),
            ),
        )
    }

    private suspend fun updatePollReferences(user: UserProfile) {
        val snapshot = firestore
            .collectionGroup(PollsCollection)
            .whereEqualTo("createdByID", user.uid)
            .get()
            .await()

        updateProfileDocuments(
            documents = snapshot.documents.filterIsInstance<QueryDocumentSnapshot>(),
            payload = UserProfilePayload.pollProfilePayload(user),
        )
    }

    private suspend fun updateProfileDocuments(
        documents: List<QueryDocumentSnapshot>,
        payload: Map<String, Any?>,
    ) {
        if (documents.isEmpty()) return

        val payloadWithUpdatedAt = UserProfilePayload.withUpdatedAt(
            payload = payload,
            serverTimestamp = serverTimestamp(),
        )
        var batch = firestore.batch()
        var writes = 0
        for (document in documents) {
            batch.set(document.reference, payloadWithUpdatedAt, SetOptions.merge())
            writes += 1

            if (writes >= FirestoreBatchFlushSize) {
                batch.commit().await()
                batch = firestore.batch()
                writes = 0
            }
        }
        batch.commitIfNeeded(writes)
    }

    private suspend fun WriteBatch.commitIfNeeded(writes: Int) {
        if (writes > 0) commit().await()
    }

    private fun Any?.toStringMapList(): List<Map<String, Any?>> =
        (this as? List<*>)
            ?.mapNotNull { value ->
                (value as? Map<*, *>)
                    ?.mapNotNull { (key, mapValue) ->
                        (key as? String)?.let { it to mapValue }
                    }
                    ?.toMap()
            }
            .orEmpty()

    private fun serverTimestamp(): Any =
        com.google.firebase.firestore.FieldValue.serverTimestamp()

    private fun deleteField(): Any =
        com.google.firebase.firestore.FieldValue.delete()

    private companion object {
        const val UsersCollection = "users"
        const val RegistrationsCollection = "registrations"
        const val TeamsCollection = "teams"
        const val CheckInsCollection = "checkIns"
        const val ChatCollection = "chat"
        const val AnnouncementsCollection = "announcements"
        const val PollsCollection = "polls"
        const val FirestoreBatchFlushSize = 450
    }
}
