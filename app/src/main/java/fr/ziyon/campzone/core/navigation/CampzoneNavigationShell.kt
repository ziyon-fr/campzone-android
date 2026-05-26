package fr.ziyon.campzone.core.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.ui.camping.CampingDetailRoute
import fr.ziyon.campzone.ui.camping.CampingsRoute
import fr.ziyon.campzone.ui.camping.admin.CampingEditorRoute
import fr.ziyon.campzone.ui.camping.registrations.AttendeeProfileRoute
import fr.ziyon.campzone.ui.camping.registrations.CampingAttendeesRoute
import fr.ziyon.campzone.ui.camping.registrations.RegistrationReviewRoute
import fr.ziyon.campzone.ui.camping.register.CampingRegistrationRoute
import fr.ziyon.campzone.ui.family.FamilyParticipantsScreen
import fr.ziyon.campzone.ui.home.HomeRoute
import fr.ziyon.campzone.ui.payments.CampingRegistrationPaymentRoute
import fr.ziyon.campzone.ui.profile.ProfileScreen
import fr.ziyon.campzone.ui.profile.ProfileSettingsScreen
import fr.ziyon.campzone.ui.profile.UserDataExportScreen
import fr.ziyon.campzone.ui.schedule.ProgramDetailScreen
import fr.ziyon.campzone.ui.schedule.ProgramEditorScreen
import fr.ziyon.campzone.ui.schedule.ScheduleEditorScreen
import fr.ziyon.campzone.ui.schedule.ScheduleRoute
import fr.ziyon.campzone.ui.schedule.ScheduleViewModel
import fr.ziyon.campzone.ui.schedule.food.FoodMenuEditorScreen
import fr.ziyon.campzone.ui.schedule.food.FoodMenuRoute
import fr.ziyon.campzone.ui.schedule.food.FoodMenuViewModel
import androidx.compose.runtime.remember
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.ui.announcements.AnnouncementComposerRoute
import fr.ziyon.campzone.ui.announcements.AnnouncementDetailRoute
import fr.ziyon.campzone.ui.announcements.AnnouncementViewModel
import fr.ziyon.campzone.ui.announcements.AnnouncementsRoute

