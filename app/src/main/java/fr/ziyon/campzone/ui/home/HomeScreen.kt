package fr.ziyon.campzone.ui.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzColorPalette
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.Announcement
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.CheckInQrPayload
import fr.ziyon.campzone.data.model.Program
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import fr.ziyon.campzone.ui.camping.campingDateRange
import fr.ziyon.campzone.ui.camping.label
import fr.ziyon.campzone.ui.camping.previewCamping
import fr.ziyon.campzone.ui.transportation.QrCodeImage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.internal.userAgent
import kotlin.math.max

@Composable
fun HomeRoute(
    authenticatedUser: AuthenticatedUser,
    onOpenCamping: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenProgram: (campingId: String, programId: String) -> Unit = { _, _ -> },
    onOpenAnnouncement: (String) -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenRegistration: (String) -> Unit = {},
    onOpenSchedule: (String) -> Unit = {},
    onOpenVenueMap: (String) -> Unit = {},
    onOpenGuidelines: (String) -> Unit = {},
    onOpenPackingChecklist: (String) -> Unit = {},
    onOpenTeams: (String) -> Unit = {},
    onOpenQrPasses: (String) -> Unit = {},
    onOpenFoodMenu: (String) -> Unit = {},
    onOpenSongbook: (String) -> Unit = {},
    onOpenGames: (String) -> Unit = {},
    onOpenChat: (String) -> Unit = {},
    onOpenPolls: (String) -> Unit = {},
    onOpenAlbum: (String) -> Unit = {},
    onOpenPricing: (String) -> Unit = {},
    onOpenSupport: (String) -> Unit = {},
    onOpenTransportation: (String) -> Unit = {},
    onOpenVehicles: (String) -> Unit = {},
    onOpenCheckInScanner: (String) -> Unit = {},
    onOpenEmergency: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    LaunchedEffect(authenticatedUser.uid) {
        viewModel.loadHome(authenticatedUser)
    }
    val state by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    HomeScreen(
        state = state,
        authenticatedUser = authenticatedUser,
        isRefreshing = isRefreshing,
        onOpenCamping = onOpenCamping,
        onOpenProgram = onOpenProgram,
        onOpenAnnouncement = onOpenAnnouncement,
        onOpenNotifications = onOpenNotifications,
        onOpenRegistration = onOpenRegistration,
        onOpenSchedule = onOpenSchedule,
        onOpenVenueMap = onOpenVenueMap,
        onOpenGuidelines = onOpenGuidelines,
        onOpenPackingChecklist = onOpenPackingChecklist,
        onOpenTeams = onOpenTeams,
        onOpenQrPasses = onOpenQrPasses,
        onOpenFoodMenu = onOpenFoodMenu,
        onOpenSongbook = onOpenSongbook,
        onOpenGames = onOpenGames,
        onOpenChat = onOpenChat,
        onOpenPolls = onOpenPolls,
        onOpenAlbum = onOpenAlbum,
        onOpenPricing = onOpenPricing,
        onOpenSupport = onOpenSupport,
        onOpenTransportation = onOpenTransportation,
        onOpenVehicles = onOpenVehicles,
        onOpenCheckInScanner = onOpenCheckInScanner,
        onOpenEmergency = onOpenEmergency,
        onRefresh = { viewModel.refresh(authenticatedUser) },
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    authenticatedUser: AuthenticatedUser,
    isRefreshing: Boolean,
    onOpenCamping: (String) -> Unit,
    onOpenProgram: (campingId: String, programId: String) -> Unit,
    onOpenAnnouncement: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenRegistration: (String) -> Unit,
    onOpenSchedule: (String) -> Unit,
    onOpenVenueMap: (String) -> Unit,
    onOpenGuidelines: (String) -> Unit,
    onOpenPackingChecklist: (String) -> Unit,
    onOpenTeams: (String) -> Unit,
    onOpenQrPasses: (String) -> Unit,
    onOpenFoodMenu: (String) -> Unit,
    onOpenSongbook: (String) -> Unit,
    onOpenGames: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenPolls: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenPricing: (String) -> Unit,
    onOpenSupport: (String) -> Unit,
    onOpenTransportation: (String) -> Unit,
    onOpenVehicles: (String) -> Unit,
    onOpenCheckInScanner: (String) -> Unit,
    onOpenEmergency: (String) -> Unit = {},
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.czColors.background),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (val phase = state.phase) {
                HomePhase.Loading -> CzLoadingView(
                    modifier = Modifier.align(Alignment.Center),
                    message = stringResource(R.string.home_loading_overview),
                )

                is HomePhase.Error -> CzErrorState(
                    title = stringResource(R.string.home_error_title),
                    message = phase.message ?: stringResource(R.string.home_error_message),
                    onRetry = onRetry,
                    retryLabel = stringResource(R.string.common_retry),
                    modifier = Modifier.align(Alignment.Center),
                )

                is HomePhase.Loaded -> {
                    val featured = phase.featuredCamping
                    if (featured == null && phase.upcomingPrograms.isEmpty() && phase.announcements.isEmpty()) {
                        CzEmptyState(
                            title = stringResource(R.string.home_empty_dashboard_title),
                            message = stringResource(R.string.home_empty_dashboard_message),
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.Park,
                                    contentDescription = null,
                                    tint = MaterialTheme.czColors.ember,
                                    modifier = Modifier.size(CzSpacing.xl),
                                )
                            },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        HomeDashboard(
                            featuredCamping = featured,
                            upcomingPrograms = phase.upcomingPrograms,
                            announcements = phase.announcements,
                            livePassInfo = phase.livePassInfo,
                            authenticatedUser = authenticatedUser,
                            onOpenCamping = onOpenCamping,
                            onOpenProgram = onOpenProgram,
                            onOpenAnnouncement = onOpenAnnouncement,
                            onOpenNotifications = onOpenNotifications,
                            onOpenRegistration = onOpenRegistration,
                            onOpenSchedule = onOpenSchedule,
                            onOpenVenueMap = onOpenVenueMap,
                            onOpenGuidelines = onOpenGuidelines,
                            onOpenPackingChecklist = onOpenPackingChecklist,
                            onOpenTeams = onOpenTeams,
                            onOpenQrPasses = onOpenQrPasses,
                            onOpenFoodMenu = onOpenFoodMenu,
                            onOpenSongbook = onOpenSongbook,
                            onOpenGames = onOpenGames,
                            onOpenChat = onOpenChat,
                            onOpenPolls = onOpenPolls,
                            onOpenAlbum = onOpenAlbum,
                            onOpenPricing = onOpenPricing,
                            onOpenSupport = onOpenSupport,
                            onOpenTransportation = onOpenTransportation,
                            onOpenVehicles = onOpenVehicles,
                            onOpenCheckInScanner = onOpenCheckInScanner,
                            onOpenEmergency = onOpenEmergency,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeDashboard(
    featuredCamping: Camping?,
    upcomingPrograms: List<Program>,
    announcements: List<Announcement>,
    livePassInfo: HomeLivePassInfo,
    authenticatedUser: AuthenticatedUser,
    onOpenCamping: (String) -> Unit,
    onOpenProgram: (campingId: String, programId: String) -> Unit,
    onOpenAnnouncement: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenRegistration: (String) -> Unit,
    onOpenSchedule: (String) -> Unit,
    onOpenVenueMap: (String) -> Unit,
    onOpenGuidelines: (String) -> Unit,
    onOpenPackingChecklist: (String) -> Unit,
    onOpenTeams: (String) -> Unit,
    onOpenQrPasses: (String) -> Unit,
    onOpenFoodMenu: (String) -> Unit,
    onOpenSongbook: (String) -> Unit,
    onOpenGames: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenPolls: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenPricing: (String) -> Unit,
    onOpenSupport: (String) -> Unit,
    onOpenTransportation: (String) -> Unit,
    onOpenVehicles: (String) -> Unit,
    onOpenCheckInScanner: (String) -> Unit,
    onOpenEmergency: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLiveMode = featuredCamping?.isLiveForUser(authenticatedUser.uid) == true
    val scheduleCampingId = featuredCamping?.id ?: upcomingPrograms.firstOrNull()?.campingId

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        DashboardHeader(
            authenticatedUser = authenticatedUser,
            onOpenNotifications = onOpenNotifications,
            modifier = Modifier.padding(horizontal = CzSpacing.lg, vertical = CzSpacing.lg),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = CzSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.xl),
        ) {
            if (featuredCamping != null) {
                item(key = "hero") {
                    if (isLiveMode) {
                        CampPassHeroCard(
                            camping = featuredCamping,
                            authenticatedUser = authenticatedUser,
                            livePassInfo = livePassInfo,
                            modifier = Modifier.padding(horizontal = CzSpacing.lg),
                        )
                    } else {
                        FeaturedCampingCard(
                            camping = featuredCamping,
                            onOpen = { onOpenCamping(featuredCamping.id) },
                            onRegister = { onOpenRegistration(featuredCamping.id) },
                            modifier = Modifier.padding(horizontal = CzSpacing.lg),
                        )
                    }
                }

                item(key = "quick-actions") {
                    HomeQuickActionsRow(
                        camping = featuredCamping,
                        authenticatedUser = authenticatedUser,
                        isLive = isLiveMode,
                        onOpenSchedule = onOpenSchedule,
                        onOpenVenueMap = onOpenVenueMap,
                        onOpenGuidelines = onOpenGuidelines,
                        onOpenPackingChecklist = onOpenPackingChecklist,
                        onOpenTeams = onOpenTeams,
                        onOpenQrPasses = onOpenQrPasses,
                        onOpenFoodMenu = onOpenFoodMenu,
                        onOpenSongbook = onOpenSongbook,
                        onOpenGames = onOpenGames,
                        onOpenChat = onOpenChat,
                        onOpenPolls = onOpenPolls,
                        onOpenAlbum = onOpenAlbum,
                        onOpenPricing = onOpenPricing,
                        onOpenSupport = onOpenSupport,
                        onOpenTransportation = onOpenTransportation,
                        onOpenVehicles = onOpenVehicles,
                        onOpenCheckInScanner = onOpenCheckInScanner,
                        onOpenEmergency = onOpenEmergency,
                        modifier = Modifier.padding(horizontal = CzSpacing.lg),
                    )
                }
            }

            item(key = "programs") {
                if (upcomingPrograms.isNotEmpty() && scheduleCampingId != null) {
                    HomeScheduleTimeline(
                        programs = upcomingPrograms,
                        isLive = isLiveMode,
                        campingId = scheduleCampingId,
                        onOpenSchedule = onOpenSchedule,
                        onOpenProgram = onOpenProgram,
                        modifier = Modifier.padding(horizontal = CzSpacing.lg),
                    )
                } else {
                    ProgramsPlaceholder(
                        hasFeaturedCamping = featuredCamping != null,
                        modifier = Modifier.padding(horizontal = CzSpacing.lg),
                    )
                }
            }

            item(key = "announcements") {
                if (announcements.isEmpty()) {
                    HomeEmptyCard(
                        icon = Icons.Filled.Campaign,
                        title = stringResource(R.string.home_announcements_empty_title),
                        message = stringResource(R.string.home_announcements_empty_message),
                        modifier = Modifier.padding(horizontal = CzSpacing.lg),
                    )
                } else {
                    HomeAnnouncementsCarousel(
                        announcements = announcements,
                        onOpenAnnouncement = onOpenAnnouncement,
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    authenticatedUser: AuthenticatedUser,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Park,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.czColors.leaf,
                )

                    Text(
                        text = stringResource(R.string.home_slogan)
                            .uppercase(Locale.forLanguageTag(ComposeLocale.current.toLanguageTag())),
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

            }
            if (authenticatedUser.displayName.isEmpty() || authenticatedUser.preferredDisplayName.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_title),
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                val firstName = authenticatedUser.preferredDisplayName
                    .takeIf { it.isNotBlank() }
                    ?: authenticatedUser.displayName.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.home_title)

                Text(
                    text = firstName
                        .trim()
                        .split(Regex("\\s+"))
                        .firstOrNull()
                        ?: stringResource(R.string.home_title),
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        IconButton(
            onClick = onOpenNotifications,
            modifier = Modifier
                .size(CzSpacing.minTouchTarget)
                .clip(RoundedCornerShape(CzRadius.md))
                .background(MaterialTheme.czColors.surface)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.czColors.divider.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(CzRadius.md),
                )
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = stringResource(R.string.home_notifications_content_description),
                tint = MaterialTheme.czColors.ember,
            )
        }
    }
}

private data class HomeQuickAction(
    val kind: QuickActionKind,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color,
    val onClick: () -> Unit,
)

@Composable
private fun HomeQuickActionsRow(
    camping: Camping,
    authenticatedUser: AuthenticatedUser,
    isLive: Boolean,
    onOpenSchedule: (String) -> Unit,
    onOpenVenueMap: (String) -> Unit,
    onOpenGuidelines: (String) -> Unit,
    onOpenPackingChecklist: (String) -> Unit,
    onOpenTeams: (String) -> Unit,
    onOpenQrPasses: (String) -> Unit,
    onOpenFoodMenu: (String) -> Unit,
    onOpenSongbook: (String) -> Unit,
    onOpenGames: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenPolls: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenPricing: (String) -> Unit,
    onOpenSupport: (String) -> Unit,
    onOpenTransportation: (String) -> Unit,
    onOpenVehicles: (String) -> Unit,
    onOpenCheckInScanner: (String) -> Unit,
    onOpenEmergency: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val usageStore = rememberQuickActionUsageStore()
    val pinStore = rememberQuickActionPinStore()
    var showPinPicker by rememberSaveable { mutableStateOf(false) }
    val availableActions = HOME_QUICK_ACTION_PIN_ORDER.mapNotNull { kind ->
        if (!kind.isAccessible(camping, authenticatedUser)) return@mapNotNull null
        kind.toHomeQuickAction(
            campingId = camping.id,
            colors = colors,
            onOpenSchedule = onOpenSchedule,
            onOpenVenueMap = onOpenVenueMap,
            onOpenGuidelines = onOpenGuidelines,
            onOpenPackingChecklist = onOpenPackingChecklist,
            onOpenTeams = onOpenTeams,
            onOpenQrPasses = onOpenQrPasses,
            onOpenFoodMenu = onOpenFoodMenu,
            onOpenSongbook = onOpenSongbook,
            onOpenGames = onOpenGames,
            onOpenChat = onOpenChat,
            onOpenPolls = onOpenPolls,
            onOpenAlbum = onOpenAlbum,
            onOpenPricing = onOpenPricing,
            onOpenSupport = onOpenSupport,
            onOpenTransportation = onOpenTransportation,
            onOpenVehicles = onOpenVehicles,
            onOpenCheckInScanner = onOpenCheckInScanner,
            onOpenEmergency = onOpenEmergency,
        )
    }
    val actionByKind = availableActions.associateBy(HomeQuickAction::kind)
    val pinnedKinds = pinStore.pinned.filter(actionByKind::containsKey)
    val rankedKinds = usageStore
        .ranked(QuickActionKind.candidates(isLive))
        .filter(actionByKind::containsKey)
    val actions = (pinnedKinds + rankedKinds.filterNot(pinnedKinds::contains))
        .distinct()
        .mapNotNull(actionByKind::get)
        .take(HOME_QUICK_ACTION_TILE_COUNT)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_quick_actions),
                modifier = Modifier.weight(1f),
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = { showPinPicker = true }) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = stringResource(R.string.home_manage_quick_action_pins),
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            actions.forEach { action ->
                HomeQuickActionTile(
                    action = action,
                    isPinned = pinStore.isPinned(action.kind),
                    onClick = {
                        usageStore.record(action.kind)
                        action.onClick()
                    },
                    onLongClick = { showPinPicker = true },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showPinPicker) {
        QuickActionPinPicker(
            options = availableActions,
            pinStore = pinStore,
            onDismiss = { showPinPicker = false },
        )
    }
}

@Composable
private fun HomeQuickActionTile(
    action: HomeQuickAction,
    isPinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CzRadius.lg))
                .background(MaterialTheme.czColors.card)
                .border(
                    width = if (isPinned) 1.dp else 0.5.dp,
                    color = if (isPinned) {
                        MaterialTheme.czColors.accent.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.czColors.divider.copy(alpha = 0.5f)
                    },
                    shape = RoundedCornerShape(CzRadius.lg),
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = CzSpacing.xs, vertical = CzSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(CzRadius.md))
                    .background(action.tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = action.tint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = action.label,
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isPinned) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 5.dp, y = (-5).dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.czColors.card),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = null,
                    tint = MaterialTheme.czColors.accent,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionPinPicker(
    options: List<HomeQuickAction>,
    pinStore: QuickActionPinStore,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.home_pin_quick_actions_title),
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(
                    R.string.home_pin_quick_actions_message,
                    pinStore.pinned.size,
                    QuickActionPinStore.MaximumPinnedCount,
                ),
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (pinStore.pinned.isNotEmpty()) {
                TextButton(onClick = pinStore::clear) {
                    Text(stringResource(R.string.home_remove_all_pins))
                }
            }
            HorizontalDivider(color = MaterialTheme.czColors.divider)
            options.forEach { action ->
                val selected = pinStore.isPinned(action.kind)
                val enabled = pinStore.canPin(action.kind)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CzRadius.md))
                        .clickable(enabled = enabled) { pinStore.toggle(action.kind) }
                        .alpha(if (enabled) 1f else 0.5f)
                        .padding(CzSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null,
                        tint = action.tint,
                    )
                    Text(
                        text = action.label,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.czColors.textPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.PinDrop,
                            contentDescription = stringResource(R.string.home_quick_action_pinned),
                            tint = MaterialTheme.czColors.accent,
                        )
                    }
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.common_done))
            }
        }
    }
}

