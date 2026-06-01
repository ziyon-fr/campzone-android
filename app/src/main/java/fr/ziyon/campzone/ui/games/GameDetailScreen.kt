@file:android.annotation.SuppressLint("ViewModelConstructorInComposable")

package fr.ziyon.campzone.ui.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import fr.ziyon.campzone.data.games.previewGame
import fr.ziyon.campzone.data.model.Activity
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.Game
import fr.ziyon.campzone.data.model.PointRule
import fr.ziyon.campzone.data.model.Team
import fr.ziyon.campzone.data.camping.PreviewCampingService
import fr.ziyon.campzone.data.teams.FakeTeamService
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzSectionHeader
import kotlinx.coroutines.launch

@Composable
fun GameDetailRoute(
    gameId: String,
    campingId: String,
    camping: Camping?,
    teams: List<Team>,
    authenticatedUser: AuthenticatedUser,
    viewModel: GameViewModel,
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit,
    onOpenPointHistory: () -> Unit,
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
    val canManage = campingCtx != null && evaluator.canManageGames(permissionUser, campingCtx)
    val canAssign = campingCtx != null && evaluator.canAssignPoints(permissionUser, campingCtx)
    val canSeeHidden = campingCtx != null && (
        evaluator.canRevealWinners(permissionUser, campingCtx) ||
            evaluator.canManageGames(permissionUser, campingCtx)
        )

    LaunchedEffect(campingId) { viewModel.loadIfNeeded(campingId) }

    val uiState by viewModel.uiState.collectAsState()
    val game = viewModel.game(gameId, campingId)
    val visibleActivities = camping?.let { viewModel.visibleActivities(it, canSeeHidden) }
        ?.filter { it.gameId == gameId } ?: emptyList()

    when {
        uiState is GamesUiState.Loading -> CzLoadingView()
        game == null && uiState !is GamesUiState.Loading -> CzErrorState(
            title = stringResource(R.string.games_title),
            message = stringResource(R.string.games_not_found),
        )
        game != null -> GameDetailScreen(
            game = game,
            camping = camping,
            teams = teams,
            activities = visibleActivities,
            canManage = canManage,
            canAssign = canAssign,
            viewModel = viewModel,
            authenticatedUser = authenticatedUser,
            onBack = onBack,
            onOpenEditor = { onOpenEditor(game.id) },
            onOpenPointHistory = onOpenPointHistory,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameDetailScreen(
    game: Game,
    camping: Camping?,
    teams: List<Team>,
    activities: List<Activity>,
    canManage: Boolean,
    canAssign: Boolean,
    viewModel: GameViewModel,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenPointHistory: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteAlert by remember { mutableStateOf(false) }
    var showResetAlert by remember { mutableStateOf(false) }
    var showAwardSheet by remember { mutableStateOf(false) }
    var preselectedRuleId by remember { mutableStateOf<String?>(null) }

    if (showDeleteAlert) {
        AlertDialog(
            onDismissRequest = { showDeleteAlert = false },
            title = { Text(stringResource(R.string.common_delete_confirm_title)) },
            text = { Text(stringResource(R.string.common_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAlert = false
                        scope.launch {
                            if (viewModel.deleteGame(game)) onBack()
                        }
                    },
                ) { Text(stringResource(R.string.common_delete), color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAlert = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showResetAlert) {
        AlertDialog(
            onDismissRequest = { showResetAlert = false },
            title = { Text(stringResource(R.string.games_reset_title)) },
            text = { Text(stringResource(R.string.games_reset_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetAlert = false
                        scope.launch { viewModel.resetGameData(game, teams) }
                    },
                ) { Text(stringResource(R.string.games_reset_confirm), color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetAlert = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showAwardSheet && canAssign) {
        AwardPointsSheet(
            game = game,
            camping = camping,
            teams = teams,
            preselectedRuleId = preselectedRuleId,
            authenticatedUser = authenticatedUser,
            viewModel = viewModel,
            onDismiss = {
                preselectedRuleId = null
                showAwardSheet = false
            },
        )
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.games_detail_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.common_back), tint = colors.textPrimary)
                    }
                },
                actions = {
                    if (canManage) {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.common_options), tint = colors.textPrimary)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_edit)) },
                                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                onClick = { showMenu = false; onOpenEditor() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.games_reset)) },
                                leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                onClick = { showMenu = false; showResetAlert = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_delete), color = colors.error) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = colors.error) },
                                onClick = { showMenu = false; showDeleteAlert = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                windowInsets = WindowInsets()
            )
        },
        bottomBar = {
            if (canAssign) {
                Surface(color = colors.background) {
                    Button(
                        onClick = { preselectedRuleId = null; showAwardSheet = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = CzSpacing.lg, vertical = CzSpacing.md),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.ember),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.games_award_points),
                            modifier = Modifier.padding(start = CzSpacing.sm),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = CzSpacing.lg, vertical = CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
        ) {
            item { GameHeader(game = game) }

            if (game.rules.isNotBlank()) {
                item { GameRulesCard(rules = game.rules) }
            }

            item { CzSectionHeader(title = stringResource(R.string.games_point_rules)) }

            if (game.pointRules.isEmpty()) {
                item {
                    Surface(color = colors.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.games_no_rules),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(CzSpacing.lg),
                        )
                    }
                }
            } else {
                val grouped = game.pointRules.groupByCategory()
                items(grouped, key = { it.first }) { (category, rules) ->
                    PointRulesCategoryCard(
                        category = category,
                        rules = rules,
                        canAssign = canAssign,
                        onRuleTap = { ruleId ->
                            preselectedRuleId = ruleId
                            showAwardSheet = true
                        },
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CzSectionHeader(title = stringResource(R.string.games_section_activity))
                    if (activities.isNotEmpty()) {
                        TextButton(onClick = onOpenPointHistory) {
                            Text(stringResource(R.string.common_view_all), color = colors.ember, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            if (activities.isEmpty()) {
                item {
                    Surface(color = colors.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.games_no_activity_game),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(CzSpacing.lg),
                        )
                    }
                }
            } else {
                item {
                    Surface(color = colors.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            activities.take(8).forEachIndexed { index, activity ->
                                ActivityRow(activity = activity)
                                if (index < minOf(7, activities.size - 1)) {
                                    HorizontalDivider(color = colors.divider, modifier = Modifier.padding(start = CzSpacing.lg))
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
private fun GameHeader(game: Game) {
    val colors = MaterialTheme.czColors
    Surface(color = colors.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CzSpacing.lg), verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                Icon(Icons.Outlined.SportsEsports, null, tint = colors.ember, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.games_detail_subtitle), style = MaterialTheme.typography.labelSmall, color = colors.ember)
            }
            Text(game.name, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
            game.createdAt?.let { date ->
                val formatted = remember(date) {
                    java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(date)
                }
                Text(
                    stringResource(R.string.games_created_on, formatted),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun GameRulesCard(rules: String) {
    val colors = MaterialTheme.czColors
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        CzSectionHeader(title = stringResource(R.string.games_rules_section))
        Surface(color = colors.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Text(rules, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.padding(CzSpacing.lg))
        }
    }
}

@Composable
private fun PointRulesCategoryCard(
    category: String,
    rules: List<PointRule>,
    canAssign: Boolean,
    onRuleTap: (String) -> Unit,
) {
    val colors = MaterialTheme.czColors
    Surface(color = colors.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CzSpacing.lg), verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            Text(category, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            rules.forEachIndexed { index, rule ->
                PointRuleRow(
                    rule = rule,
                    onClick = if (canAssign) ({ onRuleTap(rule.id) }) else null,
                )
                if (index < rules.size - 1) {
                    HorizontalDivider(color = colors.divider)
                }
            }
        }
    }
}

@Composable
private fun PointRuleRow(rule: PointRule, onClick: (() -> Unit)?) {
    val colors = MaterialTheme.czColors
    val content: @Composable () -> Unit = {
        Row(modifier = Modifier.padding(vertical = CzSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.name, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                if (rule.reason.isNotBlank()) {
                    Text(rule.reason, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 2)
                }
                Text(
                    "${rule.appliesTo.displayName} · ${rule.visibility.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
            Spacer(Modifier.height(0.dp))
            Text(
                if (rule.points >= 0) "+${rule.points}" else "${rule.points}",
                style = MaterialTheme.typography.titleSmall,
                color = if (rule.points >= 0) colors.success else colors.error,
            )
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, color = colors.surface, modifier = Modifier.fillMaxWidth()) { content() }
    } else {
        content()
    }
}

private fun List<PointRule>.groupByCategory(): List<Pair<String, List<PointRule>>> {
    val uncategorized = "Uncategorized"
    val grouped = groupBy { rule ->
        rule.category?.trim()?.takeUnless { it.isBlank() } ?: uncategorized
    }
    return grouped.entries
        .sortedWith(compareBy { if (it.key == uncategorized) "￿" else it.key.lowercase() })
        .map { it.key to it.value }
}

@Preview(showBackground = true)
@Composable
private fun GameDetailScreenPreview() {
    CampzoneTheme {
        GameDetailRoute(
            gameId = "game-1",
            campingId = "preview-camp",
            camping = null,
            teams = emptyList(),
            authenticatedUser = AuthenticatedUser(
                uid = "uid", email = "a@b.com", displayName = "Admin", photoUrl = null,
                role = UserRole.Admin, church = "Central SDA", age = 30,
                preferredLanguage = "en", gender = null, onboardingCompleted = true,
            ),
            viewModel = GameViewModel(
                FakeGameService(games = listOf(previewGame())),
                FakeTeamService(),
                PreviewCampingService(),
                fr.ziyon.campzone.data.teams.FakeTeamNotificationDispatcher(),
            ),
            onBack = {}, onOpenEditor = {}, onOpenPointHistory = {},
        )
    }
}
