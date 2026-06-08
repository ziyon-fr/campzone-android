package fr.ziyon.campzone.data.venuemap

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalNavigationLauncherTest {

    @Test
    fun formatsCoordinatesWithSixUsDecimalPlaces() {
        assertEquals("45.123457", ExternalNavigationLauncher.formatCoordinate(45.1234567))
        assertEquals("-6.500000", ExternalNavigationLauncher.formatCoordinate(-6.5))
    }

    @Test
    fun buildsProviderUrlsForDirections() {
        val target = DirectionsTarget(
            id = "pin-1",
            name = "Main Stage",
            latitude = 45.9234567,
            longitude = 6.1234567,
        )

        assertEquals(
            "google.navigation:q=45.923457,6.123457",
            ExternalNavigationLauncher.uriStringFor(target, ExternalMapsApp.GoogleMaps),
        )
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=45.923457,6.123457",
            ExternalNavigationLauncher.uriStringFor(target, ExternalMapsApp.GoogleMapsWeb),
        )
        assertEquals(
            "https://waze.com/ul?ll=45.923457,6.123457&navigate=yes",
            ExternalNavigationLauncher.uriStringFor(target, ExternalMapsApp.Waze),
        )
        assertEquals(
            "geo:45.923457,6.123457?q=45.923457,6.123457(Main%20Stage)",
            ExternalNavigationLauncher.uriStringFor(target, ExternalMapsApp.Geo),
        )
    }
}