private const val HOME_QUICK_ACTION_TILE_COUNT = 4

private val HOME_QUICK_ACTION_PIN_ORDER = listOf(
    QuickActionKind.Schedule,
    QuickActionKind.VenueMap,
    QuickActionKind.FoodMenu,
    QuickActionKind.Songbook,
    QuickActionKind.Teams,
    QuickActionKind.Games,
    QuickActionKind.QrPass,
    QuickActionKind.Chat,
    QuickActionKind.Polls,
    QuickActionKind.Album,
    QuickActionKind.Guidelines,
    QuickActionKind.Packing,
    QuickActionKind.Pricing,
    QuickActionKind.Emergency,
    QuickActionKind.Support,
    QuickActionKind.Transportation,
    QuickActionKind.Vehicles,
    QuickActionKind.CheckInScanner,
)

private fun QuickActionKind.isAccessible(
    camping: Camping,
    authenticatedUser: AuthenticatedUser,
): Boolean {
    val permissions = AppPermissionEvaluator()
    val permissionUser = PermissionUser(
        role = authenticatedUser.role,
        userId = authenticatedUser.uid,
        church = authenticatedUser.church,
    )
    val campingContext = CampingPermissionContext(
        organizerLevelType = camping.organizerLevel.type.wireValue,
        organizerLevelValue = camping.organizerLevel.value,
        createdByUid = camping.createdByUid,
    )
    return when (this) {
        QuickActionKind.CheckInScanner ->
            permissions.canManageCheckIns(permissionUser, campingContext)
        QuickActionKind.Transportation,
        QuickActionKind.Vehicles,
        -> permissions.canManageTransportation(permissionUser, campingContext)
        QuickActionKind.Emergency -> true
        else -> true
    }
}

