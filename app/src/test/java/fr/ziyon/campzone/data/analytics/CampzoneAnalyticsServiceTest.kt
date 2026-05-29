package fr.ziyon.campzone.data.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class CampzoneAnalyticsServiceTest {
    @Test
    fun viewCampingMatchesIosFirebaseEventShape() {
        val logger = RecordingAnalyticsLogger()
        val service = CampzoneAnalyticsService(logger)

        service.viewCamping("camp-1", "Summer Camp")

        assertEquals(
            AnalyticsEvent(
                name = AnalyticsEventNames.ViewItem,
                parameters = mapOf(
                    AnalyticsParams.ItemId to "camp-1",
                    AnalyticsParams.ItemName to "Summer Camp",
                    AnalyticsParams.ContentType to AnalyticsContentTypes.Camping,
                ),
            ),
            logger.events.single(),
        )
    }

    @Test
    fun customCampingEventsUseIosNamesAndParameters() {
        val logger = RecordingAnalyticsLogger()
        val service = CampzoneAnalyticsService(logger)

        service.registerForCamping("camp-1")
        service.cancelCamping("camp-1")
        service.viewSchedule("camp-1")
        service.viewSongbook("camp-1")
        service.viewTeams("camp-1")

        assertEquals(
            listOf(
                AnalyticsEvent(AnalyticsEventNames.RegisterCamping, mapOf(AnalyticsParams.ItemId to "camp-1")),
                AnalyticsEvent(AnalyticsEventNames.CancelCamping, mapOf(AnalyticsParams.ItemId to "camp-1")),
                AnalyticsEvent(AnalyticsEventNames.ViewSchedule, mapOf(AnalyticsParams.CampingId to "camp-1")),
                AnalyticsEvent(AnalyticsEventNames.ViewSongbook, mapOf(AnalyticsParams.CampingId to "camp-1")),
                AnalyticsEvent(AnalyticsEventNames.ViewTeams, mapOf(AnalyticsParams.CampingId to "camp-1")),
            ),
            logger.events,
        )
    }

    @Test
    fun songEventsUseItemParameters() {
        val logger = RecordingAnalyticsLogger()
        val service = CampzoneAnalyticsService(logger)

        service.playSong("song-1", "Maranata")
        service.favoriteSong("song-1", "Maranata")

        assertEquals(
            listOf(
                AnalyticsEvent(
                    AnalyticsEventNames.PlaySong,
                    mapOf(AnalyticsParams.ItemId to "song-1", AnalyticsParams.ItemName to "Maranata"),
                ),
                AnalyticsEvent(
                    AnalyticsEventNames.FavoriteSong,
                    mapOf(AnalyticsParams.ItemId to "song-1", AnalyticsParams.ItemName to "Maranata"),
                ),
            ),
            logger.events,
        )
    }

    @Test
    fun searchIsTrimmedAndBlankSearchIsIgnored() {
        val logger = RecordingAnalyticsLogger()
        val service = CampzoneAnalyticsService(logger)

        service.searchCampings("  ")
        service.searchCampings("  annecy  ")

        assertEquals(
            AnalyticsEvent(
                AnalyticsEventNames.Search,
                mapOf(AnalyticsParams.SearchTerm to "annecy"),
            ),
            logger.events.single(),
        )
    }

    @Test
    fun authEventsMatchIosNames() {
        val logger = RecordingAnalyticsLogger()
        val service = CampzoneAnalyticsService(logger)

        service.signIn("google")
        service.signOut()

        assertEquals(
            listOf(
                AnalyticsEvent(AnalyticsEventNames.Login, mapOf(AnalyticsParams.Method to "google")),
                AnalyticsEvent(AnalyticsEventNames.SignOut),
            ),
            logger.events,
        )
    }

    private class RecordingAnalyticsLogger : AnalyticsLogger {
        val events = mutableListOf<AnalyticsEvent>()

        override fun log(event: AnalyticsEvent) {
            events += event
        }
    }
}
