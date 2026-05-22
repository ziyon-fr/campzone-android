package fr.ziyon.campzone.core.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors

@Composable
fun CampzoneNavigationShell(
    deepLinkInbox: DeepLinkInbox,
    modifier: Modifier = Modifier,
    authReady: Boolean = true,
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val pendingDeepLink by deepLinkInbox.pendingDeepLink.collectAsState()
    val navReady = currentBackStackEntry != null

    LaunchedEffect(authReady, navReady, pendingDeepLink) {
        val deepLink = pendingDeepLink
        if (authReady && navReady && deepLink != null) {
            navController.navigateToDeepLink(deepLink)
            deepLinkInbox.consume(deepLink)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            CampzoneBottomNavigation(
                selectedTab = AppRoute.topLevelForRoute(currentBackStackEntry?.destination?.route),
                onTabSelected = navController::navigateToTab,
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(AppRoute.Home.route) {
                TopLevelPlaceholderScreen(title = AppRoute.Home.label)
            }
            composable(AppRoute.Campings.route) {
                TopLevelPlaceholderScreen(title = AppRoute.Campings.label)
            }
            composable(AppRoute.Announcements.route) {
                TopLevelPlaceholderScreen(title = AppRoute.Announcements.label)
            }
            composable(AppRoute.Profile.route) {
                TopLevelPlaceholderScreen(title = AppRoute.Profile.label)
            }
            composable(
                route = AppRoutePattern.CampingDetail,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                DetailPlaceholderScreen(
                    title = "Camping",
                    value = backStackEntry.stringArg(AppRouteArgs.CampingId),
                )
            }
            composable(
                route = AppRoutePattern.AnnouncementDetail,
                arguments = listOf(navArgument(AppRouteArgs.AnnouncementId) { type = NavType.StringType }),
            ) { backStackEntry ->
                DetailPlaceholderScreen(
                    title = "Announcement",
                    value = backStackEntry.stringArg(AppRouteArgs.AnnouncementId),
                )
            }
            composable(
                route = AppRoutePattern.CampingChat,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                DetailPlaceholderScreen(
                    title = "Camping chat",
                    value = backStackEntry.stringArg(AppRouteArgs.CampingId),
                )
            }
            composable(
                route = AppRoutePattern.TeamDetail,
                arguments = teamRouteArguments(),
            ) { backStackEntry ->
                DetailPlaceholderScreen(
                    title = "Team",
                    value = backStackEntry.stringArg(AppRouteArgs.TeamId),
                )
            }
            composable(
                route = AppRoutePattern.TeamChat,
                arguments = teamRouteArguments(),
            ) { backStackEntry ->
                DetailPlaceholderScreen(
                    title = "Team chat",
                    value = backStackEntry.stringArg(AppRouteArgs.TeamId),
                )
            }
            composable(
                route = AppRoutePattern.CampingPolls,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                DetailPlaceholderScreen(
                    title = "Polls",
                    value = backStackEntry.stringArg(AppRouteArgs.CampingId),
                )
            }
            composable(
                route = AppRoutePattern.PollDetail,
                arguments = listOf(
                    navArgument(AppRouteArgs.CampingId) { type = NavType.StringType },
                    navArgument(AppRouteArgs.PollId) { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                DetailPlaceholderScreen(
                    title = "Poll",
                    value = backStackEntry.stringArg(AppRouteArgs.PollId),
                )
            }
        }
    }
}

@Composable
private fun CampzoneBottomNavigation(
    selectedTab: AppRoute.Tab,
    onTabSelected: (AppRoute.Tab) -> Unit,
) {
    val colors = MaterialTheme.czColors

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = colors.textPrimary,
    ) {
        AppRoute.topLevelTabs.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Text(
                        text = tab.iconLabel,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.onPrimary,
                    selectedTextColor = colors.textPrimary,
                    indicatorColor = colors.primary,
                    unselectedIconColor = colors.textSecondary,
                    unselectedTextColor = colors.textSecondary,
                ),
                modifier = Modifier.semantics {
                    contentDescription = tab.contentDescription
                },
            )
        }
    }
}

@Composable
private fun TopLevelPlaceholderScreen(
    title: String,
    modifier: Modifier = Modifier,
) {
    ScreenColumn(modifier = modifier) {
        Text(
            text = title,
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.displayLarge,
        )
    }
}

@Composable
private fun DetailPlaceholderScreen(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    ScreenColumn(modifier = modifier) {
        Text(
            text = title,
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = value,
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ScreenColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                PaddingValues(
                    horizontal = CzSpacing.xl,
                    vertical = CzSpacing.xxxl,
                ),
            ),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        content()
    }
}

private fun NavBackStackEntry.stringArg(name: String): String =
    arguments?.getString(name).orEmpty()

private fun teamRouteArguments() = listOf(
    navArgument(AppRouteArgs.CampingId) { type = NavType.StringType },
    navArgument(AppRouteArgs.TeamId) { type = NavType.StringType },
)

@Preview(showBackground = true)
@Composable
private fun CampzoneNavigationShellPreview() {
    CampzoneTheme {
        CampzoneNavigationShell(deepLinkInbox = DeepLinkInbox())
    }
}