@Composable
private fun QuickActionKind.toHomeQuickAction(
    campingId: String,
    colors: CzColorPalette,
    onOpenSchedule: (String) -> Unit,
    onOpenVenueMap: (String) -> Unit,
    onOpenGuidelines: (String) -> Unit,
    onOpenPackingChecklist: (String) -> Unit,
    onOpenTeams: (String) -> Unit,
    onOpenQrPasses: (String) -> Unit,
    onOpenFoodMenu: (String) -> Unit,
    onOpenSongbook: (String) -> Unit,
    onOpenGames: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenPolls: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenPricing: (String) -> Unit,
    onOpenSupport: (String) -> Unit,
    onOpenTransportation: (String) -> Unit,
    onOpenVehicles: (String) -> Unit,
    onOpenCheckInScanner: (String) -> Unit,
    onOpenEmergency: (String) -> Unit,
): HomeQuickAction? = when (this) {
    QuickActionKind.Schedule -> HomeQuickAction(
        kind = this,
        label = stringResource(R.string.home_action_schedule),
        icon = Icons.Filled.CalendarMonth,
        tint = colors.ember,
        onClick = { onOpenSchedule(campingId) },
    )
    QuickActionKind.VenueMap -> HomeQuickAction(
        kind = this,
        label = stringResource(R.string.home_action_map),
        icon = Icons.Filled.Map,
        tint = colors.leaf,
        onClick = { onOpenVenueMap(campingId) },
    )
    QuickActionKind.Songbook -> HomeQuickAction(
        kind = this,
        label = stringResource(R.string.home_action_songbook),
        icon = Icons.Filled.MusicNote,
        tint = colors.accent,
        onClick = { onOpenSongbook(campingId) },
    )
    QuickActionKind.Teams -> HomeQuickAction(
        kind = this,
        label = stringResource(R.string.home_action_teams),
        icon = Icons.Filled.Groups,
        tint = colors.textSecondary,
        onClick = { onOpenTeams(campingId) },
    )
    QuickActionKind.Games -> HomeQuickAction(
        kind = this,
        label = stringResource(R.string.home_action_games),
        icon = Icons.Filled.SportsEsports,
        tint = colors.gold,
        onClick = { onOpenGames(campingId) },
    )
    QuickActionKind.FoodMenu -> HomeQuickAction(
        kind = this,
        label = stringResource(R.string.home_action_menu),
        icon = Icons.Filled.Restaurant,
        tint = colors.success,
        onClick = { onOpenFoodMenu(campingId) },
    )
    QuickActionKind.Guidelines -> HomeQuickAction(
        kind = this,
        label = stringResource(R.string.home_action_guidelines),
        icon = Icons.Filled.Info,
        tint = colors.gold,
        onClick = { onOpenGuidelines(campingId) },
    )
    QuickActionKind.Packing -> HomeQuickAction(
        kind = this,
        label = stringResource(R.string.packing_quick_action),
        icon = Icons.Filled.Checklist,
        tint = colors.accent,
        onClick = { onOpenPackingChecklist(campingId) },
    )
    QuickActionKind.QrPass -> HomeQuickAction(
        kind = this,
        label = stringResource(R.string.home_action_qr_pass),
        icon = Icons.Filled.QrCode,
        tint = colors.gold,
        onClick = { onOpenQrPasses(campingId) },
    )
    QuickActionKind.Chat -> HomeQuickAction(
        kind = this,
        label = stringResource(R.string.home_action_chat),
        icon = Icons.AutoMirrored.Filled.Chat,
        tint = colors.pine,
        onClick = { onOpenChat(campingId) },
    )
    QuickActionKind.Polls -> HomeQuickAction(
        kind = this,
        label = stringResource(R.string.home_action_polls),
        icon = Icons.Filled.Poll,
        tint = colors.amber,
        onClick = { onOpenPolls(campingId) },
    )
    QuickActionKind.Album -> HomeQuickAction(
        kind = this,
        label = stringResource(R.string.home_action_album),
        icon = Icons.Filled.PhotoLibrary,
        tint = colors.twilight,
        onClick = { onOpenAlbum(campingId) },
    )
    QuickActionKind.Pricing -> HomeQuickAction(
        kind = this,
        label = stringResource(R.string.home_action_fees),
        icon = Icons.Filled.CreditCard,
        tint = colors.twilight,
        onClick = { onOpenPricing(campingId) },
    )
    QuickActionKind.Support -> HomeQuickAction(
        kind = this,
        label = stringResource(R.string.home_action_support),
        icon = Icons.Filled.Campaign,
        tint = colors.accent,
        onClick = { onOpenSupport(campingId) },
    )
    QuickActionKind.Transportation -> HomeQuickAction(
        kind = this,
        label = stringResource(R.string.home_action_transport),
        icon = Icons.Filled.DirectionsBus,
        tint = colors.secondary,
        onClick = { onOpenTransportation(campingId) },
    )
    QuickActionKind.Vehicles -> HomeQuickAction(
        kind = this,
        label = stringResource(R.string.home_action_vehicles),
        icon = Icons.Filled.DirectionsCar,
        tint = colors.accent,
        onClick = { onOpenVehicles(campingId) },
    )
    QuickActionKind.CheckInScanner -> HomeQuickAction(
        kind = this,
        label = stringResource(R.string.home_action_scanner),
        icon = Icons.Filled.QrCodeScanner,
        tint = colors.success,
        onClick = { onOpenCheckInScanner(campingId) },
    )
    QuickActionKind.Emergency -> HomeQuickAction(
        kind = this,
        label = stringResource(R.string.camping_emergency_safety),
        icon = Icons.Filled.Security,
        tint = colors.error,
        onClick = { onOpenEmergency(campingId) },
    )
}

