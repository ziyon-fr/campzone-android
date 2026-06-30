package fr.ziyon.campzone.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Cabin
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.vector.ImageVector
import java.net.URLEncoder

sealed interface AppRoute {
    val route: String

    sealed interface Tab : AppRoute {
        val iconLabel: ImageVector
    }

    data object Home : Tab {
        override val route = AppRoutePath.Home
        override val iconLabel = Icons.Outlined.Home
    }

    data object Campings : Tab {
        override val route = AppRoutePath.Campings
        override val iconLabel = Icons.Outlined.Cabin
    }

    data object Announcements : Tab {
        override val route = AppRoutePath.Announcements
        override val iconLabel = Icons.Outlined.Campaign
    }

    data object Profile : Tab {
        override val route = AppRoutePath.Profile
        override val iconLabel = Icons.Outlined.AccountCircle
    }

    data object ProfileEdit : AppRoute {
        override val route = "${AppRoutePath.Profile}/${AppRoutePath.ProfileEdit}"
    }

    data object ProfileAchievements : AppRoute {
        override val route = "${AppRoutePath.Profile}/${AppRoutePath.Achievements}"
    }

    data class ProfileAchievementsFor(val userId: String) : AppRoute {
        override val route = "${ProfileAchievements.route}/${userId.asRouteSegment()}"
    }

    data class ProfileAchievementDetail(val userId: String, val achievementId: String) : AppRoute {
        override val route = "${ProfileAchievementsFor(userId).route}/${achievementId.asRouteSegment()}"
    }

    data object NotificationSettings : AppRoute {
        override val route = "${AppRoutePath.Profile}/${AppRoutePath.NotificationSettings}"
    }

    data object NotificationCampingChannels : AppRoute {
        override val route = "${NotificationSettings.route}/${AppRoutePath.ChannelCampings}"
    }

    data object NotificationTeamChannels : AppRoute {
        override val route = "${NotificationSettings.route}/${AppRoutePath.ChannelTeams}"
    }

    data object NotificationFeed : AppRoute {
        override val route = "${AppRoutePath.Home}/${AppRoutePath.NotificationFeed}"
    }

    data object FamilyParticipants : AppRoute {
        override val route = "${AppRoutePath.Profile}/${AppRoutePath.FamilyParticipants}"
    }

    data object MyVehicles : AppRoute {
        override val route = "${AppRoutePath.Profile}/${AppRoutePath.MyVehicles}"
    }

    data class UserVehicleEditor(val vehicleId: String? = null) : AppRoute {
        override val route = buildString {
            append(MyVehicles.route)
            append("/")
            append(AppRoutePath.UserVehicleEditor)
            val resolved = vehicleId?.takeUnless { it.isBlank() }
            if (resolved != null) {
                append("/")
                append(resolved.asRouteSegment())
            }
        }
    }

    data object AdminTools : AppRoute {
        override val route = "${AppRoutePath.Profile}/${AppRoutePath.AdminTools}"
    }

    data object ModerationQueue : AppRoute {
        override val route = "${AppRoutePath.Profile}/${AppRoutePath.AdminTools}/${AppRoutePath.ModerationQueue}"
    }

    data object RoleManagement : AppRoute {
        override val route = "${AppRoutePath.Profile}/${AppRoutePath.AdminTools}/${AppRoutePath.RoleManagement}"
    }

    data object AdminOnboarding : AppRoute {
        override val route = "${AppRoutePath.Profile}/${AppRoutePath.AdminTools}/${AppRoutePath.AdminOnboarding}"
    }

    data object AdminAnalytics : AppRoute {
        override val route = "${AppRoutePath.Profile}/${AppRoutePath.AdminTools}/${AppRoutePath.AdminAnalytics}"
    }

    data object UserDataExport : AppRoute {
        override val route = "${AppRoutePath.Profile}/${AppRoutePath.UserDataExport}"
    }

    data object AppSupport : AppRoute {
        override val route = "${AppRoutePath.Profile}/${AppRoutePath.AppSupport}"
    }

    data class CampingDetail(val campingId: String) : AppRoute {
        override val route = "${AppRoutePath.Campings}/${campingId.asRouteSegment()}"
    }

    data class AnnouncementDetail(val announcementId: String) : AppRoute {
        override val route = "${AppRoutePath.Announcements}/${announcementId.asRouteSegment()}"
    }

