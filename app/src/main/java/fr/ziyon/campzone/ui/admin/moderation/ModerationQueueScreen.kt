package fr.ziyon.campzone.ui.admin.moderation

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SmsFailed
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzBadge
import fr.ziyon.campzone.core.designsystem.CzBadgeTone
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzCard
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.ContentReport
import fr.ziyon.campzone.data.model.ContentReportReason
import fr.ziyon.campzone.data.model.ContentReportStatus
import fr.ziyon.campzone.data.model.ContentReportTarget
import fr.ziyon.campzone.data.moderation.previewContentReports

@Composable
fun ModerationQueueRoute(
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModerationViewModel = hiltViewModel(),
) {
    val evaluator = AppPermissionEvaluator()
    val permissionUser = PermissionUser(
        role = authenticatedUser.role,
        userId = authenticatedUser.uid,
        church = authenticatedUser.church,
    )
    val canModerateContent = evaluator.canModerateContent(permissionUser)
    val uiState by viewModel.uiState.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()

    LaunchedEffect(canModerateContent) {
        if (canModerateContent) viewModel.loadIfNeeded()
    }

    ModerationQueueScreen(
        uiState = uiState,
        canModerateContent = canModerateContent,
        reviewerId = authenticatedUser.uid,
        isSaving = isSaving,
        operationMessage = operationMessage,
        onBack = onBack,
        onRetry = viewModel::load,
        onUpdateStatus = viewModel::updateStatus,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModerationQueueScreen(
    uiState: ModerationUiState,
    canModerateContent: Boolean,
    reviewerId: String,
    isSaving: Boolean,
    operationMessage: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onUpdateStatus: (String, ContentReportStatus, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    var showResolved by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.moderation_queue_title), color = colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                        modifier = Modifier.padding(end = CzSpacing.sm),
                    ) {
                        Text(
                            text = stringResource(R.string.moderation_show_resolved),
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Switch(
                            checked = showResolved,
                            onCheckedChange = { showResolved = it },
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.background,
                    scrolledContainerColor = colors.background,
                ),
                windowInsets = WindowInsets(),
            )
        },
    ) { innerPadding ->
        if (!canModerateContent) {
            CzEmptyState(
                title = stringResource(R.string.moderation_restricted_title),
                message = stringResource(R.string.moderation_restricted_message),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                icon = {
                    Icon(Icons.Rounded.Security, contentDescription = null, tint = colors.textSecondary)
                },
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(innerPadding),
        ) {
            if (isSaving) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.ember,
                )
            }
            if (!operationMessage.isNullOrBlank()) {
                Text(
                    text = operationMessage,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = CzSpacing.lg, vertical = CzSpacing.sm),
                )
            }

            when (uiState) {
                ModerationUiState.Loading -> CzLoadingView(
                    modifier = Modifier.fillMaxWidth(),
                    message = stringResource(R.string.moderation_loading),
                )

                is ModerationUiState.Error -> CzErrorState(
                    title = stringResource(R.string.moderation_error_title),
                    message = uiState.message,
                    onRetry = onRetry,
                    retryLabel = stringResource(R.string.common_retry),
                    modifier = Modifier.fillMaxWidth(),
                )

                is ModerationUiState.Loaded -> {
                    val pendingCount = uiState.reports.count { it.status == ContentReportStatus.Pending }
                    val displayed = if (showResolved) {
                        uiState.reports
                    } else {
                        uiState.reports.filter { it.status == ContentReportStatus.Pending }
                    }
                    ModerationReportList(
                        reports = displayed,
                        pendingCount = pendingCount,
                        showResolved = showResolved,
                        reviewerId = reviewerId,
                        onUpdateStatus = onUpdateStatus,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModerationReportList(
    reports: List<ContentReport>,
    pendingCount: Int,
    showResolved: Boolean,
    reviewerId: String,
    onUpdateStatus: (String, ContentReportStatus, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (reports.isEmpty()) {
        CzEmptyState(
            title = if (showResolved) {
                stringResource(R.string.moderation_empty_all_title)
            } else {
                stringResource(R.string.moderation_empty_pending_title)
            },
            message = if (showResolved) {
                stringResource(R.string.moderation_empty_all_message)
            } else {
                stringResource(R.string.moderation_empty_pending_message)
            },
            modifier = modifier.fillMaxWidth(),
            icon = {
                Icon(
                    if (showResolved) Icons.Rounded.Flag else Icons.Rounded.DoneAll,
                    contentDescription = null,
                    tint = MaterialTheme.czColors.textSecondary,
                )
            },
        )
        return
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        if (!showResolved && pendingCount > 0) {
            item {
                PendingBanner(pendingCount)
            }
        }
        items(reports, key = { it.id }) { report ->
            ReportRow(
                report = report,
                reviewerId = reviewerId,
                onUpdateStatus = onUpdateStatus,
            )
        }
        item { Spacer(modifier = Modifier.padding(CzSpacing.sm)) }
    }
}

@Composable
private fun PendingBanner(pendingCount: Int) {
    val colors = MaterialTheme.czColors
    CzCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = colors.warning)
            Column {
                Text(
                    text = stringResource(R.string.moderation_pending_count, pendingCount),
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.moderation_pending_hint),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ReportRow(
    report: ContentReport,
    reviewerId: String,
    onUpdateStatus: (String, ContentReportStatus, String) -> Unit,
) {
    val colors = MaterialTheme.czColors
    CzCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(report.target.icon(), contentDescription = null, tint = colors.ember)
                Text(
                    text = report.target.label(),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusBadge(report.status)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    report.reason.icon(),
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(CzSpacing.lg),
                )
                Text(
                    text = report.reason.label(),
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            Text(
                text = stringResource(R.string.moderation_content_id, report.contentId),
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.moderation_reporter_id, report.reporterId),
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (report.note.isNotBlank()) {
                Text(
                    text = stringResource(R.string.moderation_note, report.note),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = DateUtils.getRelativeTimeSpanString(
                    report.createdAt.time,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString(),
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
            )

            if (report.status == ContentReportStatus.Pending) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CzButton(
                        text = stringResource(R.string.moderation_dismiss),
                        onClick = {
                            onUpdateStatus(report.id, ContentReportStatus.Dismissed, reviewerId)
                        },
                        variant = CzButtonVariant.Outline,
                        leadingIcon = {
                            Icon(Icons.Rounded.Cancel, contentDescription = null)
                        },
                    )
                    CzButton(
                        text = stringResource(R.string.moderation_resolve),
                        onClick = {
                            onUpdateStatus(report.id, ContentReportStatus.Resolved, reviewerId)
                        },
                        variant = CzButtonVariant.Secondary,
                        leadingIcon = {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ContentReportStatus) {
    val tone = when (status) {
        ContentReportStatus.Pending -> CzBadgeTone.Warning
        ContentReportStatus.Dismissed -> CzBadgeTone.Neutral
        ContentReportStatus.Resolved -> CzBadgeTone.Success
    }
    CzBadge(text = status.label(), tone = tone)
}

@Composable
private fun ContentReportTarget.label(): String =
    stringResource(
        when (this) {
            ContentReportTarget.Announcement -> R.string.moderation_target_announcement
            ContentReportTarget.Camping -> R.string.moderation_target_camping
            ContentReportTarget.ChatMessage -> R.string.moderation_target_chat_message
        },
    )

private fun ContentReportTarget.icon(): ImageVector = when (this) {
    ContentReportTarget.Announcement -> Icons.Rounded.Report
    ContentReportTarget.Camping -> Icons.Rounded.Flag
    ContentReportTarget.ChatMessage -> Icons.Rounded.ChatBubble
}

@Composable
private fun ContentReportReason.label(): String =
    stringResource(
        when (this) {
            ContentReportReason.Inappropriate -> R.string.chat_report_reason_inappropriate
            ContentReportReason.Spam -> R.string.chat_report_reason_spam
            ContentReportReason.Misinformation -> R.string.chat_report_reason_misinformation
            ContentReportReason.Harassment -> R.string.chat_report_reason_harassment
            ContentReportReason.Other -> R.string.chat_report_reason_other
        },
    )

private fun ContentReportReason.icon(): ImageVector = when (this) {
    ContentReportReason.Inappropriate -> Icons.Rounded.Warning
    ContentReportReason.Spam -> Icons.Rounded.Cancel
    ContentReportReason.Misinformation -> Icons.Rounded.Help
    ContentReportReason.Harassment -> Icons.Rounded.SmsFailed
    ContentReportReason.Other -> Icons.Rounded.Flag
}

@Composable
private fun ContentReportStatus.label(): String =
    stringResource(
        when (this) {
            ContentReportStatus.Pending -> R.string.moderation_status_pending
            ContentReportStatus.Dismissed -> R.string.moderation_status_dismissed
            ContentReportStatus.Resolved -> R.string.moderation_status_resolved
        },
    )

@Preview
@Composable
private fun ModerationQueueScreenPreview() {
    CampzoneTheme {
        ModerationQueueScreen(
            uiState = ModerationUiState.Loaded(previewContentReports()),
            canModerateContent = true,
            reviewerId = "admin-preview",
            isSaving = false,
            operationMessage = null,
            onBack = {},
            onRetry = {},
            onUpdateStatus = { _, _, _ -> },
        )
    }
}