@Composable
private fun ProgramsPlaceholder(
    hasFeaturedCamping: Boolean,
    modifier: Modifier = Modifier,
) {
    HomeEmptyCard(
        icon = if (hasFeaturedCamping) Icons.Filled.CalendarMonth else Icons.Filled.Park,
        title = stringResource(
            if (hasFeaturedCamping) {
                R.string.home_programs_empty_title
            } else {
                R.string.home_no_featured_camping_title
            },
        ),
        message = stringResource(
            if (hasFeaturedCamping) {
                R.string.home_programs_empty_message
            } else {
                R.string.home_no_featured_camping_message
            },
        ),
        modifier = modifier,
    )
}

@Composable
private fun HomeScheduleTimeline(
    programs: List<Program>,
    isLive: Boolean,
    campingId: String,
    onOpenSchedule: (String) -> Unit,
    onOpenProgram: (campingId: String, programId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = rememberHomeClock()
    val dayLabel = programs.firstOrNull()?.startDate?.homeProgramLongDayText().orEmpty()
    val title = if (isLive) {
        stringResource(R.string.home_schedule_today_title, dayLabel)
    } else {
        stringResource(R.string.home_schedule_opening_day_title, dayLabel)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            Icon(
                imageVector = Icons.Filled.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.czColors.ember,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = title,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.home_see_all),
                color = MaterialTheme.czColors.ember,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onOpenSchedule(campingId) },
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            programs.forEach { program ->
                HomeTimelineRow(
                    program = program,
                    now = now,
                    onClick = { onOpenProgram(program.campingId, program.id) },
                )
            }
        }
    }
}

