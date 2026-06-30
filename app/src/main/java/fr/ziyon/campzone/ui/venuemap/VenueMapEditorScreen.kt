package fr.ziyon.campzone.ui.venuemap

import android.Manifest
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTextField
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.VenueCategory
import fr.ziyon.campzone.data.model.VenueIconCatalog
import fr.ziyon.campzone.data.model.VenueMap
import fr.ziyon.campzone.data.model.VenuePoint
import fr.ziyon.campzone.data.model.hasImage
import fr.ziyon.campzone.data.model.isAtPointCapacity
import fr.ziyon.campzone.data.model.remainingPointCapacity
import fr.ziyon.campzone.data.venuemap.GPXParser
import fr.ziyon.campzone.data.venuemap.ParsedGpxPoint
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.util.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Where the next illustration tap lands. */
private sealed interface Placement {
    data object New : Placement
    data class Move(val id: String) : Placement
}

private data class PointEditorTarget(
    val editingId: String?,
    val imageX: Double?,
    val imageY: Double?,
    val initialForm: VenuePointForm? = null,
)

@Composable
fun VenueMapEditorRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenPreview: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VenueMapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.load(campingId, authenticatedUser)
    }

    VenueMapEditorScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onOpenPreview = { onOpenPreview(campingId) },
        onUploadImage = viewModel::uploadSiteImage,
        onRemoveImage = viewModel::removeSiteImage,
        onSavePoint = viewModel::savePoint,
        onImportGpxPoints = viewModel::importGpxPoints,
        onMovePoint = viewModel::movePoint,
        onDeletePoint = viewModel::deletePoint,
        onSetCoordinate = viewModel::setCoordinate,
        onClearMessage = viewModel::clearOperationMessage,
        onClearError = viewModel::clearOperationError,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VenueMapEditorScreen(
    state: VenueMapUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenPreview: () -> Unit,
    onUploadImage: (ByteArray, String, String) -> Unit,
    onRemoveImage: () -> Unit,
    onSavePoint: (VenuePointForm, String?, Double?, Double?) -> Unit,
    onImportGpxPoints: (List<ParsedGpxPoint>, VenueCategory) -> Unit,
    onMovePoint: (String, Double, Double) -> Unit,
    onDeletePoint: (String) -> Unit,
    onSetCoordinate: (String, Double?, Double?) -> Unit,
    onClearMessage: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val gpxReadError = stringResource(R.string.venue_gpx_read_error)
    val gpxParseError = stringResource(R.string.venue_gpx_parse_error)
    val locationDenied = stringResource(R.string.venue_location_denied)
    val currentLocationError = stringResource(R.string.venue_current_location_error)

    var placement by remember { mutableStateOf<Placement?>(null) }
    var editorTarget by remember { mutableStateOf<PointEditorTarget?>(null) }
    var locationTarget by remember { mutableStateOf<VenuePoint?>(null) }
    var gpxImportPoints by remember { mutableStateOf<List<ParsedGpxPoint>?>(null) }

    val ready = state as? VenueMapUiState.Ready

    // Surface transient operation messages / errors.
    LaunchedEffect(ready?.operationMessage) {
        ready?.operationMessage?.let {
            snackbar.showSnackbar(it)
            onClearMessage()
        }
    }
    LaunchedEffect(ready?.operationError) {
        ready?.operationError?.let {
            snackbar.showSnackbar(it)
            onClearError()
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
            onUploadImage(bytes, mimeType, ext)
        }
    }
    val gpxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error(gpxReadError)
                GPXParser.parse(bytes)
            }.onSuccess { points ->
                gpxImportPoints = points
            }.onFailure {
                snackbar.showSnackbar(gpxParseError)
            }
        }
    }
    val currentLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!granted) {
            scope.launch { snackbar.showSnackbar(locationDenied) }
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val location = runCatching { currentLocation(context) }.getOrNull()
            if (location == null) {
                snackbar.showSnackbar(currentLocationError)
            } else {
                editorTarget = PointEditorTarget(
                    editingId = null,
                    imageX = null,
                    imageY = null,
                    initialForm = VenuePointForm(
                        latitudeText = String.format(java.util.Locale.US, "%.6f", location.latitude),
                        longitudeText = String.format(java.util.Locale.US, "%.6f", location.longitude),
                    ),
                )
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.czColors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.venue_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (ready?.canManage == true) {
                        EditorOverflowMenu(
                            canAddLocation = !ready.map.isAtPointCapacity,
                            onAddLocation = {
                                if (ready.map.hasImage) {
                                    placement = Placement.New
                                } else {
                                    editorTarget = PointEditorTarget(null, null, null)
                                }
                            },
                            onEnterCoordinates = {
                                editorTarget = PointEditorTarget(null, null, null)
                            },
                            onUseCurrentLocation = {
                                currentLocationLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            },
                            onImportGpx = {
                                gpxLauncher.launch(
                                    arrayOf(
                                        "application/gpx+xml",
                                        "application/xml",
                                        "text/xml",
                                        "*/*",
                                    ),
                                )
                            },
                            onPreview = onOpenPreview,
                        )
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                VenueMapUiState.Loading -> CzLoadingView(
                    message = stringResource(R.string.venue_loading),
                    modifier = Modifier.fillMaxSize(),
                )

                is VenueMapUiState.Error -> CzErrorState(
                    title = stringResource(R.string.venue_error_title),
                    message = state.message,
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize().padding(CzSpacing.lg),
                )

                is VenueMapUiState.Ready -> if (!state.canManage) {
                    CzEmptyState(
                        title = stringResource(R.string.venue_restricted_title),
                        message = stringResource(R.string.venue_restricted_message),
                        modifier = Modifier.fillMaxSize().padding(CzSpacing.lg),
                    )
                } else {
                    EditorContent(
                        state = state,
                        placement = placement,
                        onPickImage = { imageLauncher.launch("image/*") },
                        onRemoveImage = onRemoveImage,
                        onCancelPlacement = { placement = null },
                        onPlaceAt = { x, y ->
                            when (val p = placement) {
                                Placement.New -> editorTarget = PointEditorTarget(null, x, y)
                                is Placement.Move -> onMovePoint(p.id, x, y)
                                null -> Unit
                            }
                            placement = null
                        },
                        onTapPin = { editorTarget = PointEditorTarget(it.id, null, null) },
                        onEditPin = { editorTarget = PointEditorTarget(it.id, null, null) },
                        onRepositionPin = { placement = Placement.Move(it.id) },
                        onSetLocation = { locationTarget = it },
                        onDeletePin = { onDeletePoint(it.id) },
                    )
                }
            }
        }
    }

    val target = editorTarget
    if (ready != null && target != null) {
        val editing = target.editingId?.let { id -> ready.map.points.firstOrNull { it.id == id } }
        VenuePointEditorSheet(
            initialForm = target.initialForm ?: editing?.let { VenuePointForm.of(it) } ?: VenuePointForm(),
            isEditing = editing != null,
            isSaving = ready.isSaving,
            onSave = { form ->
                onSavePoint(form, target.editingId, target.imageX, target.imageY)
                editorTarget = null
            },
            onDismiss = { editorTarget = null },
        )
    }

    val coordTarget = locationTarget
    if (ready != null && coordTarget != null) {
        VenuePointLocationSheet(
            point = coordTarget,
            campLatitude = ready.campLatitude,
            campLongitude = ready.campLongitude,
            onSave = { lat, lon ->
                onSetCoordinate(coordTarget.id, lat, lon)
                locationTarget = null
            },
            onClear = {
                onSetCoordinate(coordTarget.id, null, null)
                locationTarget = null
            },
            onDismiss = { locationTarget = null },
        )
    }

    val pendingGpxPoints = gpxImportPoints
    if (ready != null && pendingGpxPoints != null) {
        VenueGpxImportSheet(
            points = pendingGpxPoints,
            remainingCapacity = ready.map.remainingPointCapacity,
            isSaving = ready.isSaving,
            onDismiss = { gpxImportPoints = null },
            onImport = { selectedPoints, category ->
                onImportGpxPoints(selectedPoints, category)
                gpxImportPoints = null
            },
        )
    }
}

