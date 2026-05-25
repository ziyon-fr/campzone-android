package fr.ziyon.campzone.core.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

fun NavHostController.navigateToTab(tab: AppRoute.Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

fun NavHostController.navigateToDeepLink(deepLink: CampzoneDeepLink) {
    when (deepLink) {
        is CampzoneDeepLink.Announcement -> {
            selectTabForDeepLink(AppRoute.Announcements)
            navigateTyped(AppRoute.AnnouncementDetail(deepLink.id))
        }

        is CampzoneDeepLink.Camping -> {
            selectTabForDeepLink(AppRoute.Campings)
            navigateTyped(AppRoute.CampingDetail(deepLink.id))
        }

        is CampzoneDeepLink.CampingChat -> {
            selectTabForDeepLink(AppRoute.Campings)
            navigateTyped(AppRoute.CampingDetail(deepLink.campingId))
            navigateTyped(AppRoute.CampingChat(deepLink.campingId))
        }

        is CampzoneDeepLink.TeamChat -> {
            selectTabForDeepLink(AppRoute.Campings)
            navigateTyped(AppRoute.CampingDetail(deepLink.campingId))
            navigateTyped(AppRoute.TeamDetail(deepLink.campingId, deepLink.teamId))
            navigateTyped(AppRoute.TeamChat(deepLink.campingId, deepLink.teamId))
        }

        is CampzoneDeepLink.TeamUpdate -> {
            selectTabForDeepLink(AppRoute.Campings)
            navigateTyped(AppRoute.CampingDetail(deepLink.campingId))
            navigateTyped(AppRoute.TeamDetail(deepLink.campingId, deepLink.teamId))
        }

        is CampzoneDeepLink.TeamPoints -> {
            selectTabForDeepLink(AppRoute.Campings)
            navigateTyped(AppRoute.CampingDetail(deepLink.campingId))
            navigateTyped(AppRoute.PointHistory(deepLink.campingId, deepLink.teamId))
        }

        is CampzoneDeepLink.Poll -> {
            selectTabForDeepLink(AppRoute.Campings)
            navigateTyped(AppRoute.CampingDetail(deepLink.campingId))
            if (deepLink.pollId == null) {
                navigateTyped(AppRoute.CampingPolls(deepLink.campingId))
            } else {
                navigateTyped(AppRoute.PollDetail(deepLink.campingId, deepLink.pollId))
            }
        }

        is CampzoneDeepLink.RegistrationReview -> {
            selectTabForDeepLink(AppRoute.Campings)
            navigateTyped(AppRoute.CampingDetail(deepLink.campingId))
        }
    }
}

private fun NavHostController.selectTabForDeepLink(tab: AppRoute.Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = false
        }
        launchSingleTop = true
        restoreState = false
    }
}

private fun NavHostController.navigateTyped(route: AppRoute) {
    navigate(route.route) {
        launchSingleTop = true
    }
}
