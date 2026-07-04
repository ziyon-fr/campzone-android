package fr.ziyon.campzone.data.support

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

interface SupportHubService {
    suspend fun loadCamp(campingId: String, campingTitle: String): SupportHub
    suspend fun saveCamp(campingId: String, hub: SupportHub): SupportHub
    suspend fun loadApp(): SupportHub
    suspend fun saveApp(hub: SupportHub): SupportHub
}

@Singleton
class FirestoreSupportHubService @Inject constructor(private val firestore: FirebaseFirestore) : SupportHubService {
    override suspend fun loadCamp(campingId: String, campingTitle: String) = load(campDocument(campingId), SupportHub.campFallback(campingTitle))
    override suspend fun saveCamp(campingId: String, hub: SupportHub) = save(campDocument(campingId), hub)
    override suspend fun loadApp() = load(firestore.collection("support").document("appDevelopment"), SupportHub.appFallback)
    override suspend fun saveApp(hub: SupportHub) = save(firestore.collection("support").document("appDevelopment"), hub)

    private suspend fun load(reference: com.google.firebase.firestore.DocumentReference, fallback: SupportHub): SupportHub {
        val data = reference.get().await().data ?: return fallback
        return SupportHub(
            intro = (data["intro"] as? String)?.trim().takeUnless { it.isNullOrBlank() } ?: fallback.intro,
            impactNote = (data["impactNote"] as? String)?.trim().takeUnless { it.isNullOrBlank() } ?: fallback.impactNote,
            links = data.maps("links").map { raw ->
                SupportExternalLink(
                    id = raw["id"] as? String ?: UUID.randomUUID().toString(),
                    title = raw["title"] as? String ?: "",
                    subtitle = raw["subtitle"] as? String ?: "",
                    urlString = raw["urlString"] as? String ?: "",
                    kind = SupportLinkKind.fromWire(raw["kindRawValue"] as? String),
                    isPrimary = raw["isPrimary"] as? Boolean ?: false,
                )
            },
            sponsors = data.maps("sponsors").map { raw ->
                SponsorAcknowledgement(
                    id = raw["id"] as? String ?: UUID.randomUUID().toString(),
                    name = raw["name"] as? String ?: "",
                    note = raw["note"] as? String ?: "",
                    urlString = raw["urlString"] as? String ?: "",
                    tier = SponsorTier.fromWire(raw["tierRawValue"] as? String),
                )
            },
            updatedAt = when (val value = data["updatedAt"]) { is Timestamp -> value.toDate(); is Date -> value; else -> null },
        )
    }

    private suspend fun save(reference: com.google.firebase.firestore.DocumentReference, hub: SupportHub): SupportHub {
        reference.set(
            mapOf(
                "intro" to hub.intro.trim(),
                "impactNote" to hub.impactNote.trim(),
                "links" to hub.links.map { mapOf("id" to it.id, "title" to it.title.trim(), "subtitle" to it.subtitle.trim(), "urlString" to SupportUrlValidator.normalize(it.urlString), "kindRawValue" to it.kind.wireValue, "isPrimary" to it.isPrimary) },
                "sponsors" to hub.sponsors.map { mapOf("id" to it.id, "name" to it.name.trim(), "note" to it.note.trim(), "urlString" to SupportUrlValidator.normalize(it.urlString), "tierRawValue" to it.tier.wireValue) },
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
        return hub.copy(updatedAt = Date())
    }

    private fun campDocument(campingId: String) = firestore.collection("campings").document(campingId).collection("support").document("config")
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.maps(key: String): List<Map<String, Any?>> = (this[key] as? List<*>)?.mapNotNull { it as? Map<String, Any?> }.orEmpty()

@Module @InstallIn(SingletonComponent::class)
abstract class SupportHubBindings { @Binds abstract fun bindSupportHubService(implementation: FirestoreSupportHubService): SupportHubService }
