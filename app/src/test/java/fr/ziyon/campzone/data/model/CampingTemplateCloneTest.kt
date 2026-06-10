package fr.ziyon.campzone.data.model

import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampingTemplateCloneTest {

    @Test
    fun formDefaultsCreateNextYearClosedDraft() {
        val source = camping(
            title = "Summer Camp 2025",
            startDate = utcDate(2025, 7, 5),
            endDate = utcDate(2025, 7, 8),
        )

        val form = CampingTemplateCloneForm.from(source, utcCalendar())

        assertEquals("Summer Camp 2026", form.title)
        assertEquals(2026, year(form.startDate))
        assertEquals(7, month(form.startDate))
        assertEquals(5, day(form.startDate))
        assertEquals(2026, year(form.endDate))
        assertEquals(CampingRegistrationStatus.Closed, form.registrationStatus)
        assertTrue(form.options.hasAnyContent)
    }

    @Test
    fun validationRequiresTitleDatesAndContent() {
        val form = CampingTemplateCloneForm(
            title = "  ",
            startDate = utcDate(2026, 7, 8),
            endDate = utcDate(2026, 7, 5),
            options = CampingTemplateCloneOptions(
                includeSchedule = false,
                includeTeams = false,
                includeSongbook = false,
                includeGuidelines = false,
            ),
        )

        assertEquals(
            listOf(
                CampingTemplateCloneValidationError.TitleRequired,
                CampingTemplateCloneValidationError.EndDateBeforeStartDate,
                CampingTemplateCloneValidationError.ContentRequired,
            ),
            form.validationErrors(),
        )
    }

    @Test
    fun templateCloneClearsLiveStateAndShiftsRegistrationDeadline() {
        val source = camping(
            startDate = utcDate(2025, 7, 5),
            endDate = utcDate(2025, 7, 8),
            registrationDeadline = utcDate(2025, 6, 20),
            guidelines = "Pack warm layers.",
            attendees = listOf(
                CampingAttendee(
                    id = "attendee-1",
                    userId = "attendee-1",
                    displayName = "Maria",
                    church = "Paris Central SDA",
                    age = 20,
                    languages = listOf("fr"),
                    registrationStatus = RegistrationApprovalStatus.Approved,
                ),
            ),
        )
        val request = CampingTemplateCloneRequest(
            sourceCampingId = source.id,
            targetCampingId = "camp-2026",
            title = "Summer Camp 2026",
            startDate = utcDate(2026, 7, 5),
            endDate = utcDate(2026, 7, 8),
            registrationStatus = CampingRegistrationStatus.Closed,
            options = CampingTemplateCloneOptions(includeGuidelines = false),
        )

        val clone = source.templateClone(request, utcCalendar())

        assertEquals("camp-2026", clone.id)
        assertEquals("Summer Camp 2026", clone.title)
        assertEquals(CampingRegistrationStatus.Closed, clone.registrationStatus)
        assertEquals(emptyList<CampingAttendee>(), clone.attendees)
        assertEquals("", clone.guidelines)
        assertEquals(utcDate(2026, 6, 20), clone.registrationDeadline)
        assertFalse(clone.isFeatured)
        assertEquals(null, clone.createdByUid)
        assertEquals(null, clone.createdAt)
    }

    private fun camping(
        title: String = "Summer Camp 2025",
        startDate: Date = utcDate(2025, 7, 5),
        endDate: Date = utcDate(2025, 7, 8),
        registrationDeadline: Date? = null,
        guidelines: String = "",
        attendees: List<CampingAttendee> = emptyList(),
    ) = Camping(
        id = "camp-2025",
        title = title,
        description = "A week together",
        startDate = startDate,
        endDate = endDate,
        organizerLevel = OrganizerLevel(OrganizerType.Church, "Paris Central SDA"),
        location = "Lake Annecy",
        registrationStatus = CampingRegistrationStatus.Open,
        guidelines = guidelines,
        createdByUid = "creator-1",
        createdByName = "Creator",
        createdAt = utcDate(2025, 1, 1),
        updatedAt = utcDate(2025, 1, 2),
        registrationDeadline = registrationDeadline,
        isFeatured = true,
        attendees = attendees,
    )

    private fun utcDate(year: Int, month: Int, day: Int): Date =
        utcCalendar().apply {
            clear()
            set(year, month - 1, day, 12, 0, 0)
        }.time

    private fun year(date: Date): Int = calendarPart(date, Calendar.YEAR)
    private fun month(date: Date): Int = calendarPart(date, Calendar.MONTH) + 1
    private fun day(date: Date): Int = calendarPart(date, Calendar.DAY_OF_MONTH)

    private fun calendarPart(date: Date, field: Int): Int =
        utcCalendar().apply { time = date }.get(field)

    private fun utcCalendar(): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("UTC"))
}