    data class CampingChat(val campingId: String) : AppRoute {
        override val route = "${CampingDetail(campingId).route}/${AppRoutePath.Chat}"
    }

    data class CampingTeams(val campingId: String) : AppRoute {
        override val route = "${CampingDetail(campingId).route}/${AppRoutePath.Teams}"
    }

    data class TeamDetail(
        val campingId: String,
        val teamId: String,
    ) : AppRoute {
        override val route =
            "${CampingDetail(campingId).route}/${AppRoutePath.Teams}/${teamId.asRouteSegment()}"
    }

    data class TeamEditor(
        val campingId: String,
        val teamId: String? = null,
    ) : AppRoute {
        override val route = buildString {
            append(CampingTeams(campingId).route)
            append("/")
            append(AppRoutePath.TeamEditor)
            val resolved = teamId?.takeUnless { it.isBlank() }
            if (resolved != null) {
                append("/")
                append(resolved.asRouteSegment())
            }
        }
    }

    data class TeamChat(
        val campingId: String,
        val teamId: String,
    ) : AppRoute {
        override val route = "${TeamDetail(campingId, teamId).route}/${AppRoutePath.Chat}"
    }

    data class PointHistory(
        val campingId: String,
        val teamId: String? = null,
    ) : AppRoute {
        override val route = buildString {
            append(CampingDetail(campingId).route)
            append("/")
            append(AppRoutePath.PointHistory)
            val resolvedTeamId = teamId?.takeUnless { it.isBlank() }
            if (resolvedTeamId != null) {
                append("/")
                append(resolvedTeamId.asRouteSegment())
            }
        }
    }

    data class CampingPolls(val campingId: String) : AppRoute {
        override val route = "${CampingDetail(campingId).route}/${AppRoutePath.Polls}"
    }

    data class CampingAlbum(val campingId: String) : AppRoute {
        override val route = "${CampingDetail(campingId).route}/${AppRoutePath.Album}"
    }

    data class CampingSafety(val campingId: String) : AppRoute {
        override val route = "${CampingDetail(campingId).route}/${AppRoutePath.Safety}"
    }

    data class CheckInScanner(val campingId: String) : AppRoute {
        override val route = "${CampingDetail(campingId).route}/${AppRoutePath.CheckInScanner}"
    }

    data class CheckInRecords(val campingId: String) : AppRoute {
        override val route = "${CampingDetail(campingId).route}/${AppRoutePath.CheckInRecords}"
    }

    data class CheckInQrPasses(val campingId: String) : AppRoute {
        override val route = "${CampingDetail(campingId).route}/${AppRoutePath.CheckInQrPasses}"
    }

    data class TransportationTickets(val campingId: String) : AppRoute {
        override val route = "${CampingDetail(campingId).route}/${AppRoutePath.TransportationTickets}"
    }

    data class TransportationScanner(val campingId: String) : AppRoute {
        override val route = "${CampingDetail(campingId).route}/${AppRoutePath.TransportationScanner}"
    }

    data class TransportationDashboard(val campingId: String) : AppRoute {
        override val route = "${CampingDetail(campingId).route}/${AppRoutePath.TransportationDashboard}"
    }

    data class TransportationHistory(val campingId: String) : AppRoute {
        override val route = "${CampingDetail(campingId).route}/${AppRoutePath.TransportationHistory}"
    }

    data class MyTransportation(val campingId: String) : AppRoute {
        override val route = "${CampingDetail(campingId).route}/${AppRoutePath.MyTransportation}"
    }

    data class MyTransportationJoin(val campingId: String, val invitationCode: String) : AppRoute {
        override val route = "${MyTransportation(campingId).route}/join/${invitationCode.asRouteSegment()}"
    }

    data class TransportationDecision(
        val campingId: String,
        val kind: String,
        val vehicleId: String,
        val registrationId: String,
    ) : AppRoute {
        override val route = "${MyTransportation(campingId).route}/${kind.asRouteSegment()}/${vehicleId.asRouteSegment()}/${registrationId.asRouteSegment()}"
    }

    data class VehicleForm(
        val campingId: String,
        val vehicleId: String? = null,
    ) : AppRoute {
        override val route = buildString {
            append(CampingDetail(campingId).route)
            append("/")
            append(AppRoutePath.VehicleForm)
            val resolved = vehicleId?.takeUnless { it.isBlank() }
            if (resolved != null) {
                append("/")
                append(resolved.asRouteSegment())
            }
        }
    }

