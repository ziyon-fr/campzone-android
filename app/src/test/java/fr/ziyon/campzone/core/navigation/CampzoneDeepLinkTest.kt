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
            CampzoneDeepLink.ScheduleProgram("summer-camp-2026", "evening-worship"),
            CampzoneDeepLink.fromCampzoneUrl(
                "https://campzone-web.vercel.app/program/evening-worship?campingID=summer-camp-2026",
            ),
        )
        assertEquals(
            CampzoneDeepLink.RegistrationReview("summer-camp-2026"),
            CampzoneDeepLink.fromCampzoneUrl(
                "https://campzone-web.vercel.app/registration-review/summer-camp-2026",
            ),
        )
        assertEquals(
            CampzoneDeepLink.Achievements(
                userId = "user-1",
                displayName = "Lea",
                photoUrl = null,
                campingId = "camp-2026",
            ),
            CampzoneDeepLink.fromCampzoneUrl(
                "https://campzone-web.vercel.app/badges/user-1?displayName=Lea&campingID=camp-2026",
            ),
        )
        assertEquals(
            CampzoneDeepLink.Achievement("user-1", null, null, null, "badge-1"),
            CampzoneDeepLink.fromCampzoneUrl(
                "https://campzone-web.vercel.app/badges/user-1?achievementID=badge-1",
            ),
        )
        assertEquals(
            CampzoneDeepLink.TransportationJoin("camp-1", "ABC123"),
            CampzoneDeepLink.fromCampzoneUrl(
                "https://campzone-web.vercel.app/transportation-join/camp-1?code=ABC123",
            ),
        )
        assertEquals(
            CampzoneDeepLink.TransportationJoin("camp-1", "ABC123"),
            CampzoneDeepLink.fromCampzoneUrl(
                "https://campzone-web.vercel.app/transportation-join/camp-1?code=ABC123.",
            ),
        )
        assertEquals(
            CampzoneDeepLink.TransportationInvitation("camp-1", "car-1", "reg-1"),
            CampzoneDeepLink.fromCampzoneUrl(
                "campzone://transportation-invitation/car-1?campingID=camp-1&registrationID=reg-1",
            ),
        )
        assertEquals(
            CampzoneDeepLink.Achievements(
                userId = "user-2",
                displayName = "Noah",
                photoUrl = null,
                campingId = null,
            ),
            CampzoneDeepLink.fromCampzoneUrl("campzone://achievements/user-2?displayName=Noah"),
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
            CampzoneDeepLink.ScheduleProgram("c1", "p1"),
            CampzoneDeepLink.fromPayload(
                mapOf("type" to "schedule_reminder", "campingID" to "c1", "programID" to "p1"),
            ),
        )
        assertEquals(
            CampzoneDeepLink.Camping("c1"),
            CampzoneDeepLink.fromPayload(
                mapOf("type" to "registration", "event" to "approved", "campingID" to "c1"),
            ),
        )
        assertEquals(
            CampzoneDeepLink.Achievements(
                userId = "u1",
                displayName = "Lea",
                photoUrl = "https://example.com/lea.png",
                campingId = "c1",
            ),
            CampzoneDeepLink.fromPayload(
                mapOf(
                    "type" to "badge",
                    "recipientUserID" to "u1",
                    "recipientDisplayName" to "Lea",
                    "recipientPhotoURLString" to "https://example.com/lea.png",
                    "campingID" to "c1",
                ),
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
        assertEquals(
            CampzoneDeepLink.PackingShare("c1", "share-1", "child-emma"),
            CampzoneDeepLink.fromPayload(
                mapOf(
                    "type" to "checklist",
                    "campingID" to "c1",
                    "shareID" to "share-1",
                    "actionSubjectRegistrationID" to "child-emma",
                ),
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
            CampzoneDeepLink.ScheduleProgram("c1", "program-1"),
            CampzoneDeepLink.fromPayload(mapOf("programID" to "program-1", "campingID" to "c1")),
        )
        assertEquals(
            CampzoneDeepLink.PackingShare("c1", "share-1", "child-emma"),
            CampzoneDeepLink.fromPayload(
                mapOf(
                    "campingID" to "c1",
                    "packingShareID" to "share-1",
                    "actionSubjectRegistrationID" to "child-emma",
                ),
            ),
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
        assertNull(CampzoneDeepLink.ScheduleProgram("camp-1", "program-1").canonicalShareUrlOrNull())
        assertEquals(
            "https://campzone-web.vercel.app/badges/u1",
            CampzoneDeepLink.Achievements(
                userId = "u1",
                displayName = null,
                photoUrl = null,
                campingId = null,
            ).canonicalShareUrlOrNull(),
        )
        assertEquals(
            "https://campzone-web.vercel.app/transportation-join/camp-1?code=ABC123",
            CampzoneDeepLink.TransportationJoin("camp-1", "ABC123").canonicalShareUrlOrNull(),
        )
        assertNull(CampzoneDeepLink.RegistrationReview("camp-1").canonicalShareUrlOrNull())
        assertNull(CampzoneDeepLink.TeamChat("camp-1", "team-1").canonicalShareUrlOrNull())
        assertNull(CampzoneDeepLink.TeamUpdate("camp-1", "team-1").canonicalShareUrlOrNull())
        assertNull(CampzoneDeepLink.TeamPoints("camp-1", "team-1").canonicalShareUrlOrNull())
    }

    @Test
    fun packingShareLinksRoundTrip() {
        val link = CampzoneDeepLink.PackingShare("camp-1", "share-1")
        assertEquals(
            "https://campzone-web.vercel.app/packing-share/share-1?c=camp-1",
            link.canonicalShareUrlOrNull(),
        )
        assertEquals(link, CampzoneDeepLink.fromCampzoneUrl(link.canonicalShareUrlOrNull()))
        assertEquals(
            link,
            CampzoneDeepLink.fromCampzoneUrl("campzone://packing-share/share-1?c=camp-1"),
        )

        val familyLink = CampzoneDeepLink.PackingShare("camp-1", "share-1", "child-emma")
        assertEquals(
            "https://campzone-web.vercel.app/packing-share/share-1?c=camp-1&registrationID=child-emma",
            familyLink.canonicalShareUrlOrNull(),
        )
        assertEquals(familyLink, CampzoneDeepLink.fromCampzoneUrl(familyLink.canonicalShareUrlOrNull()))
        assertEquals(
            familyLink,
            CampzoneDeepLink.fromCampzoneUrl(
                "campzone://packing-share/share-1?c=camp-1&registrationID=child-emma",
            ),
        )
    }
}
