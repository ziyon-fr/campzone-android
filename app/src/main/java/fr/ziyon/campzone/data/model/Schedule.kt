package fr.ziyon.campzone.data.model

import java.util.Date

/**
 * Schedule models (`02-firestore-schema.md` §4). `campings/{id}/schedule/config`
 * is a single doc (ID literal `config`); days are nested under it and programs
 * under days. The program `campDayID` is **always recomputed** from `startDate`
 * on write — never trust an inbound value.
 */
data class ScheduleConfig(
    val campingId: String,
    val reminderTiming: ScheduleReminderTiming = ScheduleReminderTiming.None,
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
)

data class CampDay(
    val id: String,
    val campingId: String,
    val date: Date,
    val title: String = "",
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
)

data class Program(
    val id: String,
    val campingId: String,
    val campDayId: String,
    val title: String = "",
    val type: ProgramType = ProgramType.Other,
    val startDate: Date,
    val endDate: Date,
    val location: String = "",
    val description: String = "",
    val venuePointId: String? = null,
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
)

// region decode

internal fun Map<String, Any?>.toScheduleConfig(documentId: String): ScheduleConfig =
    ScheduleConfig(
        campingId = stringValue("campingID").orEmpty(),
        reminderTiming = ScheduleReminderTiming.fromWire(stringValue("reminderTiming")),
        createdAt = dateValue("createdAt"),
        updatedAt = dateValue("updatedAt"),
    )

internal fun Map<String, Any?>.toCampDayOrNull(documentId: String): CampDay? {
    val date = dateValue("date") ?: return null
    return CampDay(
        id = documentId,
        campingId = stringValue("campingID").orEmpty(),
        date = date,
        title = rawStringValue("title").orEmpty(),
        createdAt = dateValue("createdAt"),
        updatedAt = dateValue("updatedAt"),
    )
}

internal fun Map<String, Any?>.toProgramOrNull(documentId: String): Program? {
    val startDate = dateValue("startDate") ?: return null
    val endDate = dateValue("endDate") ?: startDate
    return Program(
        id = stringValue("id") ?: documentId,
        campingId = stringValue("campingID").orEmpty(),
        campDayId = stringValue("campDayID").orEmpty(),
        title = rawStringValue("title").orEmpty(),
        type = ProgramType.fromWire(stringValue("type")),
        startDate = startDate,
        endDate = endDate,
        location = rawStringValue("location").orEmpty(),
        description = rawStringValue("description").orEmpty(),
        venuePointId = stringValue("venuePointID"),
        createdAt = dateValue("createdAt"),
        updatedAt = dateValue("updatedAt"),
    )
}

// endregion

internal object SchedulePayload {

    /** `schedule/config` upsert — `reminderTiming` is NOT written here. */
    fun configPayload(
        campingId: String,
        serverTimestamp: Any,
        includeCreatedAt: Boolean,
    ): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "campingID" to campingId,
            "updatedAt" to serverTimestamp,
        )
        if (includeCreatedAt) payload["createdAt"] = serverTimestamp
        return payload
    }

    /** Reminder-timing save path only. */
    fun reminderTimingPayload(
        timing: ScheduleReminderTiming,
        serverTimestamp: Any,
    ): Map<String, Any?> =
        linkedMapOf(
            "reminderTiming" to timing.wireValue,
            "updatedAt" to serverTimestamp,
        )

    /**
     * Day upsert. `title` is written `""` **only on first create** and never
     * overwritten thereafter (preserves a curated title set via [dayTitlePayload]).
     */
    fun dayPayload(
        campingId: String,
        date: Date,
        serverTimestamp: Any,
        includeCreatedAt: Boolean,
    ): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "campingID" to campingId,
            "date" to DateKeys.startOfDay(date),
            "updatedAt" to serverTimestamp,
        )
        if (includeCreatedAt) {
            payload["title"] = ""
            payload["createdAt"] = serverTimestamp
        }
        return payload
    }

    /** Curated day-title edit. */
    fun dayTitlePayload(
        title: String,
        serverTimestamp: Any,
    ): Map<String, Any?> =
        linkedMapOf(
            "title" to title,
            "updatedAt" to serverTimestamp,
        )

    /** Program upsert — `campDayID` is recomputed from `startDate`. */
    fun programPayload(
        program: Program,
        serverTimestamp: Any,
        deleteField: Any,
        includeCreatedAt: Boolean,
    ): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "campingID" to program.campingId,
            "campDayID" to DateKeys.campDayId(program.campingId, program.startDate),
            "title" to program.title.trim(),
            "type" to program.type.wireValue,
            "startDate" to program.startDate,
            "endDate" to program.endDate,
            "location" to program.location.trim(),
            "description" to program.description,
            "updatedAt" to serverTimestamp,
        )
        payload["venuePointID"] = program.venuePointId?.trim()?.takeUnless { it.isBlank() } ?: deleteField
        if (includeCreatedAt) payload["createdAt"] = serverTimestamp
        return payload
    }
}