    data class VehicleQr(
        val campingId: String,
        val vehicleId: String,
    ) : AppRoute {
        override val route =
            "${CampingDetail(campingId).route}/${AppRoutePath.VehicleQr}/${vehicleId.asRouteSegment()}"
    }

    data class CampingVehicles(val campingId: String) : AppRoute {
        override val route = "${CampingDetail(campingId).route}/${AppRoutePath.Vehicles}"
    }

    data class VehicleScanner(val campingId: String) : AppRoute {
        override val route = "${CampingDetail(campingId).route}/${AppRoutePath.VehicleScanner}"
    }

    data class VehicleArrival(
        val campingId: String,
        val vehicleId: String,
    ) : AppRoute {
        override val route =
            "${CampingDetail(campingId).route}/${AppRoutePath.VehicleArrival}/${vehicleId.asRouteSegment()}"
    }

    data class CampingBadgeAward(val campingId: String) : AppRoute {
        override val route = "${CampingDetail(campingId).route}/${AppRoutePath.Achievements}/${AppRoutePath.Award}"
    }

    data class PollDetail(
        val campingId: String,
        val pollId: String,
    ) : AppRoute {
        override val route = "${CampingPolls(campingId).route}/${pollId.asRouteSegment()}"
    }

    data class PollEditor(
        val campingId: String,
        val pollId: String? = null,
    ) : AppRoute {
        override val route = buildString {
            append(CampingPolls(campingId).route)
            append("/")
            append(AppRoutePath.PollEditor)
            val resolved = pollId?.takeUnless { it.isBlank() }
            if (resolved != null) {
                append("/")
                append(resolved.asRouteSegment())
            }
        }
    }

    data object CampingCreate : AppRoute {
        override val route = "${AppRoutePath.Campings}/${AppRoutePath.CampingCreate}"
    }

    data class CampingEdit(val campingId: String) : AppRoute {
        override val route = "${AppRoutePath.Campings}/${campingId.asRouteSegment()}/${AppRoutePath.CampingEdit}"
    }

    data class CampingTemplateClone(val campingId: String) : AppRoute {
        override val route =
            "${AppRoutePath.Campings}/${campingId.asRouteSegment()}/${AppRoutePath.CampingTemplateClone}"
    }

    data class CampingRegistration(val campingId: String) : AppRoute {
        override val route =
            "${AppRoutePath.Campings}/${campingId.asRouteSegment()}/${AppRoutePath.Registration}"
    }

    data class CampingRegistrationPayment(val campingId: String) : AppRoute {
        override val route =
            "${AppRoutePath.Campings}/${campingId.asRouteSegment()}/${AppRoutePath.RegistrationPayment}"
    }

    data class CampingPricing(val campingId: String) : AppRoute {
        override val route =
            "${AppRoutePath.Campings}/${campingId.asRouteSegment()}/${AppRoutePath.Fees}"
    }

    data class CampingLodging(val campingId: String) : AppRoute {
        override val route =
            "${AppRoutePath.Campings}/${campingId.asRouteSegment()}/${AppRoutePath.Lodging}"
    }

    data class CampFeedbackSurvey(val campingId: String) : AppRoute {
        override val route =
            "${AppRoutePath.Campings}/${campingId.asRouteSegment()}/${AppRoutePath.FeedbackSurvey}"
    }

    data class CampFeedbackResults(val campingId: String) : AppRoute {
        override val route =
            "${AppRoutePath.Campings}/${campingId.asRouteSegment()}/${AppRoutePath.FeedbackResults}"
    }

    data class CampingVenueMap(val campingId: String) : AppRoute {
        override val route =
            "${AppRoutePath.Campings}/${campingId.asRouteSegment()}/${AppRoutePath.VenueMap}"
    }

    data class CampingVenueMapEditor(val campingId: String) : AppRoute {
        override val route =
            "${AppRoutePath.Campings}/${campingId.asRouteSegment()}/${AppRoutePath.VenueMapEditor}"
    }

    data class CampingGuardianUpdates(val campingId: String) : AppRoute {
        override val route =
            "${AppRoutePath.Campings}/${campingId.asRouteSegment()}/${AppRoutePath.GuardianUpdates}"
    }

    data object RegistrationReview : AppRoute {
        override val route = AppRoutePath.RegistrationReview
    }

