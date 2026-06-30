package fr.ziyon.campzone.ui.camping.registrations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Festival
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.ui.camping.campingDateRange

@Composable
fun RegistrationReviewRoute(
    authenticatedUser: AuthenticatedUser,
    focusedCampingId: String? = null,
    onBack: () -> Unit,
    onOpenCamping: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegistrationReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(authenticatedUser, focusedCampingId) {
        viewModel.load(authenticatedUser, focusedCampingId)
    }
    LaunchedEffect(state.operationMessage) {
        state.operationMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(state.operationError) {
        state.operationError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    RegistrationReviewScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = { viewModel.load(authenticatedUser, focusedCampingId) },
        onOpenCamping = onOpenCamping,
        onApprove = { campingId, attendeeId ->
            viewModel.updateRegistration(campingId, attendeeId, RegistrationApprovalStatus.Approved)
        },
        onReject = { campingId, attendeeId ->
            viewModel.updateRegistration(campingId, attendeeId, RegistrationApprovalStatus.Rejected)
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationReviewScreen(
    state: RegistrationReviewUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenCamping: (String) -> Unit,
    onApprove: (String, String) -> Unit,
    onReject: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.czColors.background,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.registration_review_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.czColors.background,
                ),
                windowInsets = WindowInsets(),
            )
        },
    ) { innerPadding ->
        when (val phase = state.phase) {
            RegistrationReviewPhase.Loading -> CzLoadingView(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                message = stringResource(R.string.registration_review_loading),
            )

            RegistrationReviewPhase.Restricted -> StateCenter(
                icon = Icons.Filled.Lock,
                title = stringResource(R.string.registration_review_restricted_title),
                message = stringResource(R.string.registration_review_restricted_message),
                modifier = Modifier.padding(innerPadding),
            )

            RegistrationReviewPhase.Empty -> StateCenter(
                icon = Icons.Filled.CheckCircle,
                title = stringResource(R.string.registration_review_empty_title),
                message = stringResource(R.string.registration_review_empty_message),
                modifier = Modifier.padding(innerPadding),
            )

            is RegistrationReviewPhase.Error -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CzErrorState(
                    title = stringResource(R.string.camping_error_title),
                    message = phase.message,
                    onRetry = onRetry,
                    retryLabel = stringResource(R.string.common_retry),
                )
            }

            is RegistrationReviewPhase.Loaded -> ReviewContent(
                campings = phase.campings,
                isSaving = state.isSaving,
                onOpenCamping = onOpenCamping,
                onApprove = onApprove,
                onReject = onReject,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun ReviewContent(
    campings: List<Camping>,
    isSaving: Boolean,
    onOpenCamping: (String) -> Unit,
    onApprove: (String, String) -> Unit,
    onReject: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = CzSpacing.lg,
            top = CzSpacing.sm,
            end = CzSpacing.lg,
            bottom = CzSpacing.lg,
        ),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        campings.forEach { camping ->
            item(key = camping.id) {
                ReviewCampingSection(
                    camping = camping,
                    isSaving = isSaving,
                    onOpenCamping = onOpenCamping,
                    onApprove = onApprove,
                    onReject = onReject,
                )
            }
        }
    }
}

@Composable
private fun ReviewCampingSection(
    camping: Camping,
    isSaving: Boolean,
    onOpenCamping: (String) -> Unit,
    onApprove: (String, String) -> Unit,
    onReject: (String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        Text(
            text = campingDateRange(camping.startDate, camping.endDate),
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            color = registrationReviewCardColor(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(CzRadius.lg),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCamping(camping.id) }
                        .padding(horizontal = CzSpacing.md, vertical = CzSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Festival,
                        contentDescription = null,
                        tint = MaterialTheme.czColors.pine,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = camping.title,
                        color = MaterialTheme.czColors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.czColors.ember,
                        modifier = Modifier.size(18.dp),
                    )
                }

                camping.pendingAttendees.forEach { attendee ->
                    PendingRegistrationReviewRow(
                        attendee = attendee,
                        isSaving = isSaving,
                        onApprove = { onApprove(camping.id, attendee.id) },
                        onReject = { onReject(camping.id, attendee.id) },
                        modifier = Modifier.padding(horizontal = CzSpacing.md),
                    )
                }

                if (camping.waitlistedAttendees.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(
                            start = CzSpacing.md,
                            end = CzSpacing.md,
                            top = CzSpacing.xs,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.czColors.textSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = stringResource(
                                R.string.registration_review_waitlist_count,
                                camping.waitlistedAttendees.size,
                            ),
                            color = MaterialTheme.czColors.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    camping.waitlistedAttendees.forEach { attendee ->
                        PendingRegistrationReviewRow(
                            attendee = attendee,
                            isSaving = isSaving,
                            onApprove = { onApprove(camping.id, attendee.id) },
                            onReject = { onReject(camping.id, attendee.id) },
                            modifier = Modifier.padding(horizontal = CzSpacing.md),
                        )
                    }
                }
                Spacer(modifier = Modifier.size(CzSpacing.sm))
            }
        }
    }
}

@Composable
private fun PendingRegistrationReviewRow(
    attendee: CampingAttendee,
    isSaving: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = registrationReviewCardColor(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(CzRadius.lg),
        border = BorderStroke(1.dp, MaterialTheme.czColors.warning.copy(alpha = 0.2f)),
    ) {
        Column {
            RegistrationAttendeeRow(attendee)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CzSpacing.md)
                    .padding(bottom = CzSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            ) {
                ReviewActionButton(
                    text = stringResource(R.string.registration_review_approve),
                    icon = Icons.Filled.CheckCircle,
                    color = MaterialTheme.czColors.success,
                    enabled = !isSaving,
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                )
                ReviewActionButton(
                    text = stringResource(R.string.registration_review_reject),
                    icon = Icons.Filled.Cancel,
                    color = MaterialTheme.czColors.error,
                    enabled = !isSaving,
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ReviewActionButton(
    text: String,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        color = color.copy(alpha = if (enabled) 0.12f else 0.06f),
        contentColor = color.copy(alpha = if (enabled) 1f else 0.5f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(CzRadius.sm),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!enabled) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = color,
                )
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.size(CzSpacing.xs))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun registrationReviewCardColor(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.czColors.surface else Color.White

@Composable
private fun StateCenter(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CzEmptyState(
            title = title,
            message = message,
            icon = {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.czColors.textSecondary,
                    modifier = Modifier.size(40.dp),
                )
            },
        )
    }
}
