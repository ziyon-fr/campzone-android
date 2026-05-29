package fr.ziyon.campzone.ui.camping

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Festival
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KingBed
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzAvatar
import fr.ziyon.campzone.core.designsystem.CzAvatarSize
import fr.ziyon.campzone.core.designsystem.CzBadge
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzCard
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.navigation.CampzoneDeepLink
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.CampingAgeGroup
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.Team
import fr.ziyon.campzone.data.model.WinnerRevealPolicy
import fr.ziyon.campzone.ui.teams.TeamBadgeView
import fr.ziyon.campzone.ui.teams.TeamViewModel
import fr.ziyon.campzone.ui.teams.TeamsUiState
import fr.ziyon.campzone.ui.teams.toComposeColor
import java.util.Date

@Composable
fun CampingDetailRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenGuidelines: (String) -> Unit = {},
    onOpenSchedule: (String) -> Unit = {},
    onOpenChat: (String) -> Unit = {},
    onOpenPolls: (String) -> Unit = {},
    onOpenEditCamping: (String) -> Unit = {},
    onOpenRegistration: (String) -> Unit = {},
    onOpenRegistrationReview: () -> Unit = {},
    onOpenAttendees: (String) -> Unit = {},
    onOpenFoodMenu: (String) -> Unit = {},
    onOpenSongbook: (String) -> Unit = {},
    onOpenTeams: (String) -> Unit = {},
    onOpenGames: (String) -> Unit = {},
    onOpenTeamDetail: (String, String) -> Unit = { _, _ -> },
    onOpenTeamEditor: (String, String?) -> Unit = { _, _ -> },
    onOpenRegistrationPayment: (String) -> Unit = {},
    onOpenCheckInScanner: (String) -> Unit = {},
    onOpenCheckInRecords: (String) -> Unit = {},
    onOpenQrPasses: (String) -> Unit = {},
    onOpenTransportationTickets: (String) -> Unit = {},
    onOpenTransportationScanner: (String) -> Unit = {},
    onOpenBadgeAward: (String) -> Unit = {},
    onOpenAlbum: (String) -> Unit = {},
    viewModel: CampingDetailViewModel = hiltViewModel(),
    teamViewModel: TeamViewModel = hiltViewModel(),
) {
    LaunchedEffect(campingId) { viewModel.load(campingId, authenticatedUser) }
    LaunchedEffect(campingId) { teamViewModel.loadIfNeeded(campingId) }
    val state by viewModel.uiState.collectAsState()
    val teamsState by teamViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val teams = when (val teamState = teamsState) {
        is TeamsUiState.Loaded -> teamState.teams
        else -> emptyList()
    }
    val myTeam = teams.firstOrNull { team ->
        team.members.any { it.userId == authenticatedUser.uid }
    }
    val teamScoresHidden = state.camping?.let { camping ->
        !state.canRevealWinners &&
            !state.canManageGames &&
            (camping.winnerRevealPolicy ?: WinnerRevealPolicy()).areScoresHidden(camping.endDate)
    } ?: false

    CampingDetailScreen(
        state = state,
        myTeam = myTeam,
        teamScoresHidden = teamScoresHidden,
        onBack = onBack,
        onAttendeeSearchChange = viewModel::updateAttendeeSearch,
        onRetry = { viewModel.load(campingId, authenticatedUser) },
        onShareCamping = { camping ->
            val deepLink = CampzoneDeepLink.Camping(camping.id).canonicalShareUrlOrNull().orEmpty()
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, camping.title)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Join me at ${camping.title} · ${campingDateRange(camping.startDate, camping.endDate)} on Campzone\n$deepLink",
                )
            }
            context.startActivity(Intent.createChooser(shareIntent, camping.title))
        },
        onOpenGuidelines = onOpenGuidelines,
        onOpenSchedule = { id ->
            viewModel.trackScheduleView(id)
            onOpenSchedule(id)
        },
        onOpenChat = onOpenChat,
        onOpenPolls = onOpenPolls,
        onOpenEditCamping = onOpenEditCamping,
        onOpenRegistration = onOpenRegistration,
        onOpenRegistrationReview = onOpenRegistrationReview,
        onOpenAttendees = onOpenAttendees,
        onOpenFoodMenu = onOpenFoodMenu,
        onOpenSongbook = { id ->
            viewModel.trackSongbookView(id)
            onOpenSongbook(id)
        },
        onOpenTeams = { id ->
            viewModel.trackTeamsView(id)
            onOpenTeams(id)
        },
        onOpenGames = onOpenGames,
        onOpenTeamDetail = onOpenTeamDetail,
        onOpenTeamEditor = onOpenTeamEditor,
        onOpenRegistrationPayment = onOpenRegistrationPayment,
        onOpenCheckInScanner = onOpenCheckInScanner,
        onOpenCheckInRecords = onOpenCheckInRecords,
        onOpenQrPasses = onOpenQrPasses,
        onOpenTransportationTickets = onOpenTransportationTickets,
        onOpenTransportationScanner = onOpenTransportationScanner,
        onOpenBadgeAward = onOpenBadgeAward,
        onOpenAlbum = onOpenAlbum,
        modifier = modifier,
    )
}

