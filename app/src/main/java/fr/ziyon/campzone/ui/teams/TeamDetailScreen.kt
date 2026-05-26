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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
import fr.ziyon.campzone.data.model.TeamPenalty

// ── Route ─────────────────────────────────────────────────────────────────────

@Composable
fun TeamDetailRoute(
    teamId: String,
    campingId: String,
    camping: Camping?,
    authenticatedUser: AuthenticatedUser,
    approvedAttendees: List<CampingAttendee>,
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit,
    onOpenTeamChat: (String, String) -> Unit,
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

    val uiState by viewModel.uiState.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()

    LaunchedEffect(campingId) { viewModel.loadIfNeeded(campingId) }
    DisposableEffect(campingId) {
        viewModel.startObserving(campingId)
        onDispose { viewModel.stopObserving() }
    }

    val team = viewModel.team(teamId, campingId)

    TeamDetailScreen(
        team = team,
        campingId = campingId,
        canManageTeams = canManageTeams,
        authenticatedUserId = authenticatedUser.uid,
        approvedAttendees = approvedAttendees,
        isSaving = isSaving,
        operationError = operationError,
        operationMessage = operationMessage,
        onBack = onBack,
        onOpenEditor = { onOpenEditor(teamId) },
        onOpenTeamChat = { onOpenTeamChat(campingId, teamId) },
        onDeleteTeam = { viewModel.deleteTeam(teamId, campingId, onBack) },
        onAssignMember = { member -> viewModel.assignMember(member, teamId, campingId) },
        onRemoveMember = { memberId -> viewModel.removeMember(memberId, teamId, campingId) },
        onUpdateMemberRole = { memberId, role -> viewModel.updateMemberRole(memberId, role, teamId, campingId) },
        onUpdateScore = { delta -> viewModel.updateTeamScore(teamId, campingId, delta) },
        onApplyPenalty = { pts, reason -> viewModel.applyPenalty(teamId, campingId, pts, reason) },
        onClearError = viewModel::clearOperationError,
        onClearMessage = viewModel::clearOperationMessage,
        allTeams = viewModel.teams(campingId),
    )
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamDetailScreen(
    team: Team?,
    campingId: String,
    canManageTeams: Boolean,
    authenticatedUserId: String,
    approvedAttendees: List<CampingAttendee>,
    isSaving: Boolean,
    operationError: String?,
    operationMessage: String?,
    allTeams: List<Team>,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenTeamChat: () -> Unit,
    onDeleteTeam: () -> Unit,
    onAssignMember: (TeamMember) -> Unit,
    onRemoveMember: (String) -> Unit,
    onUpdateMemberRole: (String, TeamMemberRole) -> Unit,
    onUpdateScore: (Int) -> Unit,
    onApplyPenalty: (Int, String) -> Unit,
    onClearError: () -> Unit,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    var showDeleteAlert by remember { mutableStateOf(false) }

    if (showDeleteAlert) {
        AlertDialog(
            onDismissRequest = { showDeleteAlert = false },
            title = { Text(stringResource(R.string.teams_delete_title)) },
            text = { Text(stringResource(R.string.teams_delete_message)) },
            confirmButton = {
                TextButton(onClick = { showDeleteAlert = false; onDeleteTeam() }) {
                    Text(stringResource(R.string.common_delete), color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAlert = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(team?.name ?: stringResource(R.string.teams_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.textPrimary,
                    actionIconContentColor = colors.textPrimary,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (canManageTeams && team != null) {
                        IconButton(onClick = onOpenEditor) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.teams_edit_team))
                        }
                    }
                }, windowInsets = WindowInsets()
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        if (team == null) {
            fr.ziyon.campzone.core.designsystem.CzEmptyState(
                title = stringResource(R.string.teams_not_found),
                message = "",
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
            return@Scaffold
        }

        val teamColor = team.colorHex.toComposeColor() ?: colors.ember

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.xl),
        ) {
            // Error banner
            if (operationError != null) {
                ErrorBanner(message = operationError, onDismiss = onClearError)
            }

            // Header
            TeamHeaderSection(team = team, teamColor = teamColor)

            // Score breakdown
            TeamDetailSectionLabel(stringResource(R.string.teams_score_breakdown), Icons.Outlined.BarChart)
            ScoreBreakdownRow(team = team)

            // Team chat link (visible to members or managers)
            val isTeamMember = team.members.any { it.userId == authenticatedUserId }
            if (isTeamMember || canManageTeams) {
                TeamChatLink(team = team, teamColor = teamColor, onClick = onOpenTeamChat)
            }

            // Members
            TeamDetailSectionLabel(stringResource(R.string.teams_members), Icons.Outlined.Groups)
            MembersSection(
                team = team,
                canManage = canManageTeams,
                onRemoveMember = onRemoveMember,
                onUpdateRole = onUpdateMemberRole,
            )

            // Penalties
            if (team.penalties.isNotEmpty()) {
                TeamDetailSectionLabel(stringResource(R.string.teams_penalties), Icons.Outlined.TrendingDown)
                PenaltiesSection(penalties = team.penalties)
            }

            // Management (admin only)
            if (canManageTeams) {
                TeamDetailSectionLabel(stringResource(R.string.teams_management), Icons.Outlined.Settings)
                AssignMemberCard(
                    approvedAttendees = approvedAttendees,
                    allTeams = allTeams,
                    currentTeamId = team.id,
                    onAssign = { attendee, role ->
                        val member = TeamMember(
                            id = attendee.id,
                            userId = attendee.userId,
                            displayName = attendee.displayName,
                            church = attendee.church,
                            role = role,
                            personalScore = 0,
                            ageGroup = attendee.ageGroup,
                            gender = attendee.gender,
                            preferredLanguage = attendee.preferredLanguage,
                            languages = attendee.languages,
                            photoUrl = attendee.photoUrl,
                        )
                        onAssignMember(member)
                    },
                )
                ScoreControlCard(onUpdateScore = onUpdateScore)
                PenaltyControlCard(onApplyPenalty = onApplyPenalty)
                TextButton(
                    onClick = { showDeleteAlert = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = colors.error)
                    Spacer(Modifier.width(CzSpacing.xs))
                    Text(stringResource(R.string.teams_delete_team), color = colors.error)
                }
            }

            Spacer(Modifier.height(CzSpacing.lg))
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun TeamHeaderSection(team: Team, teamColor: Color, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.czColors
    Row(modifier = modifier.fillMaxWidth().padding(top = CzSpacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.lg)) {
        TeamBadgeView(team = team, size = 64)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(team.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (team.slogan.isNotBlank()) {
                Text(team.slogan, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = "${team.members.size} ${stringResource(R.string.teams_members)}",
                style = MaterialTheme.typography.labelSmall,
                color = teamColor,
            )
        }
    }
}

// ── Score breakdown ───────────────────────────────────────────────────────────

@Composable
private fun ScoreBreakdownRow(team: Team, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(CzRadius.lg)).background(colors.surface).padding(vertical = CzSpacing.md),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ScoreColumn("Team", team.points, colors.ember)
        Box(Modifier.width(0.5.dp).height(44.dp).background(colors.divider))
        ScoreColumn("Members", team.members.sumOf { it.personalScore }, colors.success)
        Box(Modifier.width(0.5.dp).height(44.dp).background(colors.divider))
        ScoreColumn("Penalties", -team.penalties.sumOf { it.points }, colors.error)
        Box(Modifier.width(0.5.dp).height(44.dp).background(colors.divider))
        ScoreColumn("Total", team.totalScore, colors.textPrimary)
    }
}

@Composable
private fun ScoreColumn(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.czColors
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$value", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
    }
}

// ── Team chat link ────────────────────────────────────────────────────────────

@Composable
private fun TeamChatLink(team: Team, teamColor: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.lg)).background(colors.surface)
            .clickable(onClick = onClick).padding(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(teamColor.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null, tint = teamColor, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.teams_team_chat), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
            Text(stringResource(R.string.teams_team_chat_subtitle, team.name), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
    }
}

// ── Members section ───────────────────────────────────────────────────────────

@Composable
private fun MembersSection(
    team: Team,
    canManage: Boolean,
    onRemoveMember: (String) -> Unit,
    onUpdateRole: (String, TeamMemberRole) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    if (team.members.isEmpty()) {
        Row(
            modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(CzRadius.md)).background(colors.surface).padding(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.teams_no_members), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        return
    }

    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(CzRadius.lg)).background(colors.surface),
    ) {
        team.members.forEachIndexed { idx, member ->
            MemberRow(member = member, canManage = canManage, onRemove = { onRemoveMember(member.id) }, onUpdateRole = { onUpdateRole(member.id, it) })
            if (idx < team.members.lastIndex) {
                Box(Modifier.padding(start = CzSpacing.md + 40.dp + CzSpacing.md).height(0.5.dp).fillMaxWidth().background(colors.divider))
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: TeamMember,
    canManage: Boolean,
    onRemove: () -> Unit,
    onUpdateRole: (TeamMemberRole) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    var roleMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth().padding(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        MemberAvatarView(member = member, size = 40)

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(member.displayName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(member.role.displayName(), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        }

        Text(
            text = "${member.personalScore} pts",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = colors.ember,
        )

        if (canManage) {
            Box {
                IconButton(onClick = { roleMenuExpanded = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.teams_manage_member), modifier = Modifier.size(16.dp), tint = colors.textSecondary)
                }
                DropdownMenu(expanded = roleMenuExpanded, onDismissRequest = { roleMenuExpanded = false }) {
                    TeamMemberRole.entries.forEach { role ->
                        DropdownMenuItem(
                            text = { Text(role.displayName()) },
                            onClick = { roleMenuExpanded = false; onUpdateRole(role) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.teams_remove_member), color = colors.error) },
                        onClick = { roleMenuExpanded = false; onRemove() },
                    )
                }
            }
        }
    }
}

@Composable
fun MemberAvatarView(member: TeamMember, size: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.czColors
    val photoUrl = member.photoUrl

    Box(
        modifier = modifier.size(size.dp).clip(CircleShape).background(colors.ember.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!photoUrl.isNullOrBlank()) {
            coil.compose.AsyncImage(model = photoUrl, contentDescription = member.displayName, modifier = Modifier.fillMaxSize().clip(CircleShape))
        } else {
            Text(member.displayName.take(1).uppercase(), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = colors.ember)
        }
    }
}

// ── Penalties section ─────────────────────────────────────────────────────────

@Composable
private fun PenaltiesSection(penalties: List<TeamPenalty>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.czColors
    Column(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(CzRadius.lg)).background(colors.surface)) {
        penalties.forEachIndexed { idx, penalty ->
            PenaltyRow(penalty = penalty)
            if (idx < penalties.lastIndex) Box(Modifier.padding(horizontal = CzSpacing.md).height(0.5.dp).fillMaxWidth().background(colors.divider))
        }
    }
}

@Composable
private fun PenaltyRow(penalty: TeamPenalty, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.czColors
    Row(modifier = modifier.fillMaxWidth().padding(CzSpacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
        Icon(Icons.Outlined.TrendingDown, contentDescription = null, tint = colors.error, modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(penalty.reason.ifBlank { stringResource(R.string.teams_penalty_no_reason) }, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
        }
        Text("-${penalty.points}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = colors.error)
    }
}

// ── Assign member card ────────────────────────────────────────────────────────

@Composable
private fun AssignMemberCard(
    approvedAttendees: List<CampingAttendee>,
    allTeams: List<Team>,
    currentTeamId: String,
    onAssign: (CampingAttendee, TeamMemberRole) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val sorted = remember(approvedAttendees) { approvedAttendees.sortedBy { it.displayName } }
    var selectedAttendeeId by rememberSaveable { mutableStateOf(sorted.firstOrNull()?.id ?: "") }
    var selectedRole by rememberSaveable { mutableStateOf(TeamMemberRole.Member) }
    var attendeeMenuExpanded by remember { mutableStateOf(false) }
    var roleMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(CzRadius.lg)).background(colors.surface).padding(CzSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Text(stringResource(R.string.teams_add_member), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.textSecondary)

        if (sorted.isEmpty()) {
            Text(stringResource(R.string.teams_no_approved_attendees), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                // Attendee picker
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = sorted.firstOrNull { it.id == selectedAttendeeId }?.displayName ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.teams_participant)) },
                        trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null) },
                        modifier = Modifier.fillMaxWidth().clickable { attendeeMenuExpanded = true },
                    )
                    DropdownMenu(expanded = attendeeMenuExpanded, onDismissRequest = { attendeeMenuExpanded = false }) {
                        sorted.forEach { attendee ->
                            DropdownMenuItem(text = { Text(attendee.displayName) }, onClick = { selectedAttendeeId = attendee.id; attendeeMenuExpanded = false })
                        }
                    }
                }

                // Role picker
                Box(modifier = Modifier.width(120.dp)) {
                    OutlinedTextField(
                        value = selectedRole.displayName(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.teams_role)) },
                        trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null) },
                        modifier = Modifier.fillMaxWidth().clickable { roleMenuExpanded = true },
                    )
                    DropdownMenu(expanded = roleMenuExpanded, onDismissRequest = { roleMenuExpanded = false }) {
                        TeamMemberRole.entries.forEach { role ->
                            DropdownMenuItem(text = { Text(role.displayName()) }, onClick = { selectedRole = role; roleMenuExpanded = false })
                        }
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.divider))

            TextButton(
                onClick = {
                    val attendee = sorted.firstOrNull { it.id == selectedAttendeeId } ?: return@TextButton
                    onAssign(attendee, selectedRole)
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(CzSpacing.xs))
                Text(stringResource(R.string.teams_assign_to_team), color = colors.ember)
            }
        }
    }
}

