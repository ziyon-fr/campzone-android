package fr.ziyon.campzone.ui.notifications

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Cabin
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzCard
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzSectionHeader
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.model.NotificationAuthorizationState
import fr.ziyon.campzone.data.model.NotificationSettings

@Composable
fun NotificationSettingsRoute(
    uid: String,
    role: UserRole,
    onBack: () -> Unit,
    onOpenCampingChannels: () -> Unit,
    onOpenTeamChannels: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    val channels by viewModel.channels.collectAsState()

    LaunchedEffect(uid, role) {
        viewModel.load(uid, role)
        viewModel.loadChannelsIfNeeded()
    }

    NotificationSettingsScreen(
        uiState = uiState,
        channels = channels,
        isSaving = isSaving,
        operationMessage = operationMessage,
        onBack = onBack,
        onRetry = viewModel::reload,
        onSetMaster = viewModel::setMasterEnabled,
        onSetCategory = viewModel::setCategory,
        onToggleRole = viewModel::toggleRole,
        onOpenCampingChannels = onOpenCampingChannels,
        onOpenTeamChannels = onOpenTeamChannels,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    uiState: NotificationSettingsUiState,
    channels: NotificationChannelsState,
    isSaving: Boolean,
    operationMessage: NotificationOpMessage?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSetMaster: (Boolean) -> Unit,
    onSetCategory: (NotificationCategory, Boolean) -> Unit,
    onToggleRole: (UserRole, Boolean) -> Unit,
    onOpenCampingChannels: () -> Unit,
    onOpenTeamChannels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.notif_settings_title), color = colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.notif_back_cd),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (isSaving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = colors.ember)
            }
            operationMessage?.let { message ->
                Text(
                    text = stringResource(
                        when (message) {
                            NotificationOpMessage.Saved -> R.string.notif_settings_saved
                            NotificationOpMessage.SaveFailed -> R.string.notif_settings_save_error
                        },
                    ),
                    color = if (message == NotificationOpMessage.Saved) colors.success else colors.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = CzSpacing.lg, vertical = CzSpacing.sm),
                )
            }

            when (uiState) {
                NotificationSettingsUiState.Loading -> CzLoadingView(
                    modifier = Modifier.fillMaxWidth(),
                    message = stringResource(R.string.notif_settings_loading),
                )

                is NotificationSettingsUiState.Error -> CzErrorState(
                    title = stringResource(R.string.notif_settings_error_title),
                    message = uiState.message,
                    onRetry = onRetry,
                    retryLabel = stringResource(R.string.notif_retry),
                    modifier = Modifier.fillMaxWidth(),
                )

                is NotificationSettingsUiState.Loaded -> NotificationSettingsContent(
                    settings = uiState.settings,
                    roleOptions = uiState.roleOptions,
                    channels = channels,
                    onSetMaster = onSetMaster,
                    onSetCategory = onSetCategory,
                    onToggleRole = onToggleRole,
                    onOpenSystemSettings = { openNotificationSettings(context) },
                    onOpenCampingChannels = onOpenCampingChannels,
                    onOpenTeamChannels = onOpenTeamChannels,
                )
            }
        }
    }
}

