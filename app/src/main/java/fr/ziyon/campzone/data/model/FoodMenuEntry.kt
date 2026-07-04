package fr.ziyon.campzone.data.model

import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone
import java.util.UUID

/** One structured dish inside a menu entry. Allergen tokens share the profile vocabulary. */
data class FoodMenuItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val details: String? = null,
    val allergens: List<String> = emptyList(),
    val note: String? = null,
) {
    fun matchedAllergens(userFoodAllergies: Collection<String>): List<String> {
        val normalizedUser = userFoodAllergies
            .mapNotNull { it.trim().takeUnless(String::isEmpty)?.lowercase() }
            .toSet()
        if (normalizedUser.isEmpty()) return emptyList()
        return allergens.filter { it.trim().lowercase() in normalizedUser }
    }
}

/**
 * `campings/{id}/foodMenu/{entryId}` (`02-firestore-schema.md` §4.4). Doc ID is
 * deterministic: `"<yyyy-MM-dd>-<meal>"` (see [DateKeys.foodMenuId]). Not
 * Codable on iOS - hand-mapped here too.
 */
data class FoodMenuEntry(
    val id: String,
    val campingId: String,
    val date: Date,
    val meal: FoodMealKind,
    val items: List<FoodMenuItem> = emptyList(),
    val notes: String = "",
) {
    val dishes: List<String>
        get() = items.map(FoodMenuItem::name)
}

internal fun Map<String, Any?>.toFoodMenuEntryOrNull(documentId: String, campingId: String): FoodMenuEntry? {
    val date = dateValue("date") ?: return null
    val meal = FoodMealKind.fromWire(stringValue("meal")) ?: return null
    val structuredItems = mapListValue("items").mapNotNull { it.toFoodMenuItemOrNull() }
    val items = structuredItems.takeIf { it.isNotEmpty() }
        ?: stringListValue("dishes")
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { FoodMenuItem(name = it) }
    return FoodMenuEntry(
        id = documentId,
        campingId = stringValue("campingID") ?: campingId,
        date = date,
        meal = meal,
        items = items,
        notes = rawStringValue("notes").orEmpty(),
    )
}

private fun Map<String, Any?>.toFoodMenuItemOrNull(): FoodMenuItem? {
    val name = rawStringValue("name")?.trim().orEmpty()
    if (name.isEmpty()) return null
    return FoodMenuItem(
        id = rawStringValue("id")?.takeUnless { it.isBlank() } ?: UUID.randomUUID().toString(),
        name = name,
        details = rawStringValue("details")?.trim()?.takeUnless(String::isEmpty),
        allergens = stringListValue("allergens"),
        note = rawStringValue("note")?.trim()?.takeUnless(String::isEmpty),
    )
}

private fun FoodMenuItem.payload(): Map<String, Any?> = linkedMapOf<String, Any?>(
    "id" to id,
    "name" to name.trim(),
    "allergens" to allergens.map(String::trim).filter(String::isNotEmpty).distinctBy(String::lowercase),
).apply {
    details?.trim()?.takeUnless(String::isEmpty)?.let { put("details", it) }
    note?.trim()?.takeUnless(String::isEmpty)?.let { put("note", it) }
}

internal object FoodMenuPayload {
    fun entryPayload(entry: FoodMenuEntry): Map<String, Any?> =
        linkedMapOf(
            "campingID" to entry.campingId,
            "date" to entry.date,
            "meal" to entry.meal.wireValue,
            "dishes" to entry.dishes.map(String::trim).filter(String::isNotEmpty),
            "items" to entry.items.filter { it.name.isNotBlank() }.map(FoodMenuItem::payload),
            "notes" to entry.notes.trim(),
        )
}

/**
 * Application-level Menu ↔ Program sync (`02-firestore-schema.md` §4.5). There
 * is no Firestore trigger - clients write **both** the menu doc and the
 * generated program whenever either side changes. Menu dishes/notes stay in
 * the menu document; a Program's description stays leader-owned. Existing
 * scheduling fields are preserved when a leader-edited program already exists.
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
        return FoodMenuEntry(
            id = DateKeys.foodMenuId(program.startDate, meal),
            campingId = program.campingId,
            date = program.startDate,
            meal = meal,
            items = existing?.items ?: listOf(FoodMenuItem(name = mealTitle(meal))),
            notes = existing?.notes.orEmpty(),
        )
    }

    fun matches(program: Program, entry: FoodMenuEntry): Boolean =
        mealKind(program.type) == entry.meal &&
            DateKeys.dayKey(program.startDate) == DateKeys.dayKey(entry.date)

    /**
     * Produces the program to write for [entry]. When [existing] is non-null its
     * leader-owned scheduling fields are preserved; otherwise default meal times
     * and location apply.
     */
    fun programFor(entry: FoodMenuEntry, existing: Program?): Program {
        val programId = DateKeys.menuProgramId(entry.id)
        val title = entry.meal.name
        return if (existing != null) {
            existing.copy(
                title = title,
                type = programType(entry.meal),
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
                description = "",
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