// ── Score control card ────────────────────────────────────────────────────────

@Composable
private fun ScoreControlCard(onUpdateScore: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.czColors
    var deltaText by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(CzRadius.lg)).background(colors.surface).padding(CzSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Text(stringResource(R.string.teams_update_score), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.textSecondary)

        OutlinedTextField(
            value = deltaText,
            onValueChange = { deltaText = it },
            label = { Text(stringResource(R.string.teams_score_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            leadingIcon = { Icon(Icons.Outlined.TrendingUp, contentDescription = null, tint = colors.ember) },
            modifier = Modifier.fillMaxWidth(),
        )

        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.divider))

        TextButton(
            onClick = {
                val pts = deltaText.trim().toIntOrNull() ?: return@TextButton
                onUpdateScore(pts)
                deltaText = ""
            },
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.teams_update_score_action), color = colors.ember)
        }
    }
}

// ── Penalty control card ──────────────────────────────────────────────────────

@Composable
private fun PenaltyControlCard(onApplyPenalty: (Int, String) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.czColors
    var ptsText by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(CzRadius.lg)).background(colors.surface).padding(CzSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Text(stringResource(R.string.teams_apply_penalty), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.textSecondary)

        OutlinedTextField(
            value = ptsText,
            onValueChange = { ptsText = it },
            label = { Text(stringResource(R.string.teams_penalty_points_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            leadingIcon = { Icon(Icons.Outlined.TrendingDown, contentDescription = null, tint = colors.error) },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text(stringResource(R.string.teams_reason)) },
            modifier = Modifier.fillMaxWidth(),
        )

        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.divider))

        TextButton(
            onClick = {
                val pts = ptsText.trim().toIntOrNull() ?: return@TextButton
                if (pts > 0) { onApplyPenalty(pts, reason); ptsText = ""; reason = "" }
            },
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.teams_apply_penalty_action), color = colors.error)
        }
    }
}

