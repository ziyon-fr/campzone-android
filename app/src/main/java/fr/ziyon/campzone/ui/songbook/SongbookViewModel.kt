package fr.ziyon.campzone.ui.songbook

import android.media.MediaPlayer
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.i18n.StringProvider
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.analytics.AnalyticsService
import fr.ziyon.campzone.data.analytics.NoOpAnalyticsService
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Chord
import fr.ziyon.campzone.data.model.ChordLine
import fr.ziyon.campzone.data.model.ChordSheet
import fr.ziyon.campzone.data.model.Song
import fr.ziyon.campzone.data.model.SongAudio
import fr.ziyon.campzone.data.model.SongAudioKind
import fr.ziyon.campzone.data.model.SongAudioTrackType
import fr.ziyon.campzone.data.model.SongLyricsPart
import fr.ziyon.campzone.data.model.SongLyricsPartKind
import fr.ziyon.campzone.data.songbook.PendingSongAudio
import fr.ziyon.campzone.data.songbook.SongDraft
import fr.ziyon.campzone.data.songbook.SongbookService
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed interface SongbookUiState {
    data object Loading : SongbookUiState
    data class Loaded(val songs: List<Song>, val campingTitle: String) : SongbookUiState
    data class Empty(val campingTitle: String) : SongbookUiState
    data class Error(val message: String) : SongbookUiState
}

data class SongEditorForm(
    val title: String = "",
    val artist: String = "",
    val composer: String = "",
    val lyrics: String = "",
    val lyricsParts: List<SongLyricsPart> = emptyList(),
    val editingLyricsPartId: String? = null,
    val selectedLyricsPartKind: SongLyricsPartKind = SongLyricsPartKind.Verse,
    val selectedLyricsPartNumber: Int = 1,
    val selectedLyricsPartTitle: String = "",
    val lyricsPartText: String = "",
    val chordSheetText: String = "",
    val existingAudioFiles: List<SongAudio> = emptyList(),
    val pendingAudioFiles: List<PendingSongAudio> = emptyList(),
    val remoteAudioUrl: String = "",
    val youtubeLink: String = "",
    val pdfLink: String = "",
    val isPinnedTheme: Boolean = false,
) {
    val isEditingLyricsPart: Boolean get() = editingLyricsPartId != null

    val validationErrors: List<SongEditorValidationError>
        get() = buildList {
            if (title.isBlank()) add(SongEditorValidationError.TitleRequired)
            val hasLyrics = lyrics.isNotBlank() || lyricsParts.any { it.text.isNotBlank() }
            val hasChordLyrics = ChordProParser.parse(chordSheetText).lines.any {
                it.text.isNotBlank() || it.chords.isNotEmpty()
            }
            if (!hasLyrics && !hasChordLyrics) add(SongEditorValidationError.LyricsRequired)
            val hasAudio = existingAudioFiles.isNotEmpty() || pendingAudioFiles.isNotEmpty()
            val hasMainAudio = existingAudioFiles.any { it.trackType == SongAudioTrackType.MainSong } ||
                pendingAudioFiles.any { it.trackType == SongAudioTrackType.MainSong }
            if (hasAudio && !hasMainAudio) add(SongEditorValidationError.MainAudioRequired)
        }

    val isValid: Boolean get() = validationErrors.isEmpty()
}

enum class SongEditorValidationError(@param:StringRes val messageRes: Int) {
    TitleRequired(R.string.songbook_title_required),
    LyricsRequired(R.string.songbook_lyrics_required),
    MainAudioRequired(R.string.songbook_main_audio_required),
}

enum class SongMoveDirection { Up, Down }

data class SongbookAudioTrackItem(
    val id: String,
    val fileName: String,
    val type: SongAudioTrackType,
    val displayName: String,
    val isPending: Boolean,
)

@get:StringRes
internal val SongAudioTrackType.displayNameRes: Int
    get() = when (this) {
        SongAudioTrackType.MainSong -> R.string.songbook_track_main
        SongAudioTrackType.Playback -> R.string.songbook_track_playback
        SongAudioTrackType.Instrumental -> R.string.songbook_track_instrumental
        SongAudioTrackType.Soprano -> R.string.songbook_track_soprano
        SongAudioTrackType.MezzoSoprano -> R.string.songbook_track_mezzo_soprano
        SongAudioTrackType.Alto -> R.string.songbook_track_alto
        SongAudioTrackType.Tenor -> R.string.songbook_track_tenor
        SongAudioTrackType.Baritone -> R.string.songbook_track_baritone
        SongAudioTrackType.Bass -> R.string.songbook_track_bass
        SongAudioTrackType.Other -> R.string.songbook_track_other
    }

internal fun SongLyricsPart.displayTitle(): String {
    val customTitle = title.trim()
    if (kind == SongLyricsPartKind.Custom && customTitle.isNotEmpty()) return customTitle

    val base = kind.displayName
    return when (kind) {
        SongLyricsPartKind.Intro,
        SongLyricsPartKind.Bridge,
        SongLyricsPartKind.Instrumental,
        SongLyricsPartKind.Outro,
        -> if (number > 1) "$base $number" else base
        SongLyricsPartKind.Verse,
        SongLyricsPartKind.PreChorus,
        SongLyricsPartKind.Chorus,
        SongLyricsPartKind.Custom,
        -> "$base $number"
    }
}

internal val SongLyricsPartKind.displayName: String
    get() = when (this) {
        SongLyricsPartKind.Intro -> "Intro"
        SongLyricsPartKind.Verse -> "Verse"
        SongLyricsPartKind.PreChorus -> "Pre-chorus"
        SongLyricsPartKind.Chorus -> "Chorus"
        SongLyricsPartKind.Bridge -> "Bridge"
        SongLyricsPartKind.Instrumental -> "Instrumental"
        SongLyricsPartKind.Outro -> "Outro"
        SongLyricsPartKind.Custom -> "Custom"
    }

