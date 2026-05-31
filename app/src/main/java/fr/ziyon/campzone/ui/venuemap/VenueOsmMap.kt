package fr.ziyon.campzone.ui.venuemap

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/** A pin to render on the OSM map (colour resolved in a composable scope). */
data class OsmMarkerSpec(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val colorArgb: Int,
)

/** A computed driving route: the road geometry plus its duration/distance. */
data class VenueRoute(
    val points: List<GeoPoint>,
    val durationSeconds: Double,
    val distanceMeters: Double,
)

/** Lets the host screen read the user's location and recentre the map without
 *  leaking the [MapView] up the tree. */
class VenueMapController {
    internal var locationProvider: () -> GeoPoint? = { null }
    internal var recenterAction: (GeoPoint) -> Unit = {}

    fun userLocation(): GeoPoint? = locationProvider()
    fun recenterOnUser(): Boolean = userLocation()?.also(recenterAction) != null
}

@Composable
fun rememberVenueMapController(): VenueMapController = remember { VenueMapController() }

private const val DEFAULT_ZOOM = 15.5

/** Builds and lifecycle-manages an osmdroid [MapView] for Compose. */
@Composable
private fun rememberVenueMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        // osmdroid rejects tile requests without a user agent.
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }
    return mapView
}

/**
 * Read-only venue map: camp + pin markers, an optional live user-location dot
 * and a computed route overlay. The initial camera is set once so the user's
 * pan/zoom is never reset on recomposition (the route fit is the only
 * programmatic camera move).
 */
@Composable
fun VenueOsmMap(
    center: GeoPoint,
    markers: List<OsmMarkerSpec>,
    onMarkerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    controller: VenueMapController? = null,
    userLocationEnabled: Boolean = false,
    routePoints: List<GeoPoint> = emptyList(),
    routeColorArgb: Int = 0xFFFF6B35.toInt(),
    onFirstLocationFix: () -> Unit = {},
) {
    val context = LocalContext.current
    val mapView = rememberVenueMapView()
    val locationOverlay = remember(mapView) {
        MyLocationNewOverlay(GpsMyLocationProvider(context.applicationContext), mapView)
    }

    controller?.apply {
        locationProvider = { locationOverlay.myLocation }
        recenterAction = { mapView.controller.animateTo(it) }
    }

    DisposableEffect(userLocationEnabled) {
        if (userLocationEnabled) {
            locationOverlay.enableMyLocation()
            locationOverlay.runOnFirstFix {
                Handler(Looper.getMainLooper()).post(onFirstLocationFix)
            }
        } else {
            locationOverlay.disableMyLocation()
        }
        onDispose { locationOverlay.disableMyLocation() }
    }

    // Fit the camera to a freshly-computed route (the only auto camera move).
    LaunchedEffect(routePoints) {
        if (routePoints.size >= 2) {
            mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(routePoints), true, 96)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.controller.setZoom(DEFAULT_ZOOM)
            mapView.controller.setCenter(center)
            mapView
        },
        update = { view ->
            view.overlays.clear()
            if (routePoints.size >= 2) {
                view.overlays.add(
                    Polyline(view).apply {
                        setPoints(routePoints)
                        outlinePaint.color = routeColorArgb
                        outlinePaint.strokeWidth = 14f
                    },
                )
            }
            markers.forEach { spec ->
                view.overlays.add(marker(context, view, spec) { onMarkerClick(spec.id) })
            }
            if (userLocationEnabled) view.overlays.add(locationOverlay)
            view.invalidate()
        },
    )
}

/** Interactive picker map: reports its current centre as the user pans/zooms. */
@Composable
fun VenueOsmPicker(
    initial: GeoPoint,
    onCenterChanged: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mapView = rememberVenueMapView()

    AndroidView(
        modifier = modifier,
        // `factory` receives a Context; we configure the captured MapView once.
        factory = {
            mapView.controller.setZoom(DEFAULT_ZOOM)
            mapView.controller.setCenter(initial)
            mapView.addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    mapView.mapCenter.let { onCenterChanged(it.latitude, it.longitude) }
                    return true
                }

                override fun onZoom(event: ZoomEvent?): Boolean {
                    mapView.mapCenter.let { onCenterChanged(it.latitude, it.longitude) }
                    return false
                }
            })
            onCenterChanged(initial.latitude, initial.longitude)
            mapView
        },
    )
}

/**
 * Computes a driving route between two points using the public OSRM endpoint
 * (key-free, the same no-key spirit as the OSM tiles). Returns `null` on any
 * failure so the caller can show a graceful "no route" state.
 *
 * NOTE: `router.project-osrm.org` is a demo server — for production, self-host
 * OSRM (or swap in another routing provider) and point this URL at it.
 */
suspend fun fetchOsrmRoute(from: GeoPoint, to: GeoPoint): VenueRoute? = withContext(Dispatchers.IO) {
    val url = "https://router.project-osrm.org/route/v1/driving/" +
        "${from.longitude},${from.latitude};${to.longitude},${to.latitude}" +
        "?overview=full&geometries=geojson"
    runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "Campzone-Android")
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        if (json.optString("code") != "Ok") return@runCatching null
        val route = json.getJSONArray("routes").optJSONObject(0) ?: return@runCatching null
        val coordinates = route.getJSONObject("geometry").getJSONArray("coordinates")
        val points = (0 until coordinates.length()).map { index ->
            val pair = coordinates.getJSONArray(index)
            GeoPoint(pair.getDouble(1), pair.getDouble(0))
        }
        if (points.size < 2) return@runCatching null
        VenueRoute(points, route.getDouble("duration"), route.getDouble("distance"))
    }.getOrNull()
}

private fun marker(
    context: Context,
    mapView: MapView,
    spec: OsmMarkerSpec,
    onClick: () -> Unit,
): Marker = Marker(mapView).apply {
    position = GeoPoint(spec.latitude, spec.longitude)
    title = spec.title
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    icon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)
        ?.mutate()
        ?.also { DrawableCompat.setTint(it, spec.colorArgb) }
    setInfoWindow(null)
    setOnMarkerClickListener { _, _ ->
        onClick()
        true
    }
}
