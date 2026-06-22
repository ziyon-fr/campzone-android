package fr.ziyon.campzone.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import fr.ziyon.campzone.data.auth.UserGender

/**
 * Firestore enum raw values from `02-firestore-schema.md` §8. Raw strings are
 * **case-sensitive** and copied verbatim - a single drift (e.g. `providedBus`
 * vs `provided_bus`) silently corrupts cross-platform data or trips a Security
 * Rule. Reused from elsewhere: [fr.ziyon.campzone.core.permissions.UserRole],
 * [fr.ziyon.campzone.data.auth.UserGender],
 * [fr.ziyon.campzone.data.auth.CampingAgeGroup],
 * [fr.ziyon.campzone.data.family.FamilyRelationship].
 */

/** camping `registrationStatus`. */
enum class CampingRegistrationStatus(val wireValue: String) {
    Open("open"),
    Closed("closed"),
    Cancelled("cancelled");

    companion object {
        fun fromWire(value: String?): CampingRegistrationStatus =
            entries.firstOrNull { it.wireValue == value } ?: Open
    }
}

/** attendee `registrationStatus`. */
enum class RegistrationApprovalStatus(val wireValue: String) {
    Pending("pending"),
    Approved("approved"),
    Rejected("rejected"),
    Waitlisted("waitlisted");

    companion object {
        fun fromWire(value: String?): RegistrationApprovalStatus =
            entries.firstOrNull { it.wireValue == value } ?: Pending
    }
}

/** `organizerLevel.type`. */
enum class OrganizerType(val wireValue: String) {
    Church("church"),
    Regional("regional"),
    International("international"),
    Custom("custom");

    companion object {
        fun fromWire(value: String?): OrganizerType =
            entries.firstOrNull { it.wireValue == value } ?: Custom
    }
}

/** `participantKind`. Note `self` is the explicit raw for `selfParticipant`. */
enum class RegistrationParticipantKind(val wireValue: String) {
    SelfParticipant("self"),
    Child("child");

    companion object {
        fun fromWire(value: String?): RegistrationParticipantKind =
            entries.firstOrNull { it.wireValue == value } ?: SelfParticipant
    }
}

/** `transportationChoice`. */
enum class TransportationChoice(val wireValue: String) {
    OwnCar("own_car"),
    ProvidedBus("provided_bus");

    companion object {
        fun fromWire(value: String?): TransportationChoice =
            entries.firstOrNull { it.wireValue == value } ?: OwnCar
    }
}

/** transportation booking `paymentStatus`. */
enum class TransportationPaymentStatus(val wireValue: String) {
    Unpaid("unpaid"),
    Paid("paid"),
    Waived("waived");

    /** A ticket may board only once the fare is settled (paid or waived). */
    val allowsBoarding: Boolean
        get() = this == Paid || this == Waived

    companion object {
        fun fromWire(value: String?): TransportationPaymentStatus =
            entries.firstOrNull { it.wireValue == value } ?: Unpaid
    }
}

/** transportation booking `boardingStatus`. */
enum class TransportationBoardingStatus(val wireValue: String) {
    NotBoarded("not_boarded"),
    Boarded("boarded");

    companion object {
        fun fromWire(value: String?): TransportationBoardingStatus =
            entries.firstOrNull { it.wireValue == value } ?: NotBoarded
    }
}

/**
 * Which half of a round-trip a scan event belongs to. A round-trip ticket has
 * two legs (outbound = origin → camp, return = camp → home); a one-way ticket
 * only uses [Outbound]. Wire raws are **lowercase** (`outbound`/`return`).
 */
enum class TransportationLeg(val wireValue: String) {
    Outbound("outbound"),
    Return("return");

    companion object {
        fun fromWire(value: String?): TransportationLeg =
            entries.firstOrNull { it.wireValue == value } ?: Outbound
    }
}

/** Where on a leg a scan happened: boarding ([Departure]) or arrival. */
enum class TransportationCheckpoint(val wireValue: String) {
    Departure("departure"),
    Arrival("arrival");

    companion object {
        fun fromWire(value: String?): TransportationCheckpoint =
            entries.firstOrNull { it.wireValue == value } ?: Departure
    }
}

/** Where a leg currently stands. Derived on read - never stored. */
enum class TransportationLegProgress {
    NotStarted,
    InTransit,
    Arrived,
}

