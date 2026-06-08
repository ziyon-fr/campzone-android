package fr.ziyon.campzone.data.venuemap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GPXParserTest {

    @Test
    fun prefersWaypointsOverRouteAndTrackPoints() {
        val parsed = GPXParser.parse(
            """
            <gpx>
                <trk><trkseg><trkpt lat="45.000001" lon="6.000001"><name>Track</name></trkpt></trkseg></trk>
                <rte><rtept lat="45.000002" lon="6.000002"><name>Route</name></rtept></rte>
                <wpt lat="45.000003" lon="6.000003"><name>Waypoint</name></wpt>
            </gpx>
            """.trimIndent(),
        )

        assertEquals(listOf(ParsedGpxPoint("Waypoint", 45.000003, 6.000003)), parsed)
    }

    @Test
    fun fallsBackToNumberedNames() {
        val parsed = GPXParser.parse(
            """
            <gpx>
                <rte><rtept lat="45.1" lon="6.1" /></rte>
                <rte><rtept lat="45.2" lon="6.2"><name>  </name></rtept></rte>
            </gpx>
            """.trimIndent(),
        )

        assertEquals("Point 1", parsed[0].name)
        assertEquals("Point 2", parsed[1].name)
    }

    @Test
    fun rejectsEmptyOrInvalidFiles() {
        assertThrows(GpxParseException.Empty::class.java) {
            GPXParser.parse("<gpx><wpt lat=\"bad\" lon=\"6.1\" /></gpx>")
        }
        assertThrows(GpxParseException.Invalid::class.java) {
            GPXParser.parse("<gpx><wpt></gpx")
        }
    }
}