@Composable
private fun EditorContent(
    state: VenueMapUiState.Ready,
    placement: Placement?,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onCancelPlacement: () -> Unit,
    onPlaceAt: (Double, Double) -> Unit,
    onTapPin: (VenuePoint) -> Unit,
    onEditPin: (VenuePoint) -> Unit,
    onRepositionPin: (VenuePoint) -> Unit,
    onSetLocation: (VenuePoint) -> Unit,
    onDeletePin: (VenuePoint) -> Unit,
) {
    val map = state.map
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        item("image-section") {
            SiteImageSection(
                hasImage = map.hasImage,
                isUploading = state.isUploadingImage,
                onPick = onPickImage,
                onRemove = onRemoveImage,
            )
        }

        if (map.hasImage) {
            if (placement != null) {
                item("placement-hint") {
                    PlacementHint(
                        isNew = placement is Placement.New,
                        onCancel = onCancelPlacement,
                    )
                }
            }
            item("canvas") {
                VenueImageCanvas(
                    map = map,
                    selectedPointId = null,
                    isPlacing = placement != null,
                    onTapPin = onTapPin,
                    onPlaceAt = if (placement != null) onPlaceAt else null,
                )
            }
        }

        item("points-header") {
            Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                Text(
                    text = stringResource(R.string.venue_locations),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = if (map.isAtPointCapacity) {
                        stringResource(R.string.venue_point_capacity_full, VenueMap.MaxPoints)
                    } else {
                        stringResource(
                            R.string.venue_point_capacity_count,
                            map.points.size,
                            VenueMap.MaxPoints,
                        )
                    },
                    color = if (map.isAtPointCapacity) {
                        MaterialTheme.czColors.warning
                    } else {
                        MaterialTheme.czColors.textSecondary
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        if (map.points.isEmpty()) {
            item("points-empty") {
                Surface(
                    color = MaterialTheme.czColors.surface,
                    shape = RoundedCornerShape(CzRadius.lg),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.venue_points_empty),
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.fillMaxWidth().padding(CzSpacing.md),
                    )
                }
            }
        } else {
            items(map.points, key = { it.id }) { point ->
                VenuePointAdminRow(
                    point = point,
                    canReposition = map.hasImage,
                    onEdit = { onEditPin(point) },
                    onReposition = { onRepositionPin(point) },
                    onSetLocation = { onSetLocation(point) },
                    onDelete = { onDeletePin(point) },
                )
            }
        }
    }
}

