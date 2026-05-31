package fr.ziyon.campzone.ui.venuemap

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/** A pin to render on the OSM map (colour resolved in a composable scope). */
data class OsmMarkerSpec(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val colorArgb: Int,
)

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

/** Read-only venue map: camp + pin markers, recentred on [center]. */
@Composable
fun VenueOsmMap(
    center: GeoPoint,
    markers: List<OsmMarkerSpec>,
    selectedId: String?,
    onMarkerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = rememberVenueMapView()

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { view ->
            view.controller.setZoom(DEFAULT_ZOOM)
            view.controller.setCenter(center)
            view.overlays.clear()
            markers.forEach { spec ->
                view.overlays.add(
                    marker(context, view, spec) { onMarkerClick(spec.id) },
                )
            }
            // Keep the selected pin centred so the detail card lines up with it.
            markers.firstOrNull { it.id == selectedId }?.let {
                view.controller.animateTo(GeoPoint(it.latitude, it.longitude))
            }
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
