package fr.ziyon.campzone.data.announcements

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.media.ImageUploader
import fr.ziyon.campzone.data.model.Announcement
import fr.ziyon.campzone.data.model.AnnouncementAttachment
import fr.ziyon.campzone.data.model.AnnouncementAttachmentKind
import fr.ziyon.campzone.data.model.AnnouncementAudienceScope
import fr.ziyon.campzone.data.model.AnnouncementDraft
import fr.ziyon.campzone.data.model.AnnouncementPayload
import fr.ziyon.campzone.data.model.PendingAnnouncementAttachment
import fr.ziyon.campzone.data.model.toAnnouncement
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface AnnouncementService {
    fun loadAnnouncements(): Flow<List<Announcement>>
    suspend fun saveAnnouncement(draft: AnnouncementDraft): Announcement
    suspend fun deleteAnnouncement(id: String, attachmentPaths: List<String>)
}

@Singleton
class FirestoreAnnouncementService @Inject constructor(
    private val db: FirebaseFirestore,
    private val imageUploader: ImageUploader,
) : AnnouncementService {

    override fun loadAnnouncements(): Flow<List<Announcement>> = callbackFlow {
        val listener = db.collection(Collection)
            .orderBy(Field.CreatedAt, Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val announcements = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.toAnnouncement(doc.id)
                } ?: emptyList()
                trySend(announcements)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun saveAnnouncement(draft: AnnouncementDraft): Announcement {
        val document = db.collection(Collection).document(draft.id)
        val snapshot = document.get().await()
        val uploadedAttachments = uploadAttachments(draft.pendingAttachments, draft.id)
        val attachments = draft.existingAttachments + uploadedAttachments

        val payload = AnnouncementPayload.draftPayload(
            draft = draft,
            serverTimestamp = com.google.firebase.firestore.FieldValue.serverTimestamp(),
            includeCreatedAt = !snapshot.exists(),
            attachments = attachments,
        )
        document.set(payload, com.google.firebase.firestore.SetOptions.merge()).await()

        val savedSnapshot = document.get().await()
        return savedSnapshot.data?.toAnnouncement(draft.id)
            ?: throw IllegalStateException("Announcement could not be saved.")
    }

    override suspend fun deleteAnnouncement(id: String, attachmentPaths: List<String>) {
        val orphans = attachmentPaths.filter { it.isNotBlank() }
        if (orphans.isNotEmpty()) {
            android.util.Log.d("AnnouncementService", "Cloudinary orphan public IDs for $id: ${orphans.joinToString()}")
        }
        db.collection(Collection).document(id).delete().await()
    }

    private suspend fun uploadAttachments(
        attachments: List<PendingAnnouncementAttachment>,
        announcementId: String,
    ): List<AnnouncementAttachment> {
        if (attachments.isEmpty()) return emptyList()
        return attachments.map { pending ->
            val ext = if (pending.kind == AnnouncementAttachmentKind.Image) "jpg" else "pdf"
            val result = imageUploader.uploadImage(
                assetIdPrefix = "announcement-${pending.kind.wireValue}",
                folder = "campzone/announcements/$announcementId",
                tags = listOf("campzone", "announcement", "announcement:$announcementId", pending.kind.wireValue),
                bytes = pending.bytes,
                mimeType = pending.contentType,
                fileExtension = ext,
            )
            AnnouncementAttachment(
                id = pending.id,
                kind = pending.kind,
                fileName = pending.fileName,
                contentType = pending.contentType,
                storagePath = result.publicId,
                downloadUrl = result.secureUrl,
            )
        }
    }

    private companion object {
        const val Collection = "announcements"

        object Field {
            const val CreatedAt = "createdAt"
        }
    }
}

class FakeAnnouncementService(
    private val announcements: MutableList<Announcement> = mutableListOf(
        Announcement(
            id = "packing-list",
            title = "Packing list published",
            body = "Leaders have published the **first equipment checklist** for summer camp.\n\n## What to bring\n\n- Clothing (5–7 days)\n- Bible study material\n- Reusable water bottle\n- Sleeping bag + pillow",
            authorId = "preview-admin",
            authorName = "Campzone Team",
            createdAt = Date(),
            updatedAt = Date(),
        ),
        Announcement(
            id = "travel-update",
            title = "Travel coordination",
            body = "Bus assignments and meeting points will be published here **once registration opens**.",
            audienceScopeRawValue = AnnouncementAudienceScope.Camping.rawValue,
            campingId = "preview-camping",
            campingTitle = "Summer Camp 2026",
            authorId = "preview-admin",
            authorName = "Campzone Team",
            createdAt = Date(System.currentTimeMillis() - 86400000),
            updatedAt = Date(System.currentTimeMillis() - 86400000),
        ),
    ),
) : AnnouncementService {
    override fun loadAnnouncements(): Flow<List<Announcement>> = kotlinx.coroutines.flow.flow {
        emit(announcements.sortedByDescending { it.createdAt?.time ?: 0 })
    }

    override suspend fun saveAnnouncement(draft: AnnouncementDraft): Announcement {
        val now = Date()
        val attachments = draft.existingAttachments + draft.pendingAttachments.map {
            AnnouncementAttachment(
                id = it.id,
                kind = it.kind,
                fileName = it.fileName,
                contentType = it.contentType,
                storagePath = "preview/${it.fileName}",
                downloadUrl = "",
            )
        }
        val saved = Announcement(
            id = draft.id,
            title = draft.title,
            body = draft.body,
            audienceScopeRawValue = draft.audienceScopeRawValue,
            campingId = draft.campingId,
            campingTitle = draft.campingTitle,
            notificationTargetRole = draft.notificationTargetRoleRawValue?.let {
                fr.ziyon.campzone.core.permissions.UserRole.fromWire(it)
            },
            authorId = draft.authorId,
            authorName = draft.authorName,
            authorPhotoUrl = draft.authorPhotoUrl,
            attachments = attachments,
            createdAt = announcements.firstOrNull { it.id == draft.id }?.createdAt ?: now,
            updatedAt = now,
        )
        announcements.removeAll { it.id == draft.id }
        announcements.add(saved)
        return saved
    }

    override suspend fun deleteAnnouncement(id: String, attachmentPaths: List<String>) {
        announcements.removeAll { it.id == id }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AnnouncementBindings {
    @Binds
    abstract fun bindAnnouncementService(impl: FirestoreAnnouncementService): AnnouncementService
}
