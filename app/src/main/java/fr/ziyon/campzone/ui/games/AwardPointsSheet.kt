package fr.ziyon.campzone.ui.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.Game
import fr.ziyon.campzone.data.model.PointRuleTarget
import fr.ziyon.campzone.data.model.PointRuleVisibility
import fr.ziyon.campzone.data.model.Team
import kotlinx.coroutines.launch

private enum class AwardTargetKind { Team, User }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AwardPointsSheet(
    game: Game,
    camping: Camping?,
    teams: List<Team>,
    preselectedRuleId: String?,
    authenticatedUser: AuthenticatedUser,
    viewModel: GameViewModel,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var targetKind by remember { mutableStateOf(AwardTargetKind.Team) }
    var selectedTeamId by remember { mutableStateOf<String?>(null) }
    var selectedUserId by remember { mutableStateOf<String?>(null) }
    var selectedRuleId by remember { mutableStateOf<String?>(null) }
    var pointsText by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf(PointRuleVisibility.Immediate) }

    var teamMenuExpanded by remember { mutableStateOf(false) }
    var userMenuExpanded by remember { mutableStateOf(false) }
    var ruleMenuExpanded by remember { mutableStateOf(false) }

    val allMembers = remember(teams) { teams.flatMap { t -> t.members.map { t to it } } }

    fun targetKindsFor(appliesTo: PointRuleTarget?): List<AwardTargetKind> = when (appliesTo) {
        PointRuleTarget.Team -> listOf(AwardTargetKind.Team)
        PointRuleTarget.User -> listOf(AwardTargetKind.User)
        PointRuleTarget.Any, null -> AwardTargetKind.entries
    }

    fun enforceTarget(appliesTo: PointRuleTarget?) {
        val allowedKinds = targetKindsFor(appliesTo)
        if (targetKind !in allowedKinds) {
            targetKind = allowedKinds.first()
        }
        when (targetKind) {
            AwardTargetKind.Team -> selectedUserId = null
            AwardTargetKind.User -> selectedTeamId = null
        }
    }

    fun applyRule(ruleId: String?) {
        selectedRuleId = ruleId
        val rule = game.pointRules.firstOrNull { it.id == ruleId }
        if (rule == null) {
            enforceTarget(null)
            return
        }
        name = rule.name
        pointsText = rule.points.toString()
        reason = rule.reason
        visibility = rule.visibility
        enforceTarget(rule.appliesTo)
    }

    LaunchedEffect(preselectedRuleId) {
        applyRule(preselectedRuleId ?: game.pointRules.firstOrNull()?.id)
    }

    val selectedRule = game.pointRules.firstOrNull { it.id == selectedRuleId }
    val availableTargetKinds = targetKindsFor(selectedRule?.appliesTo)

    val canSubmit = pointsText.toIntOrNull() != null &&
        name.isNotBlank() &&
        targetKind in availableTargetKinds &&
        when (targetKind) {
            AwardTargetKind.Team -> selectedTeamId != null
            AwardTargetKind.User -> selectedUserId != null
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CzSpacing.lg)
                .padding(bottom = CzSpacing.xxxl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { scope.launch { sheetState.hide(); onDismiss() } }) {
                    Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
                }
                Text(
                    stringResource(R.string.games_award_points),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                if (viewModel.isAwarding) {
                    CircularProgressIndicator(color = colors.ember)
                } else {
                    TextButton(
                        onClick = {
                            val points = pointsText.toIntOrNull() ?: return@TextButton
                            val targetTeam = if (targetKind == AwardTargetKind.Team) {
                                teams.firstOrNull { it.id == selectedTeamId }
                            } else null
                            val targetMember = if (targetKind == AwardTargetKind.User) {
                                allMembers.firstOrNull { it.second.userId == selectedUserId }
                            } else null
                            val request = ActivityRequest(
                                gameId = game.id,
                                pointRuleId = selectedRuleId,
                                name = name.trim(),
                                points = points,
                                reason = reason.trim(),
                                targetTeamId = targetTeam?.id,
                                targetTeamName = targetTeam?.name,
                                targetUserId = targetMember?.second?.userId,
                                targetUserName = targetMember?.second?.displayName,
                                visibility = visibility,
                            )
                            scope.launch {
                                camping?.let { c ->
                                    val result = viewModel.awardPoints(request, c, teams, authenticatedUser, game)
                                    if (result != null) { sheetState.hide(); onDismiss() }
                                }
                            }
                        },
                        enabled = canSubmit,
                    ) {
                        Text(
                            stringResource(R.string.games_submit),
                            color = if (canSubmit) colors.ember else colors.textSecondary,
                        )
                    }
                }
            }

            viewModel.operationError?.let { err ->
                Surface(color = colors.error.copy(alpha = 0.1f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Text(err, style = MaterialTheme.typography.bodySmall, color = colors.error, modifier = Modifier.padding(CzSpacing.md))
                }
            }

            HorizontalDivider(color = colors.divider)

            Text(stringResource(R.string.games_award_target), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                availableTargetKinds.forEachIndexed { index, kind ->
                    SegmentedButton(
                        selected = targetKind == kind,
                        onClick = {
                            targetKind = kind
                            selectedTeamId = null
                            selectedUserId = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, availableTargetKinds.size),
                        label = {
                            Text(
                                when (kind) {
                                    AwardTargetKind.Team -> stringResource(R.string.games_target_team)
                                    AwardTargetKind.User -> stringResource(R.string.games_target_user)
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }

            when (targetKind) {
                AwardTargetKind.Team -> {
                    val teamName = teams.firstOrNull { it.id == selectedTeamId }?.name
                        ?: stringResource(R.string.games_select_team)
                    ExposedDropdownMenuBox(expanded = teamMenuExpanded, onExpandedChange = { teamMenuExpanded = it }) {
                        OutlinedTextField(
                            value = teamName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.games_target_team)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(teamMenuExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = teamMenuExpanded, onDismissRequest = { teamMenuExpanded = false }) {
                            teams.forEach { team ->
                                DropdownMenuItem(
                                    text = { Text(team.name) },
                                    onClick = { selectedTeamId = team.id; teamMenuExpanded = false },
                                )
                            }
                        }
                    }
                }
                AwardTargetKind.User -> {
                    val memberName = allMembers.firstOrNull { it.second.userId == selectedUserId }
                        ?.let { (t, m) -> "${m.displayName} – ${t.name}" }
                        ?: stringResource(R.string.games_select_participant)
                    ExposedDropdownMenuBox(expanded = userMenuExpanded, onExpandedChange = { userMenuExpanded = it }) {
                        OutlinedTextField(
                            value = memberName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.games_target_user)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(userMenuExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = userMenuExpanded, onDismissRequest = { userMenuExpanded = false }) {
                            allMembers.forEach { (team, member) ->
                                DropdownMenuItem(
                                    text = { Text("${member.displayName} – ${team.name}") },
                                    onClick = { selectedUserId = member.userId; userMenuExpanded = false },
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = colors.divider)

            val ruleName = game.pointRules.firstOrNull { it.id == selectedRuleId }
                ?.let { "${it.name} (${if (it.points >= 0) "+" else ""}${it.points})" }
                ?: stringResource(R.string.games_custom_rule)
            ExposedDropdownMenuBox(expanded = ruleMenuExpanded, onExpandedChange = { ruleMenuExpanded = it }) {
                OutlinedTextField(
                    value = ruleName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.games_use_rule)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(ruleMenuExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = ruleMenuExpanded, onDismissRequest = { ruleMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.games_custom_rule)) },
                        onClick = { selectedRuleId = null; ruleMenuExpanded = false; name = ""; pointsText = "" },
                    )
                    game.pointRules.forEach { rule ->
                        DropdownMenuItem(
                            text = { Text("${rule.name} (${if (rule.points >= 0) "+" else ""}${rule.points})") },
                            onClick = { applyRule(rule.id); ruleMenuExpanded = false },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.games_award_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = pointsText,
                onValueChange = { pointsText = it },
                label = { Text(stringResource(R.string.games_award_points_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text(stringResource(R.string.games_award_reason)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            Text(stringResource(R.string.games_rule_visibility), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PointRuleVisibility.entries.forEachIndexed { index, vis ->
                    SegmentedButton(
                        selected = visibility == vis,
                        onClick = { visibility = vis },
                        shape = SegmentedButtonDefaults.itemShape(index, PointRuleVisibility.entries.size),
                        label = { Text(vis.displayName, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }
    }
}
