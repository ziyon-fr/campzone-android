package fr.ziyon.campzone.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AppRouteTest {
    @Test
    fun exposesFourTopLevelTabsInDisplayOrder() {
        assertEquals(
            listOf(
                AppRoute.Home,
                AppRoute.Campings,
                AppRoute.Announcements,
                AppRoute.Profile,
            ),
            AppRoute.topLevelTabs,
        )
    }

    @Test
    fun mapsNestedRoutesToTheirOwningTabs() {
        assertEquals(AppRoute.Home, AppRoute.topLevelForRoute(AppRoute.Home.route))
        assertEquals(AppRoute.Campings, AppRoute.topLevelForRoute(AppRoutePattern.CampingDetail))
        assertEquals(
            AppRoute.Announcements,
            AppRoute.topLevelForRoute(AppRoutePattern.AnnouncementDetail),
        )
        assertEquals(AppRoute.Profile, AppRoute.topLevelForRoute(AppRoute.Profile.route))
    }

    @Test
    fun typedRoutesEncodePathArguments() {
        assertEquals("campings/camp%201", AppRoute.CampingDetail("camp 1").route)
        assertEquals(
            "campings/camp%201/teams/team%201/chat",
            AppRoute.TeamChat("camp 1", "team 1").route,
        )
        assertEquals(
            "campings/camp%201/points/team%201",
            AppRoute.PointHistory("camp 1", "team 1").route,
        )
    }
}