@Composable
private fun HomeTimelineRow(
    program: Program,
    now: Date,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = program.timelineState(now)
    val isNow = state == HomeProgramTimelineState.Now
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = program.startDate.homeProgramTimeText(),
            color = if (state == HomeProgramTimelineState.Past) {
                MaterialTheme.czColors.textTertiary
            } else {
                MaterialTheme.czColors.textPrimary
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isNow) FontWeight.Bold else FontWeight.SemiBold,
            modifier = Modifier
                .width(50.dp)
                .padding(top = if (isNow) 16.dp else 8.dp),
            maxLines = 1,
        )

        Box(
            modifier = Modifier
                .width(24.dp)
                .height(if (isNow) 128.dp else 60.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .padding(vertical = 2.dp)
                    .background(MaterialTheme.czColors.textTertiary.copy(alpha = 0.3f)),
            )
            TimelineDot(
                state = state,
                modifier = Modifier.padding(top = if (isNow) 14.dp else 10.dp),
            )
        }

        if (isNow) {
            NowProgramCard(
                program = program,
                now = now,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = CzSpacing.md, bottom = CzSpacing.md),
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = CzSpacing.md, top = 7.dp, bottom = CzSpacing.md)
                    .alpha(if (state == HomeProgramTimelineState.Past) 0.5f else 1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = program.title.ifBlank { stringResource(R.string.home_program_fallback_title) },
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (program.location.isNotBlank()) {
                    Text(
                        text = program.location,
                        color = MaterialTheme.czColors.textTertiary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineDot(
    state: HomeProgramTimelineState,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val (size, color, borderColor) = when (state) {
        HomeProgramTimelineState.Now -> Triple(14.dp, colors.ember, Color.Transparent)
        HomeProgramTimelineState.Past -> Triple(10.dp, colors.textTertiary, Color.Transparent)
        HomeProgramTimelineState.Upcoming -> Triple(10.dp, colors.background, colors.textTertiary.copy(alpha = 0.5f))
    }
    Box(
        modifier = modifier
            .size(if (state == HomeProgramTimelineState.Now) 22.dp else 14.dp)
            .clip(CircleShape)
            .background(
                if (state == HomeProgramTimelineState.Now) colors.ember.copy(alpha = 0.2f) else Color.Transparent,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (borderColor != Color.Transparent) {
                        Modifier.border(2.dp, borderColor, CircleShape)
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

@Composable
private fun NowProgramCard(
    program: Program,
    now: Date,
    modifier: Modifier = Modifier,
) {
    val fraction = program.elapsedFraction(now)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CzRadius.xl))
            .background(MaterialTheme.czColors.surface)
            .padding(CzSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.czColors.ember),
            )
            Text(
                text = stringResource(R.string.home_program_now)
                    .uppercase(Locale.forLanguageTag(ComposeLocale.current.toLanguageTag())),
                color = MaterialTheme.czColors.ember,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        Text(
            text = program.title.ifBlank { stringResource(R.string.home_program_fallback_title) },
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = if (program.location.isBlank()) {
                stringResource(R.string.home_program_started, program.startDate.startedAgoText(now))
            } else {
                stringResource(
                    R.string.home_program_location_started,
                    program.location,
                    program.startDate.startedAgoText(now),
                )
            },
            color = MaterialTheme.czColors.textTertiary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.czColors.textTertiary.copy(alpha = 0.25f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(CircleShape)
                    .background(MaterialTheme.czColors.ember),
            )
        }
    }
}

@Composable
private fun HomeAnnouncementsCarousel(
    announcements: List<Announcement>,
    onOpenAnnouncement: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val newCount = announcements.count { it.isNewForHome() }
    val colors = MaterialTheme.czColors
    val listState = rememberLazyListState()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CzSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            Icon(
                imageVector = Icons.Filled.Campaign,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.home_announcements_title),
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (newCount > 0) {
                Text(
                    text = pluralStringResource(R.plurals.home_announcements_new_count, newCount, newCount),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.accent)
                        .padding(horizontal = CzSpacing.sm, vertical = 3.dp),
                )
            }
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = CzSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
        ) {
            items(announcements, key = { it.id }) { announcement ->
                HomeAnnouncementCard(
                    announcement = announcement,
                    onClick = { onOpenAnnouncement(announcement.id) },
                )
            }
        }
    }
}

@Composable
private fun HomeAnnouncementCard(
    announcement: Announcement,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDarkMode = isSystemInDarkTheme()
    val colors = MaterialTheme.czColors
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = modifier
            .width(248.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(CzRadius.xxl))
            .background(if (isDarkMode) colors.card else Color.White)
            .border(
                width = 0.5.dp,
                color = colors.divider.copy(alpha = 0.5f),
                shape = RoundedCornerShape(CzRadius.xxl),
            )
            .clickable {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(CzSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(CzRadius.sm))
                    .background(colors.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Campaign,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(15.dp),
                )
            }
            if (announcement.isNewForHome()) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                )
            }
            Box(modifier = Modifier.weight(1f))
            announcement.createdAt?.let { createdAt ->
                Text(
                    text = createdAt.shortRelativeText(),
                    color = colors.textTertiary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Text(
            text = announcement.title.ifBlank { stringResource(R.string.home_announcement_fallback_title) },
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = announcement.summary,
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

private data class HomeCampPass(
    val id: String,
    val qrValue: String,
    val name: String,
    val campName: String,
    val isSelf: Boolean,
    val photoUrl: String?,
    val teamName: String?,
    val lodgingName: String?,
)

@Composable
private fun CampPassHeroCard(
    camping: Camping,
    authenticatedUser: AuthenticatedUser,
    livePassInfo: HomeLivePassInfo,
    modifier: Modifier = Modifier,
) {
    val selfAttendee = camping.homePassAttendee(authenticatedUser.uid)
        ?.takeIf { it.registrationStatus == RegistrationApprovalStatus.Approved }
    val managedPassInfo = livePassInfo.passes.ifEmpty {
        selfAttendee?.let { attendee ->
            listOf(
                HomeCampPassInfo(
                    attendee = attendee,
                    teamName = livePassInfo.teamName,
                    lodgingName = livePassInfo.lodgingName,
                ),
            )
        }.orEmpty()
    }
    val passes = remember(camping.id, camping.title, managedPassInfo, authenticatedUser.photoUrl) {
        managedPassInfo.map { passInfo ->
            val attendee = passInfo.attendee
            val isSelf = attendee.participantKind == RegistrationParticipantKind.SelfParticipant
            HomeCampPass(
                id = attendee.id,
                qrValue = CheckInQrPayload(
                    campingId = camping.id,
                    attendeeId = attendee.id,
                    userId = attendee.userId,
                ).encoded(),
                name = attendee.displayName,
                campName = camping.title,
                isSelf = isSelf,
                photoUrl = attendee.photoUrl ?: authenticatedUser.photoUrl.takeIf { isSelf },
                teamName = passInfo.teamName,
                lodgingName = passInfo.lodgingName,
            )
        }
    }
    val primaryPass = passes.firstOrNull { it.isSelf } ?: passes.firstOrNull()
    val displayName = primaryPass?.name?.takeUnless { it.isBlank() }
        ?: authenticatedUser.preferredDisplayName
    val isCheckedIn = primaryPass?.isSelf == true &&
        livePassInfo.checkInRecord?.attendeeId == primaryPass.id
    val dayText = camping.dayProgressText(isCheckedIn)
    val accentGlow = MaterialTheme.czColors.accent
    val qrValue = primaryPass?.qrValue
    val accessibilityLabel = stringResource(R.string.home_pass_card_accessibility, displayName)
    val isDarkMode = isSystemInDarkTheme()
    var showFullPass by remember(camping.id, primaryPass?.id) { mutableStateOf(false) }

    if (showFullPass && passes.isNotEmpty()) {
        CampPassFullScreenDialog(
            passes = passes,
            initialPassId = primaryPass?.id,
            onDismiss = { showFullPass = false },
        )
    }

    Card(
        onClick = { if (passes.isNotEmpty()) showFullPass = true },
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDarkMode) 0.dp else 16.dp,
                shape = RoundedCornerShape(CzRadius.xxl),
                clip = false,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
            },
        shape = RoundedCornerShape(CzRadius.xxl),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.czColors.espresso),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.czColors.espresso,
                            MaterialTheme.czColors.espressoDeep,
                        ),
                    ),
                )
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentGlow.copy(alpha = 0.45f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width, 0f),
                            radius = size.maxDimension * 0.62f,
                        ),
                    )
                },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CzSpacing.md)
                        .padding(top = CzSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.home_event_pass)
                            .uppercase(Locale.forLanguageTag(ComposeLocale.current.toLanguageTag())),
                        color = MaterialTheme.czColors.cream.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (dayText != null) {
                        CampPassStatusPill(
                            text = dayText,
                            isCheckedIn = isCheckedIn,
                        )
                    }
                }

                Row(
                    modifier = Modifier.padding(CzSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                ) {
                    if (qrValue != null) {
                        CampPassQrTile(qrValue = qrValue)
                    } else {
                        Box(
                            modifier = Modifier
                                .size(112.dp)
                                .clip(RoundedCornerShape(CzRadius.md))
                                .background(Color.White),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.QrCode,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(58.dp),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = displayName,
                            color = MaterialTheme.czColors.cream,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = camping.title,
                            color = MaterialTheme.czColors.cream.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!primaryPass?.teamName.isNullOrBlank() || !primaryPass?.lodgingName.isNullOrBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = CzSpacing.xs),
                                horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                            ) {
                                primaryPass?.teamName?.takeUnless { it.isBlank() }?.let { teamName ->
                                    CampPassChip(
                                        text = teamName,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                }
                                primaryPass?.lodgingName?.takeUnless { it.isBlank() }?.let { lodgingName ->
                                    CampPassChip(
                                        text = lodgingName,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                }
                            }
                        }
                    }
                }

                CampPassDashedDivider(
                    modifier = Modifier.padding(horizontal = CzSpacing.md),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CzSpacing.md, vertical = CzSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCode,
                        contentDescription = null,
                        tint = MaterialTheme.czColors.cream.copy(alpha = 0.55f),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.home_pass_hint),
                        color = MaterialTheme.czColors.cream.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.home_pass_enlarge),
                        color = MaterialTheme.czColors.accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun CampPassStatusPill(
    text: String,
    isCheckedIn: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(
                if (isCheckedIn) {
                    MaterialTheme.czColors.leaf
                } else {
                    Color.White.copy(alpha = 0.14f)
                },
            )
            .padding(horizontal = CzSpacing.sm, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Icon(
            imageVector = if (isCheckedIn) Icons.Filled.Check else Icons.Filled.CalendarMonth,
            contentDescription = null,
            tint = if (isCheckedIn) Color.White else MaterialTheme.czColors.cream,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = text,
            color = if (isCheckedIn) Color.White else MaterialTheme.czColors.cream,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun CampPassQrTile(
    qrValue: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(112.dp)
            .clip(RoundedCornerShape(CzRadius.md))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        QrCodeImage(
            value = qrValue,
            modifier = Modifier.size(96.dp),
        )
    }
}

@Composable
private fun CampPassDashedDivider(
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.czColors.cream.copy(alpha = 0.18f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp),
    ) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(5.dp.toPx(), 4.dp.toPx()),
            ),
        )
    }
}