@Composable
private fun SiteImageSection(
    hasImage: Boolean,
    isUploading: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.xl),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.venue_site_section_title),
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.venue_site_section_hint),
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                CzButton(
                    text = if (hasImage) {
                        stringResource(R.string.venue_replace_image)
                    } else {
                        stringResource(R.string.venue_choose_image)
                    },
                    onClick = onPick,
                    enabled = !isUploading,
                    loading = isUploading,
                    leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                if (hasImage) {
                    CzButton(
                        text = stringResource(R.string.venue_remove_image),
                        onClick = onRemove,
                        enabled = !isUploading,
                        variant = CzButtonVariant.Outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlacementHint(isNew: Boolean, onCancel: () -> Unit) {
    Surface(
        color = MaterialTheme.czColors.ember.copy(alpha = 0.12f),
        shape = RoundedCornerShape(CzRadius.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(CzSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Place,
                contentDescription = null,
                tint = MaterialTheme.czColors.ember,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = if (isNew) {
                    stringResource(R.string.venue_place_new_hint)
                } else {
                    stringResource(R.string.venue_place_move_hint)
                },
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) {
                Text(
                    text = stringResource(R.string.common_cancel),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun VenuePointAdminRow(
    point: VenuePoint,
    canReposition: Boolean,
    onEdit: () -> Unit,
    onReposition: () -> Unit,
    onSetLocation: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    VenueLegendRow(
        point = point,
        onClick = onEdit,
        trailing = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.venue_point_actions),
                        tint = MaterialTheme.czColors.textSecondary,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.venue_action_edit)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onEdit() },
                    )
                    if (canReposition) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.venue_action_reposition)) },
                            leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
                            onClick = { menuOpen = false; onReposition() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.venue_action_set_location)) },
                        leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
                        onClick = { menuOpen = false; onSetLocation() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.venue_action_delete)) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.czColors.error) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        },
    )
}

