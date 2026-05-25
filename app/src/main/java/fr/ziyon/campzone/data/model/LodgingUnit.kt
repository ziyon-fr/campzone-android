package fr.ziyon.campzone.data.model

import java.util.Date

/**
 * `campings/{id}/lodging/{unitId}` (`02-firestore-schema.md` §7.3). Occupancy is
 * denormalized into [occupantIds] on the unit doc (no assignment collection).
 */
data class LodgingUnit(
    val id: String,
    val campingId: String,
    val name: String = "",
    val kind: LodgingKind = LodgingKind.Tent,
    val capacity: Int = 4,
    val genderPolicy: LodgingGenderPolicy = LodgingGenderPolicy.Any,
    val notes: String = "",
    val occupantIds: List<String> = emptyList(),
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
) {
    val isFull: Boolean
        get() = occupantIds.size >= capacity
}

internal fun Map<String, Any?>.toLodgingUnitOrNull(documentId: String): LodgingUnit =
    LodgingUnit(
        id = documentId,
        campingId = stringValue("campingID").orEmpty(),
        name = rawStringValue("name").orEmpty(),
        kind = LodgingKind.fromWire(stringValue("kind")),
        capacity = (intValue("capacity") ?: 4).coerceAtLeast(1),
        genderPolicy = LodgingGenderPolicy.fromWire(stringValue("genderPolicy")),
        notes = rawStringValue("notes").orEmpty(),
        occupantIds = stringListValue("occupantIDs"),
        createdAt = dateValue("createdAt"),
        updatedAt = dateValue("updatedAt"),
    )

internal object LodgingPayload {
    fun unitPayload(
        unit: LodgingUnit,
        serverTimestamp: Any,
        includeCreatedAt: Boolean,
    ): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "campingID" to unit.campingId,
            "name" to unit.name.trim(),
            "kind" to unit.kind.wireValue,
            "capacity" to unit.capacity.coerceAtLeast(1),
            "genderPolicy" to unit.genderPolicy.wireValue,
            "notes" to unit.notes,
            "occupantIDs" to unit.occupantIds,
            "updatedAt" to serverTimestamp,
        )
        if (includeCreatedAt) payload["createdAt"] = serverTimestamp
        return payload
    }
}
