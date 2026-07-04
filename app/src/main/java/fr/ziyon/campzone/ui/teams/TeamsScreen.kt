package fr.ziyon.campzone.ui.teams

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.Team
import fr.ziyon.campzone.data.model.TeamMember
import fr.ziyon.campzone.data.model.TeamMemberRole
import fr.ziyon.campzone.data.model.WinnerRevealPolicy
import fr.ziyon.campzone.data.teams.TeamBalanceResult
import fr.ziyon.campzone.ui.games.GameViewModel
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.launch

// ── Route ─────────────────────────────────────────────────────────────────────

@Composable
fun TeamsRoute(
    campingId: String,
    camping: Camping?,
    authenticatedUser: AuthenticatedUser,
    approvedAttendees: List<CampingAttendee>,
    onBack: () -> Unit,
    onOpenTeamDetail: (String) -> Unit,
    onOpenTeamEditor: (String?) -> Unit,
    onOpenGames: (String) -> Unit,
    viewModel: TeamViewModel = hiltViewModel(),
    revealViewModel: GameViewModel = hiltViewModel(),
) {
    val evaluator = remember { AppPermissionEvaluator() }
    val scope = rememberCoroutineScope()
    var localRevealPolicy by remember(camping?.id) { mutableStateOf<WinnerRevealPolicy?>(null) }
    val currentCamping = localRevealPolicy?.let { policy ->
        camping?.copy(winnerRevealPolicy = policy)
    } ?: camping
    val permissionUser = PermissionUser(
        role = authenticatedUser.role,
        userId = authenticatedUser.uid,
        church = authenticatedUser.church,
    )
    val campingCtx = currentCamping?.let { c ->
        CampingPermissionContext(
            organizerLevelType = c.organizerLevel.type.wireValue,
            organizerLevelValue = c.organizerLevel.value,
            createdByUid = c.createdByUid,
        )
    }

    val canManageTeams = campingCtx != null && evaluator.canManageTeams(permissionUser, campingCtx)
    val canToggleReveal = campingCtx != null && evaluator.canRevealWinners(permissionUser, campingCtx)
    val canSeeScores = campingCtx != null &&
        (evaluator.canRevealWinners(permissionUser, campingCtx) ||
            evaluator.canManageGames(permissionUser, campingCtx))
    val revealPolicy = currentCamping?.winnerRevealPolicy ?: WinnerRevealPolicy()
    val isPolicyRevealed = revealPolicy.hasRevealFired()
    val revealPolicyTriggered = currentCamping
        ?.let { revealPolicy.hasRevealFired() && !revealPolicy.areScoresHidden(it.endDate) }
        ?: false
    val scoresHidden = currentCamping
        ?.let { !canSeeScores && (it.winnerRevealPolicy ?: WinnerRevealPolicy()).areScoresHidden(it.endDate) }
        ?: false

    val uiState by viewModel.uiState.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isPreviewingAutoBalance by viewModel.isAutoBalancePreviewRunning.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    val autoBalancePreview by viewModel.autoBalancePreview.collectAsState()

    LaunchedEffect(campingId) { viewModel.loadIfNeeded(campingId) }
    DisposableEffect(campingId) {
        viewModel.startObserving(campingId)
        onDispose { viewModel.stopObserving() }
    }

    TeamsScreen(
        campingId = campingId,
        uiState = uiState,
        isSaving = isSaving,
        operationError = operationError,
        canManageTeams = canManageTeams,
        canToggleReveal = canToggleReveal,
        isPolicyRevealed = isPolicyRevealed,
        revealPolicyTriggered = revealPolicyTriggered,
        revealedAt = revealPolicy.revealedAt,
        isUpdatingReveal = revealViewModel.isUpdatingReveal,
        revealOperationError = revealViewModel.operationError,
        scoresHidden = scoresHidden,
        authenticatedUserId = authenticatedUser.uid,
        approvedAttendees = approvedAttendees,
        autoBalancePreview = autoBalancePreview,
        isPreviewingAutoBalance = isPreviewingAutoBalance,
        onBack = onBack,
        onOpenTeamDetail = onOpenTeamDetail,
        onCreateTeam = { onOpenTeamEditor(null) },
        onOpenGames = { onOpenGames(campingId) },
        onRefresh = { viewModel.refresh(campingId) },
        onPreviewAutoBalance = { teamIds -> viewModel.previewAutoBalance(approvedAttendees, teamIds) },
        onApplyAutoBalance = { teamIds, onSuccess ->
            viewModel.applyAutoBalance(campingId, approvedAttendees, teamIds, onSuccess)
        },
        onClearAutoBalancePreview = viewModel::clearAutoBalancePreview,
        onClearError = viewModel::clearOperationError,
        onRevealWinner = {
            scope.launch {
                val saved = revealViewModel.reveal(campingId, currentCamping?.winnerRevealPolicy, authenticatedUser)
                if (saved != null) localRevealPolicy = saved
            }
        },
        onHideWinner = {
            scope.launch {
                val saved = revealViewModel.unreveal(campingId, currentCamping?.winnerRevealPolicy)
                if (saved != null) localRevealPolicy = saved
            }
        },
    )
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamsScreen(
    campingId: String,
    uiState: TeamsUiState,
    isSaving: Boolean,
    operationError: String?,
    canManageTeams: Boolean,
    canToggleReveal: Boolean,
    isPolicyRevealed: Boolean,
    revealPolicyTriggered: Boolean,
    revealedAt: java.util.Date? = null,
    isUpdatingReveal: Boolean,
    revealOperationError: String?,
    scoresHidden: Boolean,
    authenticatedUserId: String,
    approvedAttendees: List<CampingAttendee>,
    autoBalancePreview: TeamBalanceResult?,
    isPreviewingAutoBalance: Boolean,
    onBack: () -> Unit,
    onOpenTeamDetail: (String) -> Unit,
    onCreateTeam: () -> Unit,
    onOpenGames: () -> Unit,
    onRefresh: () -> Unit,
    onPreviewAutoBalance: (List<String>) -> Unit,
    onApplyAutoBalance: (List<String>, () -> Unit) -> Unit,
    onClearAutoBalancePreview: () -> Unit,
    onClearError: () -> Unit,
    onRevealWinner: () -> Unit,
    onHideWinner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val context = LocalContext.current
    val ceremonyPrefs = remember(context) {
        context.getSharedPreferences("campzone_team_ceremony", Context.MODE_PRIVATE)
    }
    val ceremonyKey = remember(campingId) { "cz.team.ceremony.seen.$campingId" }
    var menuExpanded by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showAutoBalanceSheet by rememberSaveable { mutableStateOf(false) }
    var showRevealConfirm by rememberSaveable { mutableStateOf(false) }
    var showHideRevealConfirm by rememberSaveable { mutableStateOf(false) }
    var ceremonyTeam by remember { mutableStateOf<Team?>(null) }
    var ceremonyAcknowledged by remember(campingId) {
        mutableStateOf(ceremonyPrefs.getBoolean(ceremonyKey, false))
    }
    var ceremonyHasShown by rememberSaveable(campingId) { mutableStateOf(false) }
    var revealStateInitialized by rememberSaveable(campingId) { mutableStateOf(false) }
    var previousRevealPolicyTriggered by rememberSaveable(campingId) { mutableStateOf(false) }
    var pendingCeremony by rememberSaveable(campingId) { mutableStateOf(false) }

    val loadedTeams = (uiState as? TeamsUiState.Loaded)?.teams.orEmpty()
    val winningTeam = loadedTeams.firstOrNull()

    fun markCeremonySeen() {
        ceremonyPrefs.edit().putBoolean(ceremonyKey, true).apply()
        ceremonyAcknowledged = true
    }

    fun clearCeremonySeen() {
        ceremonyPrefs.edit().remove(ceremonyKey).apply()
        ceremonyAcknowledged = false
    }

    LaunchedEffect(revealPolicyTriggered, winningTeam?.id) {
        if (!revealStateInitialized) {
            revealStateInitialized = true
            previousRevealPolicyTriggered = revealPolicyTriggered
            if (revealPolicyTriggered && !ceremonyAcknowledged) {
                markCeremonySeen()
                ceremonyHasShown = true
            }
            return@LaunchedEffect
        }

        if (!revealPolicyTriggered) {
            clearCeremonySeen()
            ceremonyHasShown = false
            pendingCeremony = false
            ceremonyTeam = null
            previousRevealPolicyTriggered = false
            return@LaunchedEffect
        }

        if (!previousRevealPolicyTriggered && !ceremonyAcknowledged && !ceremonyHasShown) {
            winningTeam?.let { ceremonyTeam = it } ?: run { pendingCeremony = true }
        } else if (pendingCeremony && !ceremonyAcknowledged && !ceremonyHasShown) {
            winningTeam?.let {
                ceremonyTeam = it
                pendingCeremony = false
            }
        }

        previousRevealPolicyTriggered = revealPolicyTriggered
    }

    if (showRevealConfirm) {
        AlertDialog(
            onDismissRequest = { showRevealConfirm = false },
            title = { Text(stringResource(R.string.teams_reveal_confirm_title)) },
            text = { Text(stringResource(R.string.teams_reveal_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRevealConfirm = false
                        onRevealWinner()
                    },
                ) { Text(stringResource(R.string.teams_reveal_winner), color = colors.success) }
            },
            dismissButton = {
                TextButton(onClick = { showRevealConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showHideRevealConfirm) {
        AlertDialog(
            onDismissRequest = { showHideRevealConfirm = false },
            title = { Text(stringResource(R.string.teams_hide_confirm_title)) },
            text = { Text(stringResource(R.string.teams_hide_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showHideRevealConfirm = false
                        onHideWinner()
                    },
                ) { Text(stringResource(R.string.teams_hide_winner), color = colors.warning) }
            },
            dismissButton = {
                TextButton(onClick = { showHideRevealConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showAutoBalanceSheet) {
        TeamAutoBalanceSheet(
            teams = loadedTeams,
            approvedAttendees = approvedAttendees,
            preview = autoBalancePreview,
            isSaving = isSaving,
            isPreviewing = isPreviewingAutoBalance,
            onPreview = onPreviewAutoBalance,
            onApply = {
                onApplyAutoBalance(it) { showAutoBalanceSheet = false }
            },
            onClearPreview = onClearAutoBalancePreview,
            onDismiss = {
                showAutoBalanceSheet = false
                onClearAutoBalancePreview()
            },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = colors.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.teams_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.background,
                        titleContentColor = colors.textPrimary,
                        actionIconContentColor = colors.textPrimary,
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenGames) {
                            Icon(
                                imageVector = Icons.Outlined.SportsEsports,
                                contentDescription = stringResource(R.string.teams_games_and_points),
                            )
                        }
                        if (isUpdatingReveal) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = colors.ember,
                                strokeWidth = 2.dp,
                            )
                        }
                        if (canManageTeams) {
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.teams_management))
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.teams_new_team)) },
                                        onClick = { menuExpanded = false; onCreateTeam() },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.teams_auto_balance)) },
                                        onClick = { menuExpanded = false; showAutoBalanceSheet = true },
                                    )
                                    if (canToggleReveal) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (isPolicyRevealed) {
                                                        stringResource(R.string.teams_hide_winner)
                                                    } else {
                                                        stringResource(R.string.teams_reveal_winner)
                                                    },
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (isPolicyRevealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                                    contentDescription = null,
                                                    tint = if (isPolicyRevealed) colors.warning else colors.success,
                                                )
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                if (isPolicyRevealed) showHideRevealConfirm = true else showRevealConfirm = true
                                            },
                                            enabled = !isUpdatingReveal,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    windowInsets = WindowInsets()
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) { innerPadding ->
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    onRefresh()
                    isRefreshing = false
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (uiState) {
                    is TeamsUiState.Loading -> {
                        fr.ziyon.campzone.core.designsystem.CzLoadingView(
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    is TeamsUiState.Empty -> {
                        fr.ziyon.campzone.core.designsystem.CzEmptyState(
                            title = stringResource(R.string.teams_empty_title),
                            message = stringResource(R.string.teams_empty_message),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    is TeamsUiState.Error -> {
                        fr.ziyon.campzone.core.designsystem.CzErrorState(
                            title = stringResource(R.string.teams_title),
                            message = uiState.message,
                            onRetry = onRefresh,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    is TeamsUiState.Loaded -> {
                        val showTrophy = revealPolicyTriggered && ceremonyAcknowledged
                        TeamsLoadedContent(
                            teams = uiState.teams,
                            showTrophy = showTrophy,
                            scoresHidden = scoresHidden,
                            authenticatedUserId = authenticatedUserId,
                            onOpenTeamDetail = { onOpenTeamDetail(it) },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = operationError != null || revealOperationError != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(CzSpacing.lg),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CzRadius.md))
                    .background(colors.error.copy(alpha = 0.12f))
                    .border(1.dp, colors.error.copy(alpha = 0.24f), RoundedCornerShape(CzRadius.md))
                    .padding(CzSpacing.md),
            ) {
                Text(
                    text = revealOperationError ?: operationError.orEmpty(),
                    color = colors.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        ceremonyTeam?.let { team ->
            WinnerRevealCeremonyOverlay(
                winningTeam = team,
                revealedAt = revealedAt,
                onComplete = {
                    markCeremonySeen()
                    ceremonyHasShown = true
                    ceremonyTeam = null
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun HiddenScoresBanner(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.lg))
            .background(colors.warning.copy(alpha = 0.12f))
            .border(1.dp, colors.warning.copy(alpha = 0.28f), RoundedCornerShape(CzRadius.lg))
            .padding(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Icon(
            imageVector = Icons.Outlined.VisibilityOff,
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier.size(22.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.teams_hidden_banner_title),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.teams_hidden_banner_message),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun AwaitingGamesBanner(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.md))
            .background(colors.surface)
            .padding(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Icon(
            imageVector = Icons.Outlined.SportsEsports,
            contentDescription = null,
            tint = colors.accent.copy(alpha = 0.6f),
            modifier = Modifier.size(28.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.teams_awaiting_games_title),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.teams_awaiting_games_message),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamAutoBalanceSheet(
    teams: List<Team>,
    approvedAttendees: List<CampingAttendee>,
    preview: TeamBalanceResult?,
    isSaving: Boolean,
    isPreviewing: Boolean,
    onPreview: (List<String>) -> Unit,
    onApply: (List<String>) -> Unit,
    onClearPreview: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    var selectedTeamIds by rememberSaveable(teams.map { it.id }) {
        mutableStateOf(teams.map { it.id })
    }
    val orderedSelectedTeamIds = teams.map { it.id }.filter { it in selectedTeamIds }
    val currentSignature = remember(approvedAttendees, orderedSelectedTeamIds) {
        AutoBalanceSheetSignature.from(approvedAttendees, orderedSelectedTeamIds)
    }
    var appliedSignature by remember { mutableStateOf<AutoBalanceSheetSignature?>(null) }
    LaunchedEffect(currentSignature, preview) {
        if (preview != null && appliedSignature != currentSignature) {
            onClearPreview()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isSaving && !isPreviewing) onDismiss()
        },
        containerColor = colors.background,
        modifier = modifier,
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CzSpacing.lg)
                    .padding(bottom = CzSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
            ) {
                Text(
                    text = stringResource(R.string.teams_auto_balance_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(
                        R.string.teams_auto_balance_summary,
                        approvedAttendees.size,
                        teams.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )

                if (teams.isEmpty()) {
                    Text(
                        text = stringResource(R.string.teams_auto_balance_no_teams),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(CzRadius.lg))
                            .background(colors.surface),
                    ) {
                        teams.forEachIndexed { index, team ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isPreviewing && !isSaving) {
                                        selectedTeamIds = if (team.id in selectedTeamIds) {
                                            selectedTeamIds - team.id
                                        } else {
                                            selectedTeamIds + team.id
                                        }
                                        appliedSignature = null
                                        onClearPreview()
                                    }
                                    .padding(horizontal = CzSpacing.sm, vertical = CzSpacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                            ) {
                                Checkbox(
                                    checked = team.id in selectedTeamIds,
                                    enabled = !isPreviewing && !isSaving,
                                    onCheckedChange = { checked ->
                                        selectedTeamIds = if (checked) {
                                            (selectedTeamIds + team.id).distinct()
                                        } else {
                                            selectedTeamIds - team.id
                                        }
                                        appliedSignature = null
                                        onClearPreview()
                                    },
                                )
                                TeamBadgeView(team = team, size = 32)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = team.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = stringResource(R.string.teams_auto_balance_current_members, team.members.size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textSecondary,
                                    )
                                }
                            }
                            if (index < teams.lastIndex) {
                                Box(Modifier.height(0.5.dp).fillMaxWidth().background(colors.divider))
                            }
                        }
                    }
                }

                if (preview != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.teams_auto_balance_preview),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = stringResource(R.string.teams_auto_balance_score, preview.balanceScore.toInt()),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (preview.balanceScore <= 4.0) colors.success else colors.warning,
                            )
                        }
                        orderedSelectedTeamIds.forEach { teamId ->
                            val team = teams.firstOrNull { it.id == teamId } ?: return@forEach
                            val assignments = preview.assignmentsByTeamId[teamId].orEmpty()
                            AutoBalancePreviewRow(team = team, attendees = assignments)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                ) {
                    OutlinedButton(
                        onClick = {
                            appliedSignature = currentSignature
                            onPreview(orderedSelectedTeamIds)
                        },
                        enabled = orderedSelectedTeamIds.isNotEmpty() && approvedAttendees.isNotEmpty() && !isSaving && !isPreviewing,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isPreviewing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.teams_auto_balance_preview_action))
                        }
                    }
                    Button(
                        onClick = { onApply(orderedSelectedTeamIds) },
                        enabled = preview != null && !isSaving && !isPreviewing,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.teams_auto_balance_apply))
                    }
                }
            }

            if (isPreviewing) {
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(CzRadius.lg))
                        .background(colors.card)
                        .border(1.dp, colors.divider, RoundedCornerShape(CzRadius.lg))
                        .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.teams_auto_balance_processing_data),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AutoBalancePreviewRow(
    team: Team,
    attendees: List<CampingAttendee>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val visibleAttendees = attendees.take(MaxPreviewMembersPerTeam)
    val hiddenCount = attendees.size - visibleAttendees.size
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.md))
            .background(colors.surface)
            .padding(CzSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            TeamBadgeView(team = team, size = 36)
            Text(
                text = team.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${attendees.size}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = team.colorHex.toComposeColor() ?: colors.ember,
            )
        }
        if (attendees.isEmpty()) {
            Text(
                text = stringResource(R.string.teams_auto_balance_no_assignments),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        } else {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 128.dp),
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            ) {
                visibleAttendees.forEach { attendee ->
                    Text(
                        text = attendee.displayName,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.12f))
                            .padding(horizontal = CzSpacing.sm, vertical = CzSpacing.xs),
                    )
                }
                if (hiddenCount > 0) {
                    Text(
                        text = stringResource(R.string.teams_auto_balance_more_members, hiddenCount),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textSecondary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.textSecondary.copy(alpha = 0.10f))
                            .padding(horizontal = CzSpacing.sm, vertical = CzSpacing.xs),
                    )
                }
            }
        }
    }
}

