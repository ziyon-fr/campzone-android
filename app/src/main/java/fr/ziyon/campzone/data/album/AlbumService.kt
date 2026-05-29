package fr.ziyon.campzone.data.album

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.model.AlbumSettings
import fr.ziyon.campzone.data.model.MediaItem
import fr.ziyon.campzone.data.model.MediaPayload
import fr.ziyon.campzone.data.model.toAlbumSettings
import fr.ziyon.campzone.data.model.toMediaItemOrNull
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

interface AlbumService {
    suspend fun loadMedia(campingId: String): List<MediaItem>
    suspend fun addMedia(media: MediaItem): MediaItem
    suspend fun updateCaption(campingId: String, mediaId: String, caption: String)
    suspend fun deleteMedia(campingId: String, mediaId: String)
    suspend fun loadSettings(campingId: String): AlbumSettings
    suspend fun saveSettings(campingId: String, settings: AlbumSettings)
}

@Singleton
class FirestoreAlbumService @Inject constructor(
    private val db: FirebaseFirestore,
) : AlbumService {
    override suspend fun loadMedia(campingId: String): List<MediaItem> {
        val snapshot = mediaCollection(campingId)
            .orderBy(Field.UploadedAt, Query.Direction.DESCENDING)
            .get()
            .await()
        return snapshot.documents.mapNotNull { document ->
            @Suppress("UNCHECKED_CAST")
            (document.data as? Map<String, Any?>)?.toMediaItemOrNull(document.id)
        }
    }

    override suspend fun addMedia(media: MediaItem): MediaItem {
        require(media.campingId.isNotBlank()) { "Camping is required." }
        require(media.id.isNotBlank()) { "Media id is required." }
        mediaCollection(media.campingId)
            .document(media.id)
            .set(MediaPayload.mediaPayload(media, FieldValue.serverTimestamp()))
            .await()
        return media
    }

    override suspend fun updateCaption(campingId: String, mediaId: String, caption: String) {
        mediaCollection(campingId)
            .document(mediaId)
            .set(mapOf(Field.Caption to caption.trim()), SetOptions.merge())
            .await()
    }

    override suspend fun deleteMedia(campingId: String, mediaId: String) {
        mediaCollection(campingId).document(mediaId).delete().await()
    }

    override suspend fun loadSettings(campingId: String): AlbumSettings {
        val snapshot = settingsDocument(campingId).get().await()
        @Suppress("UNCHECKED_CAST")
        return (snapshot.data as? Map<String, Any?>)?.toAlbumSettings() ?: AlbumSettings()
    }

    override suspend fun saveSettings(campingId: String, settings: AlbumSettings) {
        settingsDocument(campingId)
            .set(MediaPayload.albumSettingsPayload(settings), SetOptions.merge())
            .await()
    }

    private fun mediaCollection(campingId: String) =
        db.collection(Collection.Campings).document(campingId).collection(Collection.Media)

    private fun settingsDocument(campingId: String) =
        db.collection(Collection.Campings)
            .document(campingId)
            .collection(Collection.AlbumSettings)
            .document(Collection.SettingsDoc)

    private object Collection {
        const val Campings = "campings"
        const val Media = "media"
        const val AlbumSettings = "albumSettings"
        const val SettingsDoc = "default"
    }

    private object Field {
        const val UploadedAt = "uploadedAt"
        const val Caption = "caption"
    }
}

class FakeAlbumService(
    initialMedia: List<MediaItem> = emptyList(),
    initialSettings: Map<String, AlbumSettings> = emptyMap(),
    var shouldFail: Boolean = false,
) : AlbumService {
    private val mediaByCamping = initialMedia.groupBy { it.campingId }
        .mapValues { it.value.toMutableList() }
        .toMutableMap()
    private val settingsByCamping = initialSettings.toMutableMap()

    private fun check() {
        if (shouldFail) throw IllegalStateException("FakeAlbumService configured to fail.")
    }

    override suspend fun loadMedia(campingId: String): List<MediaItem> {
        check()
        return mediaByCamping[campingId].orEmpty()
            .sortedByDescending { it.uploadedAt ?: Date(0) }
    }

    override suspend fun addMedia(media: MediaItem): MediaItem {
        check()
        val list = mediaByCamping.getOrPut(media.campingId) { mutableListOf() }
        list.removeAll { it.id == media.id }
        list.add(media)
        return media
    }

    override suspend fun updateCaption(campingId: String, mediaId: String, caption: String) {
        check()
        val list = mediaByCamping[campingId] ?: return
        val idx = list.indexOfFirst { it.id == mediaId }
        if (idx >= 0) {
            list[idx] = list[idx].copy(caption = caption.trim())
        }
    }

    override suspend fun deleteMedia(campingId: String, mediaId: String) {
        check()
        mediaByCamping[campingId]?.removeAll { it.id == mediaId }
    }

    override suspend fun loadSettings(campingId: String): AlbumSettings {
        check()
        return settingsByCamping[campingId] ?: AlbumSettings()
    }

    override suspend fun saveSettings(campingId: String, settings: AlbumSettings) {
        check()
        settingsByCamping[campingId] = settings
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AlbumBindings {
    @Binds
    @Singleton
    abstract fun bindAlbumService(impl: FirestoreAlbumService): AlbumService
}
