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
        is CampzoneDeepLink.Achievements -> {
            selectTabForDeepLink(AppRoute.Profile)
            navigateTyped(AppRoute.ProfileAchievementsFor(deepLink.userId))
        }

        is CampzoneDeepLink.Achievement -> {
            selectTabForDeepLink(AppRoute.Profile)
            navigateTyped(AppRoute.ProfileAchievementDetail(deepLink.userId, deepLink.achievementId))
        }

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

        is CampzoneDeepLink.Schedule -> {
            selectTabForDeepLink(AppRoute.Campings)
            navigateTyped(AppRoute.CampingDetail(deepLink.campingId))
            navigateTyped(AppRoute.CampingSchedule(deepLink.campingId))
        }

        is CampzoneDeepLink.CampPass -> {
            selectTabForDeepLink(AppRoute.Campings)
            navigateTyped(AppRoute.CampingDetail(deepLink.campingId))
            navigateTyped(AppRoute.CheckInQrPasses(deepLink.campingId))
        }

        is CampzoneDeepLink.Packing -> {
            selectTabForDeepLink(AppRoute.Campings)
            navigateTyped(AppRoute.CampingDetail(deepLink.campingId))
            navigateTyped(AppRoute.CampingPackingChecklist(deepLink.campingId))
        }

        is CampzoneDeepLink.Teams -> {
            selectTabForDeepLink(AppRoute.Campings)
            navigateTyped(AppRoute.CampingDetail(deepLink.campingId))
            navigateTyped(AppRoute.CampingTeams(deepLink.campingId))
        }

        is CampzoneDeepLink.RegistrationReview -> {
            selectTabForDeepLink(AppRoute.Campings)
            navigateTyped(AppRoute.CampingRegistrationReview(deepLink.campingId))
        }

        is CampzoneDeepLink.ScheduleProgram -> {
            selectTabForDeepLink(AppRoute.Campings)
            navigateTyped(AppRoute.CampingDetail(deepLink.campingId))
            navigateTyped(AppRoute.CampingScheduleProgram(deepLink.campingId, deepLink.programId))
        }

        is CampzoneDeepLink.Transportation -> {
            selectTabForDeepLink(AppRoute.Campings)
            navigateTyped(AppRoute.CampingDetail(deepLink.campingId))
            navigateTyped(AppRoute.MyTransportation(deepLink.campingId))
        }

        is CampzoneDeepLink.TransportationJoin -> {
            selectTabForDeepLink(AppRoute.Campings)
            navigateTyped(AppRoute.CampingDetail(deepLink.campingId))
            navigateTyped(AppRoute.MyTransportationJoin(deepLink.campingId, deepLink.invitationCode))
        }

        is CampzoneDeepLink.TransportationInvitation -> {
            selectTabForDeepLink(AppRoute.Campings)
            navigateTyped(AppRoute.CampingDetail(deepLink.campingId))
            navigateTyped(AppRoute.TransportationDecision(deepLink.campingId, "invitation", deepLink.vehicleId, deepLink.registrationId))
        }

        is CampzoneDeepLink.TransportationRequest -> {
            selectTabForDeepLink(AppRoute.Campings)
            navigateTyped(AppRoute.CampingDetail(deepLink.campingId))
            navigateTyped(AppRoute.TransportationDecision(deepLink.campingId, "request", deepLink.vehicleId, deepLink.registrationId))
        }

        is CampzoneDeepLink.PackingShare -> {
            selectTabForDeepLink(AppRoute.Campings)
            navigateTyped(AppRoute.CampingDetail(deepLink.campingId))
            navigateTyped(
                AppRoute.CampingPackingShareImport(
                    campingId = deepLink.campingId,
                    shareId = deepLink.shareId,
                    registrationId = deepLink.registrationId,
                ),
            )
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
