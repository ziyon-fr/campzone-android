package fr.ziyon.campzone.data.songbook

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.media.AudioUploader
import fr.ziyon.campzone.data.model.ChordSheet
import fr.ziyon.campzone.data.model.Song
import fr.ziyon.campzone.data.model.SongAudio
import fr.ziyon.campzone.data.model.SongAudioKind
import fr.ziyon.campzone.data.model.SongAudioTrackType
import fr.ziyon.campzone.data.model.SongLyricsPart
import fr.ziyon.campzone.data.model.SongPayload
import fr.ziyon.campzone.data.model.toSongOrNull
import fr.ziyon.campzone.data.model.normalizeSongAudioFiles
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

interface SongbookService {
    suspend fun loadSongs(campingId: String): List<Song>
    suspend fun saveSong(draft: SongDraft): Song
    suspend fun deleteSong(id: String, campingId: String)
    suspend fun reorderSongs(campingId: String, orderedIds: List<String>): List<Song>
    suspend fun setPinnedTheme(songId: String, campingId: String): List<Song>
    suspend fun setFavorite(songId: String, campingId: String, userId: String, isFavorite: Boolean): Song
}

data class PendingSongAudio(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
    val kind: SongAudioKind,
    val duration: Double = 0.0,
    val fileSize: Long = bytes.size.toLong(),
    val voiceType: String = SongAudioTrackType.MainSong.wireValue,
    val displayName: String = "",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PendingSongAudio) return false
        return id == other.id &&
            fileName == other.fileName &&
            contentType == other.contentType &&
            bytes.contentEquals(other.bytes) &&
            kind == other.kind &&
            duration == other.duration &&
            fileSize == other.fileSize &&
            voiceType == other.voiceType &&
            displayName == other.displayName
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + duration.hashCode()
        result = 31 * result + fileSize.hashCode()
        result = 31 * result + voiceType.hashCode()
        result = 31 * result + displayName.hashCode()
        return result
    }

    val trackType: SongAudioTrackType get() = SongAudioTrackType.fromWire(voiceType)

    fun withTrackType(type: SongAudioTrackType, customName: String = ""): PendingSongAudio = copy(
        voiceType = type.wireValue,
        displayName = if (type.allowsCustomName) customName.trim() else "",
    )
}

data class SongDraft(
    val id: String,
    val campingId: String,
    val title: String,
    val artist: String = "",
    val composer: String = "",
    val lyrics: String,
    val chords: String,
    val chordedLyrics: String = "",
    val cantusSlug: String = "",
    val existingAudio: SongAudio? = null,
    val existingAudioFiles: List<SongAudio> = emptyList(),
    val pendingAudioFiles: List<PendingSongAudio> = emptyList(),
    val lyricsParts: List<SongLyricsPart> = emptyList(),
    val chordSheet: ChordSheet = ChordSheet(id = id),
    val youtubeLink: String = "",
    val pdfLink: String = "",
    val pptxLink: String = "",
    val orderIndex: Int,
    val isPinnedTheme: Boolean,
    val favoriteUserIds: List<String> = emptyList(),
)