private const val MaxPreviewMembersPerTeam = 16

private data class AutoBalanceSheetSignature(
    val teamIds: List<String>,
    val attendeeKeys: List<String>,
) {
    companion object {
        fun from(attendees: List<CampingAttendee>, teamIds: List<String>) = AutoBalanceSheetSignature(
            teamIds = teamIds.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            attendeeKeys = attendees.map { attendee ->
                listOf(
                    attendee.id,
                    attendee.userId,
                    attendee.registrationStatus.wireValue,
                    attendee.church,
                    attendee.preferredLanguage,
                    attendee.ageGroup.wireValue,
                    attendee.gender?.wireValue ?: "unknown",
                    attendee.languages.joinToString(","),
                ).joinToString("|")
            },
        )
    }
}

// ── Loaded content ────────────────────────────────────────────────────────────

@Composable
private fun TeamsLoadedContent(
    teams: List<Team>,
    showTrophy: Boolean,
    scoresHidden: Boolean,
    authenticatedUserId: String,
    onOpenTeamDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val personalTeam = teams.firstOrNull { t -> t.members.any { it.userId == authenticatedUserId } }
    val displayTeams = if (scoresHidden) {
        teams.sortedBy { it.name.lowercase() }
    } else {
        teams
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xl),
    ) {
        if (scoresHidden) {
            HiddenScoresBanner()
        }

        // Personal team card
        if (personalTeam != null) {
            val member = personalTeam.members.firstOrNull { it.userId == authenticatedUserId }
            val rank = if (scoresHidden) {
                null
            } else {
                teams.indexOfFirst { it.id == personalTeam.id }.takeIf { it >= 0 }?.plus(1)
            }
            if (member != null) {
                TeamSectionLabel(stringResource(R.string.teams_my_team), Icons.Outlined.Groups)
                PersonalTeamCard(
                    team = personalTeam,
                    member = member,
                    rank = rank,
                    scoresHidden = scoresHidden,
                    onClick = { onOpenTeamDetail(personalTeam.id) },
                )
            }
        }

        // Podium or awaiting-games banner
        if (!scoresHidden && teams.size >= 2) {
            TeamSectionLabel(stringResource(R.string.teams_top_teams), Icons.Outlined.EmojiEvents)
            if (teams.any { it.totalScore > 0 }) {
                PodiumSection(topTeams = teams.take(3), showTrophy = showTrophy)
            } else {
                AwaitingGamesBanner()
            }
        }

        // Full ranking list
        TeamSectionLabel(stringResource(R.string.teams_ranking), Icons.AutoMirrored.Outlined.List)
        RankingList(teams = displayTeams, scoresHidden = scoresHidden, onClick = onOpenTeamDetail)

    }
}

