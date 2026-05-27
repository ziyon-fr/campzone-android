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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import fr.ziyon.campzone.data.model.WinnerRevealPolicy
import fr.ziyon.campzone.data.camping.PreviewCampingService
import fr.ziyon.campzone.data.teams.FakeTeamService
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzColorPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzSectionHeader

@Composable
fun GamesRoute(
    campingId: String,
    camping: Camping?,
    authenticatedUser: AuthenticatedUser,
    viewModel: GameViewModel,
    onBack: () -> Unit,
    onOpenGameDetail: (String) -> Unit,
    onOpenGameEditor: (String?) -> Unit,
    onOpenPointHistory: () -> Unit,
    onOpenRevealSettings: () -> Unit,
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
    val canSeeHidden = campingCtx != null && (
        evaluator.canRevealWinners(permissionUser, campingCtx) ||
            evaluator.canManageGames(permissionUser, campingCtx)
        )
    val canRevealWinners = campingCtx != null && evaluator.canRevealWinners(permissionUser, campingCtx)

    LaunchedEffect(campingId) { viewModel.loadIfNeeded(campingId) }

    val uiState by viewModel.uiState.collectAsState()
    GamesScreen(
        campingId = campingId,
        uiState = uiState,
        camping = camping,
        canManage = canManage,
        canSeeHidden = canSeeHidden,
        canRevealWinners = canRevealWinners,
        viewModel = viewModel,
        authenticatedUser = authenticatedUser,
        onBack = onBack,
        onRetry = { viewModel.load(campingId) },
        onOpenGameDetail = onOpenGameDetail,
        onOpenGameEditor = onOpenGameEditor,
        onOpenPointHistory = onOpenPointHistory,
        onOpenRevealSettings = onOpenRevealSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GamesScreen(
    campingId: String,
    uiState: GamesUiState,
    camping: Camping?,
    canManage: Boolean,
    canSeeHidden: Boolean,
    canRevealWinners: Boolean,
    viewModel: GameViewModel,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenGameDetail: (String) -> Unit,
    onOpenGameEditor: (String?) -> Unit,
    onOpenPointHistory: () -> Unit,
    onOpenRevealSettings: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.games_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = colors.textPrimary,
                        )
                    }
                },
                actions = {
                    if (canManage) {
                        IconButton(
                            onClick = { onOpenGameEditor(null) },
                            modifier = Modifier.semantics { contentDescription = "New game" },
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.ember)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                windowInsets = WindowInsets()
            )
        },
    ) { innerPadding ->
        when (uiState) {
            is GamesUiState.Loading -> CzLoadingView(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            is GamesUiState.Error -> CzErrorState(
                title = stringResource(R.string.games_title),
                message = uiState.message,
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            is GamesUiState.Empty -> CzEmptyState(
                icon = { Icon(Icons.Outlined.SportsEsports, null, tint = MaterialTheme.czColors.ember) },
                title = stringResource(R.string.games_empty_title),
                message = stringResource(R.string.games_empty_message),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                action = if (canManage) ({
                    TextButton(onClick = { onOpenGameEditor(null) }) {
                        Text(stringResource(R.string.games_create), color = MaterialTheme.czColors.ember)
                    }
                }) else null,
            )

            is GamesUiState.Loaded -> {
                val visibleActivities = camping?.let {
                    viewModel.visibleActivities(it, canSeeHidden)
                } ?: emptyList()
                GamesContent(
                    campingId = campingId,
                    games = uiState.games,
                    activities = visibleActivities,
                    camping = camping,
                    canManage = canManage,
                    canRevealWinners = canRevealWinners,
                    viewModel = viewModel,
                    authenticatedUser = authenticatedUser,
                    innerPadding = innerPadding,
                    onOpenGameDetail = onOpenGameDetail,
                    onOpenGameEditor = onOpenGameEditor,
                    onOpenPointHistory = onOpenPointHistory,
                    onOpenRevealSettings = onOpenRevealSettings,
                )
            }
        }
    }
}

@Composable
private fun GamesContent(
    campingId: String,
    games: List<Game>,
    activities: List<Activity>,
    camping: Camping?,
    canManage: Boolean,
    canRevealWinners: Boolean,
    viewModel: GameViewModel,
    authenticatedUser: AuthenticatedUser,
    innerPadding: PaddingValues,
    onOpenGameDetail: (String) -> Unit,
    onOpenGameEditor: (String?) -> Unit,
    onOpenPointHistory: () -> Unit,
    onOpenRevealSettings: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(horizontal = CzSpacing.lg, vertical = CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        // Reveal banner (mirrors iOS revealBanner)
        if (camping != null) {
            val policy = camping.winnerRevealPolicy
            val endDate = camping.endDate ?: java.util.Date()
            val effectiveHideDate = policy?.hideDate ?: java.util.Date(endDate.time - 24 * 60 * 60 * 1000L)
            val scoresHidden = policy != null && !(policy.isRevealed || (policy.revealDate?.let { it <= java.util.Date() } ?: false)) &&
                (policy.hideDate?.let { it <= java.util.Date() } ?: (java.util.Date() >= effectiveHideDate))
            if (scoresHidden || canRevealWinners) {
                item(key = "reveal_banner") {
                    RevealBanner(
                        campingId = campingId,
                        scoresHidden = scoresHidden,
                        canRevealWinners = canRevealWinners,
                        policy = policy,
                        viewModel = viewModel,
                        authenticatedUser = authenticatedUser,
                        scope = scope,
                        colors = colors,
                        onOpenRevealSettings = onOpenRevealSettings,
                    )
                }
            }
        }

        item { CzSectionHeader(title = stringResource(R.string.games_section_games)) }

        if (games.isEmpty()) {
            item {
                Surface(color = colors.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(CzSpacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                    ) {
                        Icon(Icons.Outlined.SportsEsports, null, tint = colors.ember, modifier = Modifier.size(36.dp))
                        Text(stringResource(R.string.games_empty_title), style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                        Text(stringResource(R.string.games_empty_message), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        if (canManage) {
                            TextButton(onClick = { onOpenGameEditor(null) }) {
                                Text(stringResource(R.string.games_create), color = colors.ember)
                            }
                        }
                    }
                }
            }
        } else {
            items(games, key = { it.id }) { game ->
                GameRow(game = game, onClick = { onOpenGameDetail(game.id) })
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
                        stringResource(R.string.games_no_activity),
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

@Composable
private fun GameRow(game: Game, onClick: () -> Unit) {
    val colors = MaterialTheme.czColors
    Surface(onClick = onClick, color = colors.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(CzSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = colors.ember.copy(alpha = 0.18f), shape = CircleShape, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.SportsEsports, null, tint = colors.ember, modifier = Modifier.padding(8.dp))
            }
            Spacer(Modifier.width(CzSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(game.name, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1)
                Text(
                    "${game.pointRules.size} ${stringResource(R.string.games_rules_count)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun RevealBanner(
    campingId: String,
    scoresHidden: Boolean,
    canRevealWinners: Boolean,
    policy: WinnerRevealPolicy?,
    viewModel: GameViewModel,
    authenticatedUser: AuthenticatedUser,
    scope: CoroutineScope,
    colors: CzColorPalette,
    onOpenRevealSettings: () -> Unit,
) {
    val bannerColor = if (scoresHidden) colors.warning.copy(alpha = 0.12f) else colors.success.copy(alpha = 0.10f)
    Surface(
        color = bannerColor,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            ) {
                Icon(
                    if (scoresHidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = if (scoresHidden) colors.warning else colors.success,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    if (scoresHidden) stringResource(R.string.reveal_banner_hidden)
                    else stringResource(R.string.reveal_banner_visible),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textPrimary,
                )
            }
            if (canRevealWinners) {
                Spacer(Modifier.height(CzSpacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                    if (scoresHidden) {
                        Button(
                            onClick = {
                                scope.launch {
                                    viewModel.reveal(campingId, policy, authenticatedUser)
                                }
                            },
                            enabled = !viewModel.isUpdatingReveal,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.ember),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = CzSpacing.md, vertical = CzSpacing.xs,
                            ),
                        ) {
                            Icon(Icons.Outlined.EmojiEvents, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(CzSpacing.xs))
                            Text(stringResource(R.string.reveal_now), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenRevealSettings,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ember),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = CzSpacing.md, vertical = CzSpacing.xs,
                        ),
                    ) {
                        Icon(Icons.Outlined.Settings, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(CzSpacing.xs))
                        Text(stringResource(R.string.reveal_settings), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GamesScreenPreview() {
    CampzoneTheme {
        GamesRoute(
            campingId = "preview-camp",
            camping = null,
            authenticatedUser = AuthenticatedUser(
                uid = "uid", email = "a@b.com", displayName = "Admin", photoUrl = null,
                role = UserRole.Admin, church = "Central SDA", age = 30,
                preferredLanguage = "en", gender = null, onboardingCompleted = true,
            ),
            viewModel = GameViewModel(FakeGameService(games = listOf(previewGame())), FakeTeamService(), PreviewCampingService()),
            onBack = {}, onOpenGameDetail = {}, onOpenGameEditor = {}, onOpenPointHistory = {},
            onOpenRevealSettings = {},
        )
    }
}