@Composable
fun CampingDetailScreen(
    state: CampingDetailUiState,
    onBack: () -> Unit,
    onAttendeeSearchChange: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onShareCamping: (Camping) -> Unit = {},
    onOpenEditCamping: (String) -> Unit = {},
    onOpenGuidelines: (String) -> Unit = {},
    onOpenSchedule: (String) -> Unit = {},
    onOpenTeams: (String) -> Unit = {},
    onOpenGames: (String) -> Unit = {},
    onOpenChat: (String) -> Unit = {},
    onOpenPolls: (String) -> Unit = {},
    onOpenRegistration: (String) -> Unit = {},
    onOpenRegistrationReview: () -> Unit = {},
    onOpenAttendees: (String) -> Unit = {},
    onOpenVenueMap: (String) -> Unit = {},
    onOpenFoodMenu: (String) -> Unit = {},
    onOpenSongbook: (String) -> Unit = {},
    myTeam: Team? = null,
    teamScoresHidden: Boolean = false,
    onOpenTeamDetail: (String, String) -> Unit = { _, _ -> },
    onOpenTeamEditor: (String, String?) -> Unit = { _, _ -> },
    onOpenRegistrationPayment: (String) -> Unit = {},
    onOpenCheckInScanner: (String) -> Unit = {},
    onOpenCheckInRecords: (String) -> Unit = {},
    onOpenQrPasses: (String) -> Unit = {},
    onOpenTransportationTickets: (String) -> Unit = {},
    onOpenTransportationScanner: (String) -> Unit = {},
    onOpenBadgeAward: (String) -> Unit = {},
    onOpenAlbum: (String) -> Unit = {},
) {
    val camping = state.camping
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(),
        containerColor = MaterialTheme.czColors.background,
        floatingActionButton = {
            if (camping != null && state.showRegisterCta) {
                RegistrationBottomBar(
                    camping = camping,
                    onOpenRegistration = onOpenRegistration,
                )
            }
        }, floatingActionButtonPosition = FabPosition.Center
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            DetailTopBar(
                camping = camping,
                canEditCamping = state.canEditCamping,
                onBack = onBack,
                onShareCamping = onShareCamping,
                onOpenEditCamping = onOpenEditCamping,
            )

            when {
                state.isLoading -> CzLoadingView(
                    modifier = Modifier.fillMaxSize(),
                    message = stringResource(R.string.camping_loading),
                )

                state.errorMessage != null || camping == null -> Box(
                    Modifier.fillMaxSize(),
                    Alignment.Center,
                ) {
                    CzErrorState(
                        title = stringResource(R.string.camping_error_title),
                        message = state.errorMessage,
                        onRetry = onRetry,
                        retryLabel = stringResource(R.string.common_retry),
                    )
                }

                else -> CampingDetailContent(
                    state = state,
                    camping = camping,
                    myTeam = myTeam,
                    teamScoresHidden = teamScoresHidden,
                    onAttendeeSearchChange = onAttendeeSearchChange,
                    onOpenGuidelines = onOpenGuidelines,
                    onOpenSchedule = onOpenSchedule,
                    onOpenTeams = onOpenTeams,
                    onOpenGames = onOpenGames,
                    onOpenTeamDetail = onOpenTeamDetail,
                    onOpenTeamEditor = onOpenTeamEditor,
                    onOpenChat = onOpenChat,
                    onOpenPolls = onOpenPolls,
                    onOpenRegistrationReview = onOpenRegistrationReview,
                    onOpenAttendees = onOpenAttendees,
                    onOpenVenueMap = onOpenVenueMap,
                    onOpenFoodMenu = onOpenFoodMenu,
                    onOpenSongbook = onOpenSongbook,
                    onOpenRegistrationPayment = onOpenRegistrationPayment,
                    onOpenCheckInScanner = onOpenCheckInScanner,
                    onOpenCheckInRecords = onOpenCheckInRecords,
                    onOpenQrPasses = onOpenQrPasses,
                    onOpenTransportationTickets = onOpenTransportationTickets,
                    onOpenTransportationScanner = onOpenTransportationScanner,
                    onOpenBadgeAward = onOpenBadgeAward,
                    onOpenAlbum = onOpenAlbum,
                )
            }
        }
    }
}