// ── Error banner ──────────────────────────────────────────────────────────────

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(CzRadius.md))
            .background(colors.error.copy(alpha = 0.08f)).padding(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = colors.error, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_dismiss), color = colors.error) }
    }
}

// ── Section label ─────────────────────────────────────────────────────────────

@Composable
private fun TeamDetailSectionLabel(title: String, icon: ImageVector, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.czColors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.ember, modifier = Modifier.size(14.dp))
        Text(text = title, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun TeamDetailScreenPreview() {
    val team = Team(
        id = "lions",
        campingId = "preview-camping",
        name = "Lions",
        slogan = "Courage in service",
        symbolName = "flame.fill",
        colorHex = "#D9432F",
        points = 180,
        penalties = listOf(TeamPenalty("p1", "Late to lineup", 10, java.util.Date())),
        members = listOf(
            TeamMember("u1", "u1", "Preview Admin", "Central SDA", role = TeamMemberRole.Captain, personalScore = 35),
            TeamMember("u2", "u2", "Ana Silva", "Central SDA", personalScore = 22),
        ),
    )
    CampzoneTheme {
        TeamDetailScreen(
            team = team,
            campingId = "preview-camping",
            canManageTeams = true,
            authenticatedUserId = "u1",
            approvedAttendees = emptyList(),
            isSaving = false,
            operationError = null,
            operationMessage = null,
            allTeams = listOf(team),
            onBack = {},
            onOpenEditor = {},
            onOpenTeamChat = {},
            onDeleteTeam = {},
            onAssignMember = {},
            onRemoveMember = {},
            onUpdateMemberRole = { _, _ -> },
            onUpdateScore = {},
            onApplyPenalty = { _, _ -> },
            onClearError = {},
            onClearMessage = {},
        )
    }
}
