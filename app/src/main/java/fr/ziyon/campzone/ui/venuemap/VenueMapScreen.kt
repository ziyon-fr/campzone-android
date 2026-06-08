package fr.ziyon.campzone.ui.venuemap

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import fr.ziyon.campzone.data.venuemap.DirectionsTarget
import fr.ziyon.campzone.data.venuemap.ExternalMapsApp
import fr.ziyon.campzone.data.venuemap.ExternalNavigationLauncher
import fr.ziyon.campzone.data.model.VenueMap
import fr.ziyon.campzone.data.model.VenuePoint
import fr.ziyon.campzone.data.model.hasContent
import fr.ziyon.campzone.data.model.hasImage
import fr.ziyon.campzone.data.model.pointsWithCoordinate
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
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
    var directionsTarget by remember { mutableStateOf<DirectionsTarget?>(null) }

    directionsTarget?.let { target ->
        ExternalDirectionsSheet(
            target = target,
            onDismiss = { directionsTarget = null },
        )
    }

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
                    VenueMapMode.Map -> MapMode(
                        state = state,
                        onSelectPoint = onSelectPoint,
                        onRouteTo = { directionsTarget = it },
                    )
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
                            text = stringResource(R.string.venue_route),
                            onClick = {
                                directionsTarget = DirectionsTarget(
                                    id = selected.id,
                                    name = selected.name,
                                    latitude = selected.latitude!!,
                                    longitude = selected.longitude!!,
                                )
                            },
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
    onRouteTo: (DirectionsTarget) -> Unit,
) {
    val context = LocalContext.current
    val map = state.map
    val coordinatePins = map.pointsWithCoordinate
    val campLat = state.campLatitude
    val campLon = state.campLongitude
    val campMarkerLabel = stringResource(R.string.venue_camp_marker)
    val emberArgb = MaterialTheme.czColors.ember.toArgb()
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

    // Live user location + in-app directions (osmdroid dot + OSRM route),
    // mirroring the iOS VenueMapKitCanvas behaviour without a Maps API key.
    val scope = rememberCoroutineScope()
    val controller = rememberVenueMapController()
    var locationGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    var locationDenied by remember { mutableStateOf(false) }
    var hasFix by remember { mutableStateOf(false) }
    var route by remember { mutableStateOf<VenueRoute?>(null) }
    var isRouting by remember { mutableStateOf(false) }
    var routeError by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        locationGranted = granted
        locationDenied = !granted
    }
    LaunchedEffect(Unit) {
        if (!locationGranted) permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }
    // A new selection invalidates the current route (iOS clears it too).
    LaunchedEffect(state.selectedPointId) {
        route = null
        routeError = false
    }

    val selectedCoordPin = state.selectedPoint?.takeIf { it.hasCoordinate }
    val destination = selectedCoordPin?.let { GeoPoint(it.latitude!!, it.longitude!!) }
        ?: if (campLat != null && campLon != null) GeoPoint(campLat, campLon) else null
    val destinationName = selectedCoordPin?.name ?: campMarkerLabel

    Box(modifier = Modifier.fillMaxSize()) {
        VenueOsmMap(
            center = center,
            markers = markers,
            onMarkerClick = { id ->
                if (id == CAMP_MARKER_ID && campLat != null && campLon != null) {
                    onRouteTo(
                        DirectionsTarget(
                            id = CAMP_MARKER_ID,
                            name = campMarkerLabel,
                            latitude = campLat,
                            longitude = campLon,
                        ),
                    )
                } else {
                    onSelectPoint(id)
                }
            },
            controller = controller,
            userLocationEnabled = locationGranted,
            routePoints = route?.points ?: emptyList(),
            routeColorArgb = emberArgb,
            onFirstLocationFix = { hasFix = true },
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = CzSpacing.sm),
        ) {
            when {
                locationDenied && !locationGranted -> VenueMapPill(
                    icon = Icons.Filled.LocationOff,
                    text = stringResource(R.string.venue_location_denied),
                    tint = MaterialTheme.czColors.warning,
                    onClick = { openAppSettings(context) },
                )

                route != null -> VenueMapPill(
                    icon = Icons.Filled.DirectionsCar,
                    text = routeEtaText(route!!),
                    tint = MaterialTheme.czColors.ember,
                    trailingClear = true,
                    onClick = { route = null },
                )

                routeError -> VenueMapPill(
                    icon = Icons.Filled.ErrorOutline,
                    text = stringResource(R.string.venue_route_error),
                    tint = MaterialTheme.czColors.warning,
                    onClick = { routeError = false },
                )

                destination != null && locationGranted && hasFix -> VenueMapPill(
                    icon = Icons.Filled.Directions,
                    text = if (isRouting) {
                        stringResource(R.string.venue_route_finding)
                    } else {
                        stringResource(R.string.venue_directions_to, destinationName)
                    },
                    tint = MaterialTheme.czColors.ember,
                    loading = isRouting,
                    onClick = {
                        val from = controller.userLocation() ?: return@VenueMapPill
                        isRouting = true
                        routeError = false
                        scope.launch {
                            val result = fetchOsrmRoute(from, destination)
                            isRouting = false
                            if (result != null) route = result else routeError = true
                        }
                    },
                )
            }
        }

        if (locationGranted && hasFix) {
            SmallFloatingActionButton(
                onClick = { controller.recenterOnUser() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(CzSpacing.lg),
                containerColor = MaterialTheme.czColors.surface.compositeOver(MaterialTheme.czColors.background),
                contentColor = MaterialTheme.czColors.ember,
            ) {
                Icon(
                    Icons.Filled.MyLocation,
                    contentDescription = stringResource(R.string.venue_recenter),
                )
            }
        }
    }
}

