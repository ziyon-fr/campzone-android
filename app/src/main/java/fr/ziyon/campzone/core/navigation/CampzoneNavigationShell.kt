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
import androidx.navigation.NavController
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
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.ui.camping.CampingDetailRoute
import fr.ziyon.campzone.ui.camping.CampingDetailViewModel
import fr.ziyon.campzone.ui.camping.CampingsRoute
import fr.ziyon.campzone.ui.camping.admin.CampingEditorRoute
import fr.ziyon.campzone.ui.camping.registrations.AttendeeProfileRoute
import fr.ziyon.campzone.ui.camping.registrations.CampingAttendeesRoute
import fr.ziyon.campzone.ui.camping.registrations.RegistrationReviewRoute
import fr.ziyon.campzone.ui.camping.register.CampingRegistrationRoute
import fr.ziyon.campzone.ui.chat.CampingChatRoute
import fr.ziyon.campzone.ui.chat.TeamChatRoute
import fr.ziyon.campzone.ui.checkin.CheckInQrPassesRoute
import fr.ziyon.campzone.ui.checkin.CheckInRecordsRoute
import fr.ziyon.campzone.ui.checkin.CheckInScannerRoute
import fr.ziyon.campzone.ui.family.FamilyParticipantsScreen
import fr.ziyon.campzone.ui.home.HomeRoute
import fr.ziyon.campzone.ui.camping.pricing.CampingPricingRoute
import fr.ziyon.campzone.ui.payments.CampingRegistrationPaymentRoute
import fr.ziyon.campzone.ui.profile.ProfileScreen
import fr.ziyon.campzone.ui.profile.ProfileSettingsScreen
import fr.ziyon.campzone.ui.profile.UserDataExportScreen
import fr.ziyon.campzone.ui.profile.badges.AchievementsRoute
import fr.ziyon.campzone.ui.profile.badges.CampingBadgeAwardRoute
import fr.ziyon.campzone.ui.schedule.ProgramDetailScreen
import fr.ziyon.campzone.ui.schedule.ProgramEditorScreen
import fr.ziyon.campzone.ui.schedule.ScheduleEditorScreen
import fr.ziyon.campzone.ui.schedule.ScheduleRoute
import fr.ziyon.campzone.ui.schedule.ScheduleViewModel
import fr.ziyon.campzone.ui.camping.guidelines.CampingGuidelinesRoute
import fr.ziyon.campzone.ui.games.GameDetailRoute
import fr.ziyon.campzone.ui.games.GameEditorRoute
import fr.ziyon.campzone.ui.polls.CampingPollsRoute
import fr.ziyon.campzone.ui.polls.PollDetailRoute
import fr.ziyon.campzone.ui.polls.PollEditorRoute
import fr.ziyon.campzone.ui.polls.PollViewModel
import fr.ziyon.campzone.ui.games.GameViewModel
import fr.ziyon.campzone.ui.games.GamesRoute
import fr.ziyon.campzone.ui.games.PointHistoryRoute
import fr.ziyon.campzone.ui.games.WinnerRevealRoute
import fr.ziyon.campzone.ui.teams.TeamDetailRoute
import fr.ziyon.campzone.ui.teams.TeamEditorRoute
import fr.ziyon.campzone.ui.teams.TeamViewModel
import fr.ziyon.campzone.ui.teams.TeamsRoute
import fr.ziyon.campzone.ui.schedule.food.FoodMenuEditorScreen
import fr.ziyon.campzone.ui.schedule.food.FoodMenuRoute
import fr.ziyon.campzone.ui.schedule.food.FoodMenuViewModel
import fr.ziyon.campzone.ui.songbook.SongDetailRoute
import fr.ziyon.campzone.ui.songbook.SongEditorRoute
import fr.ziyon.campzone.ui.songbook.SongbookRoute
import fr.ziyon.campzone.ui.songbook.SongbookViewModel
import androidx.compose.runtime.remember
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.ui.announcements.AnnouncementComposerRoute
import fr.ziyon.campzone.ui.announcements.AnnouncementDetailRoute
import fr.ziyon.campzone.ui.announcements.AnnouncementViewModel
import fr.ziyon.campzone.ui.announcements.AnnouncementsRoute
import fr.ziyon.campzone.ui.album.CampingAlbumRoute
import fr.ziyon.campzone.ui.admin.AdminToolsRoute
import fr.ziyon.campzone.ui.admin.moderation.ModerationQueueRoute
import fr.ziyon.campzone.ui.notifications.AppNotificationFeedRoute
import fr.ziyon.campzone.ui.notifications.NotificationCampingChannelsScreen
import fr.ziyon.campzone.ui.notifications.NotificationSettingsRoute
import fr.ziyon.campzone.ui.notifications.NotificationTeamChannelsScreen
import fr.ziyon.campzone.ui.transportation.TransportationDashboardRoute
import fr.ziyon.campzone.ui.transportation.TransportationHistoryRoute
import fr.ziyon.campzone.ui.transportation.TransportationScannerRoute
import fr.ziyon.campzone.ui.transportation.TransportationTicketsRoute

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
                    onOpenAnnouncement = { announcementId ->
                        navController.navigate(AppRoute.AnnouncementDetail(announcementId).route)
                    },
                    onOpenNotifications = {
                        navController.navigate(AppRoute.NotificationFeed.route)
                    },
                )
            }
            composable(AppRoute.NotificationFeed.route) {
                AppNotificationFeedRoute(
                    uid = authenticatedUser.uid,
                    role = authenticatedUser.role,
                    onBack = { navController.popBackStack() },
                    onOpenDeepLink = { deepLink -> navController.navigateToDeepLink(deepLink) },
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
            composable(AppRoutePattern.AnnouncementComposer) { backStackEntry ->
                val announcementsEntry = remember(backStackEntry) {
                    navController.announcementComposerOwner(backStackEntry)
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
                    navController.announcementDetailOwner(backStackEntry)
                }
                val announcementViewModel: AnnouncementViewModel = hiltViewModel(announcementsEntry)
                val campings by announcementViewModel.campings.collectAsState()
                val announcementId = backStackEntry.arguments?.getString(AppRouteArgs.AnnouncementId) ?: return@composable
                val permissionUser = PermissionUser(
                    role = authenticatedUser.role,
                    userId = authenticatedUser.uid,
                    church = authenticatedUser.church,
                )
                val evaluator = remember { AppPermissionEvaluator() }
                LaunchedEffect(announcementViewModel) {
                    announcementViewModel.loadIfNeeded()
                }
                LaunchedEffect(campings, authenticatedUser.uid) {
                    announcementViewModel.configureVisibility(
                        currentUser = authenticatedUser,
                        campings = campings,
                        permissionUser = permissionUser,
                        evaluator = evaluator,
                    )
                }
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
                AchievementsRoute(
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(AppRoute.NotificationSettings.route) {
                NotificationSettingsRoute(
                    uid = authenticatedUser.uid,
                    role = authenticatedUser.role,
                    onBack = { navController.popBackStack() },
                    onOpenCampingChannels = {
                        navController.navigate(AppRoute.NotificationCampingChannels.route)
                    },
                    onOpenTeamChannels = {
                        navController.navigate(AppRoute.NotificationTeamChannels.route)
                    },
                )
            }
            composable(AppRoute.NotificationCampingChannels.route) { entry ->
                val parentEntry = remember(entry) {
                    navController.getBackStackEntry(AppRoute.NotificationSettings.route)
                }
                NotificationCampingChannelsScreen(
                    viewModel = hiltViewModel(parentEntry),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(AppRoute.NotificationTeamChannels.route) { entry ->
                val parentEntry = remember(entry) {
                    navController.getBackStackEntry(AppRoute.NotificationSettings.route)
                }
                NotificationTeamChannelsScreen(
                    viewModel = hiltViewModel(parentEntry),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(AppRoute.FamilyParticipants.route) {
                FamilyParticipantsScreen(
                    authenticatedUser = authenticatedUser,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(AppRoute.AdminTools.route) {
                AdminToolsRoute(
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
                    onOpenModerationQueue = {
                        navController.navigate(AppRoute.ModerationQueue.route)
                    },
                )
            }
            composable(AppRoute.ModerationQueue.route) {
                ModerationQueueRoute(
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
                )
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
                    onOpenGuidelines = { campingId ->
                        navController.navigate(AppRoute.CampingGuidelines(campingId).route)
                    },
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
                    onOpenSongbook = { campingId ->
                        navController.navigate(AppRoute.CampingSongbook(campingId).route)
                    },
                    onOpenTeams = { campingId ->
                        navController.navigate(AppRoute.CampingTeams(campingId).route)
                    },
                    onOpenGames = { campingId ->
                        navController.navigate(AppRoute.CampingGames(campingId).route)
                    },
                    onOpenTeamDetail = { campingId, teamId ->
                        navController.navigate(AppRoute.TeamDetail(campingId, teamId).route)
                    },
                    onOpenTeamEditor = { campingId, teamId ->
                        navController.navigate(AppRoute.TeamEditor(campingId, teamId).route)
                    },
                    onOpenRegistrationPayment = { campingId ->
                        navController.navigate(AppRoute.CampingRegistrationPayment(campingId).route)
                    },
                    onOpenPricing = { campingId ->
                        navController.navigate(AppRoute.CampingPricing(campingId).route)
                    },
                    onOpenCheckInScanner = { campingId ->
                        navController.navigate(AppRoute.CheckInScanner(campingId).route)
                    },
                    onOpenCheckInRecords = { campingId ->
                        navController.navigate(AppRoute.CheckInRecords(campingId).route)
                    },
                    onOpenQrPasses = { campingId ->
                        navController.navigate(AppRoute.CheckInQrPasses(campingId).route)
                    },
                    onOpenTransportationTickets = { campingId ->
                        navController.navigate(AppRoute.TransportationTickets(campingId).route)
                    },
                    onOpenTransportationDashboard = { campingId ->
                        navController.navigate(AppRoute.TransportationDashboard(campingId).route)
                    },
                    onOpenBadgeAward = { campingId ->
                        navController.navigate(AppRoute.CampingBadgeAward(campingId).route)
                    },
                    onOpenAlbum = { campingId ->
                        navController.navigate(AppRoute.CampingAlbum(campingId).route)
                    },
                )
            }
            composable(
                route = AppRoutePattern.CampingAlbum,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val campingDetailEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingDetail(campingId).route)
                    }.getOrNull()
                }
                val campingDetailVm: CampingDetailViewModel =
                    if (campingDetailEntry != null) hiltViewModel(campingDetailEntry) else hiltViewModel()
                LaunchedEffect(campingId, authenticatedUser.uid) {
                    campingDetailVm.load(campingId, authenticatedUser)
                }
                val campingDetailState by campingDetailVm.uiState.collectAsState()
                CampingAlbumRoute(
                    campingId = campingId,
                    authenticatedUser = authenticatedUser,
                    canViewAlbum = campingDetailState.canManageAlbumMedia || campingDetailState.isApprovedParticipant,
                    canManageAlbum = campingDetailState.canManageAlbumMedia,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = AppRoutePattern.CheckInScanner,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                CheckInScannerRoute(
                    campingId = campingId,
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
                    onOpenRecords = {
                        navController.navigate(AppRoute.CheckInRecords(campingId).route)
                    },
                )
            }
            composable(
                route = AppRoutePattern.CheckInRecords,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                CheckInRecordsRoute(
                    campingId = campingId,
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
                    onOpenScanner = {
                        navController.navigate(AppRoute.CheckInScanner(campingId).route)
                    },
                )
            }
            composable(
                route = AppRoutePattern.CheckInQrPasses,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val campingDetailEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingDetail(campingId).route)
                    }.getOrNull()
                }
                val campingDetailVm: CampingDetailViewModel =
                    if (campingDetailEntry != null) hiltViewModel(campingDetailEntry) else hiltViewModel()
                LaunchedEffect(campingId, authenticatedUser.uid) {
                    campingDetailVm.load(campingId, authenticatedUser)
                }
                val campingDetailState by campingDetailVm.uiState.collectAsState()
                CheckInQrPassesRoute(
                    state = campingDetailState,
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = AppRoutePattern.TransportationTickets,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                TransportationTicketsRoute(
                    campingId = backStackEntry.stringArg(AppRouteArgs.CampingId),
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = AppRoutePattern.TransportationScanner,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                TransportationScannerRoute(
                    campingId = backStackEntry.stringArg(AppRouteArgs.CampingId),
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
                    onOpenHistory = { campingId ->
                        navController.navigate(AppRoute.TransportationHistory(campingId).route)
                    },
                )
            }
            composable(
                route = AppRoutePattern.TransportationDashboard,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                TransportationDashboardRoute(
                    campingId = campingId,
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
                    onOpenScanner = { navController.navigate(AppRoute.TransportationScanner(it).route) },
                    onOpenHistory = { navController.navigate(AppRoute.TransportationHistory(it).route) },
                )
            }
            composable(
                route = AppRoutePattern.TransportationHistory,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                TransportationHistoryRoute(
                    campingId = backStackEntry.stringArg(AppRouteArgs.CampingId),
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = AppRoutePattern.CampingBadgeAward,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                CampingBadgeAwardRoute(
                    campingId = backStackEntry.stringArg(AppRouteArgs.CampingId),
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
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
                route = AppRoutePattern.CampingPricing,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                CampingPricingRoute(
                    campingId = backStackEntry.stringArg(AppRouteArgs.CampingId),
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
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
            // AnnouncementDetail is registered earlier in the Announcements section above.
            composable(
                route = AppRoutePattern.CampingChat,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val campingDetailEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingDetail(campingId).route)
                    }.getOrNull()
                }
                val campingDetailVm: CampingDetailViewModel =
                    if (campingDetailEntry != null) hiltViewModel(campingDetailEntry) else hiltViewModel()
                LaunchedEffect(campingId, authenticatedUser.uid) {
                    campingDetailVm.load(campingId, authenticatedUser)
                }
                val campingDetailState by campingDetailVm.uiState.collectAsState()
                CampingChatRoute(
                    campingId = campingId,
                    camping = campingDetailState.camping,
                    attendees = campingDetailState.attendees,
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
                )
            }
            // Teams - register TeamEditor before TeamDetail so "team-editor" is not matched as {teamId}
            composable(
                route = AppRoutePattern.TeamEditor,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val teamsEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingTeams(campingId).route)
                    }.getOrNull()
                }
                val viewModel: TeamViewModel = if (teamsEntry != null) hiltViewModel(teamsEntry) else hiltViewModel()
                val campingDetailEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingDetail(campingId).route)
                    }.getOrNull()
                }
                val campingDetailVm: CampingDetailViewModel =
                    if (campingDetailEntry != null) hiltViewModel(campingDetailEntry) else hiltViewModel()
                val campingDetailState by campingDetailVm.uiState.collectAsState()
                TeamEditorRoute(
                    campingId = campingId,
                    teamId = null,
                    camping = campingDetailState.camping,
                    authenticatedUser = authenticatedUser,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = AppRoutePattern.TeamEdit,
                arguments = listOf(
                    navArgument(AppRouteArgs.CampingId) { type = NavType.StringType },
                    navArgument(AppRouteArgs.TeamId) { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val teamId = backStackEntry.stringArg(AppRouteArgs.TeamId)
                val teamsEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingTeams(campingId).route)
                    }.getOrNull()
                }
                val viewModel: TeamViewModel = if (teamsEntry != null) hiltViewModel(teamsEntry) else hiltViewModel()
                val campingDetailEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingDetail(campingId).route)
                    }.getOrNull()
                }
                val campingDetailVm: CampingDetailViewModel =
                    if (campingDetailEntry != null) hiltViewModel(campingDetailEntry) else hiltViewModel()
                val campingDetailState by campingDetailVm.uiState.collectAsState()
                TeamEditorRoute(
                    campingId = campingId,
                    teamId = teamId,
                    camping = campingDetailState.camping,
                    authenticatedUser = authenticatedUser,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = AppRoutePattern.TeamDetail,
                arguments = teamRouteArguments(),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val teamId = backStackEntry.stringArg(AppRouteArgs.TeamId)
                val teamsEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingTeams(campingId).route)
                    }.getOrNull()
                }
                val viewModel: TeamViewModel = if (teamsEntry != null) hiltViewModel(teamsEntry) else hiltViewModel()
                val campingDetailEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingDetail(campingId).route)
                    }.getOrNull()
                }
                val campingDetailVm: CampingDetailViewModel =
                    if (campingDetailEntry != null) hiltViewModel(campingDetailEntry) else hiltViewModel()
                val campingDetailState by campingDetailVm.uiState.collectAsState()
                TeamDetailRoute(
                    teamId = teamId,
                    campingId = campingId,
                    camping = campingDetailState.camping,
                    authenticatedUser = authenticatedUser,
                    approvedAttendees = campingDetailState.attendees
                        .filter { it.registrationStatus == RegistrationApprovalStatus.Approved },
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenEditor = { id ->
                        navController.navigate(AppRoute.TeamEditor(campingId, id).route)
                    },
                    onOpenTeamChat = { cId, tId ->
                        navController.navigate(AppRoute.TeamChat(cId, tId).route)
                    },
                    onOpenPointHistory = { cId, tId ->
                        navController.navigate(AppRoute.PointHistory(cId, tId).route)
                    },
                )
            }
            composable(
                route = AppRoutePattern.CampingTeams,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val campingDetailEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingDetail(campingId).route)
                    }.getOrNull()
                }
                val campingDetailVm: CampingDetailViewModel =
                    if (campingDetailEntry != null) hiltViewModel(campingDetailEntry) else hiltViewModel()
                val campingDetailState by campingDetailVm.uiState.collectAsState()
                val viewModel: TeamViewModel = hiltViewModel()
                TeamsRoute(
                    campingId = campingId,
                    camping = campingDetailState.camping,
                    authenticatedUser = authenticatedUser,
                    approvedAttendees = campingDetailState.attendees
                        .filter { it.registrationStatus == RegistrationApprovalStatus.Approved },
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenTeamDetail = { teamId ->
                        navController.navigate(AppRoute.TeamDetail(campingId, teamId).route)
                    },
                    onOpenTeamEditor = { teamId ->
                        navController.navigate(AppRoute.TeamEditor(campingId, teamId).route)
                    },
                    onOpenGames = {
                        navController.navigate(AppRoute.CampingGames(campingId).route)
                    },
                )
            }
            composable(
                route = AppRoutePattern.CampingGames,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val campingDetailEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingDetail(campingId).route)
                    }.getOrNull()
                }
                val campingDetailVm: CampingDetailViewModel =
                    if (campingDetailEntry != null) hiltViewModel(campingDetailEntry) else hiltViewModel()
                val campingDetailState by campingDetailVm.uiState.collectAsState()
                val viewModel: GameViewModel = hiltViewModel()
                GamesRoute(
                    campingId = campingId,
                    camping = campingDetailState.camping,
                    authenticatedUser = authenticatedUser,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenGameDetail = { gameId ->
                        navController.navigate(AppRoute.GameDetail(campingId, gameId).route)
                    },
                    onOpenGameEditor = { gameId ->
                        navController.navigate(AppRoute.GameEditor(campingId, gameId).route)
                    },
                    onOpenPointHistory = {
                        navController.navigate(AppRoute.PointHistory(campingId).route)
                    },
                    onOpenRevealSettings = {
                        navController.navigate(AppRoute.WinnerReveal(campingId).route)
                    },
                )
            }
            // Games - register static editor route before dynamic {gameId}
            composable(
                route = AppRoutePattern.GameEditor,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val gamesEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingGames(campingId).route)
                    }.getOrNull()
                }
                val viewModel: GameViewModel =
                    if (gamesEntry != null) hiltViewModel(gamesEntry) else hiltViewModel()
                GameEditorRoute(
                    campingId = campingId,
                    gameId = null,
                    authenticatedUser = authenticatedUser,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(
                route = AppRoutePattern.GameEdit,
                arguments = listOf(
                    navArgument(AppRouteArgs.CampingId) { type = NavType.StringType },
                    navArgument(AppRouteArgs.GameId) { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val gameId = backStackEntry.stringArg(AppRouteArgs.GameId)
                val gamesEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingGames(campingId).route)
                    }.getOrNull()
                }
                val viewModel: GameViewModel =
                    if (gamesEntry != null) hiltViewModel(gamesEntry) else hiltViewModel()
                GameEditorRoute(
                    campingId = campingId,
                    gameId = gameId,
                    authenticatedUser = authenticatedUser,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            // WinnerReveal - static sub-route, must be before dynamic {gameId}
            composable(
                route = AppRoutePattern.WinnerReveal,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val campingDetailEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingDetail(campingId).route)
                    }.getOrNull()
                }
                val campingDetailVm: CampingDetailViewModel =
                    if (campingDetailEntry != null) hiltViewModel(campingDetailEntry) else hiltViewModel()
                val campingDetailState by campingDetailVm.uiState.collectAsState()
                val gamesEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingGames(campingId).route)
                    }.getOrNull()
                }
                val viewModel: GameViewModel =
                    if (gamesEntry != null) hiltViewModel(gamesEntry) else hiltViewModel()
                WinnerRevealRoute(
                    campingId = campingId,
                    camping = campingDetailState.camping,
                    authenticatedUser = authenticatedUser,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = AppRoutePattern.GameDetail,
                arguments = listOf(
                    navArgument(AppRouteArgs.CampingId) { type = NavType.StringType },
                    navArgument(AppRouteArgs.GameId) { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val gameId = backStackEntry.stringArg(AppRouteArgs.GameId)
                val campingDetailEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingDetail(campingId).route)
                    }.getOrNull()
                }
                val campingDetailVm: CampingDetailViewModel =
                    if (campingDetailEntry != null) hiltViewModel(campingDetailEntry) else hiltViewModel()
                val campingDetailState by campingDetailVm.uiState.collectAsState()
                val gamesEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingGames(campingId).route)
                    }.getOrNull()
                }
                val viewModel: GameViewModel =
                    if (gamesEntry != null) hiltViewModel(gamesEntry) else hiltViewModel()
                val teamViewModel: TeamViewModel = hiltViewModel()
                LaunchedEffect(campingId) { teamViewModel.loadIfNeeded(campingId) }
                val teams = teamViewModel.teams(campingId)
                GameDetailRoute(
                    gameId = gameId,
                    campingId = campingId,
                    camping = campingDetailState.camping,
                    teams = teams,
                    authenticatedUser = authenticatedUser,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenEditor = { id ->
                        navController.navigate(AppRoute.GameEditor(campingId, id).route)
                    },
                    onOpenPointHistory = {
                        navController.navigate(AppRoute.PointHistory(campingId).route)
                    },
                )
            }
            composable(
                route = AppRoutePattern.TeamChat,
                arguments = teamRouteArguments(),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val teamId = backStackEntry.stringArg(AppRouteArgs.TeamId)
                val campingDetailEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingDetail(campingId).route)
                    }.getOrNull()
                }
                val campingDetailVm: CampingDetailViewModel =
                    if (campingDetailEntry != null) hiltViewModel(campingDetailEntry) else hiltViewModel()
                LaunchedEffect(campingId, authenticatedUser.uid) {
                    campingDetailVm.load(campingId, authenticatedUser)
                }
                val campingDetailState by campingDetailVm.uiState.collectAsState()

                val teamsEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingTeams(campingId).route)
                    }.getOrNull()
                }
                val teamViewModel: TeamViewModel =
                    if (teamsEntry != null) hiltViewModel(teamsEntry) else hiltViewModel()
                LaunchedEffect(campingId) {
                    teamViewModel.loadIfNeeded(campingId)
                }
                val teamsState by teamViewModel.uiState.collectAsState()
                val team = remember(teamsState, teamId, campingId) {
                    teamViewModel.team(teamId, campingId)
                }
                TeamChatRoute(
                    campingId = campingId,
                    teamId = teamId,
                    camping = campingDetailState.camping,
                    team = team,
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = AppRoutePattern.PointHistory,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val campingDetailEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingDetail(campingId).route)
                    }.getOrNull()
                }
                val campingDetailVm: CampingDetailViewModel =
                    if (campingDetailEntry != null) hiltViewModel(campingDetailEntry) else hiltViewModel()
                val campingDetailState by campingDetailVm.uiState.collectAsState()
                val gamesEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingGames(campingId).route)
                    }.getOrNull()
                }
                val viewModel: GameViewModel =
                    if (gamesEntry != null) hiltViewModel(gamesEntry) else hiltViewModel()
                PointHistoryRoute(
                    campingId = campingId,
                    teamId = null,
                    camping = campingDetailState.camping,
                    authenticatedUser = authenticatedUser,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = AppRoutePattern.TeamPointHistory,
                arguments = teamRouteArguments(),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val teamId = backStackEntry.stringArg(AppRouteArgs.TeamId)
                val campingDetailEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingDetail(campingId).route)
                    }.getOrNull()
                }
                val campingDetailVm: CampingDetailViewModel =
                    if (campingDetailEntry != null) hiltViewModel(campingDetailEntry) else hiltViewModel()
                val campingDetailState by campingDetailVm.uiState.collectAsState()
                val gamesEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingGames(campingId).route)
                    }.getOrNull()
                }
                val viewModel: GameViewModel =
                    if (gamesEntry != null) hiltViewModel(gamesEntry) else hiltViewModel()
                PointHistoryRoute(
                    campingId = campingId,
                    teamId = teamId,
                    camping = campingDetailState.camping,
                    authenticatedUser = authenticatedUser,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = AppRoutePattern.CampingPolls,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val campingDetailEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingDetail(campingId).route)
                    }.getOrNull()
                }
                val campingDetailVm: CampingDetailViewModel =
                    if (campingDetailEntry != null) hiltViewModel(campingDetailEntry) else hiltViewModel()
                LaunchedEffect(campingId, authenticatedUser.uid) {
                    campingDetailVm.load(campingId, authenticatedUser)
                }
                val campingDetailState by campingDetailVm.uiState.collectAsState()
                val viewModel: PollViewModel = hiltViewModel()
                CampingPollsRoute(
                    campingId = campingId,
                    camping = campingDetailState.camping,
                    attendees = campingDetailState.attendees,
                    authenticatedUser = authenticatedUser,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenPollDetail = { pollId ->
                        navController.navigate(AppRoute.PollDetail(campingId, pollId).route)
                    },
                    onOpenPollEditor = {
                        navController.navigate(AppRoute.PollEditor(campingId).route)
                    },
                )
            }
            // Polls - register static editor route before dynamic {pollId}
            composable(
                route = AppRoutePattern.PollEditor,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val pollsEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingPolls(campingId).route)
                    }.getOrNull()
                }
                val viewModel: PollViewModel =
                    if (pollsEntry != null) hiltViewModel(pollsEntry) else hiltViewModel()
                PollEditorRoute(
                    pollId = null,
                    campingId = campingId,
                    authenticatedUser = authenticatedUser,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(
                route = AppRoutePattern.PollEdit,
                arguments = listOf(
                    navArgument(AppRouteArgs.CampingId) { type = NavType.StringType },
                    navArgument(AppRouteArgs.PollId) { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val pollId = backStackEntry.stringArg(AppRouteArgs.PollId)
                val pollsEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingPolls(campingId).route)
                    }.getOrNull()
                }
                val viewModel: PollViewModel =
                    if (pollsEntry != null) hiltViewModel(pollsEntry) else hiltViewModel()
                PollEditorRoute(
                    pollId = pollId,
                    campingId = campingId,
                    authenticatedUser = authenticatedUser,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(
                route = AppRoutePattern.PollDetail,
                arguments = listOf(
                    navArgument(AppRouteArgs.CampingId) { type = NavType.StringType },
                    navArgument(AppRouteArgs.PollId) { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val pollId = backStackEntry.stringArg(AppRouteArgs.PollId)
                val campingDetailEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingDetail(campingId).route)
                    }.getOrNull()
                }
                val campingDetailVm: CampingDetailViewModel =
                    if (campingDetailEntry != null) hiltViewModel(campingDetailEntry) else hiltViewModel()
                val campingDetailState by campingDetailVm.uiState.collectAsState()
                val pollsEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingPolls(campingId).route)
                    }.getOrNull()
                }
                val viewModel: PollViewModel =
                    if (pollsEntry != null) hiltViewModel(pollsEntry) else hiltViewModel()
                PollDetailRoute(
                    pollId = pollId,
                    campingId = campingId,
                    camping = campingDetailState.camping,
                    authenticatedUser = authenticatedUser,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenEditor = { id ->
                        navController.navigate(AppRoute.PollEditor(campingId, id).route)
                    },
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
            // Guidelines
            composable(
                route = AppRoutePattern.CampingGuidelines,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                CampingGuidelinesRoute(
                    campingId = campingId,
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
                )
            }
            // Food menu - register editor BEFORE the food menu list so it doesn't match as list
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
            // Songbook - register editor before dynamic {songId}
            composable(
                route = AppRoutePattern.SongEditor,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val songbookEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingSongbook(campingId).route)
                    }.getOrNull()
                }
                val viewModel: SongbookViewModel = if (songbookEntry != null) {
                    hiltViewModel(songbookEntry)
                } else {
                    hiltViewModel()
                }
                SongEditorRoute(
                    campingId = campingId,
                    songId = null,
                    authenticatedUser = authenticatedUser,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(
                route = AppRoutePattern.SongEdit,
                arguments = listOf(
                    navArgument(AppRouteArgs.CampingId) { type = NavType.StringType },
                    navArgument(AppRouteArgs.SongId) { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val songId = backStackEntry.stringArg(AppRouteArgs.SongId)
                val songbookEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingSongbook(campingId).route)
                    }.getOrNull()
                }
                val viewModel: SongbookViewModel = if (songbookEntry != null) {
                    hiltViewModel(songbookEntry)
                } else {
                    hiltViewModel()
                }
                SongEditorRoute(
                    campingId = campingId,
                    songId = songId,
                    authenticatedUser = authenticatedUser,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(
                route = AppRoutePattern.SongDetail,
                arguments = listOf(
                    navArgument(AppRouteArgs.CampingId) { type = NavType.StringType },
                    navArgument(AppRouteArgs.SongId) { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                val songId = backStackEntry.stringArg(AppRouteArgs.SongId)
                val songbookEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AppRoute.CampingSongbook(campingId).route)
                    }.getOrNull()
                }
                val viewModel: SongbookViewModel = if (songbookEntry != null) {
                    hiltViewModel(songbookEntry)
                } else {
                    hiltViewModel()
                }
                SongDetailRoute(
                    campingId = campingId,
                    songId = songId,
                    authenticatedUser = authenticatedUser,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenEditor = { id ->
                        navController.navigate(AppRoute.SongEditor(campingId, id).route)
                    },
                )
            }
            composable(
                route = AppRoutePattern.CampingSongbook,
                arguments = listOf(navArgument(AppRouteArgs.CampingId) { type = NavType.StringType }),
            ) { backStackEntry ->
                val campingId = backStackEntry.stringArg(AppRouteArgs.CampingId)
                SongbookRoute(
                    campingId = campingId,
                    authenticatedUser = authenticatedUser,
                    onBack = { navController.popBackStack() },
                    onOpenSong = { songId ->
                        navController.navigate(AppRoute.SongDetail(campingId, songId).route)
                    },
                    onOpenEditor = { songId ->
                        navController.navigate(AppRoute.SongEditor(campingId, songId).route)
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

private fun NavController.announcementDetailOwner(
    currentEntry: NavBackStackEntry,
): NavBackStackEntry =
    announcementsBackStackEntryOrNull() ?: currentEntry

private fun NavController.announcementComposerOwner(
    currentEntry: NavBackStackEntry,
): NavBackStackEntry =
    announcementsBackStackEntryOrNull() ?: previousBackStackEntry ?: currentEntry

private fun NavController.announcementsBackStackEntryOrNull(): NavBackStackEntry? =
    runCatching { getBackStackEntry(AppRoute.Announcements.route) }.getOrNull()

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
