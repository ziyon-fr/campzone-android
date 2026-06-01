package fr.ziyon.campzone.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.runtime.remember
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
import fr.ziyon.campzone.core.permissions.AppPermission
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
    onOpenAdminOnboarding: () -> Unit,
    onOpenRoleManagement: () -> Unit,
    onOpenRegistrationReview: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModerationViewModel = hiltViewModel(),
) {
    val evaluator = remember { AppPermissionEvaluator() }
    val permissionUser = PermissionUser(
        role = authenticatedUser.role,
        userId = authenticatedUser.uid,
        church = authenticatedUser.church,
    )
    val canModerateContent = evaluator.canModerateContent(permissionUser)
    val canViewAdminTools = evaluator.canViewAdminTools(permissionUser)
    val canAssignAnyRole = evaluator.canAssignAnyRole(permissionUser)
    val assignsAllRoles = evaluator.can(permissionUser, AppPermission.AssignLeadershipRoles)
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(canModerateContent) {
        if (canModerateContent) viewModel.loadIfNeeded()
    }

    AdminToolsScreen(
        canModerateContent = canModerateContent,
        canViewAdminTools = canViewAdminTools,
        canAssignAnyRole = canAssignAnyRole,
        assignsAllRoles = assignsAllRoles,
        pendingCount = (uiState as? ModerationUiState.Loaded)
            ?.reports
            ?.count { it.status == ContentReportStatus.Pending }
            ?: 0,
        onBack = onBack,
        onOpenModerationQueue = onOpenModerationQueue,
        onOpenAdminOnboarding = onOpenAdminOnboarding,
        onOpenRoleManagement = onOpenRoleManagement,
        onOpenRegistrationReview = onOpenRegistrationReview,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminToolsScreen(
    canModerateContent: Boolean,
    canViewAdminTools: Boolean,
    canAssignAnyRole: Boolean,
    assignsAllRoles: Boolean,
    pendingCount: Int,
    onBack: () -> Unit,
    onOpenModerationQueue: () -> Unit,
    onOpenAdminOnboarding: () -> Unit,
    onOpenRoleManagement: () -> Unit,
    onOpenRegistrationReview: () -> Unit,
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
                            tint = colors.textPrimary,
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
        if (!canModerateContent && !canViewAdminTools && !canAssignAnyRole) {
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
                .padding(innerPadding),
            contentPadding = PaddingValues(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            // ── Operations ────────────────────────────────────────────────
            item(key = "operations-header") {
                AdminSectionHeader(
                    title = stringResource(R.string.admin_operations_section),
                    icon = Icons.Rounded.Tune,
                )
            }
            if (canViewAdminTools) {
                item(key = "setup-guide") {
                    AdminToolRow(
                        icon = Icons.Rounded.Checklist,
                        title = stringResource(R.string.admin_setup_guide_title),
                        subtitle = stringResource(R.string.admin_setup_guide_subtitle),
                        onClick = onOpenAdminOnboarding,
                    )
                }
            }
            item(key = "registration-review") {
                AdminToolRow(
                    icon = Icons.Rounded.HowToReg,
                    title = stringResource(R.string.admin_registration_review_title),
                    subtitle = stringResource(R.string.admin_registration_review_subtitle),
                    onClick = onOpenRegistrationReview,
                )
            }
            if (canAssignAnyRole) {
                item(key = "role-assignment") {
                    AdminToolRow(
                        icon = Icons.Rounded.ManageAccounts,
                        title = stringResource(R.string.admin_role_assignment_title),
                        subtitle = stringResource(
                            if (assignsAllRoles) {
                                R.string.admin_role_assignment_subtitle_all
                            } else {
                                R.string.admin_role_assignment_subtitle_church
                            },
                        ),
                        onClick = onOpenRoleManagement,
                    )
                }
            }

            // ── Moderation ────────────────────────────────────────────────
            if (canModerateContent) {
                item(key = "moderation-header") {
                    AdminSectionHeader(
                        title = stringResource(R.string.admin_moderation_section),
                        icon = Icons.Rounded.Flag,
                    )
                }
                item(key = "content-reports") {
                    AdminToolRow(
                        icon = Icons.Rounded.Flag,
                        title = stringResource(R.string.admin_content_reports_title),
                        subtitle = stringResource(R.string.admin_content_reports_subtitle),
                        badge = pendingCount.takeIf { it > 0 }?.toString(),
                        onClick = onOpenModerationQueue,
                    )
                }
            }

            // ── Infrastructure ────────────────────────────────────────────
            if (canViewAdminTools) {
                item(key = "infrastructure-header") {
                    AdminSectionHeader(
                        title = stringResource(R.string.admin_infrastructure_section),
                        icon = Icons.Rounded.AdminPanelSettings,
                    )
                }
                item(key = "infra-security") {
                    AdminInfoRow(
                        icon = Icons.Rounded.Security,
                        title = stringResource(R.string.profile_security_rules),
                        note = stringResource(R.string.admin_infra_firebase_console),
                    )
                }
                item(key = "infra-backup") {
                    AdminInfoRow(
                        icon = Icons.Rounded.Backup,
                        title = stringResource(R.string.admin_infra_backup_title),
                        note = stringResource(R.string.admin_infra_backup_note),
                    )
                }
                item(key = "infra-dispatch") {
                    AdminInfoRow(
                        icon = Icons.Rounded.NotificationsActive,
                        title = stringResource(R.string.admin_infra_dispatch_title),
                        note = stringResource(R.string.admin_infra_dispatch_note),
                    )
                }
                item(key = "infra-footer") {
                    Text(
                        text = stringResource(R.string.admin_infrastructure_footer),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = CzSpacing.xs),
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
    onClick: () -> Unit,
    badge: String? = null,
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
                Text(
                    note,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
            canAssignAnyRole = true,
            assignsAllRoles = true,
            pendingCount = 2,
            onBack = {},
            onOpenModerationQueue = {},
            onOpenAdminOnboarding = {},
            onOpenRoleManagement = {},
            onOpenRegistrationReview = {},
        )
    }
}
