package fr.ziyon.campzone.ui.teams

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import fr.ziyon.campzone.data.teams.FakeTeamService

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
) {
    val evaluator = remember { AppPermissionEvaluator() }
    val permissionUser = PermissionUser(
        role = authenticatedUser.role,
        userId = authenticatedUser.uid,
        church = authenticatedUser.church,
    )
    val campingCtx = camping?.let { c ->
        CampingPermissionContext(
            organizerLevelType = c.organizerLevel.type.wireValue,
            organizerLevelValue = c.organizerLevel.value,
            createdByUid = c.createdByUid,
        )
    }

    val canManageTeams = campingCtx != null && evaluator.canManageTeams(permissionUser, campingCtx)
    val canSeeScores = campingCtx != null &&
        (evaluator.canRevealWinners(permissionUser, campingCtx) ||
            evaluator.canManageGames(permissionUser, campingCtx))
    val scoresHidden = camping
        ?.let { !canSeeScores && (it.winnerRevealPolicy ?: WinnerRevealPolicy()).areScoresHidden(it.endDate) }
        ?: false

    val uiState by viewModel.uiState.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
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
        scoresHidden = scoresHidden,
        authenticatedUserId = authenticatedUser.uid,
        approvedAttendees = approvedAttendees,
        autoBalancePreview = autoBalancePreview,
        onBack = onBack,
        onOpenTeamDetail = onOpenTeamDetail,
        onCreateTeam = { onOpenTeamEditor(null) },
        onOpenGames = { onOpenGames(campingId) },
        onRefresh = { viewModel.refresh(campingId) },
        onPreviewAutoBalance = { teamIds -> viewModel.previewAutoBalance(approvedAttendees, teamIds) },
        onApplyAutoBalance = { viewModel.applyAutoBalance(campingId) },
        onClearAutoBalancePreview = viewModel::clearAutoBalancePreview,
        onClearError = viewModel::clearOperationError,
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
    scoresHidden: Boolean,
    authenticatedUserId: String,
    approvedAttendees: List<CampingAttendee>,
    autoBalancePreview: TeamBalanceResult?,
    onBack: () -> Unit,
    onOpenTeamDetail: (String) -> Unit,
    onCreateTeam: () -> Unit,
    onOpenGames: () -> Unit,
    onRefresh: () -> Unit,
    onPreviewAutoBalance: (List<String>) -> Unit,
    onApplyAutoBalance: () -> Unit,
    onClearAutoBalancePreview: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    var menuExpanded by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showAutoBalanceSheet by rememberSaveable { mutableStateOf(false) }

    val loadedTeams = (uiState as? TeamsUiState.Loaded)?.teams.orEmpty()
    if (showAutoBalanceSheet) {
        TeamAutoBalanceSheet(
            teams = loadedTeams,
            approvedAttendees = approvedAttendees,
            preview = autoBalancePreview,
            isSaving = isSaving,
            onPreview = onPreviewAutoBalance,
            onApply = {
                onApplyAutoBalance()
                showAutoBalanceSheet = false
            },
            onClearPreview = onClearAutoBalancePreview,
            onDismiss = {
                showAutoBalanceSheet = false
                onClearAutoBalancePreview()
            },
        )
    }

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
                            }
                        }
                    }
                },
                windowInsets = WindowInsets()
            )
        },
        modifier = modifier,
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
            when (val s = uiState) {
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
                        message = s.message,
                        onRetry = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is TeamsUiState.Loaded -> {
                    TeamsLoadedContent(
                        teams = s.teams,
                        scoresHidden = scoresHidden,
                        authenticatedUserId = authenticatedUserId,
                        onOpenTeamDetail = { onOpenTeamDetail(it) },
                    )
                }
            }
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
    onPreview: (List<String>) -> Unit,
    onApply: () -> Unit,
    onClearPreview: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    var selectedTeamIds by rememberSaveable(teams.map { it.id }) {
        mutableStateOf(teams.map { it.id })
    }
    val orderedSelectedTeamIds = teams.map { it.id }.filter { it in selectedTeamIds }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        modifier = modifier,
    ) {
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
                                .clickable {
                                    selectedTeamIds = if (team.id in selectedTeamIds) {
                                        selectedTeamIds - team.id
                                    } else {
                                        selectedTeamIds + team.id
                                    }
                                    onClearPreview()
                                }
                                .padding(horizontal = CzSpacing.sm, vertical = CzSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                        ) {
                            Checkbox(
                                checked = team.id in selectedTeamIds,
                                onCheckedChange = { checked ->
                                    selectedTeamIds = if (checked) (selectedTeamIds + team.id).distinct() else selectedTeamIds - team.id
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
                    onClick = { onPreview(orderedSelectedTeamIds) },
                    enabled = orderedSelectedTeamIds.isNotEmpty() && approvedAttendees.isNotEmpty() && !isSaving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.teams_auto_balance_preview_action))
                }
                Button(
                    onClick = onApply,
                    enabled = preview != null && !isSaving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.teams_auto_balance_apply))
                }
            }
        }
    }
}

@Composable
private fun AutoBalancePreviewRow(
    team: Team,
    attendees: List<CampingAttendee>,
    modifier: Modifier = Modifier,
) {
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
        TeamBadgeView(team = team, size = 36)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = team.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = attendees.take(4).joinToString { it.displayName }
                    .ifBlank { stringResource(R.string.teams_auto_balance_no_assignments) },
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "${attendees.size}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = team.colorHex.toComposeColor() ?: colors.ember,
        )
    }
}