    data class CampingRegistrationReview(val campingId: String) : AppRoute {
        override val route = "${RegistrationReview.route}/${campingId.asRouteSegment()}"
    }

    data class CampingAttendees(val campingId: String) : AppRoute {
        override val route =
            "${AppRoutePath.Campings}/${campingId.asRouteSegment()}/${AppRoutePath.Attendees}"
    }

    data class AttendeeProfile(
        val campingId: String,
        val attendeeId: String,
    ) : AppRoute {
        override val route =
            "${CampingAttendees(campingId).route}/${attendeeId.asRouteSegment()}"
    }

    data class CampingSchedule(val campingId: String) : AppRoute {
        override val route =
            "${CampingDetail(campingId).route}/${AppRoutePath.Schedule}"
    }

    data class CampingScheduleEditor(val campingId: String) : AppRoute {
        override val route =
            "${CampingSchedule(campingId).route}/${AppRoutePath.CampingEdit}"
    }

    data class CampingScheduleProgram(
        val campingId: String,
        val programId: String,
    ) : AppRoute {
        override val route =
            "${CampingSchedule(campingId).route}/${programId.asRouteSegment()}"
    }

    data class ProgramAttendance(
        val campingId: String,
        val programId: String,
    ) : AppRoute {
        override val route =
            "${CampingScheduleProgram(campingId, programId).route}/${AppRoutePath.ProgramAttendance}"
    }

    data class ProgramAttendanceScanner(
        val campingId: String,
        val programId: String,
    ) : AppRoute {
        override val route =
            "${CampingScheduleProgram(campingId, programId).route}/${AppRoutePath.ProgramAttendanceScanner}"
    }

    data class CampingScheduleProgramEditor(val campingId: String) : AppRoute {
        override val route =
            "${CampingSchedule(campingId).route}/${AppRoutePath.ProgramEditor}"
    }

    data object AnnouncementComposer : AppRoute {
        override val route = "${AppRoutePath.Announcements}/${AppRoutePath.AnnouncementComposer}"
    }

    data class CampingFoodMenu(val campingId: String) : AppRoute {
        override val route =
            "${CampingDetail(campingId).route}/${AppRoutePath.FoodMenu}"
    }

    data class CampingFoodMenuEditor(val campingId: String) : AppRoute {
        override val route =
            "${CampingFoodMenu(campingId).route}/${AppRoutePath.CampingEdit}"
    }

    data class CampingSongbook(val campingId: String) : AppRoute {
        override val route =
            "${CampingDetail(campingId).route}/${AppRoutePath.Songbook}"
    }

    data class SongDetail(
        val campingId: String,
        val songId: String,
    ) : AppRoute {
        override val route =
            "${CampingSongbook(campingId).route}/${songId.asRouteSegment()}"
    }

    data class SongEditor(
        val campingId: String,
        val songId: String? = null,
    ) : AppRoute {
        override val route = buildString {
            append(CampingSongbook(campingId).route)
            append("/")
            append(AppRoutePath.SongEditor)
            val resolvedSongId = songId?.takeUnless { it.isBlank() }
            if (resolvedSongId != null) {
                append("/")
                append(resolvedSongId.asRouteSegment())
            }
        }
    }

    data class CampingGuidelines(val campingId: String) : AppRoute {
        override val route =
            "${CampingDetail(campingId).route}/${AppRoutePath.Guidelines}"
    }

    data class CampingSupport(val campingId: String) : AppRoute {
        override val route = "${CampingDetail(campingId).route}/${AppRoutePath.Support}"
    }

    data class CampingPackingChecklist(val campingId: String) : AppRoute {
        override val route = "${CampingDetail(campingId).route}/${AppRoutePath.PackingChecklist}"
    }

    data class CampingPackingChecklistEditor(val campingId: String) : AppRoute {
        override val route = "${CampingPackingChecklist(campingId).route}/${AppRoutePath.CampingEdit}"
    }

    data class CampingPackingShareImport(val campingId: String, val shareId: String) : AppRoute {
        override val route = "${CampingPackingChecklist(campingId).route}/${AppRoutePath.PackingShare}/${shareId.asRouteSegment()}"
    }

    data class CampingGames(val campingId: String) : AppRoute {
        override val route = "${CampingDetail(campingId).route}/${AppRoutePath.Games}"
    }

