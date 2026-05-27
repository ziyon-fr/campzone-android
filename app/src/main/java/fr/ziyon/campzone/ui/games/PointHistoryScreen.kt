package fr.ziyon.campzone.ui.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.games.FakeGameService
import fr.ziyon.campzone.data.model.Activity
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.teams.FakeTeamService
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import java.util.Calendar
import java.util.Date

private enum class ActivityPeriod {
    Today, Yesterday, ThisWeek, Earlier;

    companion object {
        fun of(date: Date, now: Date): ActivityPeriod {
            val cal = Calendar.getInstance()
            cal.time = now
            val calDate = Calendar.getInstance()
            calDate.time = date
            return when {
                cal.get(Calendar.DAY_OF_YEAR) == calDate.get(Calendar.DAY_OF_YEAR) &&
                    cal.get(Calendar.YEAR) == calDate.get(Calendar.YEAR) -> Today
                cal.get(Calendar.DAY_OF_YEAR) - calDate.get(Calendar.DAY_OF_YEAR) == 1 &&
                    cal.get(Calendar.YEAR) == calDate.get(Calendar.YEAR) -> Yesterday
                cal.get(Calendar.WEEK_OF_YEAR) == calDate.get(Calendar.WEEK_OF_YEAR) &&
                    cal.get(Calendar.YEAR) == calDate.get(Calendar.YEAR) -> ThisWeek
                else -> Earlier
            }
        }
    }
}

@Composable
fun PointHistoryRoute(
    campingId: String,
    teamId: String?,
    camping: Camping?,
    authenticatedUser: AuthenticatedUser,
    viewModel: GameViewModel,
    onBack: () -> Unit,
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
    val canSeeHidden = campingCtx != null && (
        evaluator.canRevealWinners(permissionUser, campingCtx) ||
            evaluator.canManageGames(permissionUser, campingCtx)
        )

    LaunchedEffect(campingId) { viewModel.loadIfNeeded(campingId) }
    val uiState by viewModel.uiState.collectAsState()

    val allActivities = camping?.let { viewModel.visibleActivities(it, canSeeHidden) } ?: emptyList()
    val games = viewModel.gamesFor(campingId)

    PointHistoryScreen(
        activities = allActivities,
        games = games.map { it.id to it.name },
        teamFilter = teamId,
        loading = uiState is GamesUiState.Loading,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PointHistoryScreen(
    activities: List<Activity>,
    games: List<Pair<String, String>>,
    teamFilter: String?,
    loading: Boolean,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    var selectedGameId by remember { mutableStateOf<String?>(null) }

    val filtered = activities.filter { activity ->
        if (teamFilter != null && activity.targetTeamId != teamFilter) return@filter false
        if (selectedGameId != null && activity.gameId != selectedGameId) return@filter false
        true
    }

    val now = remember { Date() }
    val grouped = filtered
        .groupBy { ActivityPeriod.of(it.createdAt, now) }
        .entries
        .sortedBy { it.key.ordinal }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.games_history_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.common_back), tint = colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                windowInsets = WindowInsets()
            )
        },
    ) { innerPadding ->
        when {
            loading -> CzLoadingView(modifier = Modifier.fillMaxSize().padding(innerPadding))
            else -> Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (games.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = CzSpacing.lg, vertical = CzSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                    ) {
                        item {
                            FilterChip(
                                selected = selectedGameId == null,
                                onClick = { selectedGameId = null },
                                label = { Text(stringResource(R.string.games_all_games)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.ember,
                                    selectedLabelColor = androidx.compose.ui.graphics.Color.White,
                                ),
                            )
                        }
                        items(games, key = { it.first }) { (id, name) ->
                            FilterChip(
                                selected = selectedGameId == id,
                                onClick = { selectedGameId = id },
                                label = { Text(name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.ember,
                                    selectedLabelColor = androidx.compose.ui.graphics.Color.White,
                                ),
                            )
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    CzEmptyState(
                        icon = { Icon(Icons.Outlined.History, null, tint = MaterialTheme.czColors.ember) },
                        title = stringResource(R.string.games_history_empty_title),
                        message = stringResource(R.string.games_history_empty_message),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = CzSpacing.lg, vertical = CzSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
                    ) {
                        grouped.forEach { (period, periodActivities) ->
                            item(key = period.name) {
                                Surface(
                                    color = colors.surface,
                                    shape = MaterialTheme.shapes.large,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column {
                                        Text(
                                            text = periodLabel(period),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.textPrimary,
                                            modifier = Modifier.padding(
                                                horizontal = CzSpacing.lg,
                                                vertical = CzSpacing.md,
                                            ),
                                        )
                                        HorizontalDivider(color = colors.divider)
                                        periodActivities.forEachIndexed { index, activity ->
                                            ActivityRow(activity = activity)
                                            if (index < periodActivities.size - 1) {
                                                HorizontalDivider(
                                                    color = colors.divider,
                                                    modifier = Modifier.padding(start = CzSpacing.lg),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun periodLabel(period: ActivityPeriod): String = when (period) {
    ActivityPeriod.Today -> stringResource(R.string.games_period_today)
    ActivityPeriod.Yesterday -> stringResource(R.string.games_period_yesterday)
    ActivityPeriod.ThisWeek -> stringResource(R.string.games_period_this_week)
    ActivityPeriod.Earlier -> stringResource(R.string.games_period_earlier)
}

@Preview(showBackground = true)
@Composable
private fun PointHistoryScreenPreview() {
    CampzoneTheme {
        PointHistoryRoute(
            campingId = "preview-camp",
            teamId = null,
            camping = null,
            authenticatedUser = AuthenticatedUser(
                uid = "uid", email = "a@b.com", displayName = "Admin", photoUrl = null,
                role = UserRole.Admin, church = "Central SDA", age = 30,
                preferredLanguage = "en", gender = null, onboardingCompleted = true,
            ),
            viewModel = GameViewModel(FakeGameService(), FakeTeamService()),
            onBack = {},
        )
    }
}