@Composable
private fun CampPassChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = MaterialTheme.czColors.cream,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(CzRadius.sm))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = CzSpacing.sm, vertical = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CampPassFullScreenDialog(
    passes: List<HomeCampPass>,
    initialPassId: String?,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    val initialPage = passes.indexOfFirst { it.id == initialPassId }
        .takeIf { it >= 0 }
        ?: 0
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { passes.size },
    )
    val hasMultiplePasses = passes.size > 1
    DisposableEffect(Unit) {
        val window = view.context.findActivity()?.window
        val previousBrightness = window?.attributes?.screenBrightness
        window?.setScreenBrightness(1f)
        onDispose {
            if (previousBrightness != null) {
                window?.setScreenBrightness(previousBrightness)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.czColors.espresso,
                            MaterialTheme.czColors.espressoDeep,
                        ),
                    ),
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(vertical = CzSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CzSpacing.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasMultiplePasses) {
                    Text(
                        text = stringResource(
                            R.string.home_pass_position,
                            pagerState.currentPage + 1,
                            passes.size,
                        ),
                        color = MaterialTheme.czColors.cream.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = MaterialTheme.czColors.cream,
                    )
                }
            }

            if (hasMultiplePasses) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { page ->
                    CampPassFullScreenContent(
                        pass = passes[page],
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = CzSpacing.xl),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = CzSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                ) {
                    passes.indices.forEach { index ->
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.czColors.cream.copy(
                                        alpha = if (index == pagerState.currentPage) 0.95f else 0.3f,
                                    ),
                                ),
                        )
                    }
                }
            } else {
                passes.firstOrNull()?.let { pass ->
                    CampPassFullScreenContent(
                        pass = pass,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = CzSpacing.xl),
                    )
                }
            }

            Row(
                modifier = Modifier.padding(top = CzSpacing.xl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCode,
                    contentDescription = null,
                    tint = MaterialTheme.czColors.cream.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.home_pass_hint),
                    color = MaterialTheme.czColors.cream.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun CampPassFullScreenContent(
    pass: HomeCampPass,
    modifier: Modifier = Modifier,
) {
    val kindLabel = stringResource(
        if (pass.isSelf) R.string.home_pass_you else R.string.home_pass_participant,
    )
    val accessibilityLabel = stringResource(
        R.string.home_pass_accessibility,
        kindLabel,
        pass.name,
    )
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = accessibilityLabel
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .padding(horizontal = CzSpacing.md, vertical = CzSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            Icon(
                imageVector = if (pass.isSelf) Icons.Filled.Person else Icons.Filled.ChildCare,
                contentDescription = null,
                tint = MaterialTheme.czColors.cream,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = kindLabel,
                color = MaterialTheme.czColors.cream,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        QrCodeImage(
            value = pass.qrValue,
            modifier = Modifier
                .padding(top = CzSpacing.lg)
                .size(320.dp)
                .clip(RoundedCornerShape(CzRadius.xl))
                .background(Color.White)
                .padding(CzSpacing.lg),
        )

        Row(
            modifier = Modifier.padding(top = CzSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            if (!pass.photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = pass.photoUrl,
                    contentDescription = pass.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape),
                )
            }
            Column(
                horizontalAlignment = if (pass.photoUrl.isNullOrBlank()) Alignment.CenterHorizontally else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            ) {
                Text(
                    text = pass.name,
                    color = MaterialTheme.czColors.cream,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pass.campName,
                    color = MaterialTheme.czColors.cream.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (!pass.teamName.isNullOrBlank() || !pass.lodgingName.isNullOrBlank()) {
            Row(
                modifier = Modifier.padding(top = CzSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            ) {
                pass.teamName?.takeUnless { it.isBlank() }?.let { teamName ->
                    CampPassFullScreenChip(
                        text = teamName,
                        icon = Icons.Filled.Groups,
                    )
                }
                pass.lodgingName?.takeUnless { it.isBlank() }?.let { lodgingName ->
                    CampPassFullScreenChip(
                        text = lodgingName,
                        icon = Icons.Filled.Terrain,
                    )
                }
            }
        }
    }
}

@Composable
private fun CampPassFullScreenChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.czColors.cream,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            color = MaterialTheme.czColors.cream,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Window.setScreenBrightness(brightness: Float) {
    attributes = WindowManager.LayoutParams().also { next ->
        next.copyFrom(attributes)
        next.screenBrightness = brightness
    }
}

@Composable
private fun FeaturedCampingCard(
    camping: Camping,
    onOpen: () -> Unit,
    onRegister: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDarkMode = isSystemInDarkTheme()
    val capacity = camping.participantCapacity ?: 0
    val ratio = if (capacity > 0) {
        (camping.participantCount.toFloat() / capacity.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val fillBrush = homeFeatureFillBrush(ratio)
    val fillAccent = homeFeatureFillAccentColor(ratio)
    val ctaState = camping.featuredCtaState()
    val daysUntilClose = camping.daysUntilRegistrationClose()
    val fillText = when {
        ratio == 0f -> camping.registrationStatus.label()
        ratio >= 1f -> stringResource(R.string.home_fully_booked)
        ratio >= 0.8f -> stringResource(R.string.home_almost_full, (ratio * 100).toInt())
        else -> stringResource(R.string.home_percent_filled, (ratio * 100).toInt())
    }
    val registrationSummary = if (capacity > 0) {
        stringResource(R.string.home_spots_taken_capacity, camping.participantCount, capacity)
    } else {
        stringResource(R.string.home_registered_count, camping.participantCount)
    }
    val recentAvatarUrls = camping.approvedAttendees
        .mapNotNull { attendee -> attendee.photoUrl?.takeUnless { it.isBlank() } }
        .take(3)
    val heroGradient = if (isDarkMode) {
        listOf(MaterialTheme.czColors.night, MaterialTheme.czColors.twilight, Color(0xFF2D1005))
    } else {
        listOf(
            MaterialTheme.czColors.amber,
            MaterialTheme.czColors.primary,
            MaterialTheme.czColors.secondary
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.lg)),
        shape = RoundedCornerShape(CzRadius.lg),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) MaterialTheme.czColors.surface else Color.White,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp),
                ) {
                    if (!camping.logoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = camping.logoUrl,
                            contentDescription = stringResource(R.string.camping_logo_content_description, camping.title),
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Brush.verticalGradient(heroGradient)),
                        )
                        FeaturedMountainBackground(
                            modifier = Modifier
                                .matchParentSize()
                                .alpha(if (isDarkMode) 0.82f else 0.4f),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f to Color.Transparent,
                                        0.38f to Color.Black.copy(alpha = 0.16f),
                                        1f to Color.Black.copy(alpha = 0.78f),
                                    ),
                                ),
                            ),
                    )

                    FeaturedTag(modifier = Modifier.align(Alignment.TopStart))

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(CzSpacing.md)
                            .padding(bottom = CzSpacing.md),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = camping.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    shadow = featuredCampingTextShadow(),
                                ),
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = camping.location.ifBlank {
                                        stringResource(R.string.home_location_pending)
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        shadow = featuredCampingTextShadow(),
                                    ),
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isDarkMode) MaterialTheme.czColors.surface else Color.White)
                        .padding(CzSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.czColors.textSecondary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = campingDateRange(camping.startDate, camping.endDate),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.czColors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Text(
                            text = fillText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = fillAccent,
                            maxLines = 1,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.czColors.divider),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(ratio)
                                .clip(CircleShape)
                                .background(fillBrush),
                        )
                    }

                    StackedAvatarsRow(
                        avatarUrls = recentAvatarUrls,
                        registeredCount = camping.participantCount,
                        summary = registrationSummary,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CzSpacing.md)
                    .padding(bottom = CzSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (daysUntilClose != null && ctaState != FeaturedCampingCta.Closed) {
                    CountdownChip(days = daysUntilClose)
                }
                FeaturedRegisterButton(
                    ctaState = ctaState,
                    onClick = onRegister,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private enum class FeaturedCampingCta {
    Register,
    Waitlist,
    Closed,
}

@Composable
private fun FeaturedTag(modifier: Modifier = Modifier) {
    val accessibilityLabel = stringResource(R.string.home_featured_tag)
    Row(
        modifier = modifier
            .padding(CzSpacing.md)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.92f))
            .padding(CzSpacing.sm)
            .semantics { contentDescription = accessibilityLabel },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.czColors.ember,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun CountdownChip(
    days: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(CzRadius.lg))
            .background(if (isSystemInDarkTheme()) MaterialTheme.czColors.card else MaterialTheme.czColors.background)
            .padding(horizontal = CzSpacing.md),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.home_days_until_close_count, days),
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = stringResource(R.string.home_days_until_close_label),
            color = MaterialTheme.czColors.textTertiary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun FeaturedRegisterButton(
    ctaState: FeaturedCampingCta,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = ctaState != FeaturedCampingCta.Closed
    val borderBrush = Brush.horizontalGradient(
        listOf(
            MaterialTheme.czColors.ember,
            Color(0xFFB05CFF),
            MaterialTheme.czColors.textSecondary,
        ),
    )
    Row(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(CzRadius.lg))
            .background(MaterialTheme.czColors.surface)
            .then(
                if (enabled) {
                    Modifier.border(1.dp, borderBrush, RoundedCornerShape(CzRadius.lg))
                } else {
                    Modifier.border(1.dp, MaterialTheme.czColors.divider, RoundedCornerShape(CzRadius.lg))
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.62f)
            .padding(horizontal = CzSpacing.base),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(
                when (ctaState) {
                    FeaturedCampingCta.Register -> R.string.home_cta_secure_spot
                    FeaturedCampingCta.Waitlist -> R.string.home_cta_join_waitlist
                    FeaturedCampingCta.Closed -> R.string.home_cta_registration_closed
                },
            ),
            color = if (enabled) MaterialTheme.czColors.textPrimary else MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StackedAvatarsRow(
    avatarUrls: List<String>,
    registeredCount: Int,
    summary: String,
    modifier: Modifier = Modifier,
) {
    val visibleUrls = avatarUrls.take(3)
    val extraCount = max(0, registeredCount - visibleUrls.size)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        if (visibleUrls.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                visibleUrls.forEach { url ->
                    ParticipantAvatar(url = url)
                }
                if (extraCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.czColors.background)
                            .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.camping_avatar_extra_count, extraCount),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.czColors.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Text(
            text = summary,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.czColors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ParticipantAvatar(url: String) {
    AsyncImage(
        model = url,
        contentDescription = null,
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun homeFeatureFillBrush(ratio: Float): Brush {
    val colors = MaterialTheme.czColors
    return when {
        ratio < 0.5f -> Brush.horizontalGradient(
            listOf(colors.leaf, colors.leaf.copy(alpha = 0.85f)),
        )
        ratio < 0.75f -> Brush.horizontalGradient(
            listOf(colors.amber, colors.flame.copy(alpha = 0.80f)),
        )
        else -> Brush.horizontalGradient(
            listOf(colors.flame, colors.error),
        )
    }
}

@Composable
private fun homeFeatureFillAccentColor(ratio: Float): Color {
    val colors = MaterialTheme.czColors
    return when {
        ratio < 0.5f -> colors.leaf
        ratio < 0.75f -> colors.amber
        else -> colors.error
    }
}

@Composable
internal fun CampingLogoBadge(
    logoUrl: String?,
    title: String,
    size: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(CzRadius.md))
            .background(MaterialTheme.czColors.secondary.copy(alpha = 0.18f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.55f),
                shape = RoundedCornerShape(CzRadius.md),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = stringResource(R.string.camping_logo_content_description, title),
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Terrain,
                contentDescription = null,
                tint = MaterialTheme.czColors.secondary,
                modifier = Modifier.size((size * 0.48f).dp),
            )
        }
    }
}

@Composable
private fun HomeEmptyCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.lg))
            .background(MaterialTheme.czColors.surface)
            .padding(CzSpacing.md),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.czColors.textSecondary,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private enum class HomeProgramTimelineState {
    Past,
    Now,
    Upcoming,
}

@Composable
private fun rememberHomeClock(): Date {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(30_000)
            now = Date()
        }
    }
    return now
}