    data class GameDetail(val campingId: String, val gameId: String) : AppRoute {
        override val route = "${CampingGames(campingId).route}/${gameId.asRouteSegment()}"
    }

    data class GameEditor(
        val campingId: String,
        val gameId: String? = null,
    ) : AppRoute {
        override val route = buildString {
            append(CampingGames(campingId).route)
            append("/")
            append(AppRoutePath.GameEditor)
            val resolved = gameId?.takeUnless { it.isBlank() }
            if (resolved != null) {
                append("/")
                append(resolved.asRouteSegment())
            }
        }
    }

    data class WinnerReveal(val campingId: String) : AppRoute {
        override val route = "${CampingGames(campingId).route}/${AppRoutePath.WinnerReveal}"
    }

    companion object {
        val topLevelTabs: List<Tab> = listOf(Home, Campings, Announcements, Profile)

        fun topLevelForRoute(route: String?): Tab = when {
            route == null -> Home
            route.startsWith(AppRoutePath.RegistrationReview) -> Campings
            route.startsWith(AppRoutePath.Campings) -> Campings
            route.startsWith(AppRoutePath.Announcements) -> Announcements
            route.startsWith(AppRoutePath.Profile) -> Profile
            else -> Home
        }
    }
}

internal object AppRouteArgs {
    const val CampingId = "campingId"
    const val AnnouncementId = "announcementId"
    const val TeamId = "teamId"
    const val PollId = "pollId"
    const val AttendeeId = "attendeeId"
    const val ProgramId = "programId"
    const val SongId = "songId"
    const val GameId = "gameId"
    const val VehicleId = "vehicleId"
    const val UserVehicleId = "userVehicleId"
    const val AchievementId = "achievementId"
    const val UserId = "userId"
    const val InvitationCode = "invitationCode"
    const val DecisionKind = "decisionKind"
    const val RegistrationId = "registrationId"
    const val ShareId = "shareId"
}

internal object AppRoutePath {
    const val Home = "home"
    const val Campings = "campings"
    const val Announcements = "announcements"
    const val Profile = "profile"
    const val ProfileEdit = "edit"
    const val Achievements = "achievements"
    const val NotificationSettings = "notifications"
    const val NotificationFeed = "notifications"
    const val ChannelCampings = "channel-campings"
    const val ChannelTeams = "channel-teams"
    const val FamilyParticipants = "family"
    const val MyVehicles = "vehicles"
    const val UserVehicleEditor = "vehicle-editor"
    const val AdminTools = "admin"
    const val ModerationQueue = "moderation"
    const val RoleManagement = "roles"
    const val AdminOnboarding = "onboarding"
    const val AdminAnalytics = "analytics"
    const val UserDataExport = "export"
    const val AppSupport = "support"
    const val Chat = "chat"
    const val Teams = "teams"
    const val PointHistory = "points"
    const val Polls = "polls"
    const val Album = "album"
    const val Safety = "safety"
    const val CheckInScanner = "check-in-scanner"
    const val CheckInRecords = "check-in-records"
    const val CheckInQrPasses = "qr-passes"
    const val TransportationTickets = "transportation"
    const val TransportationScanner = "transportation-scanner"
    const val TransportationDashboard = "transportation-dashboard"
    const val TransportationHistory = "transportation-history"
    const val MyTransportation = "my-transportation"
    const val VehicleForm = "vehicle-form"
    const val VehicleQr = "vehicle-qr"
    const val Vehicles = "vehicles"
    const val VehicleScanner = "vehicle-scanner"
    const val VehicleArrival = "vehicle-arrival"
    const val Award = "award"
    const val PollEditor = "poll-editor"
    const val Registration = "register"
    const val RegistrationPayment = "registration-payment"
    const val Fees = "fees"
    const val Lodging = "lodging"
    const val FeedbackSurvey = "feedback-survey"
    const val FeedbackResults = "feedback"
    const val VenueMap = "venue-map"
    const val VenueMapEditor = "venue-map-editor"
    const val GuardianUpdates = "family-at-camp"
    const val RegistrationReview = "registration-review"
    const val Attendees = "attendees"
    const val CampingCreate = "create"
    const val CampingEdit = "edit"
    const val CampingTemplateClone = "template-clone"
    const val Schedule = "schedule"
    const val ProgramEditor = "program-editor"
    const val ProgramAttendance = "attendance"
    const val ProgramAttendanceScanner = "attendance-scanner"
    const val FoodMenu = "food-menu"
    const val Songbook = "songbook"
    const val SongEditor = "song-editor"
    const val TeamEditor = "team-editor"
    const val Guidelines = "guidelines"
    const val PackingChecklist = "packing"
    const val PackingShare = "shared"
    const val Support = "support"
    const val AnnouncementComposer = "compose"
    const val Games = "games"
    const val GameEditor = "game-editor"
    const val WinnerReveal = "winner-reveal"
}

