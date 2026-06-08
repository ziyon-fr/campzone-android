package fr.ziyon.campzone.ui.venuemap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.games.GameService
import fr.ziyon.campzone.data.media.ImageUploader
import fr.ziyon.campzone.data.model.VenueCategory
import fr.ziyon.campzone.data.model.VenueIconCatalog
import fr.ziyon.campzone.data.model.VenueMap
import fr.ziyon.campzone.data.model.VenuePoint
import fr.ziyon.campzone.data.model.visibleForGameLocationRules
import fr.ziyon.campzone.data.venuemap.VenueMapService
import fr.ziyon.campzone.data.venuemap.ParsedGpxPoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Editor form for a single pin's metadata (name/category/note); position and
 *  coordinate are captured separately, mirroring the iOS `VenuePointForm`. */
data class VenuePointForm(
    val name: String = "",
    val category: VenueCategory = VenueCategory.Other,
    val customCategoryName: String = "",
    val customIconName: String = VenueIconCatalog.defaultIconName,
    val note: String = "",
    val latitudeText: String = "",
    val longitudeText: String = "",
) {
    val trimmedName: String get() = name.trim()
    val trimmedCustomCategoryName: String get() = customCategoryName.trim()
    val hasCoordinateInput: Boolean
        get() = latitudeText.isNotBlank() || longitudeText.isNotBlank()
    val parsedCoordinate: Pair<Double, Double>?
        get() {
            val lat = latitudeText.trim().toDoubleOrNull()
            val lon = longitudeText.trim().toDoubleOrNull()
            return if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                lat to lon
            } else {
                null
            }
        }
    val isValid: Boolean
        get() = trimmedName.isNotEmpty() &&
            (category != VenueCategory.Custom || trimmedCustomCategoryName.isNotEmpty()) &&
            (!hasCoordinateInput || parsedCoordinate != null)

    companion object {
        fun of(point: VenuePoint) = VenuePointForm(
            name = point.name,
            category = point.category,
            customCategoryName = point.customCategoryName.orEmpty(),
            customIconName = point.customIconName ?: VenueIconCatalog.defaultIconName,
            note = point.note,
            latitudeText = point.latitude?.toString().orEmpty(),
            longitudeText = point.longitude?.toString().orEmpty(),
        )
    }
}

sealed interface VenueMapUiState {
    data object Loading : VenueMapUiState

    data class Error(val message: String) : VenueMapUiState

    data class Ready(
        val campingId: String,
        val map: VenueMap,
        /** `canManageTeams || canManageSchedule` — drives editor access + the viewer's edit affordances. */
        val canManage: Boolean,
        val campLatitude: Double? = null,
        val campLongitude: Double? = null,
        val selectedPointId: String? = null,
        val isSaving: Boolean = false,
        val isUploadingImage: Boolean = false,
        val operationError: String? = null,
        val operationMessage: String? = null,
    ) : VenueMapUiState {
        val selectedPoint: VenuePoint?
            get() = selectedPointId?.let { id -> map.points.firstOrNull { it.id == id } }
    }
}

/**
 * Backs both the read-only [VenueMapScreen] and the manager [VenueMapEditorScreen]
 * for one camp's `venueMap/config`. Mirrors the iOS `VenueMapObserver`: a single
 * owned Firestore listener keeps the embedded pin set live, every pin/image
 * mutation rewrites the whole config doc, and the site illustration is uploaded
 * through the shared backend-signed Cloudinary path. Read is open to signed-in
 * users; mutating calls are only reachable from the editor (gated `canManage`).
 */