@Singleton
class FirestoreSongbookService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val audioUploader: AudioUploader,
) : SongbookService {

    override suspend fun loadSongs(campingId: String): List<Song> {
        val snapshot = songsCollection(campingId)
            .orderBy(Field.OrderIndex, Query.Direction.ASCENDING)
            .limit(500)
            .get()
            .await()

        return snapshot.documents
            .mapNotNull { document -> document.data?.toSongOrNull(document.id) }
            .sortedWith(songComparator)
    }

    override suspend fun saveSong(draft: SongDraft): Song {
        val document = songDocument(draft.id, draft.campingId)
        val exists = document.get().await().exists()
        val uploadedAudio = uploadAudioFiles(
            audios = draft.pendingAudioFiles,
            songId = draft.id,
            campingId = draft.campingId,
        )
        val existingAudio = draft.existingAudioFiles.ifEmpty {
            draft.existingAudio?.let(::listOf).orEmpty()
        }
        val audioFiles = normalizeSongAudioFiles(existingAudio + uploadedAudio)
        val primaryAudio = audioFiles.firstOrNull { it.trackType == SongAudioTrackType.MainSong }

        if (draft.isPinnedTheme) {
            clearPinnedTheme(campingId = draft.campingId, exceptSongId = draft.id)
        }

        val song = Song(
            id = draft.id,
            title = draft.title,
            artist = draft.artist,
            composer = draft.composer,
            lyrics = draft.lyrics,
            chords = draft.chords,
            chordedLyrics = draft.chordedLyrics,
            cantusSlug = draft.cantusSlug,
            lyricsParts = draft.lyricsParts,
            chordSheet = draft.chordSheet,
            audio = primaryAudio,
            audioFiles = audioFiles,
            youtubeLink = draft.youtubeLink,
            pdfLink = draft.pdfLink,
            pptxLink = draft.pptxLink,
            orderIndex = draft.orderIndex,
            isPinnedTheme = draft.isPinnedTheme,
            favoriteUserIds = draft.favoriteUserIds,
        )

        document.set(
            SongPayload.songPayload(
                song = song,
                serverTimestamp = FieldValue.serverTimestamp(),
                deleteField = FieldValue.delete(),
                rawDate = Date(),
                includeCreatedAt = !exists,
            ),
            SetOptions.merge(),
        ).await()

        val saved = document.get().await()
        return saved.data?.toSongOrNull(saved.id)
            ?: error("Song could not be saved.")
    }

    override suspend fun deleteSong(id: String, campingId: String) {
        songDocument(id, campingId).delete().await()
        val remaining = loadSongs(campingId)
        reorderSongs(campingId, remaining.map { it.id })
    }

    override suspend fun reorderSongs(campingId: String, orderedIds: List<String>): List<Song> {
        val batch = firestore.batch()
        orderedIds.forEachIndexed { index, id ->
            batch.set(
                songDocument(id, campingId),
                mapOf(
                    Field.OrderIndex to index,
                    Field.UpdatedAt to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
        }
        batch.commit().await()
        return loadSongs(campingId)
    }

    override suspend fun setPinnedTheme(songId: String, campingId: String): List<Song> {
        clearPinnedTheme(campingId = campingId, exceptSongId = songId)
        songDocument(songId, campingId).set(
            mapOf(
                Field.IsPinnedTheme to true,
                Field.UpdatedAt to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
        return loadSongs(campingId)
    }

    override suspend fun setFavorite(
        songId: String,
        campingId: String,
        userId: String,
        isFavorite: Boolean,
    ): Song {
        songDocument(songId, campingId).set(
            mapOf(
                Field.FavoriteUserIds to if (isFavorite) {
                    FieldValue.arrayUnion(userId)
                } else {
                    FieldValue.arrayRemove(userId)
                },
                Field.UpdatedAt to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()

        val snapshot = songDocument(songId, campingId).get().await()
        return snapshot.data?.toSongOrNull(snapshot.id)
            ?: error("Song could not be found.")
    }

    private suspend fun clearPinnedTheme(campingId: String, exceptSongId: String) {
        val pinned = songsCollection(campingId)
            .whereEqualTo(Field.IsPinnedTheme, true)
            .get()
            .await()

        val batch = firestore.batch()
        pinned.documents
            .filterNot { it.id == exceptSongId }
            .forEach { document ->
                batch.set(
                    document.reference,
                    mapOf(
                        Field.IsPinnedTheme to false,
                        Field.UpdatedAt to FieldValue.serverTimestamp(),
                    ),
                    SetOptions.merge(),
                )
            }
        batch.commit().await()
    }

    private suspend fun uploadAudioFiles(
        audios: List<PendingSongAudio>,
        songId: String,
        campingId: String,
    ): List<SongAudio> {
        if (audios.isEmpty()) return emptyList()
        val folder = "campzone/songbook/$campingId/$songId"

        return audios.map { pending ->
            val result = audioUploader.uploadAudio(
                assetIdPrefix = "song-audio",
                folder = folder,
                tags = listOf("campzone", "songbook", "camping:$campingId", "song:$songId", pending.kind.wireValue),
                bytes = pending.bytes,
                mimeType = pending.contentType,
                fileExtension = pending.fileExtension,
            )
            SongAudio(
                id = pending.id,
                fileName = pending.fileName,
                contentType = pending.contentType,
                storagePath = result.publicId,
                downloadUrl = result.secureUrl,
                kind = pending.kind,
                duration = result.duration ?: pending.duration,
                fileSize = result.bytes ?: pending.fileSize,
                voiceType = pending.voiceType,
                displayName = pending.displayName,
            )
        }
    }

    private fun songsCollection(campingId: String) =
        firestore.collection(Field.Campings).document(campingId).collection(Field.Songs)

    private fun songDocument(songId: String, campingId: String) =
        songsCollection(campingId).document(songId)

    private val PendingSongAudio.fileExtension: String
        get() = fileName.substringAfterLast('.', kind.wireValue).ifBlank { kind.wireValue }

    private object Field {
        const val Campings = "campings"
        const val Songs = "songs"
        const val OrderIndex = "orderIndex"
        const val IsPinnedTheme = "isPinnedTheme"
        const val FavoriteUserIds = "favoriteUserIDs"
        const val UpdatedAt = "updatedAt"
    }
}

class FakeSongbookService(
    initialSongsByCampingId: Map<String, List<Song>> = mapOf(
        "summer-camp-2026" to previewSongs("summer-camp-2026"),
    ),
    var shouldFail: Boolean = false,
) : SongbookService {
    private val songsByCampingId: MutableMap<String, MutableList<Song>> =
        initialSongsByCampingId.mapValues { it.value.toMutableList() }.toMutableMap()

    override suspend fun loadSongs(campingId: String): List<Song> {
        checkFailure()
        return sorted(songsByCampingId[campingId].orEmpty())
    }

    override suspend fun saveSong(draft: SongDraft): Song {
        checkFailure()
        val songs = songsByCampingId.getOrPut(draft.campingId) { mutableListOf() }
        val now = Date()
        val uploadedAudio = draft.pendingAudioFiles.map { pending ->
            SongAudio(
                id = pending.id,
                fileName = pending.fileName,
                contentType = pending.contentType,
                storagePath = "preview/songbook/${draft.campingId}/${draft.id}/${pending.fileName}",
                downloadUrl = "",
                kind = pending.kind,
                duration = pending.duration,
                fileSize = pending.fileSize,
                voiceType = pending.voiceType,
                displayName = pending.displayName,
            )
        }
        val existingAudio = draft.existingAudioFiles.ifEmpty {
            draft.existingAudio?.let(::listOf).orEmpty()
        }
        val audioFiles = normalizeSongAudioFiles(existingAudio + uploadedAudio)
        val saved = Song(
            id = draft.id,
            title = draft.title,
            artist = draft.artist,
            composer = draft.composer,
            lyrics = draft.lyrics,
            chords = draft.chords,
            chordedLyrics = draft.chordedLyrics,
            cantusSlug = draft.cantusSlug,
            lyricsParts = draft.lyricsParts,
            chordSheet = draft.chordSheet,
            audio = audioFiles.firstOrNull { it.trackType == SongAudioTrackType.MainSong },
            audioFiles = audioFiles,
            youtubeLink = draft.youtubeLink,
            pdfLink = draft.pdfLink,
            pptxLink = draft.pptxLink,
            orderIndex = draft.orderIndex,
            isPinnedTheme = draft.isPinnedTheme,
            favoriteUserIds = draft.favoriteUserIds,
            createdAt = songs.firstOrNull { it.id == draft.id }?.createdAt ?: now,
            updatedAt = now,
        )

        if (saved.isPinnedTheme) {
            songs.replaceAll { it.copy(isPinnedTheme = it.id == saved.id) }
        }
        songs.removeAll { it.id == saved.id }
        songs += saved
        songsByCampingId[draft.campingId] = sorted(songs).toMutableList()
        return saved
    }

    override suspend fun deleteSong(id: String, campingId: String) {
        checkFailure()
        songsByCampingId[campingId]?.removeAll { it.id == id }
        normalizeOrder(campingId)
    }

    override suspend fun reorderSongs(campingId: String, orderedIds: List<String>): List<Song> {
        checkFailure()
        val songs = songsByCampingId[campingId].orEmpty().map { song ->
            val index = orderedIds.indexOf(song.id)
            if (index >= 0) song.copy(orderIndex = index) else song
        }
        songsByCampingId[campingId] = sorted(songs).toMutableList()
        return sorted(songs)
    }

    override suspend fun setPinnedTheme(songId: String, campingId: String): List<Song> {
        checkFailure()
        val songs = songsByCampingId[campingId].orEmpty()
            .map { it.copy(isPinnedTheme = it.id == songId) }
        songsByCampingId[campingId] = sorted(songs).toMutableList()
        return sorted(songs)
    }

    override suspend fun setFavorite(
        songId: String,
        campingId: String,
        userId: String,
        isFavorite: Boolean,
    ): Song {
        checkFailure()
        val songs = songsByCampingId.getOrPut(campingId) { mutableListOf() }
        val index = songs.indexOfFirst { it.id == songId }
        check(index >= 0) { "Song could not be found." }
        val favorites = if (isFavorite) {
            (songs[index].favoriteUserIds + userId).distinct()
        } else {
            songs[index].favoriteUserIds.filterNot { it == userId }
        }
        songs[index] = songs[index].copy(favoriteUserIds = favorites)
        songsByCampingId[campingId] = sorted(songs).toMutableList()
        return songs[index]
    }

    private fun normalizeOrder(campingId: String) {
        songsByCampingId[campingId] = sorted(songsByCampingId[campingId].orEmpty())
            .mapIndexed { index, song -> song.copy(orderIndex = index) }
            .toMutableList()
    }

    private fun checkFailure() {
        if (shouldFail) error("The preview songbook service was configured to fail.")
    }

    private fun sorted(songs: List<Song>): List<Song> = songs.sortedWith(songComparator)

    companion object {
        fun previewSongs(campingId: String = "summer-camp-2026"): List<Song> = listOf(
            Song(
                id = "campzone-theme",
                title = "Hino Campzone",
                lyrics = "Let every voice rise together\nIn one hope, one faith, one song.",
                chords = "[G] Let every voice rise [D] together\n[Em] In one hope, one [C] song.",
                audio = SongAudio(
                    id = "audio-theme",
                    fileName = "Hino Campzone.mp3",
                    contentType = "audio/mpeg",
                    storagePath = "preview/songbook/$campingId/campzone-theme.mp3",
                    downloadUrl = "",
                    kind = SongAudioKind.Mp3,
                ),
                orderIndex = 0,
                isPinnedTheme = true,
                favoriteUserIds = listOf("preview-user"),
                createdAt = Date(1),
                updatedAt = Date(1),
            ),
            Song(
                id = "por-toda-terra",
                title = "Por Toda Terra",
                lyrics = "We carry light across the fields,\nWe carry peace into the night.",
                chords = "[C] We carry light across the [G] fields\n[Am] We carry peace into the [F] night",
                audio = SongAudio(
                    id = "audio-terra",
                    fileName = "Por Toda Terra.mp3",
                    contentType = "audio/mpeg",
                    storagePath = "preview/songbook/$campingId/por-toda-terra.mp3",
                    downloadUrl = "",
                    kind = SongAudioKind.Mp3,
                ),
                orderIndex = 1,
                createdAt = Date(2),
                updatedAt = Date(2),
            ),
            Song(
                id = "maranata",
                title = "Maranata",
                lyrics = "Hope is near and love is calling,\nMorning breaks beyond the hill.",
                chords = "",
                orderIndex = 2,
                createdAt = Date(3),
                updatedAt = Date(3),
            ),
        )
    }
}

private val songComparator = compareByDescending<Song> { it.isPinnedTheme }
    .thenBy { it.orderIndex }
    .thenBy { it.title.lowercase() }

@Module
@InstallIn(SingletonComponent::class)
abstract class SongbookBindings {
    @Binds
    @Singleton
    abstract fun bindSongbookService(impl: FirestoreSongbookService): SongbookService
}
