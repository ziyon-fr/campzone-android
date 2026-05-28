package fr.ziyon.campzone.ui.polls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.Poll
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.ui.camping.registrations.permissionContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampingPollsRoute(
    campingId: String,
    camping: Camping?,
    attendees: List<CampingAttendee>,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenPollDetail: (String) -> Unit,
    onOpenPollEditor: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PollViewModel = hiltViewModel(),
) {
    if (camping == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CzLoadingView(message = stringResource(R.string.poll_loading))
        }
        return
    }
    val evaluator = remember { AppPermissionEvaluator() }
    val permissionUser = remember(authenticatedUser) {
        PermissionUser(authenticatedUser.role, authenticatedUser.uid, authenticatedUser.church)
    }
    val canManage = evaluator.canManagePolls(permissionUser, camping.permissionContext())
    val isApprovedParticipant = attendees.any {
        it.registrationStatus == RegistrationApprovalStatus.Approved &&
            (it.userId == authenticatedUser.uid ||
                it.guardianId == authenticatedUser.uid ||
                it.id == authenticatedUser.uid)
    }
    val canAccess = canManage || isApprovedParticipant

    val state by viewModel.listState.collectAsState()

    LaunchedEffect(canAccess, campingId) {
        if (canAccess) viewModel.loadPolls(campingId)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.poll_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (canManage) {
                        IconButton(onClick = onOpenPollEditor) {
                            Icon(Icons.Rounded.Add, stringResource(R.string.poll_create))
                        }
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !canAccess -> CzEmptyState(
                    title = stringResource(R.string.poll_restricted_title),
                    message = stringResource(R.string.poll_restricted_message),
                    modifier = Modifier.fillMaxSize().padding(CzSpacing.lg),
                )
                state is PollListUiState.Loading -> CzLoadingView(
                    message = stringResource(R.string.poll_loading),
                    modifier = Modifier.fillMaxSize().padding(CzSpacing.lg),
                )
                state is PollListUiState.Error -> CzErrorState(
                    title = stringResource(R.string.poll_error_title),
                    message = (state as PollListUiState.Error).message,
                    onRetry = { viewModel.retry(campingId) },
                    retryLabel = stringResource(R.string.common_retry),
                    modifier = Modifier.fillMaxSize().padding(CzSpacing.lg),
                )
                state is PollListUiState.Empty -> CzEmptyState(
                    title = stringResource(R.string.poll_empty_title),
                    message = stringResource(R.string.poll_empty_message),
                    modifier = Modifier.fillMaxSize().padding(CzSpacing.lg),
                )
                state is PollListUiState.Loaded -> {
                    val loaded = state as PollListUiState.Loaded
                    val liveTitle = stringResource(R.string.poll_section_live)
                    val closedTitle = stringResource(R.string.poll_section_closed)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(CzSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
                    ) {
                        pollSection(liveTitle, loaded.openPolls, onOpenPollDetail)
                        pollSection(closedTitle, loaded.closedPolls, onOpenPollDetail)
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.pollSection(
    title: String,
    polls: List<Poll>,
    onOpenPollDetail: (String) -> Unit,
) {
    if (polls.isEmpty()) return
    item(key = "header-$title") {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = CzSpacing.sm),
        )
    }
    items(polls, key = { it.id }) { poll ->
        PollCard(poll = poll, onClick = { onOpenPollDetail(poll.id) })
    }
}