@Composable
private fun VenueMapPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color,
    onClick: () -> Unit,
    loading: Boolean = false,
    trailingClear: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.CircleShape,
        color = MaterialTheme.czColors.surface.compositeOver(MaterialTheme.czColors.background),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = tint,
                )
            } else {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Text(
                text = text,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (trailingClear) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    tint = MaterialTheme.czColors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun routeEtaText(route: VenueRoute): String {
    val minutes = max(1, (route.durationSeconds / 60.0).roundToInt())
    val distance = if (route.distanceMeters < 1000) {
        stringResource(R.string.venue_route_distance_m, route.distanceMeters.roundToInt())
    } else {
        stringResource(R.string.venue_route_distance_km, "%.1f".format(route.distanceMeters / 1000.0))
    }
    return stringResource(R.string.venue_route_eta, minutes, distance)
}

private fun hasLocationPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: android.content.Context) {
    val intent = Intent(
        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExternalDirectionsSheet(
    target: DirectionsTarget,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val apps = remember(target) { availableDirectionsApps(context, target) }
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
            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.venue_directions_title),
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
            apps.forEach { app ->
                Surface(
                    onClick = {
                        openExternalDirections(context, target, app)
                        onDismiss()
                    },
                    color = MaterialTheme.czColors.surface,
                    shape = RoundedCornerShape(CzRadius.lg),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(CzSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = when (app) {
                                ExternalMapsApp.GoogleMaps,
                                ExternalMapsApp.GoogleMapsWeb,
                                ExternalMapsApp.Geo -> Icons.Filled.Map
                                ExternalMapsApp.Waze -> Icons.Filled.DirectionsCar
                            },
                            contentDescription = null,
                            tint = MaterialTheme.czColors.ember,
                            modifier = Modifier.size(28.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.displayName(),
                                color = MaterialTheme.czColors.textPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.venue_route_to, target.name),
                                color = MaterialTheme.czColors.textSecondary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun availableDirectionsApps(
    context: android.content.Context,
    target: DirectionsTarget,
): List<ExternalMapsApp> {
    val packageManager = context.packageManager
    val googleNative = Intent(
        Intent.ACTION_VIEW,
        ExternalNavigationLauncher.uriFor(target, ExternalMapsApp.GoogleMaps),
    ).setPackage("com.google.android.apps.maps")
    val waze = Intent(
        Intent.ACTION_VIEW,
        ExternalNavigationLauncher.uriFor(target, ExternalMapsApp.Waze),
    ).setPackage("com.waze")
    return buildList {
        if (googleNative.resolveActivity(packageManager) != null) add(ExternalMapsApp.GoogleMaps)
        add(ExternalMapsApp.GoogleMapsWeb)
        if (waze.resolveActivity(packageManager) != null) add(ExternalMapsApp.Waze)
        add(ExternalMapsApp.Geo)
    }
}

private fun openExternalDirections(
    context: android.content.Context,
    target: DirectionsTarget,
    app: ExternalMapsApp,
) {
    val intent = Intent(Intent.ACTION_VIEW, ExternalNavigationLauncher.uriFor(target, app)).apply {
        when (app) {
            ExternalMapsApp.GoogleMaps -> setPackage("com.google.android.apps.maps")
            ExternalMapsApp.Waze -> setPackage("com.waze")
            else -> Unit
        }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    ExternalNavigationLauncher.uriFor(target, ExternalMapsApp.GoogleMapsWeb),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
}

@Composable
private fun ExternalMapsApp.displayName(): String = when (this) {
    ExternalMapsApp.GoogleMaps -> stringResource(R.string.venue_directions_google_maps)
    ExternalMapsApp.GoogleMapsWeb -> stringResource(R.string.venue_directions_google_maps_web)
    ExternalMapsApp.Waze -> stringResource(R.string.venue_directions_waze)
    ExternalMapsApp.Geo -> stringResource(R.string.venue_directions_other)
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
        VenuePoint(
            id = "p1",
            name = "Main Stage",
            category = fr.ziyon.campzone.data.model.VenueCategory.Stage,
            note = "Evening worship",
            imageX = 0.5,
            imageY = 0.32,
            latitude = 45.9,
            longitude = 6.13,
        ),
        VenuePoint(
            id = "p2",
            name = "Dining Hall",
            category = fr.ziyon.campzone.data.model.VenueCategory.Dining,
            note = "Meals",
            imageX = 0.28,
            imageY = 0.6,
        ),
        VenuePoint(
            id = "p3",
            name = "Medic Tent",
            category = fr.ziyon.campzone.data.model.VenueCategory.FirstAid,
            note = "24h",
            imageX = 0.72,
            imageY = 0.58,
        ),
    ),
)