internal object AppRoutePattern {
    const val CampingDetail = "${AppRoutePath.Campings}/{${AppRouteArgs.CampingId}}"
    const val AnnouncementDetail =
        "${AppRoutePath.Announcements}/{${AppRouteArgs.AnnouncementId}}"
    const val CampingChat = "$CampingDetail/${AppRoutePath.Chat}"
    const val CampingTeams = "$CampingDetail/${AppRoutePath.Teams}"
    const val TeamEditor = "$CampingTeams/${AppRoutePath.TeamEditor}"
    const val TeamEdit = "$TeamEditor/{${AppRouteArgs.TeamId}}"
    const val TeamDetail = "$CampingTeams/{${AppRouteArgs.TeamId}}"
    const val TeamChat = "$TeamDetail/${AppRoutePath.Chat}"
    const val PointHistory = "$CampingDetail/${AppRoutePath.PointHistory}"
    const val TeamPointHistory = "$PointHistory/{${AppRouteArgs.TeamId}}"
    const val CampingPolls = "$CampingDetail/${AppRoutePath.Polls}"
    const val CampingAlbum = "$CampingDetail/${AppRoutePath.Album}"
    const val CampingSafety = "$CampingDetail/${AppRoutePath.Safety}"
    const val MyVehicles = "${AppRoutePath.Profile}/${AppRoutePath.MyVehicles}"
    const val UserVehicleEditor = "$MyVehicles/${AppRoutePath.UserVehicleEditor}"
    const val UserVehicleEdit = "$UserVehicleEditor/{${AppRouteArgs.UserVehicleId}}"
    const val CheckInScanner = "$CampingDetail/${AppRoutePath.CheckInScanner}"
    const val CheckInRecords = "$CampingDetail/${AppRoutePath.CheckInRecords}"
    const val CheckInQrPasses = "$CampingDetail/${AppRoutePath.CheckInQrPasses}"
    const val TransportationTickets = "$CampingDetail/${AppRoutePath.TransportationTickets}"
    const val TransportationScanner = "$CampingDetail/${AppRoutePath.TransportationScanner}"
    const val TransportationDashboard = "$CampingDetail/${AppRoutePath.TransportationDashboard}"
    const val TransportationHistory = "$CampingDetail/${AppRoutePath.TransportationHistory}"
    const val MyTransportation = "$CampingDetail/${AppRoutePath.MyTransportation}"
    const val MyTransportationJoin = "$MyTransportation/join/{${AppRouteArgs.InvitationCode}}"
    const val TransportationDecision = "$MyTransportation/{${AppRouteArgs.DecisionKind}}/{${AppRouteArgs.VehicleId}}/{${AppRouteArgs.RegistrationId}}"
    const val VehicleForm = "$CampingDetail/${AppRoutePath.VehicleForm}"
    const val VehicleEdit = "$VehicleForm/{${AppRouteArgs.VehicleId}}"
    const val VehicleQr = "$CampingDetail/${AppRoutePath.VehicleQr}/{${AppRouteArgs.VehicleId}}"
    const val CampingVehicles = "$CampingDetail/${AppRoutePath.Vehicles}"
    const val VehicleScanner = "$CampingDetail/${AppRoutePath.VehicleScanner}"
    const val VehicleArrival = "$CampingDetail/${AppRoutePath.VehicleArrival}/{${AppRouteArgs.VehicleId}}"
    const val CampingBadgeAward = "$CampingDetail/${AppRoutePath.Achievements}/${AppRoutePath.Award}"
    const val PollEditor = "$CampingPolls/${AppRoutePath.PollEditor}"
    const val PollEdit = "$PollEditor/{${AppRouteArgs.PollId}}"
    const val PollDetail = "$CampingPolls/{${AppRouteArgs.PollId}}"
    const val CampingRegistration = "$CampingDetail/${AppRoutePath.Registration}"
    const val CampingRegistrationPayment = "$CampingDetail/${AppRoutePath.RegistrationPayment}"
    const val CampingPricing = "$CampingDetail/${AppRoutePath.Fees}"
    const val CampingLodging = "$CampingDetail/${AppRoutePath.Lodging}"
    const val CampFeedbackSurvey = "$CampingDetail/${AppRoutePath.FeedbackSurvey}"
    const val CampFeedbackResults = "$CampingDetail/${AppRoutePath.FeedbackResults}"
    const val CampingVenueMap = "$CampingDetail/${AppRoutePath.VenueMap}"
    const val CampingVenueMapEditor = "$CampingDetail/${AppRoutePath.VenueMapEditor}"
    const val CampingGuardianUpdates = "$CampingDetail/${AppRoutePath.GuardianUpdates}"
    const val RegistrationReview = AppRoutePath.RegistrationReview
    const val CampingRegistrationReview = "$RegistrationReview/{${AppRouteArgs.CampingId}}"
    const val CampingAttendees = "$CampingDetail/${AppRoutePath.Attendees}"
    const val AttendeeProfile = "$CampingAttendees/{${AppRouteArgs.AttendeeId}}"
    const val CampingCreate = "${AppRoutePath.Campings}/${AppRoutePath.CampingCreate}"
    const val CampingEdit = "$CampingDetail/${AppRoutePath.CampingEdit}"
    const val CampingTemplateClone = "$CampingDetail/${AppRoutePath.CampingTemplateClone}"
    const val CampingSchedule = "$CampingDetail/${AppRoutePath.Schedule}"
    const val CampingScheduleEditor = "$CampingSchedule/${AppRoutePath.CampingEdit}"
    const val CampingScheduleProgramEditor = "$CampingSchedule/${AppRoutePath.ProgramEditor}"
    const val ProgramAttendance =
        "$CampingSchedule/{${AppRouteArgs.ProgramId}}/${AppRoutePath.ProgramAttendance}"
    const val ProgramAttendanceScanner =
        "$CampingSchedule/{${AppRouteArgs.ProgramId}}/${AppRoutePath.ProgramAttendanceScanner}"
    const val CampingScheduleProgram = "$CampingSchedule/{${AppRouteArgs.ProgramId}}"
    const val CampingFoodMenu = "$CampingDetail/${AppRoutePath.FoodMenu}"
    const val CampingFoodMenuEditor = "$CampingFoodMenu/${AppRoutePath.CampingEdit}"
    const val CampingSongbook = "$CampingDetail/${AppRoutePath.Songbook}"
    const val SongEditor = "$CampingSongbook/${AppRoutePath.SongEditor}"
    const val SongEdit = "$SongEditor/{${AppRouteArgs.SongId}}"
    const val SongDetail = "$CampingSongbook/{${AppRouteArgs.SongId}}"
    const val CampingGuidelines = "$CampingDetail/${AppRoutePath.Guidelines}"
    const val CampingPackingChecklist = "$CampingDetail/${AppRoutePath.PackingChecklist}"
    const val CampingPackingChecklistEditor = "$CampingPackingChecklist/${AppRoutePath.CampingEdit}"
    const val CampingPackingShareImport = "$CampingPackingChecklist/${AppRoutePath.PackingShare}/{${AppRouteArgs.ShareId}}"
    const val CampingSupport = "$CampingDetail/${AppRoutePath.Support}"
    const val AnnouncementComposer = "${AppRoutePath.Announcements}/${AppRoutePath.AnnouncementComposer}"
    const val CampingGames = "$CampingDetail/${AppRoutePath.Games}"
    const val GameEditor = "$CampingGames/${AppRoutePath.GameEditor}"
    const val GameEdit = "$GameEditor/{${AppRouteArgs.GameId}}"
    const val WinnerReveal = "$CampingGames/${AppRoutePath.WinnerReveal}"
    const val GameDetail = "$CampingGames/{${AppRouteArgs.GameId}}"
    const val ModerationQueue = "${AppRoutePath.Profile}/${AppRoutePath.AdminTools}/${AppRoutePath.ModerationQueue}"
    const val ProfileAchievementsFor = "${AppRoutePath.Profile}/${AppRoutePath.Achievements}/{${AppRouteArgs.UserId}}"
    const val ProfileAchievementDetail = "$ProfileAchievementsFor/{${AppRouteArgs.AchievementId}}"
}

private fun String.asRouteSegment(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")