@get:StringRes
internal val SongLyricsPartKind.displayNameRes: Int
    get() = when (this) {
        SongLyricsPartKind.Intro -> R.string.songbook_part_intro
        SongLyricsPartKind.Verse -> R.string.songbook_part_verse
        SongLyricsPartKind.PreChorus -> R.string.songbook_part_pre_chorus
        SongLyricsPartKind.Chorus -> R.string.songbook_part_chorus
        SongLyricsPartKind.Bridge -> R.string.songbook_part_bridge
        SongLyricsPartKind.Instrumental -> R.string.songbook_part_instrumental
        SongLyricsPartKind.Outro -> R.string.songbook_part_outro
        SongLyricsPartKind.Custom -> R.string.songbook_part_custom
    }

@HiltViewModel
class SongbookViewModel @Inject constructor(
    private val songbookService: SongbookService,
    private val campingService: CampingService,
    private val stringProvider: StringProvider,
    private val analyticsService: AnalyticsService = NoOpAnalyticsService,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SongbookUiState>(SongbookUiState.Loading)
    val uiState: StateFlow<SongbookUiState> = _uiState.asStateFlow()

    private val _form = MutableStateFlow(SongEditorForm())
    val form: StateFlow<SongEditorForm> = _form.asStateFlow()

    private val _canManageSongbook = MutableStateFlow(false)
    val canManageSongbook: StateFlow<Boolean> = _canManageSongbook.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _showsFavoritesOnly = MutableStateFlow(false)
    val showsFavoritesOnly: StateFlow<Boolean> = _showsFavoritesOnly.asStateFlow()

    private val _playingSongId = MutableStateFlow<String?>(null)
    val playingSongId: StateFlow<String?> = _playingSongId.asStateFlow()

    private val _playingAudioId = MutableStateFlow<String?>(null)
    val playingAudioId: StateFlow<String?> = _playingAudioId.asStateFlow()

    private val _isAudioPlaying = MutableStateFlow(false)
    val isAudioPlaying: StateFlow<Boolean> = _isAudioPlaying.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    private val _playbackDurationMs = MutableStateFlow(0L)
    val playbackDurationMs: StateFlow<Long> = _playbackDurationMs.asStateFlow()

    private val _editingSongId = MutableStateFlow<String?>(null)
    val editingSongId: StateFlow<String?> = _editingSongId.asStateFlow()

    private val _trackTypeEditorAudioId = MutableStateFlow<String?>(null)
    val trackTypeEditorAudioId: StateFlow<String?> = _trackTypeEditorAudioId.asStateFlow()

    private val permissions = AppPermissionEvaluator()
    private var loadedCampingIds = mutableSetOf<String>()
    private var songsByCampingId = mutableMapOf<String, List<Song>>()
    private var campingTitleById = mutableMapOf<String, String>()
    private var campingContextById = mutableMapOf<String, CampingPermissionContext>()
    private var mediaPlayer: MediaPlayer? = null
    private var playbackProgressJob: Job? = null

    fun loadIfNeeded(campingId: String, user: AuthenticatedUser? = null) {
        if (loadedCampingIds.contains(campingId) && _uiState.value !is SongbookUiState.Loading) {
            updateCanManage(user, campingContextById[campingId])
            publish(campingId)
            return
        }
        load(campingId, user)
    }

    fun load(campingId: String, user: AuthenticatedUser? = null) {
        viewModelScope.launch {
            _uiState.value = SongbookUiState.Loading
            _operationError.value = null
            runCatching {
                val camping = runCatching { campingService.fetchCamping(campingId) }.getOrNull()
                campingTitleById[campingId] = camping?.title ?: stringProvider.get(R.string.common_camping)
                val campingContext = camping?.let {
                    CampingPermissionContext(
                        organizerLevelType = it.organizerLevel.type.wireValue,
                        organizerLevelValue = it.organizerLevel.value,
                        createdByUid = it.createdByUid,
                    )
                }
                if (campingContext != null) campingContextById[campingId] = campingContext
                updateCanManage(user, campingContext)
                songsByCampingId[campingId] = songbookService.loadSongs(campingId)
                loadedCampingIds.add(campingId)
                publish(campingId)
            }.onFailure { e ->
                _uiState.value = SongbookUiState.Error(e.message ?: operationFailedMessage())
            }
        }
    }

    fun songs(campingId: String): List<Song> = songsByCampingId[campingId].orEmpty()

    fun visibleSongs(campingId: String, userId: String?): List<Song> {
        val query = _searchText.value.trim()
        return songs(campingId)
            .filter { song ->
                query.isEmpty() ||
                    song.title.contains(query, ignoreCase = true) ||
                    song.artist.contains(query, ignoreCase = true) ||
                    song.composer.contains(query, ignoreCase = true) ||
                    song.lyrics.contains(query, ignoreCase = true) ||
                    song.chords.contains(query, ignoreCase = true) ||
                    song.chordSheet.lines.flatMap { it.chords }.any {
                        it.chord.contains(query, ignoreCase = true)
                    }
            }
            .filter { song -> !_showsFavoritesOnly.value || userId?.let(song::isFavoritedBy) == true }
    }

    fun songById(songId: String, campingId: String): Song? =
        songs(campingId).firstOrNull { it.id == songId }

    fun pinnedSong(campingId: String): Song? =
        songs(campingId).firstOrNull { it.isPinnedTheme }

    fun currentPlayingSong(): Song? {
        val songId = _playingSongId.value ?: return null
        return songsByCampingId.values.asSequence()
            .flatMap { it.asSequence() }
            .firstOrNull { it.id == songId }
    }

    fun currentPlayingSongEntry(): Pair<String, Song>? {
        val songId = _playingSongId.value ?: return null
        return songsByCampingId.entries.asSequence()
            .firstNotNullOfOrNull { (campingId, songs) ->
                songs.firstOrNull { it.id == songId }?.let { campingId to it }
            }
    }

    fun hasNextSong(): Boolean =
        nextPlayableSong() != null

    fun playNextSong() {
        val (_, nextSong) = nextPlayableSong() ?: return
        val mainAudio = nextSong.mainAudio ?: return
        playAudio(nextSong, mainAudio)
    }

    fun updateSearch(text: String) {
        _searchText.value = text
    }

    fun toggleFavoritesOnly() {
        _showsFavoritesOnly.value = !_showsFavoritesOnly.value
    }

    fun prepareNewSong(campingId: String) {
        _editingSongId.value = null
        _form.value = SongEditorForm()
        _operationError.value = null
        _operationMessage.value = null
        _trackTypeEditorAudioId.value = null
    }

    fun prepareEditingSong(song: Song) {
        _editingSongId.value = song.id
        _form.value = formFrom(song)
        _operationError.value = null
        _operationMessage.value = null
        _trackTypeEditorAudioId.value = null
    }

    fun updateForm(update: (SongEditorForm) -> SongEditorForm) {
        _form.value = update(_form.value)
    }

    fun addPendingAudio(fileName: String, contentType: String, bytes: ByteArray): Boolean {
        if (!isSupportedAudio(fileName, contentType)) {
            _operationError.value = stringProvider.get(R.string.songbook_audio_unsupported_error)
            return false
        }

        val pending = PendingSongAudio(
            fileName = fileName,
            contentType = contentType,
            bytes = bytes,
            kind = audioKind(fileName, contentType),
            voiceType = nextAvailableTrackType().wireValue,
        )
        _form.value = _form.value.copy(pendingAudioFiles = _form.value.pendingAudioFiles + pending)
        _trackTypeEditorAudioId.value = pending.id
        _operationError.value = null
        return true
    }

    fun removeAudio(id: String) {
        _form.value = _form.value.copy(
            existingAudioFiles = _form.value.existingAudioFiles.filterNot { it.id == id },
            pendingAudioFiles = _form.value.pendingAudioFiles.filterNot { it.id == id },
        )
        if (_trackTypeEditorAudioId.value == id) _trackTypeEditorAudioId.value = null
        promoteMainSongIfNeeded()
    }

    fun addRemoteAudio(urlText: String): Boolean {
        val url = urlText.trim()
        val parsed = runCatching { java.net.URI(url) }.getOrNull()
        if (parsed?.scheme !in setOf("http", "https") || parsed?.host.isNullOrBlank()) {
            _operationError.value = stringProvider.get(R.string.songbook_remote_url_invalid)
            return false
        }
        if (_form.value.existingAudioFiles.any { it.downloadUrl == url }) {
            _operationError.value = stringProvider.get(R.string.songbook_remote_url_duplicate)
            return false
        }
        val fileName = parsed?.path?.substringAfterLast('/')?.takeUnless { it.isBlank() }
            ?: "remote-audio.mp3"
        val contentType = contentTypeForAudioName(fileName)
        if (!isSupportedAudio(fileName, contentType)) {
            _operationError.value = stringProvider.get(R.string.songbook_remote_url_unsupported)
            return false
        }
        val audio = SongAudio(
            id = UUID.randomUUID().toString(),
            fileName = fileName,
            contentType = contentType,
            downloadUrl = url,
            kind = audioKind(fileName, contentType),
            voiceType = nextAvailableTrackType().wireValue,
        )
        _form.value = _form.value.copy(
            existingAudioFiles = _form.value.existingAudioFiles + audio,
            remoteAudioUrl = "",
        )
        _trackTypeEditorAudioId.value = audio.id
        _operationError.value = null
        return true
    }

    fun orderedFormAudio(): List<SongbookAudioTrackItem> {
        val form = _form.value
        return buildList {
            addAll(form.existingAudioFiles.map {
                SongbookAudioTrackItem(it.id, it.fileName, it.trackType, it.displayName, false)
            })
            addAll(form.pendingAudioFiles.map {
                SongbookAudioTrackItem(it.id, it.fileName, it.trackType, it.displayName, true)
            })
        }.sortedWith(compareBy<SongbookAudioTrackItem> { it.type.sortOrder }.thenBy { it.fileName.lowercase() })
    }

    fun editAudioTrackType(id: String) {
        if (orderedFormAudio().any { it.id == id }) _trackTypeEditorAudioId.value = id
    }

    fun dismissAudioTrackTypeEditor() {
        _trackTypeEditorAudioId.value = null
    }

    fun availableTrackTypes(audioId: String?): List<SongAudioTrackType> =
        SongAudioTrackType.entries.filter { it.allowsMultiple || !isTrackTypeTaken(it, audioId) }

    fun updateAudioTrackType(
        id: String,
        type: SongAudioTrackType,
        displayName: String = "",
    ): Boolean {
        if (isTrackTypeTaken(type, id)) {
            _operationError.value = stringProvider.get(R.string.songbook_duplicate_track_type)
            return false
        }
        val form = _form.value
        val pendingIndex = form.pendingAudioFiles.indexOfFirst { it.id == id }
        val existingIndex = form.existingAudioFiles.indexOfFirst { it.id == id }
        if (pendingIndex < 0 && existingIndex < 0) return false
        _form.value = form.copy(
            pendingAudioFiles = form.pendingAudioFiles.mapIndexed { index, audio ->
                if (index == pendingIndex) audio.withTrackType(type, displayName) else audio
            },
            existingAudioFiles = form.existingAudioFiles.mapIndexed { index, audio ->
                if (index == existingIndex) audio.withTrackType(type, displayName) else audio
            },
        )
        _operationError.value = null
        _trackTypeEditorAudioId.value = null
        return true
    }

    fun startLyricsPartEditor(partId: String? = null) {
        val form = _form.value
        val part = partId?.let { id -> form.lyricsParts.firstOrNull { it.id == id } }
        _form.value = if (part != null) {
            form.copy(
                editingLyricsPartId = part.id,
                selectedLyricsPartKind = part.kind,
                selectedLyricsPartNumber = part.number.coerceIn(1, 24),
                selectedLyricsPartTitle = part.title,
                lyricsPartText = part.text,
            )
        } else {
            val kind = nextLyricsPartKind(form.lyricsParts)
            form.copy(
                editingLyricsPartId = null,
                selectedLyricsPartKind = kind,
                selectedLyricsPartNumber = nextLyricsPartNumber(form.lyricsParts, kind),
                selectedLyricsPartTitle = "",
                lyricsPartText = "",
            )
        }
        _operationError.value = null
    }

    fun updateLyricsPartKind(kind: SongLyricsPartKind) {
        val form = _form.value
        _form.value = form.copy(
            selectedLyricsPartKind = kind,
            selectedLyricsPartNumber = if (form.isEditingLyricsPart) {
                form.selectedLyricsPartNumber
            } else {
                nextLyricsPartNumber(form.lyricsParts, kind)
            },
            selectedLyricsPartTitle = if (kind == SongLyricsPartKind.Custom) {
                form.selectedLyricsPartTitle
            } else {
                ""
            },
        )
    }

    fun updateLyricsPartNumber(number: Int) {
        _form.value = _form.value.copy(selectedLyricsPartNumber = number.coerceIn(1, 24))
    }

    fun updateLyricsPartTitle(title: String) {
        _form.value = _form.value.copy(selectedLyricsPartTitle = title)
    }

    fun updateLyricsPartText(text: String) {
        _form.value = _form.value.copy(lyricsPartText = text)
    }

    fun saveLyricsPart() {
        val form = _form.value
        val text = form.lyricsPartText.trim()
        if (text.isEmpty()) {
            _operationError.value = stringProvider.get(R.string.songbook_lyrics_text_required)
            return
        }

        val part = SongLyricsPart(
            id = form.editingLyricsPartId ?: UUID.randomUUID().toString(),
            kind = form.selectedLyricsPartKind,
            number = form.selectedLyricsPartNumber.coerceIn(1, 24),
            title = form.selectedLyricsPartTitle.trim(),
            text = text,
        )
        val parts = if (form.editingLyricsPartId != null) {
            form.lyricsParts.map { if (it.id == form.editingLyricsPartId) part else it }
        } else {
            form.lyricsParts + part
        }
        _form.value = resetLyricsPartEditor(form.copy(lyricsParts = parts, lyrics = syncedLyricsText(parts)))
        _operationError.value = null
    }

    fun cancelLyricsPartEditing() {
        _form.value = resetLyricsPartEditor(_form.value)
        _operationError.value = null
    }

    fun removeLyricsPart(id: String) {
        val form = _form.value
        val parts = form.lyricsParts.filterNot { it.id == id }
        val updated = form.copy(lyricsParts = parts, lyrics = syncedLyricsText(parts))
        _form.value = if (form.editingLyricsPartId == id) resetLyricsPartEditor(updated) else updated
    }

    fun moveLyricsPart(id: String, direction: SongMoveDirection) {
        val form = _form.value
        val parts = form.lyricsParts.toMutableList()
        val index = parts.indexOfFirst { it.id == id }
        if (index < 0) return
        val destination = if (direction == SongMoveDirection.Up) index - 1 else index + 1
        if (destination !in parts.indices) return
        parts.swap(index, destination)
        _form.value = form.copy(lyricsParts = parts, lyrics = syncedLyricsText(parts))
    }

    fun saveSong(campingId: String, onSuccess: () -> Unit = {}) {
        if (!canManageOrWarn()) return

        val form = _form.value
        if (!form.isValid) {
            _operationError.value = form.validationErrors.firstOrNull()?.let { stringProvider.get(it.messageRes) }
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            _operationError.value = null
            runCatching {
                val existingSong = _editingSongId.value?.let { songById(it, campingId) }
                val songId = existingSong?.id ?: UUID.randomUUID().toString()
                val chordSheet = ChordProParser.parse(
                    text = form.chordSheetText,
                    existingId = existingSong?.chordSheet?.id ?: songId,
                )
                val lyrics = resolvedLyrics(form)
                val draft = SongDraft(
                    id = songId,
                    campingId = campingId,
                    title = form.title.trim(),
                    artist = form.artist.trim(),
                    composer = form.composer.trim(),
                    lyrics = lyrics,
                    chords = form.chordSheetText.trim(),
                    existingAudio = form.existingAudioFiles.firstOrNull(),
                    existingAudioFiles = form.existingAudioFiles,
                    pendingAudioFiles = form.pendingAudioFiles,
                    lyricsParts = resolvedLyricsParts(form),
                    chordSheet = chordSheet,
                    youtubeLink = form.youtubeLink.trim(),
                    pdfLink = form.pdfLink.trim(),
                    orderIndex = existingSong?.orderIndex ?: songs(campingId).size,
                    isPinnedTheme = form.isPinnedTheme,
                    favoriteUserIds = existingSong?.favoriteUserIds.orEmpty(),
                )
                val saved = songbookService.saveSong(draft)
                upsert(saved, campingId)
                _editingSongId.value = saved.id
                _form.value = formFrom(saved)
                _operationMessage.value = stringProvider.get(R.string.songbook_saved)
                loadedCampingIds.add(campingId)
                publish(campingId)
                onSuccess()
            }.onFailure { e ->
                _operationError.value = e.message ?: operationFailedMessage()
            }
            _isSaving.value = false
        }
    }

    fun deleteSong(songId: String, campingId: String, onDeleted: () -> Unit = {}) {
        if (!canManageOrWarn()) return

        viewModelScope.launch {
            _operationError.value = null
            runCatching {
                songbookService.deleteSong(songId, campingId)
                songsByCampingId[campingId] = songs(campingId)
                    .filterNot { it.id == songId }
                    .mapIndexed { index, song -> song.copy(orderIndex = index) }
                if (_playingSongId.value == songId) stopAudio()
                _operationMessage.value = stringProvider.get(R.string.songbook_deleted)
                publish(campingId)
                onDeleted()
            }.onFailure { e ->
                _operationError.value = e.message ?: operationFailedMessage()
            }
        }
    }

    fun moveSong(songId: String, direction: SongMoveDirection, campingId: String) {
        if (!canManageOrWarn()) return

        val current = songs(campingId).toMutableList()
        val index = current.indexOfFirst { it.id == songId }
        if (index < 0) return
        val destination = if (direction == SongMoveDirection.Up) index - 1 else index + 1
        if (destination !in current.indices) return
        current.swap(index, destination)
        val ordered = current.mapIndexed { orderIndex, song -> song.copy(orderIndex = orderIndex) }

        viewModelScope.launch {
            _operationError.value = null
            runCatching {
                songsByCampingId[campingId] = songbookService.reorderSongs(campingId, ordered.map { it.id })
                _operationMessage.value = stringProvider.get(R.string.songbook_order_updated)
                publish(campingId)
            }.onFailure { e ->
                _operationError.value = e.message ?: operationFailedMessage()
            }
        }
    }

    fun setPinnedTheme(songId: String, campingId: String) {
        if (!canManageOrWarn()) return

        viewModelScope.launch {
            _operationError.value = null
            runCatching {
                songsByCampingId[campingId] = songbookService.setPinnedTheme(songId, campingId)
                _operationMessage.value = stringProvider.get(R.string.songbook_theme_pinned)
                publish(campingId)
            }.onFailure { e ->
                _operationError.value = e.message ?: operationFailedMessage()
            }
        }
    }

    fun toggleFavorite(songId: String, campingId: String, userId: String?) {
        if (userId == null) {
            _operationError.value = stringProvider.get(R.string.songbook_sign_in_favorite)
            return
        }
        val song = songById(songId, campingId) ?: return
        val willFavorite = !song.isFavoritedBy(userId)
        viewModelScope.launch {
            _operationError.value = null
            runCatching {
                val updated = songbookService.setFavorite(
                    songId = songId,
                    campingId = campingId,
                    userId = userId,
                    isFavorite = willFavorite,
                )
                if (willFavorite) analyticsService.favoriteSong(updated.id, updated.title)
                upsert(updated, campingId)
                publish(campingId)
            }.onFailure { e ->
                _operationError.value = e.message ?: operationFailedMessage()
            }
        }
    }

    fun toggleAudio(song: Song) {
        if (_playingSongId.value == song.id) {
            val player = mediaPlayer
            if (player == null) {
                _playingSongId.value = null
                _isAudioPlaying.value = false
                _playbackPositionMs.value = 0L
                _playbackDurationMs.value = 0L
                return
            }
            runCatching {
                if (_isAudioPlaying.value) {
                    player.pause()
                    updatePlaybackSnapshot(player)
                    stopProgressUpdates()
                    _isAudioPlaying.value = false
                } else {
                    player.start()
                    _isAudioPlaying.value = true
                    startProgressUpdates()
                }
            }.onFailure {
                _operationError.value = stringProvider.get(R.string.songbook_audio_play_error)
                stopAudio()
            }
            return
        }

        val mainAudio = song.mainAudio
        if (mainAudio == null) {
            _operationError.value = stringProvider.get(R.string.songbook_audio_missing)
            return
        }
        playAudio(song, mainAudio)
    }

    fun playAudio(song: Song, track: SongAudio) {
        if (_playingSongId.value == song.id && _playingAudioId.value == track.id) {
            toggleAudio(song)
            return
        }

        val url = track.downloadUrl.takeUnless { it.isBlank() }
        if (url == null) {
            _operationError.value = stringProvider.get(R.string.songbook_audio_missing)
            return
        }

        runCatching {
            stopAudio()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener {
                    updatePlaybackSnapshot(it)
                    it.start()
                    _playingSongId.value = song.id
                    _playingAudioId.value = track.id
                    _isAudioPlaying.value = true
                    startProgressUpdates()
                    analyticsService.playSong(song.id, song.title)
                }
                setOnCompletionListener {
                    stopProgressUpdates()
                    runCatching { it.seekTo(0) }
                    _playbackPositionMs.value = 0L
                    _playbackDurationMs.value = runCatching {
                        it.duration.toLong().coerceAtLeast(0L)
                    }.getOrDefault(_playbackDurationMs.value)
                    _isAudioPlaying.value = false
                }
                setOnErrorListener { _, _, _ ->
                    stopAudio()
                    _operationError.value = stringProvider.get(R.string.songbook_audio_play_error)
                    true
                }
                prepareAsync()
            }
        }.onFailure {
            _operationError.value = stringProvider.get(R.string.songbook_audio_play_error)
            stopAudio()
        }
    }

    fun seekAudioTo(progress: Float) {
        val player = mediaPlayer ?: return
        val duration = _playbackDurationMs.value.takeIf { it > 0L }
            ?: runCatching { player.duration.toLong().coerceAtLeast(0L) }.getOrDefault(0L)
        if (duration <= 0L) return
        val target = (duration.coerceAtMost(Int.MAX_VALUE.toLong()).toFloat() * progress.coerceIn(0f, 1f))
            .roundToInt()
            .coerceAtLeast(0)
        runCatching {
            player.seekTo(target)
            _playbackPositionMs.value = target.toLong()
            _playbackDurationMs.value = duration
        }.onFailure {
            _operationError.value = stringProvider.get(R.string.songbook_audio_play_error)
        }
    }

    fun stopAudio() {
        stopProgressUpdates()
        mediaPlayer?.release()
        mediaPlayer = null
        _playingSongId.value = null
        _playingAudioId.value = null
        _isAudioPlaying.value = false
        _playbackPositionMs.value = 0L
        _playbackDurationMs.value = 0L
    }

    fun clearOperationMessage() { _operationMessage.value = null }
    fun clearOperationError() { _operationError.value = null }

    override fun onCleared() {
        stopAudio()
        super.onCleared()
    }

    private fun publish(campingId: String) {
        val songs = songs(campingId)
        val title = campingTitleById[campingId] ?: stringProvider.get(R.string.common_camping)
        _uiState.value = if (songs.isEmpty()) SongbookUiState.Empty(title)
        else SongbookUiState.Loaded(songs, title)
    }

    private fun updateCanManage(user: AuthenticatedUser?, camping: CampingPermissionContext?) {
        val permissionUser = user?.let {
            PermissionUser(role = it.role, userId = it.uid, church = it.church)
        }
        _canManageSongbook.value = permissions.canManageSongbook(permissionUser, camping)
    }

    private fun canManageOrWarn(): Boolean {
        if (_canManageSongbook.value) return true
        _operationError.value = stringProvider.get(R.string.songbook_restricted_message)
        return false
    }

    private fun operationFailedMessage(): String =
        stringProvider.get(R.string.songbook_operation_failed)

    private fun startProgressUpdates() {
        playbackProgressJob?.cancel()
        playbackProgressJob = viewModelScope.launch {
            while (true) {
                val player = mediaPlayer ?: break
                updatePlaybackSnapshot(player)
                delay(500)
            }
        }
    }

    private fun stopProgressUpdates() {
        playbackProgressJob?.cancel()
        playbackProgressJob = null
    }

    private fun updatePlaybackSnapshot(player: MediaPlayer) {
        runCatching {
            _playbackPositionMs.value = player.currentPosition.toLong().coerceAtLeast(0L)
            _playbackDurationMs.value = player.duration.toLong().coerceAtLeast(0L)
        }
    }

    private fun upsert(song: Song, campingId: String) {
        val current = songs(campingId).toMutableList()
        if (song.isPinnedTheme) {
            current.replaceAll { it.copy(isPinnedTheme = it.id == song.id) }
        }
        current.removeAll { it.id == song.id }
        current += song
        songsByCampingId[campingId] = current.sortedWith(songComparator)
    }

    private fun nextPlayableSong(): Pair<String, Song>? {
        val (campingId, song) = currentPlayingSongEntry() ?: return null
        val orderedSongs = songs(campingId)
        val currentIndex = orderedSongs.indexOfFirst { it.id == song.id }
        if (currentIndex < 0) return null
        return orderedSongs
            .drop(currentIndex + 1)
            .firstOrNull { it.mainAudio?.downloadUrl?.isNotBlank() == true }
            ?.let { campingId to it }
    }

    private fun formFrom(song: Song): SongEditorForm = SongEditorForm(
        title = song.title,
        artist = song.artist,
        composer = song.composer,
        lyrics = plainLyrics(song),
        lyricsParts = lyricsPartsFor(song),
        chordSheetText = ChordProParser.toText(song.chordSheet).ifBlank { song.chords },
        existingAudioFiles = song.normalizedAudioFiles,
        youtubeLink = song.youtubeLink,
        pdfLink = song.pdfLink,
        isPinnedTheme = song.isPinnedTheme,
    )

    private fun resolvedLyrics(form: SongEditorForm): String {
        val parts = resolvedLyricsParts(form)
        if (parts.isNotEmpty()) return syncedLyricsText(parts)
        return form.lyrics.trim()
    }

    private fun resolvedLyricsParts(form: SongEditorForm): List<SongLyricsPart> {
        if (form.lyricsParts.isNotEmpty()) return form.lyricsParts
        return lyricsPartsFor(form.lyrics)
    }

    private fun lyricsPartsFor(song: Song): List<SongLyricsPart> =
        song.lyricsParts.ifEmpty { lyricsPartsFor(song.lyrics) }

    private fun lyricsPartsFor(lyrics: String): List<SongLyricsPart> =
        lyrics.trim().takeUnless { it.isBlank() }?.let {
            listOf(SongLyricsPart(id = UUID.randomUUID().toString(), kind = SongLyricsPartKind.Verse, number = 1, text = it))
        }.orEmpty()

    private fun plainLyrics(song: Song): String =
        if (song.lyricsParts.isNotEmpty()) syncedLyricsText(song.lyricsParts)
        else song.lyrics

    private fun syncedLyricsText(parts: List<SongLyricsPart>): String =
        parts.joinToString("\n\n") { "[${it.displayTitle()}]\n${it.text}" }

    private fun resetLyricsPartEditor(form: SongEditorForm): SongEditorForm {
        val kind = nextLyricsPartKind(form.lyricsParts)
        return form.copy(
            editingLyricsPartId = null,
            selectedLyricsPartKind = kind,
            selectedLyricsPartNumber = nextLyricsPartNumber(form.lyricsParts, kind),
            selectedLyricsPartTitle = "",
            lyricsPartText = "",
        )
    }

    private fun nextLyricsPartKind(parts: List<SongLyricsPart>): SongLyricsPartKind =
        parts.lastOrNull()?.kind ?: SongLyricsPartKind.Verse

    private fun nextLyricsPartNumber(parts: List<SongLyricsPart>, kind: SongLyricsPartKind): Int =
        ((parts.filter { it.kind == kind }.maxOfOrNull { it.number } ?: 0) + 1).coerceIn(1, 24)

    private fun audioKind(fileName: String, contentType: String): SongAudioKind {
        val name = fileName.lowercase()
        val type = contentType.lowercase()
        return when {
            name.endsWith(".mp3") || type in setOf("audio/mpeg", "audio/mp3") -> SongAudioKind.Mp3
            name.endsWith(".m4a") || type in setOf("audio/mp4", "audio/x-m4a") -> SongAudioKind.M4a
            name.endsWith(".wav") || type in setOf("audio/wav", "audio/x-wav") -> SongAudioKind.Wav
            name.endsWith(".aac") || type in setOf("audio/aac", "audio/x-aac") -> SongAudioKind.Aac
            else -> SongAudioKind.Other
        }
    }

    private fun isSupportedAudio(fileName: String, contentType: String): Boolean {
        val name = fileName.lowercase()
        val type = contentType.lowercase()
        return listOf(".mp3", ".m4a", ".aac", ".wav").any(name::endsWith) ||
            type in setOf(
                "audio/mpeg",
                "audio/mp3",
                "audio/mp4",
                "audio/x-m4a",
                "audio/aac",
                "audio/x-aac",
                "audio/wav",
                "audio/x-wav",
            )
    }

    private fun contentTypeForAudioName(fileName: String): String = when {
        fileName.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
        fileName.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
        fileName.endsWith(".aac", ignoreCase = true) -> "audio/aac"
        fileName.endsWith(".wav", ignoreCase = true) -> "audio/wav"
        else -> "application/octet-stream"
    }

    private fun isTrackTypeTaken(type: SongAudioTrackType, excludingId: String?): Boolean {
        if (type.allowsMultiple) return false
        val form = _form.value
        return form.existingAudioFiles.any { it.id != excludingId && it.trackType == type } ||
            form.pendingAudioFiles.any { it.id != excludingId && it.trackType == type }
    }

    private fun nextAvailableTrackType(): SongAudioTrackType =
        SongAudioTrackType.entries.firstOrNull { !it.allowsMultiple && !isTrackTypeTaken(it, null) }
            ?: SongAudioTrackType.Other

    private fun promoteMainSongIfNeeded() {
        val form = _form.value
        if (form.existingAudioFiles.any { it.trackType == SongAudioTrackType.MainSong } ||
            form.pendingAudioFiles.any { it.trackType == SongAudioTrackType.MainSong }
        ) return
        _form.value = when {
            form.existingAudioFiles.isNotEmpty() -> form.copy(
                existingAudioFiles = form.existingAudioFiles.mapIndexed { index, audio ->
                    if (index == 0) audio.withTrackType(SongAudioTrackType.MainSong) else audio
                },
            )
            form.pendingAudioFiles.isNotEmpty() -> form.copy(
                pendingAudioFiles = form.pendingAudioFiles.mapIndexed { index, audio ->
                    if (index == 0) audio.withTrackType(SongAudioTrackType.MainSong) else audio
                },
            )
            else -> form
        }
    }
}

