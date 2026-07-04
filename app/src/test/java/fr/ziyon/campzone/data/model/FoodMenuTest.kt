package fr.ziyon.campzone.data.model

import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodMenuTest {

    @Test
    fun deterministicIds() {
        val date = dateOf(2026, 7, 15)
        assertEquals("2026-07-15-lunch", DateKeys.foodMenuId(date, FoodMealKind.Lunch))
        assertEquals("menu-2026-07-15-lunch", DateKeys.menuProgramId("2026-07-15-lunch"))
    }

    @Test
    fun entryPayloadRoundTrips() {
        val date = dateOf(2026, 7, 15)
        val original = FoodMenuEntry(
            id = DateKeys.foodMenuId(date, FoodMealKind.Dinner),
            campingId = "camp-1",
            date = date,
            meal = FoodMealKind.Dinner,
            items = listOf(
                FoodMenuItem(
                    id = "rice",
                    name = "Rice",
                    details = "Steamed",
                    allergens = listOf("sesame"),
                    note = "Serve warm",
                ),
                FoodMenuItem(id = "beans", name = "Beans"),
                FoodMenuItem(id = "blank", name = " "),
            ),
            notes = "Vegan option available",
        )
        val payload = FoodMenuPayload.entryPayload(original)
        assertEquals("camp-1", payload["campingID"])
        assertEquals("dinner", payload["meal"])
        assertEquals(listOf("Rice", "Beans"), payload["dishes"]) // blanks dropped
        @Suppress("UNCHECKED_CAST")
        val itemPayloads = payload["items"] as List<Map<String, Any?>>
        assertEquals(2, itemPayloads.size)
        assertEquals("Steamed", itemPayloads.first()["details"])
        assertEquals(listOf("sesame"), itemPayloads.first()["allergens"])

        val decoded = payload.toFoodMenuEntryOrNull(documentId = original.id, campingId = "camp-1")!!
        assertEquals(FoodMealKind.Dinner, decoded.meal)
        assertEquals(listOf("Rice", "Beans"), decoded.dishes.take(2))
        assertEquals("Steamed", decoded.items.first().details)
        assertEquals("Serve warm", decoded.items.first().note)
        assertEquals("Vegan option available", decoded.notes)
    }

    @Test
    fun legacyDishNamesDecodeAsStructuredItems() {
        val date = dateOf(2026, 7, 15)
        val decoded = mapOf<String, Any?>(
            "campingID" to "camp-1",
            "date" to date,
            "meal" to "lunch",
            "dishes" to listOf(" Soup ", "", "Bread"),
        ).toFoodMenuEntryOrNull("2026-07-15-lunch", "camp-1")!!

        assertEquals(listOf("Soup", "Bread"), decoded.dishes)
        assertTrue(decoded.items.all { it.allergens.isEmpty() })
    }

    @Test
    fun structuredItemsTakePriorityAndMatchProfileAllergens() {
        val date = dateOf(2026, 7, 15)
        val decoded = mapOf<String, Any?>(
            "date" to date,
            "meal" to "dinner",
            "dishes" to listOf("Legacy dish"),
            "items" to listOf(
                mapOf(
                    "id" to "dish-1",
                    "name" to "Satay",
                    "allergens" to listOf("peanuts", "soy"),
                ),
            ),
        ).toFoodMenuEntryOrNull("2026-07-15-dinner", "camp-1")!!

        assertEquals(listOf("Satay"), decoded.dishes)
        assertEquals(listOf("peanuts"), decoded.items.single().matchedAllergens(setOf("PEANUTS")))
    }

    @Test
    fun newMenuProgramUsesDefaultMealWindowAndType() {
        val date = dateOf(2026, 7, 15, hour = 0, minute = 0)
        val entry = FoodMenuEntry(
            id = DateKeys.foodMenuId(date, FoodMealKind.Breakfast),
            campingId = "camp-1",
            date = date,
            meal = FoodMealKind.Breakfast,
            items = listOf(FoodMenuItem(name = "Eggs")),
        )
        val program = FoodMenuProgramSync.programFor(entry, existing = null)

        assertEquals("menu-2026-07-15-breakfast", program.id)
        assertEquals(ProgramType.Breakfast, program.type)
        assertEquals("Dining hall", program.location)
        // breakfast default 08:00, 45 min
        val cal = GregorianCalendar(TimeZone.getDefault()).apply { time = program.startDate }
        assertEquals(8, cal.get(GregorianCalendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(GregorianCalendar.MINUTE))
        assertEquals(45 * 60_000L, program.endDate.time - program.startDate.time)
    }

    @Test
    fun newMenuProgramHonorsExplicitEntryTime() {
        val date = dateOf(2026, 7, 15, hour = 9, minute = 15)
        val entry = FoodMenuEntry(
            id = DateKeys.foodMenuId(date, FoodMealKind.Breakfast),
            campingId = "camp-1",
            date = date,
            meal = FoodMealKind.Breakfast,
            items = listOf(FoodMenuItem(name = "Eggs")),
        )

        val program = FoodMenuProgramSync.programFor(entry, existing = null)

        val cal = GregorianCalendar(TimeZone.getDefault()).apply { time = program.startDate }
        assertEquals(9, cal.get(GregorianCalendar.HOUR_OF_DAY))
        assertEquals(15, cal.get(GregorianCalendar.MINUTE))
        assertEquals(45 * 60_000L, program.endDate.time - program.startDate.time)
    }

    @Test
    fun existingProgramPreservesLeaderEditedSchedulingFields() {
        val date = dateOf(2026, 7, 15)
        val entry = FoodMenuEntry(
            id = DateKeys.foodMenuId(date, FoodMealKind.Lunch),
            campingId = "camp-1",
            date = date,
            meal = FoodMealKind.Lunch,
            items = listOf(FoodMenuItem(name = "Soup")),
        )
        val existing = Program(
            id = "menu-2026-07-15-lunch",
            campingId = "camp-1",
            campDayId = DateKeys.campDayId("camp-1", date),
            title = "OLD TITLE",
            type = ProgramType.Other,
            startDate = Date(date.time + 99_000_000), // leader moved it
            endDate = Date(date.time + 99_600_000),
            location = "Riverside",
            description = "old",
        )
        val program = FoodMenuProgramSync.programFor(entry, existing)

        // Meal identity is synchronized…
        assertEquals(ProgramType.Lunch, program.type)
        // …but leader-owned content and scheduling are preserved.
        assertEquals("old", program.description)
        assertEquals(existing.startDate, program.startDate)
        assertEquals("Riverside", program.location)
        assertEquals(existing.id, program.id)
    }

    @Test
    fun mealProgramConvertsBackToMenuEntry() {
        val start = dateOf(2026, 8, 3, hour = 12, minute = 45)
        val program = Program(
            id = "program-1",
            campingId = "camp-1",
            campDayId = DateKeys.campDayId("camp-1", start),
            title = "Lunch",
            type = ProgramType.Lunch,
            startDate = start,
            endDate = Date(start.time + 60 * 60_000L),
            location = "Dining hall",
            description = "Meet by the dining tent.",
        )
        val existing = FoodMenuEntry(
            id = "2026-08-03-lunch",
            campingId = "camp-1",
            date = start,
            meal = FoodMealKind.Lunch,
            items = listOf(FoodMenuItem(name = "Chili"), FoodMenuItem(name = "Rice")),
            notes = "Gluten-free",
        )

        val entry = FoodMenuProgramSync.menuEntryFor(program, existing = existing)!!

        assertEquals("2026-08-03-lunch", entry.id)
        assertEquals(FoodMealKind.Lunch, entry.meal)
        assertEquals(listOf("Chili", "Rice"), entry.dishes)
        assertEquals("Gluten-free", entry.notes)
        assertTrue(FoodMenuProgramSync.matches(program, entry))
    }

    @Test
    fun newMealProgramDoesNotSerializeMenuIntoDescription() {
        val date = dateOf(2026, 7, 15)
        val entry = FoodMenuEntry(
            id = DateKeys.foodMenuId(date, FoodMealKind.Dinner),
            campingId = "camp-1",
            date = date,
            meal = FoodMealKind.Dinner,
            items = listOf(FoodMenuItem(name = "Rice"), FoodMenuItem(name = "Beans")),
            notes = "Vegan option",
        )

        assertEquals("", FoodMenuProgramSync.programFor(entry, existing = null).description)
    }

    private companion object {
        fun dateOf(year: Int, month: Int, day: Int, hour: Int = 9, minute: Int = 0): Date =
            GregorianCalendar(TimeZone.getDefault()).apply {
                clear()
                set(year, month - 1, day, hour, minute, 0)
            }.time
    }
}
