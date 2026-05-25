package fr.ziyon.campzone.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.Announcement
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.graphics.vector.ImageVector
import java.net.URLEncoder
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.rounded.PersonPinCircle

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
        override val iconLabel = Icons.Filled.Home
        override val contentDescription = "Home tab"
    }

    data object Campings : Tab {
        override val route = AppRoutePath.Campings
        override val label = "Campings"
        override val iconLabel = Icons.Default.Landscape
        override val contentDescription = "Campings tab"
    }

    data object Announcements : Tab {
        override val route = AppRoutePath.Announcements
        override val label = "Announcements"
        override val iconLabel = Icons.Rounded.Announcement
        override val contentDescription = "Announcements tab"
    }

    data object Profile : Tab {
        override val route = AppRoutePath.Profile
        override val label = "Profile"
        override val iconLabel = Icons.Default.AccountCircle
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

    companion object {
        val topLevelTabs: List<Tab> = listOf(Home, Campings, Announcements, Profile)

        fun topLevelForRoute(route: String?): Tab = when {
            route == null -> Home
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
    const val Polls = "polls"
    const val CampingCreate = "create"
    const val CampingEdit = "edit"
}

internal object AppRoutePattern {
    const val CampingDetail = "${AppRoutePath.Campings}/{${AppRouteArgs.CampingId}}"
    const val AnnouncementDetail =
        "${AppRoutePath.Announcements}/{${AppRouteArgs.AnnouncementId}}"
    const val CampingChat = "$CampingDetail/${AppRoutePath.Chat}"
    const val TeamDetail = "$CampingDetail/${AppRoutePath.Teams}/{${AppRouteArgs.TeamId}}"
    const val TeamChat = "$TeamDetail/${AppRoutePath.Chat}"
    const val CampingPolls = "$CampingDetail/${AppRoutePath.Polls}"
    const val PollDetail = "$CampingPolls/{${AppRouteArgs.PollId}}"
    const val CampingCreate = "${AppRoutePath.Campings}/${AppRoutePath.CampingCreate}"
    const val CampingEdit = "$CampingDetail/${AppRoutePath.CampingEdit}"
}

private fun String.asRouteSegment(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")
