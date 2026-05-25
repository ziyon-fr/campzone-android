package fr.ziyon.campzone.data.model

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulePayloadTest {

    @Test
    fun configPayloadDoesNotWriteReminderTiming() {
        val payload = SchedulePayload.configPayload("camp-1", TS, includeCreatedAt = true)
        assertEquals("camp-1", payload["campingID"])
        assertEquals(TS, payload["createdAt"])
        assertFalse(payload.containsKey("reminderTiming"))

        val reminder = SchedulePayload.reminderTimingPayload(ScheduleReminderTiming.FifteenMinutes, TS)
        assertEquals("fifteenMinutes", reminder["reminderTiming"])
    }

    @Test
    fun dayWritesEmptyTitleOnlyOnCreate() {
        val onCreate = SchedulePayload.dayPayload("camp-1", Date(1_000_000), TS, includeCreatedAt = true)
        assertEquals("", onCreate["title"])
        assertEquals(TS, onCreate["createdAt"])

        val onUpdate = SchedulePayload.dayPayload("camp-1", Date(1_000_000), TS, includeCreatedAt = false)
        assertFalse(onUpdate.containsKey("title")) // curated title preserved
        assertFalse(onUpdate.containsKey("createdAt"))

        val titleEdit = SchedulePayload.dayTitlePayload("Opening Day", TS)
        assertEquals("Opening Day", titleEdit["title"])
    }

    @Test
    fun programRecomputesCampDayIdAndClearsVenueWithDelete() {
        val start = Date(1_700_000_000_000L)
        val program = Program(
            id = "p1",
            campingId = "camp-1",
            campDayId = "STALE-INBOUND-VALUE",
            title = "Morning prayer",
            type = ProgramType.Prayer,
            startDate = start,
            endDate = Date(start.time + 3_600_000),
            location = "Chapel",
            venuePointId = null,
        )
        val payload = SchedulePayload.programPayload(program, TS, DEL, includeCreatedAt = true)

        // campDayID is recomputed from startDate, never trusting the inbound value
        assertEquals(DateKeys.campDayId("camp-1", start), payload["campDayID"])
        assertFalse(payload["campDayID"] == "STALE-INBOUND-VALUE")
        assertEquals("prayer", payload["type"])
        assertEquals(DEL, payload["venuePointID"]) // delete-when-empty
        assertEquals(TS, payload["createdAt"])
    }

    @Test
    fun programRoundTrips() {
        val start = Date(1_700_000_000_000L)
        val original = Program(
            id = "p1",
            campingId = "camp-1",
            campDayId = DateKeys.campDayId("camp-1", start),
            title = "Games",
            type = ProgramType.Games,
            startDate = start,
            endDate = Date(start.time + 5_400_000),
            location = "Field",
            description = "Capture the flag",
            venuePointId = "pin-3",
        )
        val payload = SchedulePayload.programPayload(original, Date(8), DEL, includeCreatedAt = false)
        val decoded = payload.toProgramOrNull(documentId = "p1")!!

        assertEquals(original.title, decoded.title)
        assertEquals(ProgramType.Games, decoded.type)
        assertEquals(original.startDate, decoded.startDate)
        assertEquals(original.location, decoded.location)
        assertEquals(original.description, decoded.description)
        assertEquals("pin-3", decoded.venuePointId)
        assertEquals(original.campDayId, decoded.campDayId)
    }

    @Test
    fun campDayIdMatchesContractFormat() {
        // gregorian / en_US_POSIX / local TZ "yyyy-MM-dd"
        val id = DateKeys.campDayId("camp-1", Date(1_700_000_000_000L))
        assertTrue(id.startsWith("camp-1-day-"))
        assertTrue(id.removePrefix("camp-1-day-").matches(Regex("""\d{4}-\d{2}-\d{2}""")))
    }

    private companion object {
        const val TS = "serverTimestamp"
        const val DEL = "delete"
    }
}
