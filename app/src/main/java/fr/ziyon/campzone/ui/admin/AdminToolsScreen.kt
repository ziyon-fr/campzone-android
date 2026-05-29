package fr.ziyon.campzone.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzBadge
import fr.ziyon.campzone.core.designsystem.CzBadgeTone
import fr.ziyon.campzone.core.designsystem.CzCard
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.ContentReportStatus
import fr.ziyon.campzone.ui.admin.moderation.ModerationUiState
import fr.ziyon.campzone.ui.admin.moderation.ModerationViewModel

@Composable
fun AdminToolsRoute(
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenModerationQueue: () -> Unit,
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
    val canViewAdminTools = evaluator.canViewAdminTools(permissionUser)
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(canModerateContent) {
        if (canModerateContent) viewModel.loadIfNeeded()
    }

    AdminToolsScreen(
        canModerateContent = canModerateContent,
        canViewAdminTools = canViewAdminTools,
        pendingCount = (uiState as? ModerationUiState.Loaded)
            ?.reports
            ?.count { it.status == ContentReportStatus.Pending }
            ?: 0,
        onBack = onBack,
        onOpenModerationQueue = onOpenModerationQueue,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminToolsScreen(
    canModerateContent: Boolean,
    canViewAdminTools: Boolean,
    pendingCount: Int,
    onBack: () -> Unit,
    onOpenModerationQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.admin_tools_title), color = colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
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
        if (!canModerateContent && !canViewAdminTools) {
            CzEmptyState(
                title = stringResource(R.string.admin_tools_restricted_title),
                message = stringResource(R.string.admin_tools_restricted_message),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                icon = {
                    Icon(
                        Icons.Rounded.Security,
                        contentDescription = null,
                        tint = colors.textSecondary,
                    )
                },
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(innerPadding)
                .padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            item {
                AdminSectionHeader(
                    title = stringResource(R.string.admin_moderation_section),
                    icon = Icons.Rounded.Security,
                )
            }
            if (canModerateContent) {
                item {
                    AdminToolRow(
                        icon = Icons.Rounded.Flag,
                        title = stringResource(R.string.admin_content_reports_title),
                        subtitle = stringResource(R.string.admin_content_reports_subtitle),
                        badge = pendingCount.takeIf { it > 0 }?.toString(),
                        onClick = onOpenModerationQueue,
                    )
                }
            }
            if (canViewAdminTools) {
                item {
                    AdminSectionHeader(
                        title = stringResource(R.string.admin_infrastructure_section),
                        icon = Icons.Rounded.AdminPanelSettings,
                    )
                }
                item {
                    AdminInfoRow(
                        icon = Icons.Rounded.Gavel,
                        title = stringResource(R.string.profile_role_assignment),
                        note = stringResource(R.string.profile_firebase),
                    )
                }
                item {
                    AdminInfoRow(
                        icon = Icons.Rounded.Security,
                        title = stringResource(R.string.profile_security_rules),
                        note = stringResource(R.string.profile_firebase),
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminSectionHeader(title: String, icon: ImageVector) {
    val colors = MaterialTheme.czColors
    Row(
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.ember, modifier = Modifier.size(CzSpacing.lg))
        Text(
            text = title,
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AdminToolRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String?,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    CzCard(
        onClick = onClick,
        contentDescription = title,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.base),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = colors.ember, modifier = Modifier.size(CzSpacing.xl))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (badge != null) {
                CzBadge(text = badge, tone = CzBadgeTone.Warning)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.textSecondary)
        }
    }
}

@Composable
private fun AdminInfoRow(icon: ImageVector, title: String, note: String) {
    val colors = MaterialTheme.czColors
    CzCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.base),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(CzSpacing.xl))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = colors.textPrimary, style = MaterialTheme.typography.titleMedium)
                Text(note, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.success)
        }
    }
}

@Preview
@Composable
private fun AdminToolsScreenPreview() {
    CampzoneTheme {
        AdminToolsScreen(
            canModerateContent = true,
            canViewAdminTools = true,
            pendingCount = 2,
            onBack = {},
            onOpenModerationQueue = {},
        )
    }
}
