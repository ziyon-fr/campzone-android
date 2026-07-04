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
) {
    companion object {
        const val MaxPoints = 120
    }
}

data class VenuePoint(
    val id: String,
    val name: String,
    val category: VenueCategory = VenueCategory.Other,
    val customCategoryName: String? = null,
    val customIconName: String? = null,
    val note: String = "",
    val imageX: Double? = null,
    val imageY: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    /** True when the pin can be drawn over the uploaded illustration. */
    val hasImagePosition: Boolean get() = imageX != null && imageY != null

    /** True when the pin carries a real-world coordinate (map / directions). */
    val hasCoordinate: Boolean get() = latitude != null && longitude != null

    val resolvedDisplayName: String
        get() = if (category == VenueCategory.Custom) {
            customCategoryName?.trim()?.takeUnless { it.isBlank() } ?: category.wireValue
        } else {
            category.wireValue
        }

    val resolvedIconName: String
        get() = if (category == VenueCategory.Custom) {
            customIconName?.takeUnless { it.isBlank() } ?: VenueIconCatalog.defaultIconName
        } else {
            VenueIconCatalog.categoryIconName(category)
        }
}

/** Whether the camp has uploaded a site illustration. */
val VenueMap.hasImage: Boolean get() = !imageUrl.isNullOrBlank()

/** Drives the self-silencing entry card: anything worth showing at all. */
val VenueMap.hasContent: Boolean get() = hasImage || points.isNotEmpty()

/** Guard for the single Firestore document that owns every embedded venue pin. */
val VenueMap.isAtPointCapacity: Boolean get() = points.size >= VenueMap.MaxPoints

/** How many new pins can be added before the single venue-map document is full. */
val VenueMap.remainingPointCapacity: Int get() = (VenueMap.MaxPoints - points.size).coerceAtLeast(0)

/** Pins that can be drawn over the illustration (have a relative position). */
val VenueMap.pointsOnIllustration: List<VenuePoint> get() = points.filter { it.hasImagePosition }

/** Pins that carry a real-world coordinate. */
val VenueMap.pointsWithCoordinate: List<VenuePoint> get() = points.filter { it.hasCoordinate }

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
        customCategoryName = stringValue("customCategoryName"),
        customIconName = stringValue("customIconName"),
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
        if (point.category == VenueCategory.Custom) {
            point.customCategoryName?.trim()?.takeUnless { it.isBlank() }?.let { map["customCategoryName"] = it }
            point.customIconName?.trim()?.takeUnless { it.isBlank() }?.let { map["customIconName"] = it }
        }
        point.imageX?.let { map["imageX"] = it }
        point.imageY?.let { map["imageY"] = it }
        point.latitude?.let { map["latitude"] = it }
        point.longitude?.let { map["longitude"] = it }
        return map
    }
}

object VenueIconCatalog {
    const val defaultIconName = "mappin.circle"

    data class Section(
        val id: String,
        val title: String,
        val icons: List<String>,
    )

    val sections: List<Section> = listOf(
        Section(
            id = "places",
            title = "Places & shelter",
            icons = listOf(
                "tent.fill", "house.fill", "building.2.fill", "signpost.right.fill",
                "mappin.and.ellipse", "flag.fill", "star.fill", "fireplace.fill",
            ),
        ),
        Section(
            id = "facilities",
            title = "Facilities",
            icons = listOf(
                "toilet.fill", "shower.fill", "drop.fill", "fork.knife",
                "cup.and.saucer.fill", "cart.fill", "trash.fill", "bolt.fill",
                "wifi", "phone.fill", "powerplug.fill", "spigot.fill",
            ),
        ),
        Section(
            id = "activity",
            title = "Activities & sport",
            icons = listOf(
                "figure.run", "figure.pool.swim", "figure.hiking", "sportscourt.fill",
                "soccerball", "volleyball.fill", "music.mic", "guitars.fill",
                "theatermasks.fill", "paintpalette.fill", "book.fill", "gamecontroller.fill",
            ),
        ),
        Section(
            id = "safety",
            title = "Safety & service",
            icons = listOf(
                "cross.case.fill", "staroflife.fill", "shield.lefthalf.filled", "bell.fill",
                "exclamationmark.triangle.fill", "car.fill", "bus.fill", "parkingsign",
                "figure.wave", "person.2.fill", "hands.sparkles.fill", "leaf.fill",
            ),
        ),
    )

    val allIconNames: List<String> = sections.flatMap { it.icons }.distinct()

    fun categoryIconName(category: VenueCategory): String = when (category) {
        VenueCategory.Tent -> "tent.fill"
        VenueCategory.Stage -> "music.mic"
        VenueCategory.Dining -> "fork.knife"
        VenueCategory.FirstAid -> "cross.case.fill"
        VenueCategory.Restroom -> "toilet.fill"
        VenueCategory.Parking -> "parkingsign"
        VenueCategory.Water -> "drop.fill"
        VenueCategory.Program -> "calendar"
        VenueCategory.Info -> "info.circle.fill"
        VenueCategory.Other -> "mappin"
        VenueCategory.Custom -> defaultIconName
    }
}