@HiltViewModel
class VenueMapViewModel @Inject constructor(
    private val service: VenueMapService,
    private val campingService: CampingService,
    private val gameService: GameService,
    private val imageUploader: ImageUploader,
) : ViewModel() {

    private val permissions = AppPermissionEvaluator()

    private val _uiState = MutableStateFlow<VenueMapUiState>(VenueMapUiState.Loading)
    val uiState: StateFlow<VenueMapUiState> = _uiState.asStateFlow()

    private var campingId: String = ""
    private var user: AuthenticatedUser? = null
    private var canManage: Boolean = false
    private var canManageGames: Boolean = false
    private var campLatitude: Double? = null
    private var campLongitude: Double? = null
    private var gamesForVisibility = emptyList<fr.ziyon.campzone.data.model.Game>()
    private var observeJob: Job? = null
    private var loadedKey: Pair<String, String>? = null

    fun load(campingId: String, user: AuthenticatedUser) {
        val key = campingId to user.uid
        if (loadedKey == key && _uiState.value !is VenueMapUiState.Error) return
        loadedKey = key
        this.campingId = campingId
        this.user = user
        _uiState.value = VenueMapUiState.Loading

        viewModelScope.launch {
            runCatching { campingService.fetchCamping(campingId) }
                .onSuccess { camping ->
                    val context = CampingPermissionContext(
                        organizerLevelType = camping.organizerLevel.type.wireValue,
                        organizerLevelValue = camping.organizerLevel.value,
                        createdByUid = camping.createdByUid,
                    )
                    val permissionUser = PermissionUser(user.role, user.uid, user.church)
                    canManage = permissions.canManageTeams(permissionUser, context) ||
                        permissions.canManageSchedule(permissionUser, context)
                    canManageGames = permissions.canManageGames(permissionUser, context)
                    campLatitude = camping.locationLatitude
                    campLongitude = camping.locationLongitude
                    gamesForVisibility = runCatching { gameService.loadGames(campingId) }
                        .getOrDefault(emptyList())
                    observeMap()
                }
                .onFailure {
                    // The camp lookup is only needed for permission + camp coordinates;
                    // a transient failure there must not hide a published map, so fall
                    // back to a read-only (non-managing) view and still stream the map.
                    canManage = false
                    canManageGames = false
                    campLatitude = null
                    campLongitude = null
                    gamesForVisibility = emptyList()
                    observeMap()
                }
        }
    }

    fun retry() {
        val current = user ?: return
        loadedKey = null
        load(campingId, current)
    }

    private fun observeMap() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            service.observeMap(campingId)
                .catch { error ->
                    _uiState.value = VenueMapUiState.Error(error.message ?: DEFAULT_ERROR)
                }
                .collect { map ->
                    val visibleMap = map.visibleForGameLocationRules(
                        games = gamesForVisibility,
                        canSeeHiddenGameLocations = canManage || canManageGames,
                    )
                    _uiState.update { current ->
                        val ready = current as? VenueMapUiState.Ready
                        VenueMapUiState.Ready(
                            campingId = campingId,
                            map = visibleMap,
                            canManage = canManage,
                            campLatitude = campLatitude,
                            campLongitude = campLongitude,
                            selectedPointId = ready?.selectedPointId
                                ?.takeIf { id -> map.points.any { it.id == id } },
                            isSaving = ready?.isSaving ?: false,
                            isUploadingImage = ready?.isUploadingImage ?: false,
                            operationError = ready?.operationError,
                            operationMessage = ready?.operationMessage,
                        )
                    }
                }
        }
    }

    // MARK: - Selection (viewer)

    fun selectPoint(id: String?) {
        _uiState.update { (it as? VenueMapUiState.Ready)?.copy(selectedPointId = id) ?: it }
    }

    // MARK: - Pin CRUD (editor)

    /** Creates a new pin (using [imageX]/[imageY] when placed on the illustration)
     *  or updates [editingId]'s metadata. */
    fun savePoint(
        form: VenuePointForm,
        editingId: String?,
        imageX: Double? = null,
        imageY: Double? = null,
    ) {
        if (!form.isValid) return
        val ready = _uiState.value as? VenueMapUiState.Ready ?: return
        val points = ready.map.points.toMutableList()

        if (editingId != null) {
            val index = points.indexOfFirst { it.id == editingId }
            if (index < 0) return
            points[index] = points[index].copy(
                name = form.trimmedName,
                category = form.category,
                customCategoryName = form.resolvedCustomCategoryName(),
                customIconName = form.resolvedCustomIconName(),
                note = form.note.trim(),
                latitude = form.parsedCoordinate?.first,
                longitude = form.parsedCoordinate?.second,
            )
        } else {
            points += VenuePoint(
                id = UUID.randomUUID().toString(),
                name = form.trimmedName,
                category = form.category,
                customCategoryName = form.resolvedCustomCategoryName(),
                customIconName = form.resolvedCustomIconName(),
                note = form.note.trim(),
                imageX = imageX?.coerceIn(0.0, 1.0),
                imageY = imageY?.coerceIn(0.0, 1.0),
                latitude = form.parsedCoordinate?.first,
                longitude = form.parsedCoordinate?.second,
            )
        }
        persist(ready.map.copy(points = points), MSG_SAVED)
    }

    fun importGpxPoints(parsedPoints: List<ParsedGpxPoint>) {
        if (parsedPoints.isEmpty()) return
        val ready = _uiState.value as? VenueMapUiState.Ready ?: return
        val imported = parsedPoints.map { point ->
            VenuePoint(
                id = UUID.randomUUID().toString(),
                name = point.name,
                category = VenueCategory.Other,
                latitude = point.latitude,
                longitude = point.longitude,
            )
        }
        persist(ready.map.copy(points = ready.map.points + imported), MSG_IMPORTED)
    }

    fun deletePoint(id: String) {
        val ready = _uiState.value as? VenueMapUiState.Ready ?: return
        persist(ready.map.copy(points = ready.map.points.filterNot { it.id == id }), MSG_REMOVED)
    }

    /** Moves an existing pin's illustration position (reposition flow). */
    fun movePoint(id: String, imageX: Double, imageY: Double) {
        val ready = _uiState.value as? VenueMapUiState.Ready ?: return
        val points = ready.map.points.map {
            if (it.id == id) it.copy(imageX = imageX.coerceIn(0.0, 1.0), imageY = imageY.coerceIn(0.0, 1.0)) else it
        }
        persist(ready.map.copy(points = points), MSG_MOVED)
    }

    /** Sets (or clears, when both are null) a pin's real-world coordinate. */
    fun setCoordinate(pointId: String, latitude: Double?, longitude: Double?) {
        val ready = _uiState.value as? VenueMapUiState.Ready ?: return
        val points = ready.map.points.map {
            if (it.id == pointId) it.copy(latitude = latitude, longitude = longitude) else it
        }
        persist(
            ready.map.copy(points = points),
            if (latitude == null) MSG_COORD_CLEARED else MSG_COORD_SET,
        )
    }

    // MARK: - Site illustration (Cloudinary)

    fun uploadSiteImage(bytes: ByteArray, mimeType: String, fileExtension: String) {
        val ready = _uiState.value as? VenueMapUiState.Ready ?: return
        _uiState.update { (it as? VenueMapUiState.Ready)?.copy(isUploadingImage = true, operationError = null) ?: it }
        viewModelScope.launch {
            runCatching {
                imageUploader.uploadImage(
                    assetIdPrefix = "campzone/campings/$campingId/venue-map",
                    folder = "campzone/campings",
                    tags = listOf("campzone", "venue_map", "camping_$campingId"),
                    bytes = bytes,
                    mimeType = mimeType,
                    fileExtension = fileExtension,
                )
            }.onSuccess { result ->
                val latest = (_uiState.value as? VenueMapUiState.Ready)?.map ?: ready.map
                persist(
                    latest.copy(imageUrl = result.secureUrl, imagePublicId = result.publicId),
                    MSG_IMAGE_UPDATED,
                    clearUploading = true,
                )
            }.onFailure { error ->
                _uiState.update {
                    (it as? VenueMapUiState.Ready)?.copy(
                        isUploadingImage = false,
                        operationError = error.message ?: DEFAULT_UPLOAD_ERROR,
                    ) ?: it
                }
            }
        }
    }

    fun removeSiteImage() {
        val ready = _uiState.value as? VenueMapUiState.Ready ?: return
        persist(ready.map.copy(imageUrl = null, imagePublicId = null), MSG_IMAGE_REMOVED)
    }

    fun clearOperationError() {
        _uiState.update { (it as? VenueMapUiState.Ready)?.copy(operationError = null) ?: it }
    }

    fun clearOperationMessage() {
        _uiState.update { (it as? VenueMapUiState.Ready)?.copy(operationMessage = null) ?: it }
    }

    private fun persist(map: VenueMap, message: String, clearUploading: Boolean = false) {
        _uiState.update {
            val ready = it as? VenueMapUiState.Ready ?: return@update it
            ready.copy(
                isSaving = true,
                isUploadingImage = if (clearUploading) false else ready.isUploadingImage,
                operationError = null,
            )
        }
        viewModelScope.launch {
            runCatching { service.saveMap(map) }
                .onSuccess { saved ->
                    _uiState.update {
                        (it as? VenueMapUiState.Ready)?.copy(
                            map = saved,
                            isSaving = false,
                            isUploadingImage = false,
                            operationMessage = message,
                        ) ?: it
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        (it as? VenueMapUiState.Ready)?.copy(
                            isSaving = false,
                            isUploadingImage = false,
                            operationError = error.message ?: DEFAULT_OP_ERROR,
                        ) ?: it
                    }
                }
        }
    }

    private companion object {
        const val DEFAULT_ERROR = "The venue map could not be loaded."
        const val DEFAULT_OP_ERROR = "The change could not be saved."
        const val DEFAULT_UPLOAD_ERROR = "The site map could not be uploaded."
        const val MSG_SAVED = "Location saved."
        const val MSG_REMOVED = "Location removed."
        const val MSG_MOVED = "Pin moved."
        const val MSG_COORD_SET = "Map location set."
        const val MSG_COORD_CLEARED = "Map location cleared."
        const val MSG_IMPORTED = "GPX locations imported."
        const val MSG_IMAGE_UPDATED = "Site map updated."
        const val MSG_IMAGE_REMOVED = "Site map removed."
    }
}

private fun VenuePointForm.resolvedCustomCategoryName(): String? =
    if (category == VenueCategory.Custom) trimmedCustomCategoryName.takeUnless { it.isBlank() } else null

private fun VenuePointForm.resolvedCustomIconName(): String? =
    if (category == VenueCategory.Custom) customIconName.takeUnless { it.isBlank() } else null