private fun Camping.isLiveForUser(userId: String?): Boolean {
    val now = Date()
    if (startDate.after(now) || endDate.before(now)) return false
    return homePassAttendee(userId)
        ?.registrationStatus == RegistrationApprovalStatus.Approved
}

private fun Camping.homePassAttendee(userId: String?): fr.ziyon.campzone.data.model.CampingAttendee? {
    val uid = userId?.takeUnless { it.isBlank() } ?: return null
    return attendees.firstOrNull { attendee ->
        attendee.userId == uid &&
            attendee.participantKind == RegistrationParticipantKind.SelfParticipant
    } ?: attendees.firstOrNull { attendee -> attendee.id == uid }
}

private fun Camping.featuredCtaState(): FeaturedCampingCta =
    when {
        effectiveRegistrationStatus != CampingRegistrationStatus.Open -> FeaturedCampingCta.Closed
        isAtCapacity -> FeaturedCampingCta.Waitlist
        else -> FeaturedCampingCta.Register
    }

private fun Camping.daysUntilRegistrationClose(now: Date = Date()): Int? {
    val deadline = registrationDeadline ?: return null
    if (!deadline.after(now)) return null
    val millis = deadline.time - now.time
    return max((millis / DAY_MILLIS).toInt(), 0)
}

@Composable
private fun Camping.dayProgressText(isCheckedIn: Boolean): String? {
    val calendar = Calendar.getInstance()
    val start = calendar.startOfDay(startDate)
    val end = calendar.startOfDay(endDate)
    val today = calendar.startOfDay(Date())
    val total = max(((end.time - start.time) / DAY_MILLIS).toInt() + 1, 1)
    val current = (((today.time - start.time) / DAY_MILLIS).toInt() + 1).coerceIn(1, total)
    return stringResource(
        if (isCheckedIn) R.string.home_pass_checked_in_day_status else R.string.home_pass_day_status,
        current,
        total,
    )
}

