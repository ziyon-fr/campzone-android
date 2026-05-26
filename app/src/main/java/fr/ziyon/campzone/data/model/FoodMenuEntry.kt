package fr.ziyon.campzone.data.model

import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * `campings/{id}/foodMenu/{entryId}` (`02-firestore-schema.md` §4.4). Doc ID is
 * deterministic: `"<yyyy-MM-dd>-<meal>"` (see [DateKeys.foodMenuId]). Not
 * Codable on iOS — hand-mapped here too.
 */
data class FoodMenuEntry(
    val id: String,
    val campingId: String,
    val date: Date,
    val meal: FoodMealKind,
    val dishes: List<String> = emptyList(),
    val notes: String = "",
)

internal fun Map<String, Any?>.toFoodMenuEntryOrNull(documentId: String, campingId: String): FoodMenuEntry? {
    val date = dateValue("date") ?: return null
    val meal = FoodMealKind.fromWire(stringValue("meal")) ?: return null
    return FoodMenuEntry(
        id = documentId,
        campingId = stringValue("campingID") ?: campingId,
        date = date,
        meal = meal,
        dishes = stringListValue("dishes"),
        notes = rawStringValue("notes").orEmpty(),
    )
}

internal object FoodMenuPayload {
    fun entryPayload(entry: FoodMenuEntry): Map<String, Any?> =
        linkedMapOf(
            "campingID" to entry.campingId,
            "date" to entry.date,
            "meal" to entry.meal.wireValue,
            "dishes" to entry.dishes.map { it.trim() }.filter { it.isNotEmpty() },
            "notes" to entry.notes.trim(),
        )
}

/**
 * Application-level Menu ↔ Program sync (`02-firestore-schema.md` §4.5). There
 * is no Firestore trigger — clients write **both** the menu doc and the
 * generated program whenever either side changes. `title`/`type`/`description`
 * are menu-owned (always regenerated); `startDate`/`endDate`/`location`/
 * `campDayID`/`id` are preserved when a leader-edited program already exists.
 */
internal object FoodMenuProgramSync {

    private data class MealDefault(val hour: Int, val minute: Int, val durationMinutes: Int)

    private val DefaultLocation = "Dining hall"

    private fun mealDefault(meal: FoodMealKind): MealDefault = when (meal) {
        FoodMealKind.Breakfast -> MealDefault(8, 0, 45)
        FoodMealKind.Snack -> MealDefault(10, 30, 30)
        FoodMealKind.Lunch -> MealDefault(12, 30, 60)
        FoodMealKind.Dinner -> MealDefault(18, 30, 60)
    }

    fun programType(meal: FoodMealKind): ProgramType = when (meal) {
        FoodMealKind.Breakfast -> ProgramType.Breakfast
        FoodMealKind.Lunch -> ProgramType.Lunch
        FoodMealKind.Dinner -> ProgramType.Dinner
        FoodMealKind.Snack -> ProgramType.Snack
    }

    fun mealKind(programType: ProgramType): FoodMealKind? = when (programType) {
        ProgramType.Breakfast -> FoodMealKind.Breakfast
        ProgramType.Lunch -> FoodMealKind.Lunch
        ProgramType.Dinner -> FoodMealKind.Dinner
        ProgramType.Snack -> FoodMealKind.Snack
        else -> null
    }

    fun menuEntryId(program: Program): String? {
        val meal = mealKind(program.type) ?: return null
        return DateKeys.foodMenuId(program.startDate, meal)
    }

    fun menuEntryFor(program: Program, existing: FoodMenuEntry?): FoodMenuEntry? {
        val meal = mealKind(program.type) ?: return null
        val dishes = parseDishes(program.description)
        return FoodMenuEntry(
            id = DateKeys.foodMenuId(program.startDate, meal),
            campingId = program.campingId,
            date = program.startDate,
            meal = meal,
            dishes = dishes.ifEmpty { existing?.dishes ?: listOf(mealTitle(meal)) },
            notes = parseNotes(program.description),
        )
    }

    fun matches(program: Program, entry: FoodMenuEntry): Boolean =
        mealKind(program.type) == entry.meal &&
            DateKeys.dayKey(program.startDate) == DateKeys.dayKey(entry.date)

    /** Builds the program description: each dish on `"- <dish>"`, then a blank line + `"Notes: <notes>"`. */
    fun renderDescription(dishes: List<String>, notes: String): String {
        val lines = dishes.map { "- ${it.trim()}" }.toMutableList()
        val trimmedNotes = notes.trim()
        if (trimmedNotes.isNotEmpty()) {
            lines.add("")
            lines.add("Notes: $trimmedNotes")
        }
        return lines.joinToString("\n")
    }

    /** Reverse parse: split on newline AND comma, strip `-`/`*`/`Menu:` prefixes, drop the `Notes:` line. */
    fun parseDishes(description: String): List<String> =
        description
            .split("\n", ",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.contains("Notes:", ignoreCase = true) }
            .map { line ->
                line
                    .trimStart('-', '*', ' ')
                    .replace(Regex("^Menu:\\s*", RegexOption.IGNORE_CASE), "")
                    .trim()
            }
            .filter { it.isNotEmpty() }

    fun parseNotes(description: String): String {
        val notesLine = description
            .lineSequence()
            .firstOrNull { it.contains("Notes:", ignoreCase = true) }
            ?: return ""
        val marker = notesLine.indexOf("Notes:", ignoreCase = true)
        return notesLine.substring(marker + "Notes:".length).trim()
    }

    /**
     * Produces the program to write for [entry]. When [existing] is non-null its
     * leader-owned scheduling fields are preserved; otherwise default meal times
     * and location apply.
     */
    fun programFor(entry: FoodMenuEntry, existing: Program?): Program {
        val programId = DateKeys.menuProgramId(entry.id)
        val description = renderDescription(entry.dishes, entry.notes)
        val title = entry.meal.name
        return if (existing != null) {
            existing.copy(
                title = title,
                type = programType(entry.meal),
                description = description,
            )
        } else {
            val (start, end) = defaultWindow(entry.date, entry.meal)
            Program(
                id = programId,
                campingId = entry.campingId,
                campDayId = DateKeys.campDayId(entry.campingId, start),
                title = title,
                type = programType(entry.meal),
                startDate = start,
                endDate = end,
                location = DefaultLocation,
                description = description,
            )
        }
    }

    private fun defaultWindow(date: Date, meal: FoodMealKind): Pair<Date, Date> {
        val default = mealDefault(meal)
        val calendar = GregorianCalendar(TimeZone.getDefault())
        calendar.time = date
        val hasExplicitTime = calendar.get(GregorianCalendar.HOUR_OF_DAY) != 0 ||
            calendar.get(GregorianCalendar.MINUTE) != 0
        if (!hasExplicitTime) {
            calendar.time = DateKeys.startOfDay(date)
            calendar.set(GregorianCalendar.HOUR_OF_DAY, default.hour)
            calendar.set(GregorianCalendar.MINUTE, default.minute)
        }
        calendar.set(GregorianCalendar.SECOND, 0)
        calendar.set(GregorianCalendar.MILLISECOND, 0)
        val start = calendar.time
        calendar.add(GregorianCalendar.MINUTE, default.durationMinutes)
        return start to calendar.time
    }

    private fun mealTitle(meal: FoodMealKind): String = when (meal) {
        FoodMealKind.Breakfast -> "Breakfast"
        FoodMealKind.Lunch -> "Lunch"
        FoodMealKind.Dinner -> "Dinner"
        FoodMealKind.Snack -> "Snack"
    }
}