// ── Personal team card ────────────────────────────────────────────────────────

@Composable
private fun PersonalTeamCard(
    team: Team,
    member: TeamMember,
    rank: Int?,
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
            .border(1.5.dp, teamColor.copy(alpha = 0.3f), RoundedCornerShape(CzRadius.lg))
            .clickable(onClick = onClick)
            .padding(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        TeamBadgeView(team = team, size = 44)

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = team.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = buildString {
                append(member.role.displayName())
                if (rank != null) append(" · #$rank")
            }
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        }

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (scoresHidden) {
                Icon(
                    imageVector = Icons.Outlined.VisibilityOff,
                    contentDescription = stringResource(R.string.teams_scores_hidden),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.teams_scores_hidden_short),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            } else {
                Text(
                    text = "${member.personalScore}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = teamColor,
                )
                Text(
                    text = stringResource(R.string.teams_personal_pts),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

// ── Podium ────────────────────────────────────────────────────────────────────

@Composable
private fun PodiumSection(topTeams: List<Team>, showTrophy: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = CzSpacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (topTeams.size >= 2) PodiumBlock(team = topTeams[1], rank = 2, height = 64, showTrophy = false)
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            PodiumBlock(team = topTeams[0], rank = 1, height = 88, showTrophy = showTrophy)
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (topTeams.size >= 3) PodiumBlock(team = topTeams[2], rank = 3, height = 48, showTrophy = false)
        }
    }
}

@Composable
private fun PodiumBlock(
    team: Team,
    rank: Int,
    height: Int,
    showTrophy: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val blockWidth = when (rank) { 1 -> 92; 2 -> 84; else -> 80 }
    val medalBorderColor = when (rank) {
        1 -> Color(0xFFFFE066)
        2 -> Color(0xFFE8EEF4)
        else -> Color(0xFFE8A060)
    }
    val podiumGradient = Brush.horizontalGradient(
        when (rank) {
            1 -> listOf(Color(0xFF1E1200), Color(0xFF3D2800), Color(0xFF5A3C00), Color(0xFF3D2800), Color(0xFF1E1200))
            2 -> listOf(Color(0xFF0D0D0D), Color(0xFF1C1C1C), Color(0xFF2A2A2A), Color(0xFF1C1C1C), Color(0xFF0D0D0D))
            else -> listOf(Color(0xFF120800), Color(0xFF2A1400), Color(0xFF3C1C00), Color(0xFF2A1400), Color(0xFF120800))
        }
    )

    Column(
        modifier = modifier.width(blockWidth.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        if (rank == 1) {
            if (showTrophy) {
                TrophyView(teamName = team.name)
                Spacer(Modifier.height(2.dp))
            } else {
                Spacer(Modifier.height(80.dp * 1.40f + 2.dp))
            }
        }
        TeamBadgeView(
            team = team,
            size = if (rank == 1) 56 else 48,
            borderColor = medalBorderColor,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = team.name,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${team.totalScore}",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = colors.textSecondary,
            maxLines = 1,
        )
        Spacer(Modifier.height(CzSpacing.xs))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .clip(RoundedCornerShape(topStart = CzRadius.sm, topEnd = CzRadius.sm))
                .background(podiumGradient),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$rank",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                ),
                color = Color.White.copy(alpha = 0.38f),
            )
        }
    }
}