@Composable
private fun DetailTopBar(
    camping: Camping?,
    canEditCamping: Boolean,
    onBack: () -> Unit,
    onShareCamping: (Camping) -> Unit,
    onOpenEditCamping: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CzSpacing.sm, vertical = CzSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = MaterialTheme.czColors.textPrimary,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (camping != null) {
            IconButton(onClick = { onShareCamping(camping) }) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = stringResource(R.string.camping_share),
                    tint = MaterialTheme.czColors.textPrimary,
                )
            }
            if (canEditCamping) {
                IconButton(onClick = { onOpenEditCamping(camping.id) }) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.camping_edit),
                        tint = MaterialTheme.czColors.textPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CampingDetailContent(
    state: CampingDetailUiState,
    camping: Camping,
    onAttendeeSearchChange: (String) -> Unit,
    onOpenGuidelines: (String) -> Unit,
    onOpenSchedule: (String) -> Unit,
    onOpenTeams: (String) -> Unit,
    onOpenGames: (String) -> Unit,
    myTeam: Team?,
    teamScoresHidden: Boolean,
    onOpenTeamDetail: (String, String) -> Unit,
    onOpenTeamEditor: (String, String?) -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenPolls: (String) -> Unit,
    onOpenRegistrationReview: () -> Unit,
    onOpenAttendees: (String) -> Unit,
    onOpenVenueMap: (String) -> Unit,
    onOpenFoodMenu: (String) -> Unit = {},
    onOpenSongbook: (String) -> Unit = {},
    onOpenRegistrationPayment: (String) -> Unit = {},
    onOpenCheckInScanner: (String) -> Unit = {},
    onOpenCheckInRecords: (String) -> Unit = {},
    onOpenQrPasses: (String) -> Unit = {},
    onOpenTransportationTickets: (String) -> Unit = {},
    onOpenTransportationScanner: (String) -> Unit = {},
    onOpenBadgeAward: (String) -> Unit = {},
    onOpenAlbum: (String) -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableStateOf(CampingDetailTab.Overview) }
    val disabledAlpha = if (camping.registrationStatus == CampingRegistrationStatus.Cancelled) 0.5f else 1f

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.czColors.background)
            .alpha(disabledAlpha),
        contentPadding = PaddingValues(
            start = CzSpacing.lg,
            end = CzSpacing.lg,
            top = CzSpacing.sm,
            bottom = if (state.showRegisterCta) 112.dp else CzSpacing.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xl),
    ) {
        item(key = "header") {
            HeaderSection(
                camping = camping,
                onOpenVenueMap = onOpenVenueMap,
            )
        }

        item(key = "tabs") {
            DetailTabSelector(
                selectedTab = selectedTab,
                onSelected = { selectedTab = it },
            )
        }

        when (selectedTab) {
            CampingDetailTab.Overview -> {
                item(key = "description") {
                    DescriptionCard(
                        camping = camping,
                        onOpenGuidelines = onOpenGuidelines,
                    )
                }
                item(key = "event-info") {
                    EventInfoSection(camping)
                }
                item(key = "registration") {
                    RegistrationCard(
                        state = state,
                        camping = camping,
                        onOpenRegistrationReview = onOpenRegistrationReview,
                    )
                }
                item(key = "attendees") {
                    AttendeeSection(
                        state = state,
                        camping = camping,
                        onOpenAttendees = onOpenAttendees,
                    )
                }
            }

            CampingDetailTab.Schedule -> {
                item(key = "schedule") {
                    ScheduleSection(
                        camping = camping,
                        onOpenSchedule = onOpenSchedule,
                    )
                }
            }

            CampingDetailTab.Teams -> {
                item(key = "teams") {
                    TeamsSection(
                        state = state,
                        camping = camping,
                        myTeam = myTeam,
                        teamScoresHidden = teamScoresHidden,
                        onOpenTeams = onOpenTeams,
                        onOpenGames = onOpenGames,
                        onOpenTeamDetail = onOpenTeamDetail,
                        onOpenTeamEditor = onOpenTeamEditor,
                    )
                }
            }
        }

        item(key = "resources") {
            ResourcesSection(
                state = state,
                camping = camping,
                onOpenChat = onOpenChat,
                onOpenPolls = onOpenPolls,
                onOpenFoodMenu = onOpenFoodMenu,
                onOpenSongbook = onOpenSongbook,
                onOpenRegistrationPayment = onOpenRegistrationPayment,
                onOpenCheckInScanner = onOpenCheckInScanner,
                onOpenCheckInRecords = onOpenCheckInRecords,
                onOpenQrPasses = onOpenQrPasses,
                onOpenTransportationTickets = onOpenTransportationTickets,
                onOpenTransportationScanner = onOpenTransportationScanner,
                onOpenBadgeAward = onOpenBadgeAward,
                onOpenAlbum = onOpenAlbum,
            )
        }

        // Deferred Phase D operations remain hidden until their Android
        // destinations exist.
    }
}