/** `transportationOptions[].mode` - **camelCase** raws. */
enum class TransportationMode(val wireValue: String) {
    Bus("bus"),
    Coach("coach"),
    Minibus("minibus"),
    Shuttle("shuttle"),
    Train("train"),
    Carpool("carpool"),
    OwnCar("ownCar"),
    Plane("plane"),
    Boat("boat"),
    Bike("bike"),
    OnFoot("onFoot"),
    Other("other");

    val displayName: String
        get() = when (this) {
            Bus -> "Bus"
            Coach -> "Coach"
            Minibus -> "Minibus"
            Shuttle -> "Shuttle"
            Train -> "Train"
            Carpool -> "Car-pool"
            OwnCar -> "Own car"
            Plane -> "Flight"
            Boat -> "Boat / ferry"
            Bike -> "Bicycle"
            OnFoot -> "On foot"
            Other -> "Other"
        }

    val defaultRequiresTicket: Boolean
        get() = when (this) {
            OwnCar,
            Carpool,
            Bike,
            OnFoot,
            Other,
            -> false

            Bus,
            Coach,
            Minibus,
            Shuttle,
            Train,
            Plane,
            Boat,
            -> true
        }

    companion object {
        fun fromWire(value: String?): TransportationMode =
            entries.firstOrNull { it.wireValue == value } ?: Other
    }
}

/** `priceItems[].paymentOptions[]` - **camelCase** raws. */
enum class CampingPaymentOption(val wireValue: String) {
    CardOneTime("cardOneTime"),
    CardInstallments("cardInstallments"),
    BankTransfer("bankTransfer");

    companion object {
        fun fromWire(value: String?): CampingPaymentOption? =
            entries.firstOrNull { it.wireValue == value }
    }
}

/** program `type`. */
enum class ProgramType(val wireValue: String) {
    Reception("reception"),
    Games("games"),
    Preaching("preaching"),
    Prayer("prayer"),
    Breakfast("breakfast"),
    Lunch("lunch"),
    Dinner("dinner"),
    Snack("snack"),
    Other("other"),
    Rest("rest"),
    Break("break"),
    Custom("custom");

    companion object {
        fun fromWire(value: String?): ProgramType =
            entries.firstOrNull { it.wireValue == value } ?: Other
    }
}

/** foodMenu `meal` + id component. */
enum class FoodMealKind(val wireValue: String) {
    Breakfast("breakfast"),
    Lunch("lunch"),
    Dinner("dinner"),
    Snack("snack");

    companion object {
        fun fromWire(value: String?): FoodMealKind? =
            entries.firstOrNull { it.wireValue == value }
    }
}

/** schedule config `reminderTiming`. */
enum class ScheduleReminderTiming(val wireValue: String) {
    None("none"),
    AtStart("atStart"),
    FiveMinutes("fiveMinutes"),
    FifteenMinutes("fifteenMinutes"),
    ThirtyMinutes("thirtyMinutes"),
    OneHour("oneHour");

    companion object {
        fun fromWire(value: String?): ScheduleReminderTiming =
            entries.firstOrNull { it.wireValue == value } ?: None
    }
}

/** `members[].role`. */
enum class TeamMemberRole(val wireValue: String) {
    Member("member"),
    Captain("captain"),
    ViceCaptain("viceCaptain");

    companion object {
        fun fromWire(value: String?): TeamMemberRole =
            entries.firstOrNull { it.wireValue == value } ?: Member
    }
}

/** `pointRules[].appliesTo`. */
enum class PointRuleTarget(val wireValue: String) {
    Team("team"),
    User("user"),
    Any("any");

    companion object {
        fun fromWire(value: String?): PointRuleTarget =
            entries.firstOrNull { it.wireValue == value } ?: Any
    }
}

/** `pointRules[].visibility` and activity `visibility`. RBAC literal-checks `immediate`. */
enum class PointRuleVisibility(val wireValue: String) {
    Immediate("immediate"),
    AfterReveal("afterReveal");

    companion object {
        fun fromWire(value: String?): PointRuleVisibility =
            entries.firstOrNull { it.wireValue == value } ?: Immediate
    }
}