@Composable
fun CampzoneNavigationShell(
    deepLinkInbox: DeepLinkInbox,
    authenticatedUser: AuthenticatedUser,
    onSignOut: () -> Unit,
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
                HomeRoute(
                    onOpenCamping = { campingId ->
                        navController.navigate(AppRoute.CampingDetail(campingId).route)
                    },
                    onOpenProgram = { campingId, programId ->
                        navController.navigate(AppRoute.CampingScheduleProgram(campingId, programId).route)
                    },
                    onOpenNotifications = {
                        navController.navigate(AppRoute.NotificationSettings.route)
                    },
                )
            }
            composable(AppRoute.Campings.route) {
                CampingsRoute(
                    authenticatedUser = authenticatedUser,
                    onOpenCamping = { campingId ->
                        navController.navigate(AppRoute.CampingDetail(campingId).route)
                    },
                    onCreateCamping = {
                        navController.navigate(AppRoute.CampingCreate.route)
                    },
                    onReviewRegistrations = {
                        navController.navigate(AppRoute.RegistrationReview.route)
                    },
                )
            }
            // ── Announcements ────────────────────────────────────────────────
            // Composer must be before Detail to prevent "compose" matching {announcementId}
            composable(AppRoutePattern.AnnouncementComposer) {
                val announcementsEntry = remember(it) {
                    navController.getBackStackEntry(AppRoute.Announcements.route)
                }
                val announcementViewModel: AnnouncementViewModel = hiltViewModel(announcementsEntry)
                val permissionUser = PermissionUser(
                    role = authenticatedUser.role,
                    userId = authenticatedUser.uid,
                    church = authenticatedUser.church,
                )
                val evaluator = remember { AppPermissionEvaluator() }
                AnnouncementComposerRoute(
                    viewModel = announcementViewModel,
                    authenticatedUser = authenticatedUser,
                    permissionUser = permissionUser,
                    evaluator = evaluator,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                AppRoutePattern.AnnouncementDetail,
                arguments = listOf(navArgument(AppRouteArgs.AnnouncementId) { type = NavType.StringType }),
            ) { backStackEntry ->
                val announcementsEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(AppRoute.Announcements.route)
                }
                val announcementViewModel: AnnouncementViewModel = hiltViewModel(announcementsEntry)
                val announcementId = backStackEntry.arguments?.getString(AppRouteArgs.AnnouncementId) ?: return@composable
                val permissionUser = PermissionUser(
                    role = authenticatedUser.role,
                    userId = authenticatedUser.uid,
                    church = authenticatedUser.church,
                )
                val evaluator = remember { AppPermissionEvaluator() }
                AnnouncementDetailRoute(
                    viewModel = announcementViewModel,
                    announcementId = announcementId,
                    authenticatedUser = authenticatedUser,
                    permissionUser = permissionUser,
                    evaluator = evaluator,
                    onBack = { navController.popBackStack() },
                    onOpenComposer = { navController.navigate(AppRoute.AnnouncementComposer.route) },
                )
            }
            composable(AppRoute.Announcements.route) {
                val announcementViewModel: AnnouncementViewModel = hiltViewModel()
                val permissionUser = PermissionUser(
                    role = authenticatedUser.role,
                    userId = authenticatedUser.uid,
                    church = authenticatedUser.church,
                )
                val evaluator = remember { AppPermissionEvaluator() }
                AnnouncementsRoute(
                    viewModel = announcementViewModel,
                    authenticatedUser = authenticatedUser,
                    permissionUser = permissionUser,
                    evaluator = evaluator,
                    onOpenDetail = { id ->
                        navController.navigate(AppRoute.AnnouncementDetail(id).route)
                    },
                    onOpenComposer = {
                        navController.navigate(AppRoute.AnnouncementComposer.route)
                    },
                )
            }
            composable(AppRoute.Profile.route) {
                ProfileSettingsScreen(
                    authenticatedUser = authenticatedUser,
                    onEditProfile = { navController.navigate(AppRoute.ProfileEdit.route) },
                    onOpenAchievements = { navController.navigate(AppRoute.ProfileAchievements.route) },
                    onOpenNotifications = { navController.navigate(AppRoute.NotificationSettings.route) },
                    onOpenFamilyParticipants = { navController.navigate(AppRoute.FamilyParticipants.route) },
                    onOpenAdminTools = { navController.navigate(AppRoute.AdminTools.route) },
                    onOpenDataExport = { navController.navigate(AppRoute.UserDataExport.route) },
                    onOpenSupport = { navController.navigate(AppRoute.AppSupport.route) },
                    onSignOut = onSignOut,
                )
            }
            composable(AppRoute.ProfileEdit.route) {
                ProfileScreen(
                    authenticatedUser = authenticatedUser,
                    onSignOut = onSignOut,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenDataExport = { navController.navigate(AppRoute.UserDataExport.route) },
                )
            }
            composable(AppRoute.ProfileAchievements.route) {
                DetailPlaceholderScreen(title = stringResource(R.string.profile_my_achievements), value = authenticatedUser.uid)
            }
            composable(AppRoute.NotificationSettings.route) {
                DetailPlaceholderScreen(title = stringResource(R.string.profile_notifications), value = stringResource(R.string.profile_coming_soon))
            }
            composable(AppRoute.FamilyParticipants.route) {
                FamilyParticipantsScreen(
                    authenticatedUser = authenticatedUser,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(AppRoute.AdminTools.route) {
                DetailPlaceholderScreen(title = stringResource(R.string.profile_admin_tools), value = stringResource(R.string.profile_coming_soon))
            }
            composable(AppRoute.UserDataExport.route) {
                UserDataExportScreen(
                    authenticatedUser = authenticatedUser,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(AppRoute.AppSupport.route) {
                DetailPlaceholderScreen(title = stringResource(R.string.profile_support_campzone), value = stringResource(R.string.profile_coming_soon))
            }
            composable(
                route = AppRoutePattern.CampingDetail,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                CampingDetailRoute(
                    campingId = backStackEntry.stringArg(AppRouteArgs.CampingId),
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
                    onOpenSchedule = { campingId ->
                        navController.navigate(AppRoute.CampingSchedule(campingId).route)
                    },
                    onOpenChat = { campingId ->
                        navController.navigate(AppRoute.CampingChat(campingId).route)
                    },
                    onOpenPolls = { campingId ->
                        navController.navigate(AppRoute.CampingPolls(campingId).route)
                    },
                    onOpenEditCamping = { campingId ->
                        navController.navigate(AppRoute.CampingEdit(campingId).route)
                    },
                    onOpenRegistration = { campingId ->
                        navController.navigate(AppRoute.CampingRegistration(campingId).route)
                    },
                    onOpenRegistrationReview = {
                        navController.navigate(AppRoute.RegistrationReview.route)
                    },
                    onOpenAttendees = { campingId ->
                        navController.navigate(AppRoute.CampingAttendees(campingId).route)
                    },
                    onOpenFoodMenu = { campingId ->
                        navController.navigate(AppRoute.CampingFoodMenu(campingId).route)
                    },
                )
            }
            composable(route = AppRoutePattern.RegistrationReview) {
                RegistrationReviewRoute(
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
                    onOpenCamping = { campingId ->
                        navController.navigate(AppRoute.CampingDetail(campingId).route)
                    },
                )
            }
            composable(
                route = AppRoutePattern.CampingRegistration,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                CampingRegistrationRoute(
                    campingId = backStackEntry.stringArg(AppRouteArgs.CampingId),
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
                    onAddParticipant = { navController.navigate(AppRoute.FamilyParticipants.route) },
                    onOpenPayment = { campingId ->
                        navController.navigate(AppRoute.CampingRegistrationPayment(campingId).route)
                    },
                )
            }
            composable(
                route = AppRoutePattern.CampingRegistrationPayment,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                CampingRegistrationPaymentRoute(
                    campingId = campingId,
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
                    onDone = {
                        navController.popBackStack(
                            AppRoute.CampingDetail(campingId).route,
                            inclusive = false,
                        )
                    },
                )
            }
            composable(
                route = AppRoutePattern.CampingAttendees,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                CampingAttendeesRoute(
                    campingId = backStackEntry.stringArg(AppRouteArgs.CampingId),
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { campingId, attendeeId ->
                        navController.navigate(AppRoute.AttendeeProfile(campingId, attendeeId).route)
                    },
                )
            }
            composable(
                route = AppRoutePattern.AttendeeProfile,
                arguments = listOf(
                    navArgument(AppRouteArgs.CampingId) { type = NavType.StringType },
                    navArgument(AppRouteArgs.AttendeeId) { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                AttendeeProfileRoute(
                    campingId = backStackEntry.stringArg(AppRouteArgs.CampingId),
                    attendeeId = backStackEntry.stringArg(AppRouteArgs.AttendeeId),
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(route = AppRoutePattern.CampingCreate) {
                CampingEditorRoute(
                    campingId = null,
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = AppRoutePattern.CampingEdit,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                CampingEditorRoute(
                    campingId = backStackEntry.stringArg(AppRouteArgs.CampingId),
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
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
                route = AppRoutePattern.PointHistory,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                DetailPlaceholderScreen(
                    title = "Point history",
                    value = backStackEntry.stringArg(AppRouteArgs.CampingId),
                )
            }
            composable(
                route = AppRoutePattern.TeamPointHistory,
                arguments = teamRouteArguments(),
            ) { backStackEntry ->
                DetailPlaceholderScreen(
                    title = "Point history",
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
            composable(
                route = AppRoutePattern.CampingSchedule,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                ScheduleRoute(
                    campingId = campingId,
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
                    onOpenEditor = {
                        navController.navigate(AppRoute.CampingScheduleEditor(campingId).route)
                    },
                    onOpenProgram = { programId ->
                        navController.navigate(AppRoute.CampingScheduleProgram(campingId, programId).route)
                    },
                )
            }
            // Register static schedule sub-routes BEFORE the dynamic {programId} route
            composable(
                route = AppRoutePattern.CampingScheduleEditor,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val scheduleEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(AppRoute.CampingSchedule(campingId).route)
                }
                val viewModel: ScheduleViewModel = hiltViewModel(scheduleEntry)
                ScheduleEditorScreen(
                    viewModel = viewModel,
                    campingId = campingId,
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
                    onOpenProgramEditor = {
                        navController.navigate(AppRoute.CampingScheduleProgramEditor(campingId).route)
                    },
                )
            }
            composable(
                route = AppRoutePattern.CampingScheduleProgramEditor,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val scheduleEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(AppRoute.CampingSchedule(campingId).route)
                }
                val viewModel: ScheduleViewModel = hiltViewModel(scheduleEntry)
                ProgramEditorScreen(
                    viewModel = viewModel,
                    campingId = campingId,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(
                route = AppRoutePattern.CampingScheduleProgram,
                arguments = listOf(
                    navArgument(AppRouteArgs.CampingId) { type = NavType.StringType },
                    navArgument(AppRouteArgs.ProgramId) { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val programId = backStackEntry.stringArg(AppRouteArgs.ProgramId)
                val scheduleEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingSchedule(campingId).route)
                    }.getOrNull()
                }
                val viewModel: ScheduleViewModel = if (scheduleEntry != null) {
                    hiltViewModel(scheduleEntry)
                } else {
                    hiltViewModel()
                }
                ProgramDetailScreen(
                    viewModel = viewModel,
                    campingId = campingId,
                    programId = programId,
                    onBack = { navController.popBackStack() },
                    onOpenFoodMenu = {
                        navController.navigate(AppRoute.CampingFoodMenu(campingId).route)
                    },
                )
            }
            // Food menu — register editor BEFORE the food menu list so it doesn't match as list
            composable(
                route = AppRoutePattern.CampingFoodMenuEditor,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val foodMenuEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(AppRoute.CampingFoodMenu(campingId).route)
                }
                val viewModel: FoodMenuViewModel = hiltViewModel(foodMenuEntry)
                val editingEntryId by viewModel.editingEntryId.collectAsState()
                FoodMenuEditorScreen(
                    viewModel = viewModel,
                    campingId = campingId,
                    isEditing = editingEntryId != null,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(
                route = AppRoutePattern.CampingFoodMenu,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                FoodMenuRoute(
                    campingId = campingId,
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
                    onOpenEditor = {
                        navController.navigate(AppRoute.CampingFoodMenuEditor(campingId).route)
                    },
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

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        HorizontalDivider(
            color = colors.divider,
            thickness = 1.dp,
        )
        NavigationBar(
            containerColor = colors.background,
            contentColor = colors.textSecondary,
            tonalElevation = 0.dp,
        ) {
            AppRoute.topLevelTabs.forEach { tab ->
                val selected = selectedTab == tab
                val tabContentDescription = tab.localizedContentDescription()
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTabSelected(tab) },
                    icon = {
                        Icon(
                            imageVector = tab.iconLabel,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    label = {
                        Text(
                            text = tab.localizedLabel(),
                            color = if (selected) colors.ember else colors.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.ember,
                        selectedTextColor = colors.ember,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = colors.textSecondary,
                        unselectedTextColor = colors.textSecondary,
                    ),
                    modifier = Modifier.semantics {
                        contentDescription = tabContentDescription
                    },
                )
            }
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
fun ScreenColumn(
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

@Composable
private fun AppRoute.Tab.localizedLabel(): String =
    stringResource(
        when (this) {
            AppRoute.Home -> R.string.nav_home
            AppRoute.Campings -> R.string.nav_campings
            AppRoute.Announcements -> R.string.nav_announcements
            AppRoute.Profile -> R.string.nav_profile
        },
    )

@Composable
private fun AppRoute.Tab.localizedContentDescription(): String =
    stringResource(
        when (this) {
            AppRoute.Home -> R.string.nav_home_tab
            AppRoute.Campings -> R.string.nav_campings_tab
            AppRoute.Announcements -> R.string.nav_announcements_tab
            AppRoute.Profile -> R.string.nav_profile_tab
        },
    )

@Preview(showBackground = true)
@Composable
private fun CampzoneNavigationShellPreview() {
    CampzoneTheme {
        CampzoneNavigationShell(
            deepLinkInbox = DeepLinkInbox(),
            authenticatedUser = AuthenticatedUser(
                uid = "preview",
                email = "preview@example.com",
                displayName = "Preview Camper",
                photoUrl = null,
                role = UserRole.Guest,
                church = "Paris Central SDA",
                age = 22,
                preferredLanguage = "fr",
                gender = null,
                onboardingCompleted = true,
            ),
            onSignOut = {},
        )
    }
}