private fun Program.timelineState(now: Date): HomeProgramTimelineState =
    when {
        startDate <= now && endDate.after(now) -> HomeProgramTimelineState.Now
        !endDate.after(now) -> HomeProgramTimelineState.Past
        else -> HomeProgramTimelineState.Upcoming
    }

private fun Program.elapsedFraction(now: Date): Float {
    val total = endDate.time - startDate.time
    if (total <= 0L) return 0f
    return ((now.time - startDate.time).toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

private fun Announcement.isNewForHome(now: Date = Date()): Boolean {
    val created = createdAt ?: return false
    return now.time - created.time <= ANNOUNCEMENT_NEW_WINDOW_MILLIS
}

private fun Date.homeProgramTimeText(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(this)

private fun Date.homeProgramDayText(): String =
    SimpleDateFormat("EEE", Locale.getDefault()).format(this)

private fun Date.homeProgramLongDayText(): String =
    SimpleDateFormat("EEEE", Locale.getDefault()).format(this)

@Composable
private fun Date.shortRelativeText(now: Date = Date()): String {
    val seconds = max(0L, (now.time - time) / 1000L)
    val locale = Locale.forLanguageTag(ComposeLocale.current.toLanguageTag())
    return when {
        seconds < 60L -> stringResource(R.string.home_time_now)
        seconds < 3_600L -> stringResource(R.string.home_time_minutes_short, seconds / 60L)
        seconds < 86_400L -> stringResource(R.string.home_time_hours_short, seconds / 3_600L)
        seconds < 604_800L -> stringResource(R.string.home_time_days_short, seconds / 86_400L)
        else -> SimpleDateFormat("MMM d", locale).format(this)
    }
}

@Composable
private fun Date.startedAgoText(now: Date = Date()): String {
    val seconds = max(0L, (now.time - time) / 1000L)
    return when {
        seconds < 60L -> stringResource(R.string.home_started_now)
        seconds < 3_600L -> stringResource(R.string.home_started_minutes_ago, seconds / 60L)
        seconds < 86_400L -> stringResource(R.string.home_started_hours_ago, seconds / 3_600L)
        else -> stringResource(R.string.home_started_days_ago, seconds / 86_400L)
    }
}

private fun Calendar.startOfDay(date: Date): Date {
    time = date
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    return time
}

private fun featuredCampingTextShadow(): Shadow = Shadow(
    color = Color.Black.copy(alpha = 0.42f),
    offset = Offset(x = 0f, y = 1f),
    blurRadius = 3f,
)

private const val DAY_MILLIS = 86_400_000L
private const val ANNOUNCEMENT_NEW_WINDOW_MILLIS = 48L * 60L * 60L * 1000L

@Composable
@Preview(showBackground = true)
fun HomeScreenPreview() {
    CampzoneTheme {
        val now = System.currentTimeMillis()
        val user = AuthenticatedUser(
            uid = "preview-user",
            email = "lucas@example.org",
            displayName = "Lucas Moreira",
            photoUrl = null,
            role = UserRole.User,
            church = "Lausanne SDA",
            age = 22,
            preferredLanguage = "French",
            gender = null,
            onboardingCompleted = true,
        )
        val camping = previewCamping(
            "summer-2026",
            "Summer Pathfinder Camp",
            2026, 6
        ).copy(registrationDeadline = Date(now + 4 * DAY_MILLIS))
        HomeScreen(
            state = HomeUiState(
                HomePhase.Loaded(
                    featuredCamping = camping,
                    upcomingPrograms = listOf(
                        Program(
                            id = "morning-devotion",
                            campingId = camping.id,
                            campDayId = "${camping.id}-day-preview",
                            title = "Group Intros & Icebreakers",
                            location = "Main Hall",
                            startDate = Date(now - 12 * 60 * 1000),
                            endDate = Date(now + 48 * 60 * 1000),
                        ),
                        Program(
                            id = "team-games",
                            campingId = camping.id,
                            campDayId = "${camping.id}-day-preview",
                            title = "Team games",
                            location = "Sports Field",
                            startDate = Date(now + 3 * 60 * 60 * 1000),
                            endDate = Date(now + 4 * 60 * 60 * 1000),
                        ),
                        Program(
                            id = "evening-worship",
                            campingId = camping.id,
                            campDayId = "${camping.id}-day-preview",
                            title = "Campfire Worship",
                            location = "Main Field",
                            startDate = Date(now + 8 * 60 * 60 * 1000),
                            endDate = Date(now + 9 * 60 * 60 * 1000),
                        ),
                    ),
                    announcements = listOf(
                        Announcement(
                            id = "packing-list",
                            title = "Packing list published",
                            body = "Leaders added the first equipment checklist.",
                            createdAt = Date(now - 2 * 60 * 60 * 1000),
                        ),
                        Announcement(
                            id = "travel-update",
                            title = "Travel coordination",
                            body = "Bus assignments will appear here when registration opens.",
                            createdAt = Date(now - 26 * 60 * 60 * 1000),
                        ),
                        Announcement(
                            id = "carpool",
                            title = "Carpool sign-ups open",
                            body = "Coordinate rides from Lausanne and Bern with other campers.",
                            createdAt = Date(now - 3 * DAY_MILLIS),
                        ),
                    ),
                ),
            ),
            authenticatedUser = user,
            isRefreshing = false,
            onOpenCamping = {},
            onOpenProgram = { _, _ -> },
            onOpenAnnouncement = {},
            onOpenNotifications = {},
            onOpenRegistration = {},
            onOpenSchedule = {},
            onOpenVenueMap = {},
            onOpenGuidelines = {},
            onOpenPackingChecklist = {},
            onOpenTeams = {},
            onOpenQrPasses = {},
            onOpenFoodMenu = {},
            onOpenSongbook = {},
            onOpenGames = {},
            onOpenChat = {},
            onOpenPolls = {},
            onOpenAlbum = {},
            onOpenPricing = {},
            onOpenSupport = {},
            onOpenTransportation = {},
            onOpenVehicles = {},
            onOpenCheckInScanner = {},
            onRefresh = {},
            onRetry = {},
        )
    }
}