/** announcement attachment `kind`. */
enum class AnnouncementAttachmentKind(val wireValue: String) {
    Image("image"),
    Pdf("pdf");

    companion object {
        fun fromWire(value: String?): AnnouncementAttachmentKind =
            entries.firstOrNull { it.wireValue == value } ?: Image
    }
}

/** contentReport `target` - **camelCase** `chatMessage` (unlike notification `chat_message`). */
enum class ContentReportTarget(val wireValue: String) {
    Announcement("announcement"),
    Camping("camping"),
    ChatMessage("chatMessage");

    companion object {
        /** Null on unknown - `contentReports` is a brittle read (drop whole list). */
        fun fromWire(value: String?): ContentReportTarget? =
            entries.firstOrNull { it.wireValue == value }
    }
}

/** contentReport `reason`. */
enum class ContentReportReason(val wireValue: String) {
    Inappropriate("inappropriate"),
    Spam("spam"),
    Misinformation("misinformation"),
    Harassment("harassment"),
    Other("other");

    companion object {
        fun fromWire(value: String?): ContentReportReason? =
            entries.firstOrNull { it.wireValue == value }
    }
}

/** contentReport `status`. */
enum class ContentReportStatus(val wireValue: String) {
    Pending("pending"),
    Dismissed("dismissed"),
    Resolved("resolved");

    companion object {
        fun fromWire(value: String?): ContentReportStatus? =
            entries.firstOrNull { it.wireValue == value }
    }
}

/** `ziyon_notifications` `kind`/`type` (tolerant; accepts legacy spellings). */
enum class AppNotificationKind(val wireValue: String) {
    Announcement("announcement"),
    Badge("badge"),
    ChatMessage("chat_message"),
    ChatMention("chat_mention"),
    Poll("poll"),
    Registration("registration"),
    ScheduleReminder("schedule_reminder"),
    TeamUpdate("team_update"),
    Transportation("transportation"),
    Unknown("unknown");

    companion object {
        fun fromWire(value: String?): AppNotificationKind? =
            when (value?.trim()?.lowercase()) {
                "announcement" -> Announcement
                "badge", "achievement", "achievement_badge" -> Badge
                "chat_message", "chatmessage" -> ChatMessage
                "chat_mention", "chatmention" -> ChatMention
                "poll" -> Poll
                "registration", "registration_request" -> Registration
                "schedule_reminder", "schedulereminder" -> ScheduleReminder
                "team_update", "teamupdate" -> TeamUpdate
                "transportation", "transportation_invitation", "transportation_request" -> Transportation
                "unknown" -> Unknown
                else -> null
            }
    }
}

/** checkIn `method`. */
enum class CheckInMethod(val wireValue: String) {
    Qr("qr"),
    Manual("manual");

    companion object {
        fun fromWire(value: String?): CheckInMethod? =
            entries.firstOrNull { it.wireValue == value }
    }
}

/** lodging `kind`. */
enum class LodgingKind(val wireValue: String) {
    Tent("tent"),
    Cabin("cabin"),
    Room("room"),
    Dorm("dorm");

    companion object {
        fun fromWire(value: String?): LodgingKind =
            entries.firstOrNull { it.wireValue == value } ?: Tent
    }
}

/** lodging `genderPolicy`. */
enum class LodgingGenderPolicy(val wireValue: String) {
    Any("any"),
    Male("male"),
    Female("female"),
    Family("family");

    /**
     * Whether an attendee of [gender] is eligible for this policy. `family`
     * units are grouped by family rather than raw gender, so they accept anyone.
     */
    fun accepts(gender: UserGender?): Boolean = when (this) {
        Any, Family -> true
        Male -> gender == UserGender.Male
        Female -> gender == UserGender.Female
    }

    companion object {
        fun fromWire(value: String?): LodgingGenderPolicy =
            entries.firstOrNull { it.wireValue == value } ?: Any
    }
}

/** venueMap `points[].category`. */
enum class VenueCategory(val wireValue: String) {
    Tent("tent"),
    Stage("stage"),
    Dining("dining"),
    FirstAid("firstAid"),
    Restroom("restroom"),
    Parking("parking"),
    Water("water"),
    Program("program"),
    Info("info"),
    Other("other"),
    Custom("custom");

    companion object {
        fun fromWire(value: String?): VenueCategory =
            entries.firstOrNull { it.wireValue == value } ?: Other
    }
}

