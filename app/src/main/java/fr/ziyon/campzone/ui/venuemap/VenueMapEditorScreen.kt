package fr.ziyon.campzone.ui.venuemap

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
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
import fr.ziyon.campzone.data.model.VenuePoint
import fr.ziyon.campzone.data.model.hasImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Where the next illustration tap lands. */
private sealed interface Placement {
    data object New : Placement
    data class Move(val id: String) : Placement
}

private data class PointEditorTarget(
    val editingId: String?,
    val imageX: Double?,
    val imageY: Double?,
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

    var placement by remember { mutableStateOf<Placement?>(null) }
    var editorTarget by remember { mutableStateOf<PointEditorTarget?>(null) }
    var locationTarget by remember { mutableStateOf<VenuePoint?>(null) }

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
                            onAddLocation = {
                                if (ready.map.hasImage) {
                                    placement = Placement.New
                                } else {
                                    editorTarget = PointEditorTarget(null, null, null)
                                }
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
            initialForm = editing?.let { VenuePointForm.of(it) } ?: VenuePointForm(),
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
            Text(
                text = stringResource(R.string.venue_locations),
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
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
private fun EditorOverflowMenu(onAddLocation: () -> Unit, onPreview: () -> Unit) {
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
                onClick = { open = false; onAddLocation() },
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
                        leadingIcon = { Icon(category.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
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
    var latText by remember {
        mutableStateOf(point.latitude?.toString() ?: campLatitude?.toString() ?: "")
    }
    var lonText by remember {
        mutableStateOf(point.longitude?.toString() ?: campLongitude?.toString() ?: "")
    }
    val lat = latText.trim().toDoubleOrNull()
    val lon = lonText.trim().toDoubleOrNull()
    val isValid = lat != null && lat in -90.0..90.0 && lon != null && lon in -180.0..180.0

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
            Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                CzTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = stringResource(R.string.venue_latitude),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                CzTextField(
                    value = lonText,
                    onValueChange = { lonText = it },
                    label = stringResource(R.string.venue_longitude),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            if (campLatitude != null && campLongitude != null) {
                CzButton(
                    text = stringResource(R.string.venue_use_camp_location),
                    onClick = {
                        latText = campLatitude.toString()
                        lonText = campLongitude.toString()
                    },
                    variant = CzButtonVariant.Ghost,
                )
            }
            CzButton(
                text = stringResource(R.string.venue_use_this_location),
                onClick = { if (lat != null && lon != null) onSave(lat, lon) },
                enabled = isValid,
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
            onMovePoint = { _, _, _ -> },
            onDeletePoint = {},
            onSetCoordinate = { _, _, _ -> },
            onClearMessage = {},
            onClearError = {},
        )
    }
}
