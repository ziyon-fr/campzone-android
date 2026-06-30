package fr.ziyon.campzone.data.safety

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.announcements.AnnouncementNotificationDispatcher
import fr.ziyon.campzone.data.announcements.AnnouncementService
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.Announcement
import fr.ziyon.campzone.data.model.AnnouncementAudienceScope
import fr.ziyon.campzone.data.model.AnnouncementDraft
import fr.ziyon.campzone.data.model.Camping
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

interface EmergencySafetyHubService {
    suspend fun load(camping: Camping): EmergencySafetyHub
    suspend fun save(campingId: String, hub: EmergencySafetyHub): EmergencySafetyHub
    suspend fun sendUrgentBroadcast(camping: Camping, author: AuthenticatedUser, title: String, body: String): Announcement
}

@Singleton
class FirestoreEmergencySafetyHubService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val announcementService: AnnouncementService,
    private val notificationDispatcher: AnnouncementNotificationDispatcher,
) : EmergencySafetyHubService {
    override suspend fun load(camping: Camping): EmergencySafetyHub {
        val data = document(camping.id).get().await().data ?: return EmergencySafetyHub.fallback(camping.location)
        val fallback = EmergencySafetyHub.fallback(camping.location)
        @Suppress("UNCHECKED_CAST")
        val contacts = (data["emergencyContacts"] as? List<Map<String, Any?>>).orEmpty().map { raw ->
            EmergencyContact(
                id = raw["id"] as? String ?: UUID.randomUUID().toString(),
                name = raw["name"] as? String ?: "",
                role = raw["role"] as? String ?: "",
                phoneNumber = raw["phoneNumber"] as? String ?: "",
                note = raw["note"] as? String ?: "",
                isPrimary = raw["isPrimary"] as? Boolean ?: false,
                isEmergencyService = raw["isEmergencyService"] as? Boolean ?: false,
            )
        }
        return EmergencySafetyHub(
            emergencyContacts = contacts.ifEmpty { fallback.emergencyContacts },
            emergencyInstructions = (data["emergencyInstructions"] as? String).orEmpty().ifBlank { fallback.emergencyInstructions },
            firstAidInfo = (data["firstAidInfo"] as? String).orEmpty().ifBlank { fallback.firstAidInfo },
            updatedAt = (data["updatedAt"] as? Timestamp)?.toDate() ?: data["updatedAt"] as? Date,
        ).normalized()
    }

    override suspend fun save(campingId: String, hub: EmergencySafetyHub): EmergencySafetyHub {
        val normalized = hub.normalized()
        val payload = mapOf(
            "emergencyContacts" to normalized.emergencyContacts.map { contact ->
                mapOf(
                    "id" to contact.id,
                    "name" to contact.name,
                    "role" to contact.role,
                    "phoneNumber" to contact.phoneNumber,
                    "note" to contact.note,
                    "isPrimary" to contact.isPrimary,
                    "isEmergencyService" to contact.isEmergencyService,
                )
            },
            "emergencyInstructions" to normalized.emergencyInstructions,
            "firstAidInfo" to normalized.firstAidInfo,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        document(campingId).set(payload, SetOptions.merge()).await()
        return normalized.copy(updatedAt = Date())
    }

    override suspend fun sendUrgentBroadcast(
        camping: Camping,
        author: AuthenticatedUser,
        title: String,
        body: String,
    ): Announcement {
        val draft = AnnouncementDraft(
            id = "urgent-${camping.id}-${UUID.randomUUID()}",
            title = "Urgent: ${title.trim()}",
            body = "**Urgent safety alert for ${camping.title}**\n\n${body.trim()}",
            audienceScopeRawValue = AnnouncementAudienceScope.Camping.rawValue,
            campingId = camping.id,
            campingTitle = camping.title,
            notificationTargetRoleRawValue = null,
            authorId = author.uid,
            authorName = author.preferredDisplayName,
            authorPhotoUrl = author.photoUrl,
            existingAttachments = emptyList(),
            pendingAttachments = emptyList(),
        )
        return announcementService.saveAnnouncement(draft).also {
            notificationDispatcher.dispatchAnnouncement(it)
        }
    }

    private fun document(campingId: String) = firestore.collection("campings")
        .document(campingId).collection("safetyHub").document("config")
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EmergencySafetyHubBindings {
    @Binds
    abstract fun bindEmergencySafetyHubService(
        implementation: FirestoreEmergencySafetyHubService,
    ): EmergencySafetyHubService
}
