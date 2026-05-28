package fr.ziyon.campzone.ui.polls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.Poll
import fr.ziyon.campzone.data.model.PollOption
import fr.ziyon.campzone.ui.camping.registrations.permissionContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollDetailRoute(
    pollId: String,
    campingId: String,
    camping: Camping?,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PollViewModel = hiltViewModel(),
) {
    val colors = MaterialTheme.czColors
    val evaluator = remember { AppPermissionEvaluator() }
    val permissionUser = remember(authenticatedUser) {
        PermissionUser(authenticatedUser.role, authenticatedUser.uid, authenticatedUser.church)
    }
    val canManage = camping != null &&
        evaluator.canManagePolls(permissionUser, camping.permissionContext())

    val poll by viewModel.activePoll.collectAsState()
    val activeVote by viewModel.activeVote.collectAsState()
    val selected by viewModel.selectedOptionIds.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    DisposableEffect(pollId, campingId, authenticatedUser.uid) {
        viewModel.startObservingPoll(pollId, campingId, authenticatedUser.uid)
        onDispose { viewModel.stopObservingPoll() }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.poll_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    val current = poll
                    if (canManage && current != null) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, stringResource(R.string.poll_manage))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.poll_edit)) },
                                onClick = { menuOpen = false; onOpenEditor(current.id) },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (current.isOpen) R.string.poll_close_poll else R.string.poll_reopen_poll,
                                        ),
                                    )
                                },
                                onClick = { menuOpen = false; viewModel.setOpen(current, !current.isOpen) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.poll_delete)) },
                                onClick = { menuOpen = false; confirmDelete = true },
                            )
                        }
                    }
                },
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
            )
        },
    ) { padding ->
        val current = poll
        if (current == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CzLoadingView(message = stringResource(R.string.poll_loading))
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(CzSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.xl),
            ) {
                PollHeader(current)
                val showResults = !current.resolvedIsOpen || activeVote != null
                when {
                    showResults -> PollResultsCard(
                        poll = current,
                        activeVoteOptionIds = activeVote?.selectedOptionIds.orEmpty().toSet(),
                        canChangeVote = current.resolvedIsOpen && activeVote != null,
                        onChangeVote = viewModel::changeVote,
                    )
                    current.resolvedIsOpen -> PollVotingCard(
                        poll = current,
                        selected = selected,
                        hasExistingVote = activeVote != null,
                        isSaving = isSaving,
                        onToggle = { viewModel.toggleSelection(it, current.allowsMultiple) },
                        onSubmit = { viewModel.submitVote(current, authenticatedUser.uid) },
                    )
                    else -> PollClosedCard()
                }
            }
        }
    }

    if (confirmDelete) {
        val current = poll
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.poll_delete_confirm_title)) },
            text = { Text(stringResource(R.string.poll_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    if (current != null) viewModel.deletePoll(current) { onBack() }
                }) { Text(stringResource(R.string.poll_delete), color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun PollHeader(poll: Poll) {
    val colors = MaterialTheme.czColors
    val live = poll.resolvedIsOpen
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        Row(
            modifier = Modifier
                .background((if (live) colors.success else colors.textSecondary).copy(alpha = 0.12f), CircleShape)
                .padding(horizontal = CzSpacing.sm, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            Box(Modifier.size(7.dp).background(if (live) colors.success else colors.textSecondary, CircleShape))
            Text(
                stringResource(if (live) R.string.poll_live_status else R.string.poll_closed),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (live) colors.success else colors.textSecondary,
            )
        }
        Text(poll.question, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        if (poll.description.isNotBlank()) {
            Text(poll.description, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
            Text(stringResource(R.string.poll_votes, poll.totalVotes), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            if (poll.createdByName.isNotBlank()) {
                Text(stringResource(R.string.poll_by_author, poll.createdByName), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun PollVotingCard(
    poll: Poll,
    selected: Set<String>,
    hasExistingVote: Boolean,
    isSaving: Boolean,
    onToggle: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(CzRadius.xl))
            .padding(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        poll.options.forEach { option ->
            PollVoteRow(
                option = option,
                isSelected = option.id in selected,
                allowsMultiple = poll.allowsMultiple,
                onClick = { onToggle(option.id) },
            )
        }
        Button(
            onClick = onSubmit,
            enabled = selected.isNotEmpty() && !isSaving,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = colors.ember),
        ) {
            Text(stringResource(if (hasExistingVote) R.string.poll_update_vote else R.string.poll_submit_vote))
        }
    }
}

@Composable
private fun PollVoteRow(
    option: PollOption,
    isSelected: Boolean,
    allowsMultiple: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val icon = when {
        allowsMultiple && isSelected -> Icons.Rounded.CheckBox
        allowsMultiple -> Icons.Rounded.CheckBoxOutlineBlank
        isSelected -> Icons.Rounded.RadioButtonChecked
        else -> Icons.Rounded.RadioButtonUnchecked
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isSelected) colors.ember.copy(alpha = 0.10f) else colors.background,
                RoundedCornerShape(CzRadius.md),
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) colors.ember else colors.textSecondary.copy(alpha = 0.18f),
                shape = RoundedCornerShape(CzRadius.md),
            )
            .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = if (isSelected) colors.ember else colors.textSecondary)
        Text(option.label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
    }
}

@Composable
private fun PollResultsCard(
    poll: Poll,
    activeVoteOptionIds: Set<String>,
    canChangeVote: Boolean,
    onChangeVote: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val winningCount = poll.options.maxOfOrNull { it.voteCount } ?: 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(CzRadius.xl))
            .padding(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.poll_results), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Spacer(Modifier.weight(1f))
            if (activeVoteOptionIds.isNotEmpty()) {
                Text(stringResource(R.string.poll_voted), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = colors.success)
            }
        }
        poll.options.forEach { option ->
            PollResultsBar(
                option = option,
                percentage = poll.percentage(option.id),
                isUserChoice = option.id in activeVoteOptionIds,
                isWinning = option.voteCount > 0 && option.voteCount == winningCount,
            )
        }
        if (canChangeVote) {
            TextButton(onClick = onChangeVote, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(stringResource(R.string.poll_change_vote), color = colors.ember, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PollClosedCard() {
    val colors = MaterialTheme.czColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(CzRadius.xl))
            .padding(CzSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Text(stringResource(R.string.poll_closed_message), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun PollDetailRoutePreview() {
    fr.ziyon.campzone.core.designsystem.CampzoneTheme {
        PollDetailRoute(
            pollId = "p1",
            campingId = "preview-camp",
            camping = pollPreviewCamping(),
            authenticatedUser = pollPreviewUser(),
            onBack = {},
            onOpenEditor = {},
            viewModel = PollViewModel(
                fr.ziyon.campzone.data.polls.FakePollService(polls = listOf(previewActivePoll())),
                fr.ziyon.campzone.data.polls.FakePollNotificationDispatcher(),
            ),
        )
    }
}