@Composable
private fun HeaderSection(
    camping: Camping,
    @Suppress("UNUSED_PARAMETER") onOpenVenueMap: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = CzSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        CampingLogoBadge(camping = camping, size = 64.dp)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Text(
                text = camping.title,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            ) {
                StatusPill(camping.registrationStatus)
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.czColors.ember,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    text = camping.location,
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

@Composable
private fun CampingLogoBadge(
    camping: Camping,
    size: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(CzRadius.lg))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.czColors.ember.copy(alpha = 0.30f),
                        MaterialTheme.czColors.amber.copy(alpha = 0.18f),
                    ),
                ),
            )
            .border(1.dp, MaterialTheme.czColors.divider, RoundedCornerShape(CzRadius.lg)),
        contentAlignment = Alignment.Center,
    ) {
        if (!camping.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = camping.logoUrl,
                contentDescription = stringResource(R.string.camping_logo_content_description, camping.title),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Festival,
                contentDescription = null,
                tint = MaterialTheme.czColors.ember,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun StatusPill(status: CampingRegistrationStatus) {
    val color = status.statusColor()
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = CzSpacing.sm, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = status.shortLabel(),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DetailTabSelector(
    selectedTab: CampingDetailTab,
    onSelected: (CampingDetailTab) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.full),
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            CampingDetailTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(CzRadius.full))
                        .background(
                            if (selected) MaterialTheme.czColors.ember.copy(alpha = 0.16f)
                            else Color.Transparent,
                        )
                        .clickable { onSelected(tab) }
                        .padding(horizontal = CzSpacing.sm, vertical = CzSpacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tab.label(),
                        color = if (selected) MaterialTheme.czColors.ember else MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DescriptionCard(
    camping: Camping,
    onOpenGuidelines: (String) -> Unit,
) {
    CzCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(CzSpacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
            Text(
                text = camping.description,
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
            )
            TextButton(
                onClick = { onOpenGuidelines(camping.id) },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    text = stringResource(R.string.camping_read_guidelines),
                    color = MaterialTheme.czColors.ember,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(CzSpacing.xs))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.czColors.ember,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun EventInfoSection(camping: Camping) {
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        DetailSectionHeader(
            title = stringResource(R.string.camping_event_info),
            icon = Icons.Filled.Info,
        )
        Surface(
            color = MaterialTheme.czColors.surface,
            shape = RoundedCornerShape(CzRadius.lg),
        ) {
            Column {
                DetailInfoRow(
                    label = stringResource(R.string.camping_dates),
                    value = campingDateRange(camping.startDate, camping.endDate),
                    icon = Icons.Filled.CalendarMonth,
                )
                DetailDivider()
                DetailInfoRow(
                    label = stringResource(R.string.camping_location),
                    value = camping.location,
                    icon = Icons.Filled.LocationOn,
                )
                DetailDivider()
                DetailInfoRow(
                    label = stringResource(R.string.camping_organizer),
                    value = organizerDisplayName(camping),
                    icon = Icons.Filled.AccountCircle,
                )
            }
        }
    }
}

@Composable
private fun DetailInfoRow(
    label: String,
    value: String,
    icon: ImageVector,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.czColors.ember,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RegistrationCard(
    state: CampingDetailUiState,
    camping: Camping,
    onOpenRegistrationReview: () -> Unit,
) {
    val cream = Color(0xFFFFF4E0)

    Surface(
        color = MaterialTheme.czColors.pine,
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.camping_registration),
                        color = MaterialTheme.czColors.amber,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = registrationSummary(state, camping),
                        color = cream,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (state.canApproveRegistrations && state.pendingAttendeeCount > 0) {
                        TextButton(
                            onClick = onOpenRegistrationReview,
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.camping_pending_registration_count,
                                    state.pendingAttendeeCount,
                                ),
                                color = MaterialTheme.czColors.warning,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.czColors.warning,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.czColors.amber,
                    modifier = Modifier.size(28.dp),
                )
            }

            if (state.userRegistration != null) {
                UserRegistrationStatusRow(state.userRegistration)
            } else {
                ParticipantPreview(count = state.approvedAttendeeCount)
            }

            if (camping.isPaid) {
                HorizontalDivider(color = cream.copy(alpha = 0.20f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CreditCard,
                        contentDescription = null,
                        tint = MaterialTheme.czColors.amber,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.camping_registration_fee_available),
                        color = cream,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun UserRegistrationStatusRow(registration: CampingAttendee) {
    val cream = Color(0xFFFFF4E0)
    val (icon, tint, text) = when (registration.registrationStatus) {
        RegistrationApprovalStatus.Approved -> Triple(
            Icons.Filled.CheckCircle,
            MaterialTheme.czColors.success,
            stringResource(R.string.camping_you_are_registered),
        )

        RegistrationApprovalStatus.Waitlisted -> Triple(
            Icons.Filled.Schedule,
            MaterialTheme.czColors.textSecondary,
            stringResource(R.string.camping_you_are_waitlisted),
        )

        RegistrationApprovalStatus.Rejected -> Triple(
            Icons.Filled.Lock,
            MaterialTheme.czColors.error,
            stringResource(R.string.camping_registration_rejected),
        )

        RegistrationApprovalStatus.Pending -> Triple(
            Icons.Filled.Schedule,
            MaterialTheme.czColors.warning,
            stringResource(R.string.camping_registration_pending),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = text,
                color = cream,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (registration.registrationStatus == RegistrationApprovalStatus.Waitlisted) {
            Text(
                text = stringResource(R.string.camping_waitlist_notice),
                color = cream.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun ParticipantPreview(count: Int) {
    val cream = Color(0xFFFFF4E0)
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
            repeat(minOf(3, count)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.czColors.ember.copy(alpha = 0.50f),
                                    MaterialTheme.czColors.amber.copy(alpha = 0.50f),
                                ),
                            ),
                        )
                        .border(2.dp, MaterialTheme.czColors.pine, CircleShape),
                )
            }
        }
        Text(
            text = if (count > 0) {
                stringResource(R.string.camping_pathfinder_count, count)
            } else {
                stringResource(R.string.camping_be_first_to_register)
            },
            color = cream,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = if (count > 0) CzSpacing.xl else 0.dp),
        )
    }
}

@Composable
private fun AttendeeSection(
    state: CampingDetailUiState,
    camping: Camping,
    onOpenAttendees: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailSectionHeader(
                title = stringResource(R.string.camping_attendees),
                icon = Icons.Filled.Groups,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (state.canViewAttendees && state.recentAttendees.isNotEmpty()) {
                TextButton(onClick = { onOpenAttendees(camping.id) }) {
                    Text(
                        text = stringResource(R.string.camping_see_all),
                        color = MaterialTheme.czColors.ember,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.czColors.ember,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        when {
            !state.canViewAttendees -> LockedNotice(
                text = stringResource(R.string.camping_attendees_locked_ios),
            )

            state.recentAttendees.isEmpty() -> Surface(
                color = MaterialTheme.czColors.surface,
                shape = RoundedCornerShape(CzRadius.md),
            ) {
                Text(
                    text = stringResource(R.string.camping_no_attendees),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(CzSpacing.md),
                )
            }

            else -> Surface(
                color = MaterialTheme.czColors.surface,
                shape = RoundedCornerShape(CzRadius.lg),
            ) {
                Column {
                    state.recentAttendees.forEachIndexed { index, attendee ->
                        DetailAttendeeRow(attendee)
                        if (index < state.recentAttendees.lastIndex) {
                            DetailDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailAttendeeRow(attendee: CampingAttendee) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CzAvatar(
            imageUrl = attendee.photoUrl,
            contentDescription = attendee.displayName,
            initials = attendee.displayName,
            size = CzAvatarSize.Small,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = attendee.displayName,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${attendee.church} · ${attendee.ageGroup.label()}",
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (attendee.languages.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                ) {
                    attendee.languages.forEach { language ->
                        LanguageChip(language)
                    }
                }
            }
        }

        ApprovalStatusPill(attendee.registrationStatus)
    }
}

@Composable
private fun LanguageChip(language: String) {
    Surface(
        color = MaterialTheme.czColors.background,
        shape = RoundedCornerShape(CzRadius.full),
    ) {
        Text(
            text = language,
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = CzSpacing.xs, vertical = 3.dp),
        )
    }
}

@Composable
private fun ApprovalStatusPill(status: RegistrationApprovalStatus) {
    val color = status.statusColor()
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(CzRadius.full),
    ) {
        Text(
            text = status.label(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = CzSpacing.sm, vertical = 4.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun ScheduleSection(
    camping: Camping,
    onOpenSchedule: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        DetailSectionHeader(
            title = stringResource(R.string.camping_schedule),
            icon = Icons.Filled.CalendarMonth,
        )
        DetailResourceButton(
            title = stringResource(R.string.camping_view_schedule),
            subtitle = stringResource(R.string.camping_view_schedule_subtitle),
            icon = Icons.Filled.CalendarMonth,
            onClick = { onOpenSchedule(camping.id) },
        )
    }
}

@Composable
private fun TeamsSection(
    state: CampingDetailUiState,
    camping: Camping,
    myTeam: Team?,
    teamScoresHidden: Boolean,
    onOpenTeams: (String) -> Unit,
    onOpenGames: (String) -> Unit,
    onOpenTeamDetail: (String, String) -> Unit,
    onOpenTeamEditor: (String, String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        DetailSectionHeader(
            title = stringResource(R.string.camping_teams),
            icon = Icons.Filled.Groups,
        )
        if (myTeam != null) {
            MyTeamShortcut(
                team = myTeam,
                scoresHidden = teamScoresHidden,
                onClick = { onOpenTeamDetail(camping.id, myTeam.id) },
            )
        }
        DetailResourceButton(
            title = stringResource(R.string.camping_teams_ranking),
            subtitle = stringResource(R.string.camping_teams_ranking_subtitle),
            icon = Icons.Filled.Groups,
            onClick = { onOpenTeams(camping.id) },
        )
        DetailResourceButton(
            title = stringResource(R.string.camping_games_points),
            subtitle = stringResource(R.string.camping_games_points_subtitle),
            icon = Icons.Filled.SportsEsports,
            onClick = { onOpenGames(camping.id) },
        )
        if (state.canManageTeams) {
            DetailResourceButton(
                title = stringResource(R.string.camping_create_team),
                subtitle = stringResource(R.string.camping_create_team_subtitle),
                icon = Icons.Filled.PersonAdd,
                onClick = { onOpenTeamEditor(camping.id, null) },
            )
        }
    }
}

@Composable
private fun MyTeamShortcut(
    team: Team,
    scoresHidden: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val teamColor = team.colorHex.toComposeColor() ?: colors.ember

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.lg))
            .background(colors.surface)
            .border(1.dp, teamColor.copy(alpha = 0.35f), RoundedCornerShape(CzRadius.lg))
            .clickable(onClick = onClick)
            .padding(CzSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamBadgeView(team = team, size = 40)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.teams_my_team),
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = team.name,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (scoresHidden) {
            Icon(
                imageVector = Icons.Filled.VisibilityOff,
                contentDescription = stringResource(R.string.teams_scores_hidden),
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                text = "${team.totalScore} pts",
                color = teamColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ResourcesSection(
    state: CampingDetailUiState,
    camping: Camping,
    onOpenChat: (String) -> Unit,
    onOpenPolls: (String) -> Unit,
    onOpenFoodMenu: (String) -> Unit = {},
    onOpenSongbook: (String) -> Unit = {},
    onOpenRegistrationPayment: (String) -> Unit = {},
    onOpenCheckInScanner: (String) -> Unit = {},
    onOpenCheckInRecords: (String) -> Unit = {},
    onOpenQrPasses: (String) -> Unit = {},
    onOpenTransportationTickets: (String) -> Unit = {},
    onOpenTransportationScanner: (String) -> Unit = {},
    onOpenBadgeAward: (String) -> Unit = {},
    onOpenAlbum: (String) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
        DetailSectionHeader(
            title = stringResource(R.string.camping_resources),
            icon = Icons.Filled.WorkspacePremium,
        )

        if (state.hasManagedRegistration) {
            PrimaryPassResource(onClick = { onOpenQrPasses(camping.id) })
        }

        val campLifeResources = buildList {
            add(
                DetailResource(
                    title = stringResource(R.string.camping_songbook),
                    subtitle = stringResource(R.string.camping_songbook_subtitle),
                    icon = Icons.Filled.MusicNote,
                    accent = MaterialTheme.czColors.ember,
                    onClick = { onOpenSongbook(camping.id) },
                ),
            )
            if (state.isApprovedParticipant || state.canManageAlbumMedia) {
                add(
                    DetailResource(
                        title = stringResource(R.string.camping_album),
                        subtitle = stringResource(R.string.camping_album_subtitle),
                        icon = Icons.Filled.PhotoLibrary,
                        accent = MaterialTheme.czColors.twilight,
                        onClick = { onOpenAlbum(camping.id) },
                    ),
                )
                add(
                    DetailResource(
                        title = stringResource(R.string.camping_chat),
                        subtitle = stringResource(R.string.camping_chat_subtitle),
                        icon = Icons.AutoMirrored.Filled.Chat,
                        accent = MaterialTheme.czColors.pine,
                        onClick = { onOpenChat(camping.id) },
                    ),
                )
                add(
                    DetailResource(
                        title = stringResource(R.string.camping_polls),
                        subtitle = stringResource(R.string.camping_polls_subtitle),
                        icon = Icons.Filled.Poll,
                        accent = MaterialTheme.czColors.amber,
                        onClick = { onOpenPolls(camping.id) },
                    ),
                )
                add(
                    DetailResource(
                        title = stringResource(R.string.camping_food_menu),
                        subtitle = stringResource(R.string.camping_food_menu_subtitle),
                        icon = Icons.Filled.Restaurant,
                        accent = MaterialTheme.czColors.success,
                        onClick = { onOpenFoodMenu(camping.id) },
                    ),
                )
            }
            if (state.hasPendingRegistrationPayment) {
                add(
                    DetailResource(
                        title = stringResource(R.string.camping_fees_payments),
                        subtitle = stringResource(R.string.camping_fees_payments_subtitle),
                        icon = Icons.Filled.CreditCard,
                        accent = MaterialTheme.czColors.twilight,
                        onClick = { onOpenRegistrationPayment(camping.id) },
                    ),
                )
            }
            if (state.hasManagedRegistration) {
                add(
                    DetailResource(
                        title = stringResource(R.string.camping_transportation),
                        subtitle = stringResource(R.string.camping_transportation_subtitle),
                        icon = Icons.Filled.DirectionsBus,
                        accent = MaterialTheme.czColors.pine,
                        onClick = { onOpenTransportationTickets(camping.id) },
                    ),
                )
            }
        }

        ResourceGroup(
            title = stringResource(R.string.camping_camp_life),
            icon = Icons.Filled.EmojiEvents,
            resources = campLifeResources,
        )

        val operationsResources = buildList {
            if (state.canManageCheckIns) {
                add(
                    DetailResource(
                        title = stringResource(R.string.camping_check_in_scanner),
                        subtitle = stringResource(R.string.camping_check_in_scanner_subtitle),
                        icon = Icons.Filled.QrCode,
                        accent = MaterialTheme.czColors.success,
                        onClick = { onOpenCheckInScanner(camping.id) },
                    ),
                )
                add(
                    DetailResource(
                        title = stringResource(R.string.checkin_records_title),
                        subtitle = stringResource(R.string.checkin_records_subtitle),
                        icon = Icons.Filled.CheckCircle,
                        accent = MaterialTheme.czColors.ember,
                        onClick = { onOpenCheckInRecords(camping.id) },
                    ),
                )
            }
            if (state.canManageTransportation) {
                add(
                    DetailResource(
                        title = stringResource(R.string.transportation_scanner_title),
                        subtitle = stringResource(R.string.transportation_scanner_subtitle),
                        icon = Icons.Filled.DirectionsBus,
                        accent = MaterialTheme.czColors.pine,
                        onClick = { onOpenTransportationScanner(camping.id) },
                    ),
                )
            }
            if (state.canAwardAchievements) {
                add(
                    DetailResource(
                        title = stringResource(R.string.camping_award_badges),
                        subtitle = stringResource(R.string.camping_award_badges_subtitle),
                        icon = Icons.Filled.WorkspacePremium,
                        accent = MaterialTheme.czColors.amber,
                        onClick = { onOpenBadgeAward(camping.id) },
                    ),
                )
            }
        }

        ResourceGroup(
            title = stringResource(R.string.camping_operations),
            icon = Icons.Filled.Security,
            resources = operationsResources,
        )
    }
}

@Composable
private fun PrimaryPassResource(onClick: () -> Unit) {
    val cream = Color(0xFFFFF4E0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.lg))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.czColors.pine,
                        MaterialTheme.czColors.ember.copy(alpha = 0.92f),
                    ),
                ),
            )
            .clickable { onClick() }
            .padding(CzSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(CzRadius.md))
                .background(cream.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.QrCode,
                contentDescription = null,
                tint = cream,
                modifier = Modifier.size(26.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = stringResource(R.string.camping_my_qr_passes),
                color = cream,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.camping_my_qr_passes_subtitle),
                color = cream.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = cream.copy(alpha = 0.75f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ResourceGroup(
    title: String,
    icon: ImageVector,
    resources: List<DetailResource>,
) {
    if (resources.isEmpty()) return

    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CzSpacing.md)
                    .padding(top = CzSpacing.md, bottom = CzSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(CzRadius.sm))
                        .background(MaterialTheme.czColors.ember.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.czColors.ember,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = title,
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            resources.forEachIndexed { index, resource ->
                ResourceRow(resource)
                if (index < resources.lastIndex) {
                    DetailDivider()
                }
            }
        }
    }
}

@Composable
private fun ResourceRow(resource: DetailResource) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { resource.onClick() }
            .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(CzRadius.sm))
                .background(resource.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = resource.icon,
                contentDescription = null,
                tint = resource.accent,
                modifier = Modifier.size(22.dp),
            )
            if (resource.showsBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.czColors.error)
                        .border(2.dp, MaterialTheme.czColors.surface, CircleShape),
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = resource.title,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = resource.subtitle,
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.czColors.textSecondary.copy(alpha = 0.70f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun DetailResourceButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    accent: Color = MaterialTheme.czColors.ember,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
        Row(
            modifier = Modifier.padding(CzSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(CzRadius.sm))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.czColors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ManagementSection(state: CampingDetailUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
        DetailSectionHeader(
            title = stringResource(R.string.camping_management),
            icon = Icons.Filled.Security,
        )
        if (state.canManageTeams) {
            AdminActionButton(
                title = stringResource(R.string.camping_manage_lodging),
                icon = Icons.Filled.KingBed,
            )
        }
        if (state.canManageTeams || state.canManageSchedule) {
            AdminActionButton(
                title = stringResource(R.string.camping_manage_venue_map),
                icon = Icons.Filled.Map,
            )
        }
        if (state.canManageAnyCamping && (state.camping?.endDate?.let { Date() >= it } == true)) {
            AdminActionButton(
                title = stringResource(R.string.camping_feedback_results),
                icon = Icons.Filled.Poll,
            )
        }
    }
}

@Composable
private fun AdminActionButton(
    title: String,
    icon: ImageVector,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 50.dp)
            .clickable { },
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.md),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.czColors.ember,
            )
            Text(
                text = title,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.czColors.textSecondary,
            )
        }
    }
}

@Composable
private fun DetailSectionHeader(
    title: String,
    icon: ImageVector,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.czColors.ember,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = title,
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun LockedNotice(text: String) {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.md),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CzSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.czColors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text,
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun DetailDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 48.dp),
        color = MaterialTheme.czColors.divider,
        thickness = 1.dp,
    )
}

@Composable
private fun RegistrationBottomBar(
    camping: Camping,
    onOpenRegistration: (String) -> Unit,
) {
    Column {
        CzButton(
            text = stringResource(R.string.camping_register_participants),
            onClick = { onOpenRegistration(camping.id) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CzSpacing.lg)
                .height(54.dp),
            variant = CzButtonVariant.Primary,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
    }
}

@Composable
private fun registrationSummary(
    state: CampingDetailUiState,
    camping: Camping,
): String {
    val capacity = camping.participantCapacity
    return if (capacity != null) {
        val remaining = capacity - state.approvedAttendeeCount
        if (remaining <= 0) {
            stringResource(R.string.camping_full_waitlist_open)
        } else {
            stringResource(R.string.camping_spots_remaining, remaining)
        }
    } else {
        stringResource(R.string.camping_registered_count, state.approvedAttendeeCount)
    }
}

@Composable
private fun organizerDisplayName(camping: Camping): String =
    when (camping.organizerLevel.type) {
        OrganizerType.Church -> stringResource(R.string.camping_organizer_church_value, camping.organizerLevel.value)
        OrganizerType.Regional -> stringResource(R.string.camping_organizer_regional_value, camping.organizerLevel.value)
        OrganizerType.International ->
            stringResource(R.string.camping_organizer_international_value, camping.organizerLevel.value)
        OrganizerType.Custom -> camping.organizerLevel.value
    }

@Composable
private fun CampingRegistrationStatus.shortLabel(): String = stringResource(
    when (this) {
        CampingRegistrationStatus.Open -> R.string.camping_status_short_open
        CampingRegistrationStatus.Closed -> R.string.camping_status_short_closed
        CampingRegistrationStatus.Cancelled -> R.string.camping_status_cancelled
    },
)

@Composable
private fun CampingRegistrationStatus.statusColor(): Color = when (this) {
    CampingRegistrationStatus.Open -> MaterialTheme.czColors.success
    CampingRegistrationStatus.Closed -> MaterialTheme.czColors.warning
    CampingRegistrationStatus.Cancelled -> MaterialTheme.czColors.error
}

@Composable
private fun RegistrationApprovalStatus.statusColor(): Color = when (this) {
    RegistrationApprovalStatus.Approved -> MaterialTheme.czColors.success
    RegistrationApprovalStatus.Pending -> MaterialTheme.czColors.warning
    RegistrationApprovalStatus.Waitlisted -> MaterialTheme.czColors.textSecondary
    RegistrationApprovalStatus.Rejected -> MaterialTheme.czColors.error
}

@Composable
private fun CampingAgeGroup.label(): String = stringResource(
    when (this) {
        CampingAgeGroup.Kids -> R.string.age_group_kids
        CampingAgeGroup.Youth -> R.string.age_group_youth
        CampingAgeGroup.Adult -> R.string.age_group_adult
    },
)

@Composable
private fun CampingDetailTab.label(): String = stringResource(
    when (this) {
        CampingDetailTab.Overview -> R.string.camping_tab_overview
        CampingDetailTab.Schedule -> R.string.camping_tab_schedule
        CampingDetailTab.Teams -> R.string.camping_tab_teams
    },
)

private data class DetailResource(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val showsBadge: Boolean = false,
    val onClick: () -> Unit,
)

private enum class CampingDetailTab {
    Overview,
    Schedule,
    Teams,
}

@Preview(showBackground = true)
@Composable
private fun CampingDetailScreenPreview() {
    CampzoneTheme {
        CampingDetailScreen(
            state = CampingDetailUiState(
                isLoading = false,
                camping = previewCamping("summer-2026", "Summer Pathfinder Camp", 2026, 6)
                    .copy(guidelines = "Bring a sleeping bag and a flashlight."),
                attendees = listOf(
                    previewAttendee("a1", "Maria Silva", RegistrationApprovalStatus.Approved),
                    previewAttendee("a2", "Joao Pereira", RegistrationApprovalStatus.Pending),
                    previewAttendee("a3", "Anne Laurent", RegistrationApprovalStatus.Approved),
                ),
                canViewParticipantProfiles = true,
                canRegisterForCampings = true,
                canManageTeams = true,
                canManageSchedule = true,
                canAwardAchievements = true,
            ),
            onBack = {},
            onAttendeeSearchChange = {},
            onRetry = {},
        )
    }
}

private fun previewAttendee(
    id: String,
    name: String,
    status: RegistrationApprovalStatus,
) = CampingAttendee(
    id = id,
    userId = id,
    displayName = name,
    church = "Paris Central SDA",
    age = 16,
    languages = listOf("fr", "pt"),
    registrationStatus = status,
)