internal object ChordProParser {
    private val bracketRegex = Regex("""\[(.+?)]""")
    private val leadingSectionRegex = Regex("""^\s*\[([^\]]+)]""")
    private val sectionWords = setOf(
        "intro", "verse", "pre-chorus", "prechorus", "chorus", "bridge",
        "instrumental", "outro", "tag", "ending",
    )

    fun parse(text: String, existingId: String = UUID.randomUUID().toString()): ChordSheet {
        val lines = text.lines().flatMap(::parseLine)
        return ChordSheet(
            id = existingId,
            originalKey = detectOriginalKey(lines),
            lines = lines,
        )
    }

    fun toText(sheet: ChordSheet): String {
        if (sheet.lines.isEmpty()) return ""
        return buildString {
            sheet.lines.forEach { line ->
                if (line.isSectionHeader) {
                    appendLine(line.text)
                } else {
                    if (line.chords.isNotEmpty()) {
                        appendLine(chordLineFor(line))
                    }
                    appendLine(line.text)
                }
            }
        }.trimEnd()
    }

    fun renderedChordLine(line: ChordLine): String = chordLineFor(line)

    private fun parseLine(raw: String): List<ChordLine> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return listOf(ChordLine(id = UUID.randomUUID().toString()))

        val onlyBracket = bracketRegex.matchEntire(trimmed)
        if (onlyBracket != null) {
            val value = onlyBracket.groupValues[1].trim()
            if (isSectionHeader(value)) {
                return listOf(
                    ChordLine(
                        id = UUID.randomUUID().toString(),
                        text = "[$value]",
                        isSectionHeader = true,
                    ),
                )
            }
        }

