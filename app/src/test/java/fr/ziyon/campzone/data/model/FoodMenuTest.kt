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
            dishes = listOf("Rice", "Beans", " "),
            notes = "Vegan option available",
        )
        val payload = FoodMenuPayload.entryPayload(original)
        assertEquals("camp-1", payload["campingID"])
        assertEquals("dinner", payload["meal"])
        assertEquals(listOf("Rice", "Beans"), payload["dishes"]) // blanks dropped

        val decoded = payload.toFoodMenuEntryOrNull(documentId = original.id, campingId = "camp-1")!!
        assertEquals(FoodMealKind.Dinner, decoded.meal)
        assertEquals(listOf("Rice", "Beans"), decoded.dishes)
        assertEquals("Vegan option available", decoded.notes)
    }

    @Test
    fun descriptionFormatAndReverseParse() {
        val rendered = FoodMenuProgramSync.renderDescription(
            dishes = listOf("Pasta", "Salad"),
            notes = "Gluten free",
        )
        assertEquals("- Pasta\n- Salad\n\nNotes: Gluten free", rendered)

        val parsed = FoodMenuProgramSync.parseDishes("Menu: Pasta, * Salad\n\nNotes: Gluten free")
        assertEquals(listOf("Pasta", "Salad"), parsed) // strips "- ", drops Notes line
        assertEquals("Gluten free", FoodMenuProgramSync.parseNotes(rendered))
    }

    @Test
    fun newMenuProgramUsesDefaultMealWindowAndType() {
        val date = dateOf(2026, 7, 15, hour = 0, minute = 0)
        val entry = FoodMenuEntry(
            id = DateKeys.foodMenuId(date, FoodMealKind.Breakfast),
            campingId = "camp-1",
            date = date,
            meal = FoodMealKind.Breakfast,
            dishes = listOf("Eggs"),
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
            dishes = listOf("Eggs"),
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
            dishes = listOf("Soup"),
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

        // menu-owned fields regenerated…
        assertEquals(ProgramType.Lunch, program.type)
        assertTrue(program.description.contains("- Soup"))
        // …but leader scheduling preserved
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
            description = "- Chili\n- Rice\n\nNotes: Gluten-free",
        )

        val entry = FoodMenuProgramSync.menuEntryFor(program, existing = null)!!

        assertEquals("2026-08-03-lunch", entry.id)
        assertEquals(FoodMealKind.Lunch, entry.meal)
        assertEquals(listOf("Chili", "Rice"), entry.dishes)
        assertEquals("Gluten-free", entry.notes)
        assertTrue(FoodMenuProgramSync.matches(program, entry))
    }

    private companion object {
        fun dateOf(year: Int, month: Int, day: Int, hour: Int = 9, minute: Int = 0): Date =
            GregorianCalendar(TimeZone.getDefault()).apply {
                clear()
                set(year, month - 1, day, hour, minute, 0)
            }.time
    }
}
