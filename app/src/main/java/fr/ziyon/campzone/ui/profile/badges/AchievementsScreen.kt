package fr.ziyon.campzone.ui.profile.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzAvatar
import fr.ziyon.campzone.core.designsystem.CzAvatarSize
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzCard
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.Achievement
import fr.ziyon.campzone.data.model.AchievementCatalog
import fr.ziyon.campzone.data.model.AchievementRarity
import fr.ziyon.campzone.data.model.BadgeTint
import fr.ziyon.campzone.data.model.BadgeViewModel
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.EarnedBadge
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.Team
import fr.ziyon.campzone.data.model.displayName
import fr.ziyon.campzone.data.model.displayOrder
import java.text.DateFormat
import java.util.Date

@Composable
fun AchievementsRoute(
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    viewModel: AchievementViewModel = hiltViewModel(),
) {
    LaunchedEffect(authenticatedUser.uid) { viewModel.loadProfileBadges(authenticatedUser.uid) }
    val state by viewModel.uiState.collectAsState()
    AchievementsScreen(
        state = state,
        displayName = authenticatedUser.displayName.ifBlank { authenticatedUser.email },
        photoUrl = authenticatedUser.photoUrl,
        badgesFor = viewModel::badgesFor,
        onBack = onBack,
        onRetry = { viewModel.loadProfileBadges(authenticatedUser.uid) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    state: AchievementUiState,
    displayName: String,
    photoUrl: String?,
    badgesFor: (List<EarnedBadge>) -> List<BadgeViewModel>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<BadgeViewModel?>(null) }
    Scaffold(
        topBar = { BadgeTopBar(title = stringResource(R.string.profile_my_achievements), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (state) {
            AchievementUiState.Loading -> CenterState("Loading badges...", modifier.padding(padding))
            is AchievementUiState.Error -> ErrorState(state.message, onRetry, modifier.padding(padding))
            is AchievementUiState.Loaded -> {
                val badges = badgesFor(state.earned)
                LazyColumn(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(CzSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
                ) {
                    item {
                        BadgeSummaryHeader(
                            displayName = displayName,
                            photoUrl = photoUrl,
                            earnedCount = state.earned.size,
                            totalCount = AchievementCatalog.all.size,
                        )
                    }
                    AchievementRarity.entries.sortedBy { it.displayOrder }.forEach { rarity ->
                        val tier = badges.filter { it.achievement.rarity == rarity }
                        item {
                            TierHeader(rarity, tier.count { it.isEarned }, tier.size)
                        }
                        items(tier, key = { it.id }) { badge ->
                            BadgeRow(badge, onClick = { selected = badge })
                        }
                    }
                }
            }
        }
    }
    selected?.let { badge ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            BadgeDetailSheet(badge)
        }
    }
}

@Composable
fun CampingBadgeAwardRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    viewModel: AchievementViewModel = hiltViewModel(),
) {
    LaunchedEffect(campingId, authenticatedUser.uid) { viewModel.loadAwardSurface(campingId, authenticatedUser) }
    val state by viewModel.awardState.collectAsState()
    val saving by viewModel.isSaving.collectAsState()
    val message by viewModel.operationMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message!!)
            viewModel.dismissOperationMessage()
        }
    }
    CampingBadgeAwardScreen(
        state = state,
        isSaving = saving,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = { viewModel.loadAwardSurface(campingId, authenticatedUser) },
        onSelectAchievement = viewModel::selectAchievement,
        onSelectTargetMode = viewModel::selectTargetMode,
        onSelectTeam = viewModel::selectTeam,
        onSelectAttendee = viewModel::selectAttendee,
        onNoteChange = viewModel::updateNote,
        onAward = { viewModel.awardSelected(authenticatedUser.uid) },
    )
}