        val leadingSection = leadingSectionRegex.find(raw)
        if (leadingSection != null && isSectionHeader(leadingSection.groupValues[1])) {
            val header = ChordLine(
                id = UUID.randomUUID().toString(),
                text = "[${leadingSection.groupValues[1].trim()}]",
                isSectionHeader = true,
            )
            val trailingStart = leadingSection.range.last + 1
            val trailingText = raw.substring(trailingStart)
            val trailingTokens = ChordSymbolParser.chordTokensIn(trailingText)
            if (trailingTokens.isEmpty()) return listOf(header)

            return listOf(
                header,
                ChordLine(
                    id = UUID.randomUUID().toString(),
                    text = "",
                    chords = trailingTokens.map { token ->
                        Chord(
                            id = UUID.randomUUID().toString(),
                            chord = token.symbol,
                            position = trailingStart + token.start,
                        )
                    },
                ),
            )
        }

        val inline = parseInlineChordPro(raw)
        if (inline != null) return inline

        if (looksLikeChordLine(trimmed)) {
            val chords = ChordSymbolParser.chordTokensIn(raw)
                .map { token ->
                    Chord(
                        id = UUID.randomUUID().toString(),
                        chord = token.symbol,
                        position = token.start,
                    )
                }
            return listOf(
                ChordLine(
                    id = UUID.randomUUID().toString(),
                    text = "",
                    chords = chords,
                ),
            )
        }

