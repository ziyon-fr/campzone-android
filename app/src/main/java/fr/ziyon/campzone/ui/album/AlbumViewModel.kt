package fr.ziyon.campzone.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.album.AlbumService
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.media.AudioUploader
import fr.ziyon.campzone.data.media.ImageUploader
import fr.ziyon.campzone.data.model.AlbumSettings
import fr.ziyon.campzone.data.model.MediaItem
import fr.ziyon.campzone.data.model.MediaKind
import fr.ziyon.campzone.data.model.MediaSource
import java.io.File
import java.net.URL
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AlbumUiState {
    data object Loading : AlbumUiState
    data class Loaded(
        val media: List<MediaItem>,
        val settings: AlbumSettings,
    ) : AlbumUiState

    data class Error(val message: String) : AlbumUiState
}

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val albumService: AlbumService,
    private val imageUploader: ImageUploader,
    private val audioUploader: AudioUploader,
) : ViewModel() {
    private val _uiState = MutableStateFlow<AlbumUiState>(AlbumUiState.Loading)
    val uiState: StateFlow<AlbumUiState> = _uiState.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    private var loadedCampingId: String? = null

    fun loadIfNeeded(campingId: String) {
        if (loadedCampingId == campingId && _uiState.value is AlbumUiState.Loaded) return
        load(campingId)
    }

    fun load(campingId: String) {
        loadedCampingId = campingId
        viewModelScope.launch {
            loadAlbum(campingId, preserveLoadedContent = false)
        }
    }

    fun refresh(campingId: String) {
        loadedCampingId = campingId
        viewModelScope.launch {
            loadAlbum(campingId, preserveLoadedContent = true)
        }
    }

    private suspend fun loadAlbum(campingId: String, preserveLoadedContent: Boolean) {
        val currentMedia = (_uiState.value as? AlbumUiState.Loaded)?.media.orEmpty()
        val canPreserveContent = preserveLoadedContent && currentMedia.isNotEmpty()
        if (canPreserveContent) {
            _isRefreshing.value = true
        } else {
            _uiState.value = AlbumUiState.Loading
        }
        _operationMessage.value = null
        runCatching {
            AlbumUiState.Loaded(
                media = albumService.loadMedia(campingId),
                settings = albumService.loadSettings(campingId),
            )
        }.onSuccess { loaded ->
            _uiState.value = loaded
        }.onFailure { error ->
            val message = error.message ?: "Could not load album."
            if (canPreserveContent) {
                _operationMessage.value = message
            } else {
                _uiState.value = AlbumUiState.Error(message)
            }
        }
        _isRefreshing.value = false
    }

    fun uploadMedia(
        campingId: String,
        kind: MediaKind,
        bytes: ByteArray,
        mimeType: String,
        fileExtension: String,
        caption: String,
        uploader: AuthenticatedUser,
    ) {
        uploadMediaWithResult(
            campingId = campingId,
            kind = kind,
            caption = caption,
            uploader = uploader,
        ) {
            when (kind) {
                MediaKind.Photo -> imageUploader.uploadImage(
                    assetIdPrefix = "album-photo",
                    folder = "campzone/$campingId",
                    tags = listOf("campzone", "camping:$campingId", "photo"),
                    bytes = bytes,
                    mimeType = mimeType,
                    fileExtension = fileExtension,
                )

                MediaKind.Video -> audioUploader.uploadAudio(
                    assetIdPrefix = "album-video",
                    folder = "campzone/$campingId",
                    tags = listOf("campzone", "camping:$campingId", "video"),
                    bytes = bytes,
                    mimeType = mimeType,
                    fileExtension = fileExtension,
                )
            }
        }
    }

    fun uploadMediaFile(
        campingId: String,
        kind: MediaKind,
        file: File,
        mimeType: String,
        fileExtension: String,
        caption: String,
        uploader: AuthenticatedUser,
        removeWhenDone: Boolean = true,
    ) {
        uploadMediaWithResult(
            campingId = campingId,
            kind = kind,
            caption = caption,
            uploader = uploader,
            cleanup = {
                if (removeWhenDone) file.delete()
            },
        ) {
            when (kind) {
                MediaKind.Photo -> imageUploader.uploadImageFile(
                    assetIdPrefix = "album-photo",
                    folder = "campzone/$campingId",
                    tags = listOf("campzone", "camping:$campingId", "photo"),
                    file = file,
                    mimeType = mimeType,
                    fileExtension = fileExtension,
                )

                MediaKind.Video -> audioUploader.uploadAudioFile(
                    assetIdPrefix = "album-video",
                    folder = "campzone/$campingId",
                    tags = listOf("campzone", "camping:$campingId", "video"),
                    file = file,
                    mimeType = mimeType,
                    fileExtension = fileExtension,
                )
            }
        }
    }

    private fun uploadMediaWithResult(
        campingId: String,
        kind: MediaKind,
        caption: String,
        uploader: AuthenticatedUser,
        cleanup: () -> Unit = {},
        upload: suspend () -> fr.ziyon.campzone.data.media.CloudinaryUploadResult,
    ) {
        if (_isUploading.value) {
            cleanup()
            return
        }
        viewModelScope.launch {
            _isUploading.value = true
            _operationMessage.value = null
            runCatching {
                val result = upload()
                val item = MediaItem(
                    id = UUID.randomUUID().toString(),
                    campingId = campingId,
                    kind = kind,
                    source = MediaSource.Cloudinary,
                    secureUrl = result.secureUrl,
                    externalUrl = null,
                    publicId = result.publicId,
                    uploaderId = uploader.uid,
                    uploaderName = uploader.preferredDisplayName,
                    caption = caption.trim(),
                    thumbnailUrl = thumbnailUrlFor(kind, result.secureUrl),
                    width = result.width,
                    height = result.height,
                    durationSeconds = result.duration,
                    uploadedAt = Date(),
                )
                albumService.addMedia(item)
                item
            }.onSuccess { item ->
                _uiState.update { state ->
                    if (state is AlbumUiState.Loaded) {
                        state.copy(media = (listOf(item) + state.media).distinctBy { it.id })
                    } else {
                        state
                    }
                }
                _operationMessage.value = "Uploaded to album."
            }.onFailure { error ->
                _operationMessage.value = error.message ?: "Upload failed."
            }
            cleanup()
            _isUploading.value = false
        }
    }

    fun addExternalVideo(
        campingId: String,
        videoUrl: String,
        thumbnailUrl: String?,
        caption: String,
        uploader: AuthenticatedUser,
    ) {
        val normalizedVideoUrl = normalizedWebUrl(videoUrl)
        val normalizedThumbnailUrl = normalizedWebUrl(thumbnailUrl.orEmpty())
        if (normalizedVideoUrl == null || (!thumbnailUrl.isNullOrBlank() && normalizedThumbnailUrl == null)) {
            _operationMessage.value = "Add a valid video link."
            return
        }
        viewModelScope.launch {
            _operationMessage.value = null
            runCatching {
                val item = MediaItem(
                    id = UUID.randomUUID().toString(),
                    campingId = campingId,
                    kind = MediaKind.Video,
                    source = MediaSource.ExternalVideo,
                    secureUrl = normalizedVideoUrl,
                    externalUrl = normalizedVideoUrl,
                    publicId = null,
                    uploaderId = uploader.uid,
                    uploaderName = uploader.preferredDisplayName,
                    caption = caption.trim(),
                    thumbnailUrl = normalizedThumbnailUrl,
                    uploadedAt = Date(),
                )
                albumService.addMedia(item)
                item
            }.onSuccess { item ->
                _uiState.update { state ->
                    if (state is AlbumUiState.Loaded) {
                        state.copy(media = (listOf(item) + state.media).distinctBy { it.id })
                    } else {
                        AlbumUiState.Loaded(media = listOf(item), settings = AlbumSettings())
                    }
                }
                _operationMessage.value = "Added to album."
            }.onFailure { error ->
                _operationMessage.value = error.message ?: "Could not add video."
            }
        }
    }

    fun updateCaption(campingId: String, mediaId: String, caption: String) {
        viewModelScope.launch {
            runCatching {
                albumService.updateCaption(campingId, mediaId, caption)
            }.onSuccess {
                _uiState.update { state ->
                    if (state is AlbumUiState.Loaded) {
                        state.copy(
                            media = state.media.map { item ->
                                if (item.id == mediaId) item.copy(caption = caption.trim()) else item
                            },
                        )
                    } else {
                        state
                    }
                }
                _operationMessage.value = "Caption updated."
            }.onFailure { error ->
                _operationMessage.value = error.message ?: "Could not update caption."
            }
        }
    }

    fun deleteMedia(campingId: String, mediaId: String) {
        viewModelScope.launch {
            runCatching {
                albumService.deleteMedia(campingId, mediaId)
            }.onSuccess {
                _uiState.update { state ->
                    if (state is AlbumUiState.Loaded) {
                        state.copy(media = state.media.filterNot { it.id == mediaId })
                    } else {
                        state
                    }
                }
                _operationMessage.value = "Media deleted."
            }.onFailure { error ->
                _operationMessage.value = error.message ?: "Could not delete media."
            }
        }
    }

    fun setRoleAllowed(campingId: String, role: UserRole, allowed: Boolean) {
        val loaded = _uiState.value as? AlbumUiState.Loaded ?: return
        val current = loaded.settings.allowedUploadRoles.toMutableList()
        if (allowed) {
            if (!current.contains(role)) current.add(role)
        } else {
            current.remove(role)
        }
        val updated = AlbumSettings(current.distinct().sortedBy { it.rawValue })
        viewModelScope.launch {
            runCatching {
                albumService.saveSettings(campingId, updated)
            }.onSuccess {
                _uiState.value = loaded.copy(settings = updated)
                _operationMessage.value = "Permissions updated."
            }.onFailure { error ->
                _operationMessage.value = error.message ?: "Could not update permissions."
            }
        }
    }

    internal fun thumbnailUrlFor(kind: MediaKind, secureUrl: String): String? = when (kind) {
        MediaKind.Photo -> derivedCloudinaryUrl(secureUrl, "f_auto,q_auto,w_600,c_limit")
        MediaKind.Video -> derivedCloudinaryUrl(secureUrl, "so_0,f_jpg,q_auto,w_600")
    }

    private fun derivedCloudinaryUrl(secureUrl: String, transform: String): String? {
        val url = runCatching { URL(secureUrl) }.getOrNull() ?: return null
        val parts = url.path.split("/").filter { it.isNotBlank() }.toMutableList()
        val uploadIndex = parts.indexOf("upload")
        if (uploadIndex < 0) return null
        parts.add(uploadIndex + 1, transform)
        return "${url.protocol}://${url.host}/${parts.joinToString("/")}"
    }

    private fun normalizedWebUrl(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        val lower = trimmed.lowercase()
        if ("://" in lower && !lower.startsWith("http://") && !lower.startsWith("https://")) {
            return null
        }
        val candidate = if (lower.startsWith("http://") || lower.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return runCatching { URL(candidate) }
            .getOrNull()
            ?.takeIf { it.host.isNotBlank() }
            ?.toString()
    }
}