/** media `kind`. */
enum class MediaKind(val wireValue: String) {
    Photo("photo"),
    Video("video");

    companion object {
        fun fromWire(value: String?): MediaKind? =
            entries.firstOrNull { it.wireValue == value }
    }
}

/** song `lyricsParts[].kind`. */
enum class SongLyricsPartKind(val wireValue: String) {
    Intro("intro"),
    Verse("verse"),
    PreChorus("preChorus"),
    Chorus("chorus"),
    Bridge("bridge"),
    Instrumental("instrumental"),
    Outro("outro"),
    Custom("custom");

    companion object {
        fun fromWire(value: String?): SongLyricsPartKind =
            entries.firstOrNull { it.wireValue == value } ?: Verse
    }
}

/** song audio `kind`. */
enum class SongAudioKind(val wireValue: String) {
    Mp3("mp3"),
    M4a("m4a"),
    Wav("wav"),
    Aac("aac"),
    Other("other");

    companion object {
        fun fromWire(value: String?): SongAudioKind =
            entries.firstOrNull { it.wireValue == value } ?: Other
    }
}

/** backend payment `kind`. */
enum class PaymentKind(val wireValue: String) {
    Registration("registration"),
    Transportation("transportation"),
    PriceItem("priceItem");

    companion object {
        fun fromWire(value: String?): PaymentKind? =
            entries.firstOrNull { it.wireValue == value }
    }
}

/** Achievement catalog `rarity` (in-code, never persisted as a doc field). */
enum class AchievementRarity(
    val wireValue: String,
    val materialName: String,
    val glowColor: Color,
    val medalBrush: Brush
) {

    Common(
        wireValue = "common",
        materialName = "Silver",
        glowColor = Color(0xFFBFC4D1),
        medalBrush = Brush.linearGradient(
            listOf(
                Color(0xFFE2E5EA),
                Color(0xFF8E96A3),
                Color(0xFFF8F9FA)
            )
        )
    ),

    Uncommon(
        wireValue = "uncommon",
        materialName = "Gold",
        glowColor = Color(0xFFFFC733),
        medalBrush = Brush.linearGradient(
            listOf(
                Color(0xFFFFE49A),
                Color(0xFFFFB300),
                Color(0xFFFFF1B8)
            )
        )
    ),

    Rare(
        wireValue = "rare",
        materialName = "Platinum",
        glowColor = Color(0xFFCCDFFF),
        medalBrush = Brush.linearGradient(
            listOf(
                Color(0xFFEAF2FF),
                Color(0xFF9FB7D9),
                Color(0xFFFFFFFF)
            )
        )
    ),

    Epic(
        wireValue = "epic",
        materialName = "Diamond",
        glowColor = Color(0xFF73D9FF),
        medalBrush = Brush.linearGradient(
            listOf(
                Color(0xFFE6FBFF),
                Color(0xFF4DD8FF),
                Color(0xFFFFFFFF)
            )
        )
    ),

    Legendary(
        wireValue = "legendary",
        materialName = "Painite",
        glowColor = Color(0xFFD91926),
        medalBrush = Brush.linearGradient(
            listOf(
                Color(0xFFFF6A6A),
                Color(0xFF9B000D),
                Color(0xFFFFB1B1)
            )
        )
    );

    companion object {
        fun fromWire(value: String?): AchievementRarity =
            entries.firstOrNull { it.wireValue == value } ?: Common
    }
}
/** Achievement catalog `awardKind` (in-code). */
enum class AchievementAwardKind(val wireValue: String) {
    Manual("manual"),
    Automatic("automatic");

    companion object {
        fun fromWire(value: String?): AchievementAwardKind =
            entries.firstOrNull { it.wireValue == value } ?: Manual
    }
}

/** notificationSettings `authorizationState`. */
enum class NotificationAuthorizationState(val wireValue: String) {
    NotDetermined("notDetermined"),
    Denied("denied"),
    Authorized("authorized"),
    Provisional("provisional"),
    Ephemeral("ephemeral"),
    Unknown("unknown");

    companion object {
        fun fromWire(value: String?): NotificationAuthorizationState =
            entries.firstOrNull { it.wireValue == value } ?: NotDetermined
    }
}
