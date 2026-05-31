package fr.ziyon.campzone.ui.venuemap

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.VenueMap
import fr.ziyon.campzone.data.model.VenuePoint
import fr.ziyon.campzone.data.model.hasContent
import fr.ziyon.campzone.data.model.hasImage
import fr.ziyon.campzone.data.model.pointsWithCoordinate
import org.osmdroid.util.GeoPoint

@Composable
fun VenueMapRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VenueMapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.load(campingId, authenticatedUser)
    }

    VenueMapScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onSelectPoint = viewModel::selectPoint,
        onOpenEditor = { onOpenEditor(campingId) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VenueMapScreen(
    state: VenueMapUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelectPoint: (String?) -> Unit,
    onOpenEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canManage = (state as? VenueMapUiState.Ready)?.canManage == true
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.czColors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.venue_map_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (canManage) {
                        IconButton(onClick = onOpenEditor) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.venue_edit_title),
                            )
                        }
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

                is VenueMapUiState.Ready -> if (!state.map.hasContent) {
                    EmptyVenueMap(
                        canManage = state.canManage,
                        onBuild = onOpenEditor,
                    )
                } else {
                    VenueMapContent(
                        state = state,
                        onSelectPoint = onSelectPoint,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyVenueMap(canManage: Boolean, onBuild: () -> Unit) {
    CzEmptyState(
        title = stringResource(R.string.venue_empty_title),
        message = if (canManage) {
            stringResource(R.string.venue_empty_manager_message)
        } else {
            stringResource(R.string.venue_empty_participant_message)
        },
        icon = {
            Icon(
                Icons.Filled.Map,
                contentDescription = null,
                tint = MaterialTheme.czColors.ember,
                modifier = Modifier.size(42.dp),
            )
        },
        action = if (canManage) {
            {
                CzButton(
                    text = stringResource(R.string.venue_build_cta),
                    onClick = onBuild,
                )
            }
        } else {
            null
        },
        modifier = Modifier.fillMaxSize().padding(CzSpacing.lg),
    )
}

@Composable
private fun VenueMapContent(
    state: VenueMapUiState.Ready,
    onSelectPoint: (String?) -> Unit,
) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(VenueMapMode.Illustration) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            VenueModePicker(
                mode = mode,
                onSelect = {
                    mode = it
                    onSelectPoint(null)
                },
                modifier = Modifier.padding(horizontal = CzSpacing.lg, vertical = CzSpacing.sm),
            )
            // The content fills only the space *below* the picker, and
            // `clipToBounds()` confines the osmdroid MapView's drawing to this
            // region (Compose doesn't clip child views by default, so without it
            // the map paints over the segmented control above it).
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds(),
            ) {
                when (mode) {
                    VenueMapMode.Illustration -> IllustrationMode(state, onSelectPoint)
                    VenueMapMode.Map -> MapMode(state, onSelectPoint)
                }
            }
        }

        val selected = state.selectedPoint
        if (selected != null) {
            VenuePointDetailCard(
                point = selected,
                onClose = { onSelectPoint(null) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(CzSpacing.lg),
                footer = if (selected.hasCoordinate) {
                    {
                        CzButton(
                            text = stringResource(R.string.venue_open_in_maps),
                            onClick = { openInMaps(context, selected) },
                            variant = CzButtonVariant.Outline,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

private enum class VenueMapMode { Illustration, Map }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VenueModePicker(
    mode: VenueMapMode,
    onSelect: (VenueMapMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        VenueMapMode.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = mode == option,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, VenueMapMode.entries.size),
            ) {
                Text(
                    stringResource(
                        if (option == VenueMapMode.Illustration) {
                            R.string.venue_mode_illustration
                        } else {
                            R.string.venue_mode_map
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun IllustrationMode(
    state: VenueMapUiState.Ready,
    onSelectPoint: (String?) -> Unit,
) {
    val map = state.map
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        item("canvas") {
            if (map.hasImage) {
                VenueImageCanvas(
                    map = map,
                    selectedPointId = state.selectedPointId,
                    onTapPin = { onSelectPoint(if (state.selectedPointId == it.id) null else it.id) },
                )
            } else {
                NoImageNotice()
            }
        }

        if (map.points.isNotEmpty()) {
            item("legend-header") {
                Text(
                    text = stringResource(R.string.venue_locations),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            items(map.points, key = { it.id }) { point ->
                VenueLegendRow(
                    point = point,
                    onClick = { onSelectPoint(point.id) },
                    trailing = if (point.hasCoordinate) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Place,
                                contentDescription = null,
                                tint = MaterialTheme.czColors.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun MapMode(
    state: VenueMapUiState.Ready,
    onSelectPoint: (String?) -> Unit,
) {
    val map = state.map
    val coordinatePins = map.pointsWithCoordinate
    val campLat = state.campLatitude
    val campLon = state.campLongitude
    val campMarkerLabel = stringResource(R.string.venue_camp_marker)
    val pineArgb = MaterialTheme.czColors.pine.toArgb()

    val center = when {
        state.selectedPoint?.hasCoordinate == true ->
            GeoPoint(state.selectedPoint!!.latitude!!, state.selectedPoint!!.longitude!!)
        campLat != null && campLon != null -> GeoPoint(campLat, campLon)
        coordinatePins.isNotEmpty() -> GeoPoint(coordinatePins.first().latitude!!, coordinatePins.first().longitude!!)
        else -> null
    }

    if (center == null) {
        CzEmptyState(
            title = stringResource(R.string.venue_map_no_pins_title),
            message = stringResource(R.string.venue_map_no_pins_message),
            icon = {
                Icon(
                    Icons.Filled.Place,
                    contentDescription = null,
                    tint = MaterialTheme.czColors.ember,
                    modifier = Modifier.size(42.dp),
                )
            },
            modifier = Modifier.fillMaxSize().padding(CzSpacing.lg),
        )
        return
    }

    val markers = buildList {
        coordinatePins.forEach { point ->
            add(OsmMarkerSpec(point.id, point.latitude!!, point.longitude!!, point.name, point.category.tint.toArgb()))
        }
        if (campLat != null && campLon != null) {
            add(OsmMarkerSpec(CAMP_MARKER_ID, campLat, campLon, campMarkerLabel, pineArgb))
        }
    }

    VenueOsmMap(
        center = center,
        markers = markers,
        selectedId = state.selectedPointId,
        onMarkerClick = { id -> if (id != CAMP_MARKER_ID) onSelectPoint(id) },
        modifier = Modifier.fillMaxSize(),
    )
}

private const val CAMP_MARKER_ID = "__camp__"

@Composable
private fun NoImageNotice() {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(CzSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Map,
                contentDescription = null,
                tint = MaterialTheme.czColors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.venue_no_image_notice),
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** Hands the pin's coordinate to the user's installed maps app (native Android
 *  stand-in for the iOS in-app MapKit overlay + directions). */
private fun openInMaps(context: android.content.Context, point: VenuePoint) {
    val lat = point.latitude ?: return
    val lon = point.longitude ?: return
    val label = Uri.encode(point.name)
    val uri = "geo:$lat,$lon?q=$lat,$lon($label)".toUri()
    val intent = Intent(Intent.ACTION_VIEW, uri)
    runCatching { context.startActivity(intent) }
        .onFailure {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, "https://www.google.com/maps/search/?api=1&query=$lat,$lon".toUri()),
            )
        }
}

@Preview
@Composable
private fun VenueMapScreenPreview() {
    CampzoneTheme {
        VenueMapScreen(
            state = VenueMapUiState.Ready(
                campingId = "c1",
                map = previewMap(),
                canManage = true,
                selectedPointId = "p1",
            ),
            onBack = {},
            onRetry = {},
            onSelectPoint = {},
            onOpenEditor = {},
        )
    }
}

internal fun previewMap(): VenueMap = VenueMap(
    campingId = "c1",
    imageUrl = "https://example.com/site.jpg",
    points = listOf(
        VenuePoint("p1", "Main Stage", fr.ziyon.campzone.data.model.VenueCategory.Stage, "Evening worship", 0.5, 0.32, 45.9, 6.13),
        VenuePoint("p2", "Dining Hall", fr.ziyon.campzone.data.model.VenueCategory.Dining, "Meals", 0.28, 0.6),
        VenuePoint("p3", "Medic Tent", fr.ziyon.campzone.data.model.VenueCategory.FirstAid, "24h", 0.72, 0.58),
    ),
)