        return listOf(ChordLine(id = UUID.randomUUID().toString(), text = raw))
    }

    private fun parseInlineChordPro(raw: String): List<ChordLine>? {
        val matches = bracketRegex.findAll(raw).toList()
        if (matches.none()) return null

        val output = StringBuilder()
        val chords = mutableListOf<Chord>()
        var cursor = 0

        matches.forEach { match ->
            output.append(raw.substring(cursor, match.range.first))
            val token = match.groupValues[1].trim()
            val parsedChord = ChordSymbolParser.parseOrNull(token)
            if (parsedChord != null) {
                chords += Chord(
                    id = UUID.randomUUID().toString(),
                    chord = parsedChord.canonicalSymbol,
                    position = output.length,
                )
            } else if (output.toString().isBlank() && isSectionHeader(token)) {
                output.append("[$token]")
            } else {
                output.append(match.value)
            }
            cursor = match.range.last + 1
        }
        output.append(raw.substring(cursor))

        val rendered = output.toString()
        if (chords.isEmpty() && !isSectionHeader(rendered.removeSurrounding("[", "]"))) {
            return null
        }

        if (chords.isEmpty() && isSectionHeader(rendered.removeSurrounding("[", "]"))) {
            return listOf(
                ChordLine(
                    id = UUID.randomUUID().toString(),
                    text = rendered,
                    isSectionHeader = true,
                ),
            )
        }

        return listOf(
            ChordLine(
                id = UUID.randomUUID().toString(),
                text = rendered,
                chords = chords,
            ),
        )
    }

    private fun chordLineFor(line: ChordLine): String {
        val sorted = line.chords.sortedWith(compareBy<Chord> { it.position }.thenBy { it.chord })
        val output = StringBuilder()
        var cursor = 0
        sorted.forEach { chord ->
            val target = chord.position.coerceAtLeast(cursor)
            if (target > cursor) {
                output.append(" ".repeat(target - cursor))
            } else if (output.isNotEmpty()) {
                output.append(' ')
            }
            output.append(chord.chord)
            cursor = output.length
        }
        return output.toString()
    }

    private fun looksLikeChordLine(text: String): Boolean {
        val semanticTokens = text.split(Regex("""\s+"""))
            .map { stripChartPunctuation(it) }
            .filter { it.isNotBlank() }
        if (semanticTokens.isEmpty()) return false

        val chordTokens = ChordSymbolParser.chordTokensIn(text)
        if (chordTokens.isEmpty()) return false

        val validChordTokens = semanticTokens.count { isChord(it) }
        val chordRatio = validChordTokens.toDouble() / semanticTokens.size
        val musicRatio = semanticTokens.sumOf(::countMusicCharacters).toDouble() / text.trim().length.coerceAtLeast(1)

        return chordRatio >= 0.65 || (chordRatio >= 0.45 && musicRatio >= 0.18)
    }

    private fun isSectionHeader(text: String): Boolean {
        val normalized = text.trim()
            .lowercase()
            .replace(Regex("""\s+\d+$"""), "")
        return normalized in sectionWords
    }

    private fun isChord(text: String): Boolean =
        ChordSymbolParser.isChord(stripChartPunctuation(text))

    private fun stripChartPunctuation(token: String): String {
        var stripped = token.trim().trim { it in "|:[]{};," }
        if (stripped.length > 2 && stripped.first() == '(' && stripped.last() == ')') {
            val inner = stripped.substring(1, stripped.lastIndex)
            if (ChordSymbolParser.isChord(inner)) stripped = inner
        }
        return stripped
    }

    private fun countMusicCharacters(text: String): Int =
        text.count { char ->
            char.isDigit() || char in setOf('#', '♯', 'b', '♭', '/', '°', 'º', 'ø', 'Δ', '+', '-', '(', ')', ',')
        }

    private fun detectOriginalKey(lines: List<ChordLine>): String =
        lines.asSequence()
            .flatMap { it.chords.asSequence() }
            .map { it.chord.trim() }
            .firstOrNull { it.startsWith("G") || it.startsWith("D") || it.startsWith("A") || it.startsWith("F") ||
                it.startsWith("Bb") || it.startsWith("B♭") || it.startsWith("Am") || it.startsWith("Em") }
            ?.let { chord ->
                when {
                    chord.startsWith("Bb") || chord.startsWith("B♭") -> "Bb"
                    chord.startsWith("Am") -> "Am"
                    chord.startsWith("Em") -> "Em"
                    else -> chord.take(1)
                }
            }
            ?: "C"
}

private val songComparator = compareByDescending<Song> { it.isPinnedTheme }
    .thenBy { it.orderIndex }
    .thenBy { it.title.lowercase() }

private fun <T> MutableList<T>.swap(first: Int, second: Int) {
    val tmp = this[first]
    this[first] = this[second]
    this[second] = tmp
}