// ── Loaded content ────────────────────────────────────────────────────────────

@Composable
private fun TeamsLoadedContent(
    teams: List<Team>,
    scoresHidden: Boolean,
    authenticatedUserId: String,
    onOpenTeamDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val personalTeam = teams.firstOrNull { t -> t.members.any { it.userId == authenticatedUserId } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xl),
    ) {
        // Personal team card
        if (personalTeam != null) {
            val member = personalTeam.members.firstOrNull { it.userId == authenticatedUserId }
            val rank = teams.indexOfFirst { it.id == personalTeam.id }.takeIf { it >= 0 }?.plus(1)
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

        // Podium for top 3
        if (teams.size >= 2) {
            TeamSectionLabel(stringResource(R.string.teams_top_teams), Icons.Outlined.EmojiEvents)
            PodiumSection(topTeams = teams.take(3), scoresHidden = scoresHidden)
        }

        // Full ranking list
        TeamSectionLabel(stringResource(R.string.teams_ranking), Icons.AutoMirrored.Outlined.List)
        RankingList(teams = teams, scoresHidden = scoresHidden, onClick = onOpenTeamDetail)

        Spacer(Modifier.height(CzSpacing.lg))
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
private fun PodiumSection(topTeams: List<Team>, scoresHidden: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (topTeams.size >= 2) PodiumBlock(team = topTeams[1], rank = 2, height = 64, scoresHidden = scoresHidden)
        Spacer(Modifier.width(CzSpacing.sm))
        PodiumBlock(team = topTeams[0], rank = 1, height = 88, scoresHidden = scoresHidden)
        Spacer(Modifier.width(CzSpacing.sm))
        if (topTeams.size >= 3) PodiumBlock(team = topTeams[2], rank = 3, height = 48, scoresHidden = scoresHidden)
    }
}

@Composable
private fun PodiumBlock(team: Team, rank: Int, height: Int, scoresHidden: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.czColors
    val teamColor = team.colorHex.toComposeColor() ?: colors.ember
    val medalColor = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        else -> Color(0xFFCD7F32)
    }

    Column(
        modifier = modifier.width(96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        TeamBadgeView(team = team, size = if (rank == 1) 48 else 40)
        Spacer(Modifier.height(4.dp))
        Text(
            text = team.name,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (scoresHidden) stringResource(R.string.teams_scores_hidden_short) else "${team.totalScore} pts",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            maxLines = 1,
        )
        Spacer(Modifier.height(CzSpacing.xs))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .clip(RoundedCornerShape(topStart = CzRadius.sm, topEnd = CzRadius.sm))
                .background(teamColor.copy(alpha = 0.18f))
                .border(1.dp, teamColor.copy(alpha = 0.35f), RoundedCornerShape(topStart = CzRadius.sm, topEnd = CzRadius.sm)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = medalColor,
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
            text = "#$rank",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = when (rank) {
                1 -> Color(0xFFFFD700)
                2 -> Color(0xFFC0C0C0)
                3 -> Color(0xFFCD7F32)
                else -> colors.textSecondary
            },
            modifier = Modifier.width(28.dp),
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

// ── Team badge view ───────────────────────────────────────────────────────────

@Composable
fun TeamBadgeView(team: Team, size: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.czColors
    val teamColor = team.colorHex.toComposeColor() ?: colors.ember

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(teamColor.copy(alpha = 0.18f))
            .border(1.5.dp, teamColor.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val photoUrl = team.photoUrl
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = team.name,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Text(
                text = team.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = (size * 0.35f).sp),
                color = teamColor,
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
    val c = android.graphics.Color.parseColor(this)
    Color(c)
}.getOrNull()

fun TeamMemberRole.displayName(): String = when (this) {
    fr.ziyon.campzone.data.model.TeamMemberRole.Captain -> "Captain"
    fr.ziyon.campzone.data.model.TeamMemberRole.ViceCaptain -> "Vice-captain"
    fr.ziyon.campzone.data.model.TeamMemberRole.Member -> "Member"
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun TeamsScreenPreview() {
    CampzoneTheme {
        val service = FakeTeamService()
        TeamsScreen(
            campingId = "preview-camping",
            uiState = TeamsUiState.Loaded(
                listOf(
                    Team("lions", "preview-camping", "Lions", "Courage!", "flame.fill", "#D9432F", points = 180, members = listOf(TeamMember("u1", "u1", "Admin", "SDA", role = fr.ziyon.campzone.data.model.TeamMemberRole.Captain, personalScore = 40))),
                    Team("eagles", "preview-camping", "Eagles", "Higher!", "paperplane.fill", "#2364AA", points = 150, members = listOf(TeamMember("u2", "u2", "Marc", "SDA", personalScore = 20))),
                )
            ),
            isSaving = false,
            operationError = null,
            canManageTeams = true,
            scoresHidden = false,
            authenticatedUserId = "u1",
            approvedAttendees = emptyList(),
            autoBalancePreview = null,
            onBack = {},
            onOpenTeamDetail = {},
            onCreateTeam = {},
            onOpenGames = {},
            onRefresh = {},
            onPreviewAutoBalance = {},
            onApplyAutoBalance = {},
            onClearAutoBalancePreview = {},
            onClearError = {},
        )
    }
}
