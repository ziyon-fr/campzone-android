package fr.ziyon.campzone.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CampzoneDeepLinkTest {
    @Test
    fun parsesShareableCampingUrls() {
        assertEquals(
            CampzoneDeepLink.Camping("camp-1"),
            CampzoneDeepLink.fromCampzoneUrl("campzone://camping/camp-1"),
        )
        assertEquals(
            CampzoneDeepLink.Camping("camp-2"),
            CampzoneDeepLink.fromCampzoneUrl("campzone://campings?id=camp-2"),
        )
        assertEquals(
            CampzoneDeepLink.Camping("camp-3"),
            CampzoneDeepLink.fromCampzoneUrl("campzone://camping?campingID=camp-3"),
        )
    }

    @Test
    fun parsesShareableAnnouncementUrls() {
        assertEquals(
            CampzoneDeepLink.Announcement("announcement-1"),
            CampzoneDeepLink.fromCampzoneUrl("campzone://announcement/announcement-1"),
        )
        assertEquals(
            CampzoneDeepLink.Announcement("announcement-2"),
            CampzoneDeepLink.fromCampzoneUrl(
                "campzone://announcements?announcementID=announcement-2",
            ),
        )
    }

    @Test
    fun parsesCampzoneUniversalLinks() {
        assertEquals(
            CampzoneDeepLink.Announcement("arrival"),
            CampzoneDeepLink.fromCampzoneUrl("https://campzone-web.vercel.app/announcement/arrival"),
        )
        assertEquals(
            CampzoneDeepLink.CampingChat("summer-camp-2026"),
            CampzoneDeepLink.fromCampzoneUrl("https://campzone-web.vercel.app/chat/summer-camp-2026"),
        )
        assertEquals(
            CampzoneDeepLink.TeamChat("summer-camp-2026", "blue-team"),
            CampzoneDeepLink.fromCampzoneUrl(
                "https://campzone-web.vercel.app/team-chat/blue-team?c=summer-camp-2026",
            ),
        )
        assertEquals(
            CampzoneDeepLink.TeamPoints("summer-camp-2026", "blue-team"),
            CampzoneDeepLink.fromCampzoneUrl(
                "https://campzone-web.vercel.app/points/summer-camp-2026?teamID=blue-team",
            ),
        )
        assertEquals(
            CampzoneDeepLink.Poll("summer-camp-2026", "arrival"),
            CampzoneDeepLink.fromCampzoneUrl(
                "https://campzone-web.vercel.app/polls/summer-camp-2026?pollID=arrival",
            ),
        )
        assertEquals(
            CampzoneDeepLink.RegistrationReview("summer-camp-2026"),
            CampzoneDeepLink.fromCampzoneUrl(
                "https://campzone-web.vercel.app/registration-review/summer-camp-2026",
            ),
        )
    }

    @Test
    fun rejectsUnsupportedUrls() {
        assertNull(CampzoneDeepLink.fromCampzoneUrl("https://example.com/campings/camp-1"))
        assertNull(CampzoneDeepLink.fromCampzoneUrl("campzone://unknown/camp-1"))
        assertNull(CampzoneDeepLink.fromCampzoneUrl("campzone://camping"))
    }

    @Test
    fun resolvesFcmPayloadTypesCaseInsensitively() {
        assertEquals(
            CampzoneDeepLink.Announcement("a1"),
            CampzoneDeepLink.fromPayload(
                mapOf("TYPE" to "Announcement", "announcementID" to "a1"),
            ),
        )
        assertEquals(
            CampzoneDeepLink.TeamChat("c1", "t1"),
            CampzoneDeepLink.fromPayload(
                mapOf("kind" to "chat_message", "campingID" to "c1", "teamID" to "t1"),
            ),
        )
        assertEquals(
            CampzoneDeepLink.Poll("c1", "p1"),
            CampzoneDeepLink.fromPayload(
                mapOf("type" to "poll", "campingID" to "c1", "pollID" to "p1"),
            ),
        )
        assertEquals(
            CampzoneDeepLink.RegistrationReview("c1"),
            CampzoneDeepLink.fromPayload(
                mapOf("type" to "registration_request", "campingID" to "c1"),
            ),
        )
        assertEquals(
            CampzoneDeepLink.TeamPoints("c1", "t1"),
            CampzoneDeepLink.fromPayload(
                mapOf(
                    "type" to "team_update",
                    "campingID" to "c1",
                    "teamID" to "t1",
                    "event" to "scoreChanged",
                ),
            ),
        )
        assertEquals(
            CampzoneDeepLink.TeamUpdate("c1", "t1"),
            CampzoneDeepLink.fromPayload(
                mapOf("type" to "team_update", "campingID" to "c1", "teamID" to "t1"),
            ),
        )
    }

    @Test
    fun infersDestinationWhenPayloadTypeIsMissing() {
        assertEquals(
            CampzoneDeepLink.Announcement("a1"),
            CampzoneDeepLink.fromPayload(mapOf("announcementID" to "a1")),
        )
        assertEquals(
            CampzoneDeepLink.CampingChat("c1"),
            CampzoneDeepLink.fromPayload(mapOf("messageID" to "m1", "campingID" to "c1")),
        )
        assertEquals(
            CampzoneDeepLink.Poll("c1", "p1"),
            CampzoneDeepLink.fromPayload(mapOf("pollID" to "p1", "campingID" to "c1")),
        )
        assertEquals(
            CampzoneDeepLink.Camping("c1"),
            CampzoneDeepLink.fromPayload(mapOf("campingID" to "c1")),
        )
    }

    @Test
    fun explicitPayloadDeepLinksOverrideInferredDestination() {
        assertEquals(
            CampzoneDeepLink.Camping("summer-camp-2026"),
            CampzoneDeepLink.fromPayload(
                mapOf(
                    "type" to "announcement",
                    "announcementID" to "arrival",
                    "deepLink" to "campzone://camping/summer-camp-2026",
                ),
            ),
        )
        assertEquals(
            CampzoneDeepLink.Poll("summer-camp-2026", "arrival-poll"),
            CampzoneDeepLink.fromPayload(
                mapOf(
                    "type" to "announcement",
                    "announcementID" to "arrival",
                    "deepLink" to
                        "https://campzone-web.vercel.app/polls/summer-camp-2026?pollID=arrival-poll",
                ),
            ),
        )
    }

    @Test
    fun canonicalShareUrlsOnlyExistForShareableDestinations() {
        assertEquals(
            "https://campzone-web.vercel.app/campings/camp-1",
            CampzoneDeepLink.Camping("camp-1").canonicalShareUrlOrNull(),
        )
        assertEquals(
            "https://campzone-web.vercel.app/announcements/a-1",
            CampzoneDeepLink.Announcement("a-1").canonicalShareUrlOrNull(),
        )
        assertNull(CampzoneDeepLink.CampingChat("camp-1").canonicalShareUrlOrNull())
        assertNull(CampzoneDeepLink.Poll("camp-1", "poll-1").canonicalShareUrlOrNull())
        assertNull(CampzoneDeepLink.RegistrationReview("camp-1").canonicalShareUrlOrNull())
        assertNull(CampzoneDeepLink.TeamChat("camp-1", "team-1").canonicalShareUrlOrNull())
        assertNull(CampzoneDeepLink.TeamUpdate("camp-1", "team-1").canonicalShareUrlOrNull())
        assertNull(CampzoneDeepLink.TeamPoints("camp-1", "team-1").canonicalShareUrlOrNull())
    }
}