@Composable
private fun NotificationSettingsContent(
    settings: NotificationSettings,
    roleOptions: List<UserRole>,
    channels: NotificationChannelsState,
    onSetMaster: (Boolean) -> Unit,
    onSetCategory: (NotificationCategory, Boolean) -> Unit,
    onToggleRole: (UserRole, Boolean) -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenCampingChannels: () -> Unit,
    onOpenTeamChannels: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val activeCampingChannelCount = settings.subscribedCampingIds.count { subscribedId ->
        channels.campings.any { it.id == subscribedId }
    }
    val activeTeamChannelCount = settings.subscribedTeamIds.count { subscribedId ->
        channels.personalTeams.any { it.team.id == subscribedId }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        // Master
        CzCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                NotificationToggleRow(
                    icon = Icons.Rounded.NotificationsActive,
                    title = stringResource(R.string.notif_master_title),
                    description = stringResource(R.string.notif_master_desc),
                    checked = settings.isEnabled,
                    onCheckedChange = onSetMaster,
                )
                Text(
                    text = stringResource(R.string.notif_master_footer),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                NotificationNavRow(
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    title = stringResource(R.string.notif_system_permission_title),
                    description = stringResource(R.string.notif_system_permission_desc),
                    value = stringResource(settings.authorizationState.displayNameRes()),
                    onClick = onOpenSystemSettings,
                )
            }
        }

        // Categories
        CzSectionHeader(title = stringResource(R.string.notif_section_categories))
        CzCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
                NotificationToggleRow(
                    icon = Icons.Rounded.Campaign,
                    title = stringResource(R.string.notif_cat_announcements_title),
                    description = stringResource(R.string.notif_cat_announcements_desc),
                    checked = settings.announcementsEnabled,
                    enabled = settings.isEnabled,
                    onCheckedChange = { onSetCategory(NotificationCategory.Announcements, it) },
                )
                NotificationToggleRow(
                    icon = Icons.AutoMirrored.Rounded.Chat,
                    title = stringResource(R.string.notif_cat_chat_title),
                    description = stringResource(R.string.notif_cat_chat_desc),
                    checked = settings.chatMessagesEnabled,
                    enabled = settings.isEnabled,
                    onCheckedChange = { onSetCategory(NotificationCategory.Chat, it) },
                )
                NotificationToggleRow(
                    icon = Icons.Rounded.Schedule,
                    title = stringResource(R.string.notif_cat_reminders_title),
                    description = stringResource(R.string.notif_cat_reminders_desc),
                    checked = settings.scheduleRemindersEnabled,
                    enabled = settings.isEnabled,
                    onCheckedChange = { onSetCategory(NotificationCategory.Reminders, it) },
                )
                NotificationToggleRow(
                    icon = Icons.Rounded.Shield,
                    title = stringResource(R.string.notif_cat_role_title),
                    description = stringResource(R.string.notif_cat_role_desc),
                    checked = settings.roleMessagesEnabled,
                    enabled = settings.isEnabled,
                    onCheckedChange = { onSetCategory(NotificationCategory.Role, it) },
                )
                NotificationToggleRow(
                    icon = Icons.Rounded.Groups,
                    title = stringResource(R.string.notif_cat_team_title),
                    description = stringResource(R.string.notif_cat_team_desc),
                    checked = settings.teamUpdatesEnabled,
                    enabled = settings.isEnabled,
                    onCheckedChange = { onSetCategory(NotificationCategory.Team, it) },
                )
            }
        }

        // Role audiences (only meaningful when more than one option, e.g. admins)
        if (settings.roleMessagesEnabled && roleOptions.size > 1) {
            CzSectionHeader(title = stringResource(R.string.notif_section_role_audiences))
            CzCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
                    roleOptions.forEach { option ->
                        NotificationToggleRow(
                            icon = Icons.Rounded.Shield,
                            title = option.displayName,
                            description = stringResource(R.string.notif_role_audience_desc, option.displayName),
                            checked = settings.subscribedRoles.contains(option),
                            enabled = settings.isEnabled,
                            onCheckedChange = { onToggleRole(option, it) },
                        )
                    }
                    Text(
                        text = stringResource(R.string.notif_role_audience_footer),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        // Channels
        CzSectionHeader(title = stringResource(R.string.notif_section_channels))
        CzCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
                NotificationNavRow(
                    icon = Icons.Rounded.Cabin,
                    title = stringResource(R.string.notif_chan_camping_title),
                    description = stringResource(R.string.notif_chan_camping_desc),
                    value = campingChannelValueText(
                        selectedCount = activeCampingChannelCount,
                        availableCount = channels.campings.size,
                    ),
                    enabled = settings.isEnabled && channels.campings.isNotEmpty(),
                    onClick = onOpenCampingChannels,
                )
                NotificationNavRow(
                    icon = Icons.Rounded.Groups,
                    title = stringResource(R.string.notif_chan_team_title),
                    description = stringResource(R.string.notif_chan_team_desc),
                    value = teamChannelValueText(
                        selectedCount = activeTeamChannelCount,
                        availableCount = channels.personalTeams.size,
                    ),
                    enabled = settings.isEnabled &&
                        channels.campings.isNotEmpty() &&
                        channels.personalTeams.isNotEmpty(),
                    onClick = onOpenTeamChannels,
                )
                Text(
                    text = stringResource(
                        if (channels.campings.isEmpty()) {
                            R.string.notif_channels_footer_empty
                        } else {
                            R.string.notif_channels_footer
                        },
                    ),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(modifier = Modifier.size(CzSpacing.lg))
    }
}

@Composable
private fun campingChannelValueText(selectedCount: Int, availableCount: Int): String =
    if (availableCount == 0) {
        stringResource(R.string.notif_value_not_attending)
    } else if (selectedCount == 0) {
        stringResource(R.string.notif_value_all)
    } else {
        pluralStringResource(R.plurals.notif_value_selected, selectedCount, selectedCount)
    }

@Composable
private fun teamChannelValueText(selectedCount: Int, availableCount: Int): String =
    if (availableCount == 0 || selectedCount == 0) {
        stringResource(R.string.notif_value_none)
    } else {
        pluralStringResource(R.plurals.notif_value_selected, selectedCount, selectedCount)
    }

@Composable
internal fun NotificationToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = colors.ember, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = colors.textPrimary, style = MaterialTheme.typography.bodyLarge)
            Text(text = description, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
internal fun NotificationNavRow(
    icon: ImageVector,
    title: String,
    description: String,
    value: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .let { if (enabled) it.clickableRow(onClick) else it },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = colors.ember, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = colors.textPrimary, style = MaterialTheme.typography.bodyLarge)
            Text(text = description, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            text = value,
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.textSecondary)
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

private fun openNotificationSettings(context: android.content.Context) {
    runCatching {
        val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.fromParts("package", context.packageName, null))
        }
        context.startActivity(intent)
    }
}

private fun NotificationAuthorizationState.displayNameRes(): Int = when (this) {
    NotificationAuthorizationState.NotDetermined -> R.string.notif_auth_not_requested
    NotificationAuthorizationState.Denied -> R.string.notif_auth_disabled
    NotificationAuthorizationState.Authorized -> R.string.notif_auth_enabled
    NotificationAuthorizationState.Provisional -> R.string.notif_auth_quiet
    NotificationAuthorizationState.Ephemeral -> R.string.notif_auth_temporary
    NotificationAuthorizationState.Unknown -> R.string.notif_auth_unknown
}

@Preview
@Composable
private fun NotificationSettingsScreenPreview() {
    CampzoneTheme {
        NotificationSettingsScreen(
            uiState = NotificationSettingsUiState.Loaded(
                settings = NotificationSettings(subscribedRoles = listOf(UserRole.Admin)),
                roleOptions = UserRole.allWireRoles.toList(),
            ),
            channels = NotificationChannelsState(),
            isSaving = false,
            operationMessage = null,
            onBack = {},
            onRetry = {},
            onSetMaster = {},
            onSetCategory = { _, _ -> },
            onToggleRole = { _, _ -> },
            onOpenCampingChannels = {},
            onOpenTeamChannels = {},
        )
    }
}
