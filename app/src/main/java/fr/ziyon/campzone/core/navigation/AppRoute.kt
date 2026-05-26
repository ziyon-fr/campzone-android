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
        val label: String
        val iconLabel: ImageVector
        val contentDescription: String
    }

    data object Home : Tab {
        override val route = AppRoutePath.Home
        override val label = "Home"
        override val iconLabel = Icons.Outlined.Home
        override val contentDescription = "Home tab"
    }

    data object Campings : Tab {
        override val route = AppRoutePath.Campings
        override val label = "Campings"
        override val iconLabel = Icons.Outlined.Cabin
        override val contentDescription = "Campings tab"
    }

    data object Announcements : Tab {
        override val route = AppRoutePath.Announcements
        override val label = "Announcements"
        override val iconLabel = Icons.Outlined.Campaign
        override val contentDescription = "Announcements tab"
    }

    data object Profile : Tab {
        override val route = AppRoutePath.Profile
        override val label = "Profile"
        override val iconLabel = Icons.Outlined.AccountCircle
        override val contentDescription = "Profile tab"
    }

    data object ProfileEdit : AppRoute {
        override val route = "${AppRoutePath.Profile}/${AppRoutePath.ProfileEdit}"
    }

    data object ProfileAchievements : AppRoute {
        override val route = "${AppRoutePath.Profile}/${AppRoutePath.Achievements}"
    }

    data object NotificationSettings : AppRoute {
        override val route = "${AppRoutePath.Profile}/${AppRoutePath.NotificationSettings}"
    }

    data object FamilyParticipants : AppRoute {
        override val route = "${AppRoutePath.Profile}/${AppRoutePath.FamilyParticipants}"
    }

    data object AdminTools : AppRoute {
        override val route = "${AppRoutePath.Profile}/${AppRoutePath.AdminTools}"
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

    data class TeamDetail(
        val campingId: String,
        val teamId: String,
    ) : AppRoute {
        override val route =
            "${CampingDetail(campingId).route}/${AppRoutePath.Teams}/${teamId.asRouteSegment()}"
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

    data class PollDetail(
        val campingId: String,
        val pollId: String,
    ) : AppRoute {
        override val route = "${CampingPolls(campingId).route}/${pollId.asRouteSegment()}"
    }

    data object CampingCreate : AppRoute {
        override val route = "${AppRoutePath.Campings}/${AppRoutePath.CampingCreate}"
    }

    data class CampingEdit(val campingId: String) : AppRoute {
        override val route = "${AppRoutePath.Campings}/${campingId.asRouteSegment()}/${AppRoutePath.CampingEdit}"
    }

    data class CampingRegistration(val campingId: String) : AppRoute {
        override val route =
            "${AppRoutePath.Campings}/${campingId.asRouteSegment()}/${AppRoutePath.Registration}"
    }

    data class CampingRegistrationPayment(val campingId: String) : AppRoute {
        override val route =
            "${AppRoutePath.Campings}/${campingId.asRouteSegment()}/${AppRoutePath.RegistrationPayment}"
    }

    data object RegistrationReview : AppRoute {
        override val route = AppRoutePath.RegistrationReview
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

    data class CampingGuidelines(val campingId: String) : AppRoute {
        override val route =
            "${CampingDetail(campingId).route}/${AppRoutePath.Guidelines}"
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
}

internal object AppRoutePath {
    const val Home = "home"
    const val Campings = "campings"
    const val Announcements = "announcements"
    const val Profile = "profile"
    const val ProfileEdit = "edit"
    const val Achievements = "achievements"
    const val NotificationSettings = "notifications"
    const val FamilyParticipants = "family"
    const val AdminTools = "admin"
    const val UserDataExport = "export"
    const val AppSupport = "support"
    const val Chat = "chat"
    const val Teams = "teams"
    const val PointHistory = "points"
    const val Polls = "polls"
    const val Registration = "register"
    const val RegistrationPayment = "registration-payment"
    const val RegistrationReview = "registration-review"
    const val Attendees = "attendees"
    const val CampingCreate = "create"
    const val CampingEdit = "edit"
    const val Schedule = "schedule"
    const val ProgramEditor = "program-editor"
    const val FoodMenu = "food-menu"
    const val Guidelines = "guidelines"
    const val AnnouncementComposer = "compose"
}

internal object AppRoutePattern {
    const val CampingDetail = "${AppRoutePath.Campings}/{${AppRouteArgs.CampingId}}"
    const val AnnouncementDetail =
        "${AppRoutePath.Announcements}/{${AppRouteArgs.AnnouncementId}}"
    const val CampingChat = "$CampingDetail/${AppRoutePath.Chat}"
    const val TeamDetail = "$CampingDetail/${AppRoutePath.Teams}/{${AppRouteArgs.TeamId}}"
    const val TeamChat = "$TeamDetail/${AppRoutePath.Chat}"
    const val PointHistory = "$CampingDetail/${AppRoutePath.PointHistory}"
    const val TeamPointHistory = "$PointHistory/{${AppRouteArgs.TeamId}}"
    const val CampingPolls = "$CampingDetail/${AppRoutePath.Polls}"
    const val PollDetail = "$CampingPolls/{${AppRouteArgs.PollId}}"
    const val CampingRegistration = "$CampingDetail/${AppRoutePath.Registration}"
    const val CampingRegistrationPayment = "$CampingDetail/${AppRoutePath.RegistrationPayment}"
    const val RegistrationReview = AppRoutePath.RegistrationReview
    const val CampingAttendees = "$CampingDetail/${AppRoutePath.Attendees}"
    const val AttendeeProfile = "$CampingAttendees/{${AppRouteArgs.AttendeeId}}"
    const val CampingCreate = "${AppRoutePath.Campings}/${AppRoutePath.CampingCreate}"
    const val CampingEdit = "$CampingDetail/${AppRoutePath.CampingEdit}"
    const val CampingSchedule = "$CampingDetail/${AppRoutePath.Schedule}"
    const val CampingScheduleEditor = "$CampingSchedule/${AppRoutePath.CampingEdit}"
    const val CampingScheduleProgram = "$CampingSchedule/{${AppRouteArgs.ProgramId}}"
    const val CampingScheduleProgramEditor = "$CampingSchedule/${AppRoutePath.ProgramEditor}"
    const val CampingFoodMenu = "$CampingDetail/${AppRoutePath.FoodMenu}"
    const val CampingFoodMenuEditor = "$CampingFoodMenu/${AppRoutePath.CampingEdit}"
    const val CampingGuidelines = "$CampingDetail/${AppRoutePath.Guidelines}"
    const val AnnouncementComposer = "${AppRoutePath.Announcements}/${AppRoutePath.AnnouncementComposer}"
}

private fun String.asRouteSegment(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")
