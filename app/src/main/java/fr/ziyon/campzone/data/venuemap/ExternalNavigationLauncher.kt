package fr.ziyon.campzone.data.venuemap

import android.net.Uri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

enum class ExternalMapsApp {
    GoogleMaps,
    GoogleMapsWeb,
    Waze,
    Geo,
}

data class DirectionsTarget(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

object ExternalNavigationLauncher {
    fun uriFor(target: DirectionsTarget, app: ExternalMapsApp): Uri =
        Uri.parse(uriStringFor(target, app))

    fun uriStringFor(target: DirectionsTarget, app: ExternalMapsApp): String {
        val lat = formatCoordinate(target.latitude)
        val lon = formatCoordinate(target.longitude)
        return when (app) {
            ExternalMapsApp.GoogleMaps -> "google.navigation:q=$lat,$lon"
            ExternalMapsApp.GoogleMapsWeb ->
                "https://www.google.com/maps/dir/?api=1&destination=$lat,$lon"
            ExternalMapsApp.Waze ->
                "https://waze.com/ul?ll=$lat,$lon&navigate=yes"
            ExternalMapsApp.Geo -> {
                val label = target.name.urlEncoded()
                "geo:$lat,$lon?q=$lat,$lon($label)"
            }
        }
    }

    fun formatCoordinate(value: Double): String =
        String.format(Locale.US, "%.6f", value)

    private fun String.urlEncoded(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