@Composable
fun CampingBadgeAwardScreen(
    state: BadgeAwardUiState,
    isSaving: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelectAchievement: (String) -> Unit,
    onSelectTargetMode: (BadgeAwardTargetMode) -> Unit,
    onSelectTeam: (String) -> Unit,
    onSelectAttendee: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onAward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = { BadgeTopBar(title = stringResource(R.string.camping_award_badges), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (state) {
            BadgeAwardUiState.Loading -> CenterState("Loading badges...", modifier.padding(padding))
            BadgeAwardUiState.Restricted -> ErrorState("Only camp leadership can award achievements for this camping.", onBack, modifier.padding(padding))
            is BadgeAwardUiState.Error -> ErrorState(state.message, onRetry, modifier.padding(padding))
            is BadgeAwardUiState.Loaded -> AwardLoadedContent(
                state = state,
                isSaving = isSaving,
                onSelectAchievement = onSelectAchievement,
                onSelectTargetMode = onSelectTargetMode,
                onSelectTeam = onSelectTeam,
                onSelectAttendee = onSelectAttendee,
                onNoteChange = onNoteChange,
                onAward = onAward,
                modifier = modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun AwardLoadedContent(
    state: BadgeAwardUiState.Loaded,
    isSaving: Boolean,
    onSelectAchievement: (String) -> Unit,
    onSelectTargetMode: (BadgeAwardTargetMode) -> Unit,
    onSelectTeam: (String) -> Unit,
    onSelectAttendee: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onAward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val approvedAttendees = state.camping.attendees
        .filter { it.registrationStatus == RegistrationApprovalStatus.Approved }
        .sortedBy { it.displayName.lowercase() }
    val selectedAchievement = AchievementCatalog.achievement(state.selectedAchievementId) ?: AchievementCatalog.manual.first()
    val recipients = when (state.selectedTargetMode) {
        BadgeAwardTargetMode.Team -> state.teams.firstOrNull { it.id == state.selectedTeamId }?.members.orEmpty().map { it.displayName }
        BadgeAwardTargetMode.Individual -> approvedAttendees.firstOrNull { it.id == state.selectedAttendeeId }?.let { listOf(it.displayName) }.orEmpty()
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        CzCard {
            Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                BadgeCard(selectedAchievement, isEarned = true, modifier = Modifier.size(56.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.camping.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Award manual achievements to a whole team or one approved participant.", color = MaterialTheme.czColors.textSecondary)
                }
            }
        }
        AchievementPicker(selectedAchievement.id, onSelectAchievement)
        TargetPicker(
            mode = state.selectedTargetMode,
            teams = state.teams,
            attendees = approvedAttendees,
            selectedTeamId = state.selectedTeamId,
            selectedAttendeeId = state.selectedAttendeeId,
            onSelectTargetMode = onSelectTargetMode,
            onSelectTeam = onSelectTeam,
            onSelectAttendee = onSelectAttendee,
        )
        CzCard {
            Text("Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(CzSpacing.sm))
            if (recipients.isEmpty()) {
                Text("No eligible recipients selected yet.", color = MaterialTheme.czColors.textSecondary)
            } else {
                Text(
                    if (recipients.size == 1) recipients.first() else "${recipients.size} participants selected",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                recipients.take(8).forEach { Text(it, color = MaterialTheme.czColors.textSecondary) }
            }
            Spacer(Modifier.size(CzSpacing.sm))
            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Optional note") },
                minLines = 2,
            )
        }
        Spacer(Modifier.weight(1f))
        CzButton(
            text = if (recipients.size == 1) "Award to 1 Participant" else "Award to ${recipients.size} Participants",
            onClick = onAward,
            enabled = recipients.isNotEmpty(),
            loading = isSaving,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Filled.WorkspacePremium, contentDescription = null) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AchievementPicker(selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = AchievementCatalog.achievement(selectedId) ?: AchievementCatalog.manual.first()
    CzCard {
        Text("Badge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(CzSpacing.sm))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected.title,
                onValueChange = {},
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                readOnly = true,
                label = { Text("Badge") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AchievementCatalog.manual.forEach { achievement ->
                    DropdownMenuItem(
                        text = { Text(achievement.title) },
                        leadingIcon = { Icon(achievement.icon, contentDescription = null) },
                        onClick = {
                            onSelect(achievement.id)
                            expanded = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.size(CzSpacing.sm))
        Text(selected.summary, color = MaterialTheme.czColors.textSecondary)
        Text("7 automatic badges stay managed by Campzone when backend requirements are met.", color = MaterialTheme.czColors.textSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetPicker(
    mode: BadgeAwardTargetMode,
    teams: List<Team>,
    attendees: List<CampingAttendee>,
    selectedTeamId: String?,
    selectedAttendeeId: String?,
    onSelectTargetMode: (BadgeAwardTargetMode) -> Unit,
    onSelectTeam: (String) -> Unit,
    onSelectAttendee: (String) -> Unit,
) {
    CzCard {
        Text("Recipients", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            AssistChip(onClick = { onSelectTargetMode(BadgeAwardTargetMode.Team) }, label = { Text("Team") })
            AssistChip(onClick = { onSelectTargetMode(BadgeAwardTargetMode.Individual) }, label = { Text("Individual") })
        }
        val options = if (mode == BadgeAwardTargetMode.Team) teams.map { it.id to it.name } else attendees.map { it.id to it.displayName }
        var expanded by remember(mode) { mutableStateOf(false) }
        val selected = options.firstOrNull { it.first == if (mode == BadgeAwardTargetMode.Team) selectedTeamId else selectedAttendeeId }
        if (options.isEmpty()) {
            Text(
                if (mode == BadgeAwardTargetMode.Team) "Create a team before awarding a team badge." else "Approve registrations before awarding individual badges.",
                color = MaterialTheme.czColors.textSecondary,
            )
        } else {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selected?.second.orEmpty(),
                    onValueChange = {},
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    readOnly = true,
                    label = { Text(if (mode == BadgeAwardTargetMode.Team) "Team" else "Participant") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.second) },
                            onClick = {
                                if (mode == BadgeAwardTargetMode.Team) onSelectTeam(option.first) else onSelectAttendee(option.first)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BadgeTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun BadgeSummaryHeader(displayName: String, photoUrl: String?, earnedCount: Int, totalCount: Int) {
    val progress = if (totalCount == 0) 0f else earnedCount.toFloat() / totalCount.toFloat()
    CzCard(contentPadding = PaddingValues(CzSpacing.lg)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(96.dp), color = MaterialTheme.czColors.ember)
                CzAvatar(
                    imageUrl = photoUrl,
                    contentDescription = displayName,
                    initials = displayName,
                    size = CzAvatarSize.Large,
                )
            }
            Text(displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Badges earned across Campzone events", color = MaterialTheme.czColors.textSecondary)
            Text("$earnedCount of $totalCount", color = MaterialTheme.czColors.textSecondary, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = CzSpacing.md))
        }
    }
}

@Composable
private fun TierHeader(rarity: AchievementRarity, earned: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        Text(rarity.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("$earned of $total", color = MaterialTheme.czColors.textSecondary)
    }
}

@Composable
private fun BadgeRow(badge: BadgeViewModel, onClick: () -> Unit) {
    CzCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentDescription = "${badge.achievement.title}. ${if (badge.isEarned) "Earned" else "Locked"}. ${badge.achievement.summary}",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            BadgeCard(badge.achievement, badge.isEarned, Modifier.size(56.dp))
            Column(Modifier.weight(1f)) {
                Text(badge.achievement.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(badge.achievement.summary, color = MaterialTheme.czColors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                if (badge.isEarned) badge.achievement.rarity.materialName else badge.achievement.rarity.displayName,
                color = badgeColor(badge.achievement.tint),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun BadgeDetailSheet(badge: BadgeViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CzSpacing.xl, vertical = CzSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        BadgeCard(badge.achievement, badge.isEarned, Modifier.size(96.dp))
        Text(badge.achievement.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(badge.achievement.detail, color = MaterialTheme.czColors.textSecondary, textAlign = TextAlign.Center)
        if (badge.earned != null) {
            Text("Earned ${DateFormat.getDateInstance().format(badge.earned.earnedAt ?: Date())}", color = MaterialTheme.czColors.success)
            badge.earned.note?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.czColors.textSecondary) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.czColors.textSecondary)
                Text("Not earned yet", color = MaterialTheme.czColors.textSecondary)
            }
        }
    }
}

@Composable
private fun CenterState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            CircularProgressIndicator()
            Text(message)
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            Icon(Icons.Filled.MilitaryTech, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.czColors.textSecondary)
            Text(message, textAlign = TextAlign.Center, color = MaterialTheme.czColors.textSecondary)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
        }
    }
}

@Composable
private fun badgeColor(tint: BadgeTint): Color = when (tint) {
    BadgeTint.Ember -> MaterialTheme.czColors.ember
    BadgeTint.Amber -> MaterialTheme.czColors.amber
    BadgeTint.Pine -> MaterialTheme.czColors.pine
    BadgeTint.Sky -> MaterialTheme.czColors.pine
    BadgeTint.Rose -> MaterialTheme.czColors.error
    BadgeTint.Gold -> MaterialTheme.czColors.warning
}

@Preview(showBackground = true)
@Composable
private fun AchievementsScreenPreview() {
    CampzoneTheme {
        AchievementsScreen(
            state = AchievementUiState.Loaded(
                "preview-user",
                listOf(EarnedBadge("first-adventure", "preview-user", Date()), EarnedBadge("team-captain", "preview-user", Date())),
            ),
            displayName = "Lea Muller",
            photoUrl = null,
            badgesFor = { earned -> AchievementCatalog.all.take(6).map { BadgeViewModel(it, earned.firstOrNull { e -> e.id == it.id }) } },
            onBack = {},
            onRetry = {},
        )
    }
}
