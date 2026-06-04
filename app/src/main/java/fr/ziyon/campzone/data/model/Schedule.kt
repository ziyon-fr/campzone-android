package fr.ziyon.campzone.data.model

import java.util.Calendar
import java.util.Date

/**
 * Schedule models (`02-firestore-schema.md` §4). `campings/{id}/schedule/config`
 * is a single doc (ID literal `config`); days are nested under it and programs
 * under days. The program `campDayID` is **always recomputed** from `startDate`
 * on write - never trust an inbound value.
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
    val programs: List<Program> = emptyList(),
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
    val hasCustomTitle: Boolean = false,
)

/** In-memory aggregate combining schedule config + days (each holding programs). */
data class CampingSchedule(
    val campingId: String,
    val reminderTiming: ScheduleReminderTiming = ScheduleReminderTiming.None,
    val days: List<CampDay> = emptyList(),
) {
    val sortedDays: List<CampDay>
        get() = days.sortedWith(compareBy { it.date })

    val allPrograms: List<Program>
        get() = days.flatMap { it.programs }

    val customProgramTypes: List<CustomProgramType>
        get() {
            val seen = mutableSetOf<String>()
            return allPrograms
                .mapNotNull { it.customType }
                .filter { seen.add(it.id) }
        }
}

data class CustomProgramType(
    val name: String,
    val symbol: String = FallbackSymbol,
    val colorHex: String = FallbackColorHex,
) {
    val trimmedName: String get() = name.trim()
    val isValid: Boolean get() = trimmedName.isNotEmpty()
    val id: String get() = "${trimmedName.lowercase()}|$symbol|${colorHex.uppercase()}"

    companion object {
        const val FallbackSymbol = "sparkles"
        const val FallbackColorHex = "#8D6E63"
    }
}

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
    val customTypeName: String? = null,
    val customTypeSymbol: String? = null,
    val customTypeColorHex: String? = null,
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
) {
    val customType: CustomProgramType?
        get() {
            val name = customTypeName?.trim()?.takeUnless { it.isBlank() } ?: return null
            return CustomProgramType(
                name = name,
                symbol = customTypeSymbol?.takeUnless { it.isBlank() } ?: CustomProgramType.FallbackSymbol,
                colorHex = customTypeColorHex?.takeUnless { it.isBlank() } ?: CustomProgramType.FallbackColorHex,
            ).takeIf { type == ProgramType.Custom }
        }
}

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
        customTypeName = stringValue("customTypeName"),
        customTypeSymbol = stringValue("customTypeSymbol"),
        customTypeColorHex = stringValue("customTypeColorHex"),
        createdAt = dateValue("createdAt"),
        updatedAt = dateValue("updatedAt"),
    )
}

// endregion

/**
 * Builds a display-ready schedule by merging the camping's date range with the
 * loaded days. Every date from [Camping.startDate] to [Camping.endDate] gets a
 * row, blank-titled days get a positional "Day N" label for display only (never
 * stored), and days outside the date range (with orphan programs) are appended.
 */
fun CampingSchedule.normalizedForCamping(
    camping: Camping,
    defaultDayTitle: (Int) -> String,
): CampingSchedule {
    val existingById = days.associateBy { it.id }
    val calendarDays = camping.scheduleDates.map { date ->
        val key = DateKeys.campDayId(campingId, date)
        existingById[key] ?: CampDay(id = key, campingId = campingId, date = date)
    }
    var dayNumber = 0
    val titled = calendarDays.map { day ->
        dayNumber++
        if (day.title.isBlank()) {
            day.copy(title = defaultDayTitle(dayNumber), hasCustomTitle = false)
        } else {
            day.copy(hasCustomTitle = true)
        }
    }
    val rangeDayIds = titled.map { it.id }.toSet()
    val orphanDays = existingById.values.filter { it.id !in rangeDayIds }
    return copy(days = (titled + orphanDays).sortedBy { it.date })
}

internal val Camping.scheduleDates: List<Date>
    get() {
        val dates = mutableListOf<Date>()
        val cal = Calendar.getInstance()
        cal.time = DateKeys.startOfDay(startDate)
        val endMidnight = DateKeys.startOfDay(endDate)
        while (!cal.time.after(endMidnight)) {
            dates.add(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return dates
    }

internal object SchedulePayload {

    /** `schedule/config` upsert - `reminderTiming` is NOT written here. */
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

    /** Program upsert - `campDayID` is recomputed from `startDate`. */
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
        val customType = program.customType
        if (customType != null) {
            payload["customTypeName"] = customType.trimmedName
            payload["customTypeSymbol"] = customType.symbol
            payload["customTypeColorHex"] = customType.colorHex
        } else {
            payload["customTypeName"] = deleteField
            payload["customTypeSymbol"] = deleteField
            payload["customTypeColorHex"] = deleteField
        }
        if (includeCreatedAt) payload["createdAt"] = serverTimestamp
        return payload
    }
}
