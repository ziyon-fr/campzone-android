package fr.ziyon.campzone.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Locks every Firestore enum raw string against `02-firestore-schema.md` §8.
 * Raw values are case-sensitive - a drift here is exactly the kind of bug that
 * silently corrupts cross-platform data, so these are asserted verbatim.
 */
class EnumWireValueTest {

    @Test
    fun campingAndRegistrationStatusRaws() {
        assertEquals(listOf("open", "closed", "cancelled"), CampingRegistrationStatus.entries.map { it.wireValue })
        assertEquals(
            listOf("pending", "approved", "rejected", "waitlisted"),
            RegistrationApprovalStatus.entries.map { it.wireValue },
        )
        assertEquals(listOf("church", "regional", "international", "custom"), OrganizerType.entries.map { it.wireValue })
    }

    @Test
    fun transportationRawsIncludeSnakeAndCamelCase() {
        assertEquals(listOf("self", "child"), RegistrationParticipantKind.entries.map { it.wireValue })
        assertEquals(listOf("own_car", "provided_bus"), TransportationChoice.entries.map { it.wireValue })
        assertEquals(listOf("unpaid", "paid", "waived"), TransportationPaymentStatus.entries.map { it.wireValue })
        assertEquals(listOf("not_boarded", "boarded"), TransportationBoardingStatus.entries.map { it.wireValue })
        // camelCase modes
        assertEquals("ownCar", TransportationMode.OwnCar.wireValue)
        assertEquals("onFoot", TransportationMode.OnFoot.wireValue)
        assertEquals(
            listOf("cardOneTime", "cardInstallments", "bankTransfer"),
            CampingPaymentOption.entries.map { it.wireValue },
        )
    }

    @Test
    fun scheduleFoodAndProgramRaws() {
        assertEquals(
            listOf(
                "reception", "games", "preaching", "prayer", "breakfast", "lunch",
                "dinner", "snack", "other", "rest", "break", "custom",
            ),
            ProgramType.entries.map { it.wireValue },
        )
        assertEquals(listOf("breakfast", "lunch", "dinner", "snack"), FoodMealKind.entries.map { it.wireValue })
        assertEquals(
            listOf("none", "atStart", "fiveMinutes", "fifteenMinutes", "thirtyMinutes", "oneHour"),
            ScheduleReminderTiming.entries.map { it.wireValue },
        )
    }

    @Test
    fun teamGameRaws() {
        assertEquals(listOf("member", "captain", "viceCaptain"), TeamMemberRole.entries.map { it.wireValue })
        assertEquals(listOf("team", "user", "any"), PointRuleTarget.entries.map { it.wireValue })
        assertEquals(listOf("immediate", "afterReveal"), PointRuleVisibility.entries.map { it.wireValue })
    }

    @Test
    fun moderationTargetIsCamelCaseChatMessage() {
        assertEquals("chatMessage", ContentReportTarget.ChatMessage.wireValue)
        assertEquals(
            listOf("inappropriate", "spam", "misinformation", "harassment", "other"),
            ContentReportReason.entries.map { it.wireValue },
        )
        assertEquals(listOf("pending", "dismissed", "resolved"), ContentReportStatus.entries.map { it.wireValue })
    }

    @Test
    fun notificationKindAcceptsSnakeAndLegacySpellings() {
        assertEquals("badge", AppNotificationKind.Badge.wireValue)
        assertEquals(AppNotificationKind.Badge, AppNotificationKind.fromWire("achievement_badge"))
        assertEquals("chat_message", AppNotificationKind.ChatMessage.wireValue)
        assertEquals(AppNotificationKind.ChatMessage, AppNotificationKind.fromWire("chatmessage"))
        assertEquals("checklist", AppNotificationKind.Checklist.wireValue)
        assertEquals(AppNotificationKind.Checklist, AppNotificationKind.fromWire("packing_share"))
        assertEquals(AppNotificationKind.ScheduleReminder, AppNotificationKind.fromWire("schedulereminder"))
        assertNull(AppNotificationKind.fromWire("bogus"))
    }

    @Test
    fun operationsAndMediaRaws() {
        assertEquals(listOf("qr", "manual"), CheckInMethod.entries.map { it.wireValue })
        assertEquals(listOf("tent", "cabin", "room", "dorm"), LodgingKind.entries.map { it.wireValue })
        assertEquals(listOf("any", "male", "female", "family"), LodgingGenderPolicy.entries.map { it.wireValue })
        assertEquals(
            listOf("tent", "stage", "dining", "firstAid", "restroom", "parking", "water", "program", "info", "other", "custom"),
            VenueCategory.entries.map { it.wireValue },
        )
        assertEquals(listOf("photo", "video"), MediaKind.entries.map { it.wireValue })
    }

    @Test
    fun songRaws() {
        assertEquals(
            listOf("intro", "verse", "preChorus", "chorus", "bridge", "instrumental", "outro", "custom"),
            SongLyricsPartKind.entries.map { it.wireValue },
        )
        assertEquals(listOf("mp3", "m4a", "wav", "aac", "other"), SongAudioKind.entries.map { it.wireValue })
    }
}
