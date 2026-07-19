package fr.ziyon.campzone.core.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

class CampzoneWidgetDeepLinkTest {
    private val now = 1_000_000L

    private val snapshot = CampzoneWidgetSnapshot(
        camp = WidgetCampSummary(
            id = "camp 1",
            name = "Summer Camp",
            startMillis = now - 86_400_000L,
            endMillis = now + 172_800_000L,
        ),
        programs = listOf(
            WidgetProgramSummary(
                id = "morning worship",
                title = "Morning Worship",
                startMillis = now - 600_000L,
                endMillis = now + 1_800_000L,
                location = "Main Hall",
                campId = "camp 1",
            ),
        ),
        pass = WidgetCampPassSummary(
            campId = "camp 1",
            campName = "Summer Camp",
            qrValue = "campzone://checkin?v=1",
            attendeeName = "Sarah Miller",
            teamName = "Blue Team",
            lodgingName = "Cabin 3",
        ),
        packing = WidgetPackingSummary(
            campName = "Summer Camp",
            packedCount = 12,
            totalCount = 20,
            campId = "camp 1",
        ),
        team = WidgetTeamSummary(
            teamName = "Blue Team",
            rank = 2,
            totalTeams = 6,
            points = 340,
            teamId = "team 1",
            campId = "camp 1",
        ),
        announcement = WidgetAnnouncementSummary(
            id = "announcement 1",
            title = "Bring a flashlight",
            body = "Evening vespers will be outside.",
            createdMillis = now - 3_600_000L,
            campName = "Summer Camp",
        ),
    )

    @Test
    fun widgetUrlsMatchIosDestinations() {
        assertEquals("campzone://camping/camp%201", widgetDeepLinkUrl(WidgetSurface.Countdown, snapshot, now))
        assertEquals("campzone://program/morning%20worship?c=camp%201", widgetDeepLinkUrl(WidgetSurface.UpNext, snapshot, now))
        assertEquals("campzone://camp-pass/camp%201", widgetDeepLinkUrl(WidgetSurface.Pass, snapshot, now))
        assertEquals("campzone://packing/camp%201", widgetDeepLinkUrl(WidgetSurface.Packing, snapshot, now))
        assertEquals("campzone://team/team%201?c=camp%201", widgetDeepLinkUrl(WidgetSurface.Team, snapshot, now))
        assertEquals("campzone://announcement/announcement%201", widgetDeepLinkUrl(WidgetSurface.Announcement, snapshot, now))
    }

    @Test
    fun upNextFallsBackToScheduleWhenNoProgramIsSelectable() {
        val emptyPrograms = snapshot.copy(programs = emptyList())

        assertEquals(
            "campzone://schedule/camp%201",
            widgetDeepLinkUrl(WidgetSurface.UpNext, emptyPrograms, now),
        )
    }

    @Test
    fun teamFallsBackToTeamsListWhenTeamIdIsMissing() {
        val teamWithoutId = snapshot.copy(team = snapshot.team?.copy(teamId = null))

        assertEquals(
            "campzone://camping-teams/camp%201",
            widgetDeepLinkUrl(WidgetSurface.Team, teamWithoutId, now),
        )
    }

    @Test
    fun nextRefreshTargetsTheNextBoundary() {
        assertEquals(
            now + 1_800_000L + 1_000L,
            nextWidgetRefreshMillis(snapshot, now),
        )
    }
}