@Composable
private fun EditorOverflowMenu(
    canAddLocation: Boolean,
    onAddLocation: () -> Unit,
    onEnterCoordinates: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onImportGpx: () -> Unit,
    onPreview: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.venue_more_actions),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.venue_add_location)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                enabled = canAddLocation,
                onClick = { open = false; onAddLocation() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.venue_enter_coordinates)) },
                leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
                enabled = canAddLocation,
                onClick = { open = false; onEnterCoordinates() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.venue_use_current_location)) },
                leadingIcon = { Icon(Icons.Filled.GpsFixed, contentDescription = null) },
                enabled = canAddLocation,
                onClick = { open = false; onUseCurrentLocation() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.venue_import_gpx)) },
                leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                enabled = canAddLocation,
                onClick = { open = false; onImportGpx() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.venue_preview)) },
                leadingIcon = { Icon(Icons.Filled.Visibility, contentDescription = null) },
                onClick = { open = false; onPreview() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun VenueGpxImportSheet(
    points: List<ParsedGpxPoint>,
    remainingCapacity: Int,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onImport: (List<ParsedGpxPoint>, VenueCategory) -> Unit,
) {
    val colors = MaterialTheme.czColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedIndices by remember(points, remainingCapacity) {
        mutableStateOf(points.indices.take(remainingCapacity.coerceAtLeast(0)).toSet())
    }
    var category by remember { mutableStateOf(VenueCategory.Other) }
    val selectedPoints = remember(points, selectedIndices) {
        points.filterIndexed { index, _ -> index in selectedIndices }
    }
    val canImport = selectedPoints.isNotEmpty() && selectedPoints.size <= remainingCapacity && !isSaving
    val allSelected = points.isNotEmpty() && selectedIndices.size == points.size
    val assignableCategories = remember {
        VenueCategory.entries.filterNot { it == VenueCategory.Custom }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CzSpacing.lg)
                .padding(bottom = CzSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.venue_gpx_import_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
            )

            Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                Text(
                    text = stringResource(R.string.venue_gpx_import_category),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                ) {
                    assignableCategories.forEach { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(stringResource(item.labelRes)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.venue_gpx_import_category_help),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.venue_gpx_waypoints),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
                TextButton(
                    onClick = {
                        selectedIndices = if (allSelected) {
                            emptySet()
                        } else {
                            points.indices.toSet()
                        }
                    },
                ) {
                    Text(
                        text = stringResource(
                            if (allSelected) {
                                R.string.venue_gpx_deselect_all
                            } else {
                                R.string.venue_gpx_select_all
                            },
                        ),
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            ) {
                items(points.withIndex().toList(), key = { it.index }) { indexed ->
                    val point = indexed.value
                    val isSelected = indexed.index in selectedIndices
                    Surface(
                        onClick = {
                            selectedIndices = if (isSelected) {
                                selectedIndices - indexed.index
                            } else {
                                selectedIndices + indexed.index
                            }
                        },
                        color = colors.surface,
                        shape = RoundedCornerShape(CzRadius.md),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(CzSpacing.md),
                            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (isSelected) {
                                    Icons.Filled.CheckCircle
                                } else {
                                    Icons.Filled.RadioButtonUnchecked
                                },
                                contentDescription = null,
                                tint = if (isSelected) colors.ember else colors.textSecondary,
                                modifier = Modifier.size(24.dp),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = point.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = String.format(
                                        java.util.Locale.US,
                                        "%.5f, %.5f",
                                        point.latitude,
                                        point.longitude,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }

            VenueGpxCapacityFooter(
                selectedCount = selectedPoints.size,
                remainingCapacity = remainingCapacity,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, enabled = !isSaving) {
                    Text(stringResource(R.string.common_cancel))
                }
                TextButton(
                    onClick = { onImport(selectedPoints, category) },
                    enabled = canImport,
                ) {
                    Text(stringResource(R.string.venue_gpx_import_count, selectedPoints.size))
                }
            }
        }
    }
}

@Composable
private fun VenueGpxCapacityFooter(
    selectedCount: Int,
    remainingCapacity: Int,
) {
    val colors = MaterialTheme.czColors
    val isFull = remainingCapacity <= 0
    val isOverCapacity = selectedCount > remainingCapacity
    val tint = if (isFull || isOverCapacity) colors.warning else colors.textSecondary
    val message = when {
        isFull -> stringResource(R.string.venue_gpx_capacity_full)
        isOverCapacity -> stringResource(
            R.string.venue_gpx_capacity_over,
            remainingCapacity,
            selectedCount - remainingCapacity,
        )
        else -> stringResource(
            R.string.venue_gpx_capacity_ok,
            selectedCount,
            remainingCapacity,
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isFull || isOverCapacity) Icons.Filled.Warning else Icons.Filled.Map,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun VenuePointEditorSheet(
    initialForm: VenuePointForm,
    isEditing: Boolean,
    isSaving: Boolean,
    onSave: (VenuePointForm) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var form by remember { mutableStateOf(initialForm) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.czColors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CzSpacing.lg)
                .padding(bottom = CzSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Text(
                text = if (isEditing) {
                    stringResource(R.string.venue_point_edit_title)
                } else {
                    stringResource(R.string.venue_point_new_title)
                },
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            CzTextField(
                value = form.name,
                onValueChange = { form = form.copy(name = it) },
                label = stringResource(R.string.venue_field_name),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.venue_field_category),
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                for (category in VenueCategory.entries) {
                    FilterChip(
                        selected = form.category == category,
                        onClick = { form = form.copy(category = category) },
                        label = { Text(stringResource(category.labelRes)) },
                        leadingIcon = {
                            Icon(
                                if (category == VenueCategory.Custom) {
                                    materialIconForSfSymbol(form.customIconName)
                                } else {
                                    category.icon
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
            if (form.category == VenueCategory.Custom) {
                CzTextField(
                    value = form.customCategoryName,
                    onValueChange = { form = form.copy(customCategoryName = it) },
                    label = stringResource(R.string.venue_field_custom_category),
                    isError = form.customCategoryName.isBlank(),
                    supportingText = if (form.customCategoryName.isBlank()) {
                        stringResource(R.string.venue_custom_category_required)
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.venue_field_custom_icon),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                    VenueIconCatalog.allIconNames.forEach { iconName ->
                        FilterChip(
                            selected = form.customIconName == iconName,
                            onClick = { form = form.copy(customIconName = iconName) },
                            label = {
                                Icon(
                                    imageVector = materialIconForSfSymbol(iconName),
                                    contentDescription = iconName,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.venue_field_coordinates),
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                CzTextField(
                    value = form.latitudeText,
                    onValueChange = { form = form.copy(latitudeText = it) },
                    label = stringResource(R.string.venue_field_latitude),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = form.hasCoordinateInput && form.parsedCoordinate == null,
                    modifier = Modifier.weight(1f),
                )
                CzTextField(
                    value = form.longitudeText,
                    onValueChange = { form = form.copy(longitudeText = it) },
                    label = stringResource(R.string.venue_field_longitude),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = form.hasCoordinateInput && form.parsedCoordinate == null,
                    modifier = Modifier.weight(1f),
                )
            }
            if (form.hasCoordinateInput && form.parsedCoordinate == null) {
                Text(
                    text = stringResource(R.string.venue_coordinate_invalid),
                    color = MaterialTheme.czColors.error,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            CzTextField(
                value = form.note,
                onValueChange = { form = form.copy(note = it) },
                label = stringResource(R.string.venue_field_note),
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            CzButton(
                text = stringResource(R.string.common_save),
                onClick = { onSave(form) },
                enabled = form.isValid && !isSaving,
                loading = isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VenuePointLocationSheet(
    point: VenuePoint,
    campLatitude: Double?,
    campLongitude: Double?,
    onSave: (Double, Double) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initial = remember {
        when {
            point.latitude != null && point.longitude != null -> GeoPoint(point.latitude, point.longitude)
            campLatitude != null && campLongitude != null -> GeoPoint(campLatitude, campLongitude)
            else -> GeoPoint(46.8, 8.2)
        }
    }
    var center by remember { mutableStateOf(initial.latitude to initial.longitude) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.czColors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CzSpacing.lg)
                .padding(bottom = CzSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.venue_location_title),
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.venue_location_hint, point.name),
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(CzRadius.lg)),
                contentAlignment = Alignment.Center,
            ) {
                VenueOsmPicker(
                    initial = initial,
                    onCenterChanged = { lat, lon -> center = lat to lon },
                    modifier = Modifier.fillMaxSize(),
                )
                // Fixed crosshair: the marker stays put while the map moves under it.
                Icon(
                    imageVector = Icons.Filled.GpsFixed,
                    contentDescription = null,
                    tint = MaterialTheme.czColors.ember,
                    modifier = Modifier.size(36.dp),
                )
            }
            Text(
                text = String.format(java.util.Locale.US, "%.5f, %.5f", center.first, center.second),
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
            CzButton(
                text = stringResource(R.string.venue_use_this_location),
                onClick = { onSave(center.first, center.second) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (point.hasCoordinate) {
                CzButton(
                    text = stringResource(R.string.venue_remove_location),
                    onClick = onClear,
                    variant = CzButtonVariant.Destructive,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Suppress("MissingPermission")
private suspend fun currentLocation(context: android.content.Context): android.location.Location? {
    val client = LocationServices.getFusedLocationProviderClient(context)
    return client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
        ?: client.lastLocation.await()
}

@Preview
@Composable
private fun VenueMapEditorScreenPreview() {
    CampzoneTheme {
        VenueMapEditorScreen(
            state = VenueMapUiState.Ready(
                campingId = "c1",
                map = previewMap(),
                canManage = true,
            ),
            onBack = {},
            onRetry = {},
            onOpenPreview = {},
            onUploadImage = { _, _, _ -> },
            onRemoveImage = {},
            onSavePoint = { _, _, _, _ -> },
            onImportGpxPoints = { _, _ -> },
            onMovePoint = { _, _, _ -> },
            onDeletePoint = {},
            onSetCoordinate = { _, _, _ -> },
            onClearMessage = {},
            onClearError = {},
        )
    }
}
