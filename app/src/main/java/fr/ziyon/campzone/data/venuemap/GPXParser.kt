package fr.ziyon.campzone.data.venuemap

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.SAXException

data class ParsedGpxPoint(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

sealed class GpxParseException(message: String) : Exception(message) {
    data object Invalid : GpxParseException("The GPX file is not valid XML.")
    data object Empty : GpxParseException("The GPX file does not contain usable points.")
}

/**
 * Lightweight GPX reader ported from iOS `GPXParser.swift`.
 * Waypoints win; otherwise route points; otherwise track points.
 */
object GPXParser {
    fun parse(text: String): List<ParsedGpxPoint> =
        parse(text.toByteArray(Charsets.UTF_8))

    fun parse(bytes: ByteArray): List<ParsedGpxPoint> {
        val document = try {
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                trySetFeature("http://xml.org/sax/features/external-general-entities", false)
                trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
                trySetFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        } catch (_: SAXException) {
            throw GpxParseException.Invalid
        } catch (_: Exception) {
            throw GpxParseException.Invalid
        }

        val waypoints = document.pointsForTag("wpt")
        val routePoints = document.pointsForTag("rtept")
        val trackPoints = document.pointsForTag("trkpt")
        val resolved = when {
            waypoints.isNotEmpty() -> waypoints
            routePoints.isNotEmpty() -> routePoints
            else -> trackPoints
        }
        if (resolved.isEmpty()) throw GpxParseException.Empty
        return resolved.mapIndexed { index, point ->
            ParsedGpxPoint(
                name = point.name.takeUnless { it.isBlank() } ?: "Point ${index + 1}",
                latitude = point.latitude,
                longitude = point.longitude,
            )
        }
    }

    private data class PendingPoint(
        val latitude: Double,
        val longitude: Double,
        val name: String = "",
    ) {
        fun trimmed(): PendingPoint = copy(name = name.trim())
    }

    private fun org.w3c.dom.Document.pointsForTag(tagName: String): List<PendingPoint> {
        val nodes = getElementsByTagName(tagName)
        return buildList {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                val lat = element.getAttribute("lat").toDoubleOrNull()
                val lon = element.getAttribute("lon").toDoubleOrNull()
                if (lat != null && lon != null) {
                    val name = element.getElementsByTagName("name").item(0)?.textContent.orEmpty()
                    add(PendingPoint(latitude = lat, longitude = lon, name = name).trimmed())
                }
            }
        }
    }

    private fun DocumentBuilderFactory.trySetFeature(feature: String, value: Boolean) {
        try {
            setFeature(feature, value)
        } catch (_: Exception) {
        }
    }
}
