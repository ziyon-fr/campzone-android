package fr.ziyon.campzone.data.schedule

import fr.ziyon.campzone.data.model.CampDay
import fr.ziyon.campzone.data.model.CampingSchedule
import fr.ziyon.campzone.data.model.Program
import fr.ziyon.campzone.data.model.ProgramType
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarExportTest {
    @Test
    fun plannerUsesThirtyMinuteFallbackAndCampContext() {
        val start = Date(1_800_000L)
        val draft = ScheduleCalendarExportPlanner.draft(
            program = program(
                id = "worship",
                start = start,
                end = start,
                description = "Bring a notebook",
            ),
            campingTitle = "Summer Camp",
            typeName = "Preaching",
        )

        assertEquals(start.time + 30 * 60 * 1_000L, draft.endDate.time)
        assertEquals(
            "Camping: Summer Camp\nType: Preaching\n\nBring a notebook",
            draft.notes,
        )
    }

    @Test
    fun plannerFiltersAndSortsSchedulePrograms() {
        val later = program(id = "later", start = Date(20_000L), end = Date(30_000L))
        val earlier = program(id = "earlier", start = Date(10_000L), end = Date(15_000L))
        val schedule = CampingSchedule(
            campingId = "camp",
            days = listOf(
                CampDay(
                    id = "day",
                    campingId = "camp",
                    date = Date(0L),
                    programs = listOf(later, earlier),
                ),
            ),
        )

        val drafts = ScheduleCalendarExportPlanner.drafts(
            schedule = schedule,
            campingTitle = "Camp",
            programIds = setOf("earlier"),
            typeName = { "Other" },
        )

        assertEquals(listOf("earlier"), drafts.map { it.id })
    }

    @Test
    fun insertContractCarriesEveryCalendarField() {
        val draft = CalendarEventDraft(
            id = "one",
            title = "Morning session",
            startDate = Date(10L),
            endDate = Date(20L),
            location = "Main hall",
            notes = "Camping: Camp",
        )

        assertEquals(
            CalendarInsertIntentContract(
                title = "Morning session",
                startMillis = 10L,
                endMillis = 20L,
                location = "Main hall",
                description = "Camping: Camp",
            ),
            draft.toInsertIntentContract(),
        )
    }

    @Test
    fun icsContainsAllEventsAndEscapesUserText() {
        val text = CalendarIcsEncoder.encode(
            drafts = listOf(
                CalendarEventDraft(
                    id = "one",
                    title = "Games, teams",
                    startDate = Date(0L),
                    endDate = Date(1_800_000L),
                    location = "Hall; A",
                    notes = "Line one\nLine two",
                ),
                CalendarEventDraft(
                    id = "two",
                    title = "Dinner",
                    startDate = Date(3_600_000L),
                    endDate = Date(5_400_000L),
                    location = "Kitchen",
                    notes = "Camping: Camp",
                ),
            ),
            generatedAt = Date(0L),
        )

        assertEquals(2, "BEGIN:VEVENT".toRegex().findAll(text).count())
        assertTrue(text.contains("SUMMARY:Games\\, teams\r\n"))
        assertTrue(text.contains("LOCATION:Hall\\; A\r\n"))
        assertTrue(text.contains("DESCRIPTION:Line one\\nLine two\r\n"))
        assertTrue(text.endsWith("END:VCALENDAR\r\n"))
        assertFalse(text.contains("Line one\nLine two"))
    }

    private fun program(
        id: String,
        start: Date,
        end: Date,
        description: String = "",
    ) = Program(
        id = id,
        campingId = "camp",
        campDayId = "day",
        title = id,
        type = ProgramType.Other,
        startDate = start,
        endDate = end,
        location = "Hall",
        description = description,
    )
}