// ── Ranking list ──────────────────────────────────────────────────────────────

@Composable
private fun RankingList(
    teams: List<Team>,
    scoresHidden: Boolean,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.lg))
            .background(colors.surface),
    ) {
        teams.forEachIndexed { index, team ->
            TeamRankingRow(
                team = team,
                rank = index + 1,
                scoresHidden = scoresHidden,
                onClick = { onClick(team.id) },
            )
            if (index < teams.lastIndex) {
                Box(
                    modifier = Modifier
                        .padding(start = 80.dp)
                        .height(0.5.dp)
                        .fillMaxWidth()
                        .background(colors.divider),
                )
            }
        }
    }
}

@Composable
private fun TeamRankingRow(
    team: Team,
    rank: Int,
    scoresHidden: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val teamColor = team.colorHex.toComposeColor() ?: colors.ember

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
            color = when (rank) {
                1 -> Color(0xFFFFD700)
                2 -> Color(0xFFC0C0C0)
                3 -> Color(0xFFCD7F32)
                else -> colors.textSecondary
            },
            modifier = Modifier.width(18.dp),
        )

        TeamBadgeView(team = team, size = 40)

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = team.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${team.members.size} ${stringResource(R.string.teams_members)}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }

        if (scoresHidden) {
            Icon(
                imageVector = Icons.Outlined.VisibilityOff,
                contentDescription = stringResource(R.string.teams_scores_hidden),
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Text(
                text = "${team.totalScore}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = teamColor,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

// ── Trophy view ───────────────────────────────────────────────────────────────

@Composable
internal fun TrophyView(
    teamName: String,
    size: Dp = 80.dp,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val gShadow   = Color(0xFF1E0E00)
    val gDark     = Color(0xFF5C3000)
    val gMidDark  = Color(0xFFA86200)
    val gMid      = Color(0xFFD4980A)
    val gBright   = Color(0xFFF5C800)
    val gSpecular = Color(0xFFFFF6C0)

    val crownIconSize = size * 0.20f
    val crownStarSize = size * 0.13f
    val crownTotalH   = crownIconSize + size * 0.024f

    Box(
        modifier = modifier.size(size * 1.14f, size * 1.40f),
    ) {
        // Crown: two small stars flanking a larger centre star
        Row(
            modifier = Modifier.align(Alignment.TopCenter),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = gBright,   modifier = Modifier.size(crownStarSize))
            Spacer(Modifier.width(size * 0.018f))
            Icon(Icons.Filled.Star, contentDescription = null, tint = gSpecular, modifier = Modifier.size(crownIconSize))
            Spacer(Modifier.width(size * 0.018f))
            Icon(Icons.Filled.Star, contentDescription = null, tint = gBright,   modifier = Modifier.size(crownStarSize))
        }

        // Trophy body drawn below the crown row
        Canvas(
            modifier = Modifier
                .padding(top = crownTotalH)
                .fillMaxSize(),
        ) {
            val W   = this.size.width / 1.14f
            val cx  = this.size.width / 2f
            val den = this.density

            val cylStops = arrayOf(
                0.00f to gShadow,
                0.08f to gDark,
                0.21f to gMidDark,
                0.36f to gBright,
                0.46f to gSpecular,
                0.56f to gBright,
                0.70f to gMid,
                0.88f to gDark,
                1.00f to gShadow,
            )

            fun cyl(l: Float, r: Float) = Brush.horizontalGradient(colorStops = cylStops, startX = l, endX = r)

            var y = 0f

            // Rim ellipse
            val rimW = W * 0.72f; val rimH = W * 0.08f
            drawOval(
                brush = Brush.verticalGradient(listOf(gSpecular, gBright, gMid, gDark), startY = y, endY = y + rimH),
                topLeft = Offset(cx - rimW / 2, y),
                size = Size(rimW, rimH),
            )
            y += rimH

            // Cup body
            val cupW = W * 0.72f; val cupH = W * 0.50f; val cupL = cx - cupW / 2
            val cupPath = Path().apply {
                moveTo(cupL, y)
                cubicTo(cupL - cupW * 0.03f, y + cupH * 0.38f, cupL + cupW * 0.07f, y + cupH * 0.82f, cupL + cupW * 0.17f, y + cupH)
                lineTo(cupL + cupW * 0.83f, y + cupH)
                cubicTo(cupL + cupW * 0.93f, y + cupH * 0.82f, cupL + cupW * 1.03f, y + cupH * 0.38f, cupL + cupW, y)
                close()
            }
            drawPath(cupPath, cyl(cupL, cupL + cupW))
            drawPath(cupPath, Brush.verticalGradient(listOf(Color.Transparent, gShadow.copy(alpha = 0.22f)), startY = y, endY = y + cupH))

            // Specular streak
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(gSpecular.copy(alpha = 0.60f), Color.Transparent),
                    center = Offset(cupL + cupW * 0.16f, y + cupH * 0.24f),
                    radius = W * 0.15f,
                ),
                topLeft = Offset(cupL + cupW * 0.04f, y + cupH * 0.04f),
                size = Size(W * 0.10f, W * 0.28f),
            )

            // Left handle
            val hW = W * 0.20f; val hH = W * 0.34f; val hY = y - W * 0.06f
            val strokeW = Stroke(width = W * 0.055f, cap = StrokeCap.Round)
            val lhPath = Path().apply {
                moveTo(cupL, hY + hH * 0.08f)
                cubicTo(cupL - hW, hY + hH * 0.08f, cupL - hW, hY + hH * 0.92f, cupL, hY + hH * 0.92f)
            }
            drawPath(lhPath, Brush.horizontalGradient(listOf(gMidDark, gBright, gMidDark, gShadow), startX = cupL - hW, endX = cupL), style = strokeW)

            // Right handle
            val rhPath = Path().apply {
                moveTo(cupL + cupW, hY + hH * 0.08f)
                cubicTo(cupL + cupW + hW, hY + hH * 0.08f, cupL + cupW + hW, hY + hH * 0.92f, cupL + cupW, hY + hH * 0.92f)
            }
            drawPath(rhPath, Brush.horizontalGradient(listOf(gShadow, gMidDark, gBright, gMidDark), startX = cupL + cupW, endX = cupL + cupW + hW), style = strokeW)

            // Engraving band — two lines with the team name between them
            val engBandY = y + cupH * 0.64f
            val bandH = W * 0.08f
            drawLine(gShadow.copy(alpha = 0.65f), Offset(cupL + cupW * 0.10f, engBandY), Offset(cupL + cupW * 0.90f, engBandY), 1f)
            val engFontSize = (size.value * 0.085f).sp
            val engStyle = TextStyle(
                color = gShadow.copy(alpha = 0.88f),
                fontSize = engFontSize,
                fontWeight = FontWeight.Bold,
            )
            val displayName = teamName.uppercase().let { if (it.length > 11) it.take(10) + "…" else it }
            val engResult = textMeasurer.measure(displayName, engStyle)
            drawText(
                textLayoutResult = engResult,
                topLeft = Offset(
                    x = cx - engResult.size.width / 2f,
                    y = engBandY + (bandH - engResult.size.height) / 2f,
                ),
            )
            drawLine(gShadow.copy(alpha = 0.65f), Offset(cupL + cupW * 0.14f, engBandY + bandH), Offset(cupL + cupW * 0.86f, engBandY + bandH), 1f)

            y += cupH

            // Shoulder taper
            val shW = W * 0.36f; val shH = W * 0.058f
            drawOval(cyl(cx - shW / 2, cx + shW / 2), topLeft = Offset(cx - shW / 2, y), size = Size(shW, shH))
            y += shH

            // Stem
            val stW = W * 0.10f; val stH = W * 0.16f
            drawRoundRect(cyl(cx - stW / 2, cx + stW / 2), topLeft = Offset(cx - stW / 2, y), size = Size(stW, stH), cornerRadius = CornerRadius(2f * den))
            y += stH

            // Pedestal ring
            val prW = W * 0.42f; val prH = W * 0.056f
            drawOval(cyl(cx - prW / 2, cx + prW / 2), topLeft = Offset(cx - prW / 2, y), size = Size(prW, prH))
            y += prH

            // Upper tier
            val utW = W * 0.44f; val utH = W * 0.06f
            drawRoundRect(cyl(cx - utW / 2, cx + utW / 2), topLeft = Offset(cx - utW / 2, y), size = Size(utW, utH), cornerRadius = CornerRadius(3f * den))
            y += utH

            // Mid tier
            val mtW = W * 0.58f; val mtH = W * 0.052f
            drawRoundRect(cyl(cx - mtW / 2, cx + mtW / 2), topLeft = Offset(cx - mtW / 2, y), size = Size(mtW, mtH), cornerRadius = CornerRadius(3f * den))
            y += mtH

            // Base plate
            val bpW = W * 0.76f; val bpH = W * 0.072f
            drawRoundRect(cyl(cx - bpW / 2, cx + bpW / 2), topLeft = Offset(cx - bpW / 2, y), size = Size(bpW, bpH), cornerRadius = CornerRadius(4f * den))
        }
    }
}

