package fr.ziyon.campzone.data.model

import java.util.Date

/**
 * `campings/{id}/venueMap/config` (`02-firestore-schema.md` §7.4) - single doc,
 * ID literal `config`. `imageURL`/`imagePublicID` are delete-when-empty. A pin
 * may carry an image position (0…1), a real coordinate, both, or neither.
 */
data class VenueMap(
    val campingId: String,
    val imageUrl: String? = null,
    val imagePublicId: String? = null,
    val points: List<VenuePoint> = emptyList(),
    val updatedAt: Date? = null,
)

data class VenuePoint(
    val id: String,
    val name: String,
    val category: VenueCategory = VenueCategory.Other,
    val note: String = "",
    val imageX: Double? = null,
    val imageY: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

internal fun Map<String, Any?>.toVenueMap(documentId: String): VenueMap =
    VenueMap(
        campingId = stringValue("campingID").orEmpty(),
        imageUrl = stringValue("imageURL"),
        imagePublicId = stringValue("imagePublicID"),
        points = mapListValue("points").mapNotNull { it.toVenuePointOrNull() },
        updatedAt = dateValue("updatedAt"),
    )

internal fun Map<String, Any?>.toVenuePointOrNull(): VenuePoint? {
    val id = stringValue("id") ?: return null
    val name = stringValue("name") ?: return null
    return VenuePoint(
        id = id,
        name = name,
        category = VenueCategory.fromWire(stringValue("category")),
        note = rawStringValue("note").orEmpty(),
        imageX = doubleValue("imageX"),
        imageY = doubleValue("imageY"),
        latitude = doubleValue("latitude"),
        longitude = doubleValue("longitude"),
    )
}

internal object VenueMapPayload {
    fun configPayload(
        venueMap: VenueMap,
        serverTimestamp: Any,
        deleteField: Any,
    ): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "campingID" to venueMap.campingId,
            "points" to venueMap.points.map(::pointMap),
            "updatedAt" to serverTimestamp,
        )
        payload["imageURL"] = venueMap.imageUrl?.trim()?.takeUnless { it.isBlank() } ?: deleteField
        payload["imagePublicID"] = venueMap.imagePublicId?.trim()?.takeUnless { it.isBlank() } ?: deleteField
        return payload
    }

    fun pointMap(point: VenuePoint): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>(
            "id" to point.id,
            "name" to point.name.trim(),
            "category" to point.category.wireValue,
            "note" to point.note,
        )
        point.imageX?.let { map["imageX"] = it }
        point.imageY?.let { map["imageY"] = it }
        point.latitude?.let { map["latitude"] = it }
        point.longitude?.let { map["longitude"] = it }
        return map
    }
}
