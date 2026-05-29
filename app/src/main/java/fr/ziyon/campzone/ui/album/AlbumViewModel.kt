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
            _uiState.value = AlbumUiState.Loading
            runCatching {
                AlbumUiState.Loaded(
                    media = albumService.loadMedia(campingId),
                    settings = albumService.loadSettings(campingId),
                )
            }.onSuccess { loaded ->
                _uiState.value = loaded
            }.onFailure { error ->
                _uiState.value = AlbumUiState.Error(error.message ?: "Could not load album.")
            }
        }
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
        if (_isUploading.value) return
        viewModelScope.launch {
            _isUploading.value = true
            _operationMessage.value = null
            runCatching {
                val result = when (kind) {
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
                val item = MediaItem(
                    id = UUID.randomUUID().toString(),
                    campingId = campingId,
                    kind = kind,
                    secureUrl = result.secureUrl,
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
            _isUploading.value = false
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
}