// ── Team badge view ───────────────────────────────────────────────────────────

@Composable
fun TeamBadgeView(
    team: Team,
    size: Int,
    borderColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val teamColor = team.colorHex.toComposeColor() ?: colors.ember
    val shape = RoundedCornerShape(CzRadius.md)
    val effectiveBorder = borderColor ?: teamColor.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(shape)
            .background(teamColor)
            .border(1.5.dp, effectiveBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        val photoUrl = team.photoUrl
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = team.name,
                modifier = Modifier.fillMaxSize().clip(shape),
            )
        } else {
            Text(
                text = team.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = (size * 0.35f).sp,
                ),
                color = Color.White,
            )
        }
    }
}

// ── Section label ─────────────────────────────────────────────────────────────

@Composable
private fun TeamSectionLabel(title: String, icon: ImageVector, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.ember, modifier = Modifier.size(14.dp))
        Text(text = title, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
    }
}

// ── Color utility ─────────────────────────────────────────────────────────────

fun String.toComposeColor(): Color? = runCatching {
    val c = this.toColorInt()
    Color(c)
}.getOrNull()

fun TeamMemberRole.displayName(): String = when (this) {
    TeamMemberRole.Captain -> "Captain"
    TeamMemberRole.ViceCaptain -> "Vice-captain"
    TeamMemberRole.Member -> "Member"
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun TeamsScreenPreview() {
    CampzoneTheme {
        TeamsScreen(
            campingId = "preview-camping",
            uiState = TeamsUiState.Loaded(
                listOf(
                    Team("lions", "preview-camping", "Lions", "Courage!", "flame.fill", "#D9432F", points = 180, members = listOf(TeamMember("u1", "u1", "Admin", "SDA", role = TeamMemberRole.Captain, personalScore = 40))),
                    Team("eagles", "preview-camping", "Eagles", "Higher!", "paperplane.fill", "#2364AA", points = 150, members = listOf(TeamMember("u2", "u2", "Marc", "SDA", personalScore = 20))),
                    Team("tigers", "preview-camping", "Tigers", "Pride!", "bolt.fill", "#E65100", points = 120, members = listOf(TeamMember("u3", "u3", "Alex", "SDA", personalScore = 20))),
                )
            ),
            isSaving = false,
            operationError = null,
            canManageTeams = true,
            canToggleReveal = true,
            isPolicyRevealed = false,
            revealPolicyTriggered = false,
            isUpdatingReveal = false,
            revealOperationError = null,
            scoresHidden = false,
            authenticatedUserId = "u1",
            approvedAttendees = emptyList(),
            autoBalancePreview = null,
            isPreviewingAutoBalance = false,
            onBack = {},
            onOpenTeamDetail = {},
            onCreateTeam = {},
            onOpenGames = {},
            onRefresh = {},
            onPreviewAutoBalance = {},
            onApplyAutoBalance = { _, onSuccess -> onSuccess() },
            onClearAutoBalancePreview = {},
            onClearError = {},
            onRevealWinner = {},
            onHideWinner = {},
        )
    }
}
