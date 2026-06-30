package fr.ziyon.campzone.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WorkspacePremium
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzCard
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.navigation.CampzoneDeepLink
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.model.AppNotification
import fr.ziyon.campzone.data.model.AppNotificationKind
import fr.ziyon.campzone.data.model.NotificationTopics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppNotificationFeedRoute(
    uid: String,
    role: UserRole,
    church: String,
    onBack: () -> Unit,
    onOpenDeepLink: (CampzoneDeepLink) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppNotificationFeedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uid, role, church) { viewModel.load(uid, role, church) }

    AppNotificationFeedScreen(
        uiState = uiState,
        onBack = onBack,
        onRetry = { viewModel.retry(uid, role, church) },
        onOpen = onOpenDeepLink,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNotificationFeedScreen(
    uiState: AppNotificationFeedUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (CampzoneDeepLink) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.notif_feed_title), color = colors.textPrimary) },
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
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (uiState) {
                AppNotificationFeedUiState.Loading -> CzLoadingView(
                    modifier = Modifier.fillMaxWidth(),
                    message = stringResource(R.string.notif_feed_loading),
                )

                AppNotificationFeedUiState.Empty -> CzEmptyState(
                    title = stringResource(R.string.notif_feed_empty_title),
                    message = stringResource(R.string.notif_feed_empty_msg),
                    modifier = Modifier.fillMaxSize(),
                    icon = { Icon(Icons.Rounded.Notifications, contentDescription = null, tint = colors.textSecondary) },
                )

                AppNotificationFeedUiState.Error -> CzErrorState(
                    title = stringResource(R.string.notif_feed_error_title),
                    message = stringResource(R.string.notif_feed_error_msg),
                    onRetry = onRetry,
                    retryLabel = stringResource(R.string.notif_feed_refresh),
                    modifier = Modifier.fillMaxSize(),
                )

                is AppNotificationFeedUiState.Loaded -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(CzSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
                ) {
                    items(uiState.notifications, key = { it.id }) { notification ->
                        AppNotificationRow(notification = notification, onOpen = onOpen)
                    }
                    item { Spacer(modifier = Modifier.size(CzSpacing.sm)) }
                }
            }
        }
    }
}

@Composable
private fun AppNotificationRow(
    notification: AppNotification,
    onOpen: (CampzoneDeepLink) -> Unit,
) {
    val colors = MaterialTheme.czColors
    val deepLink = notification.deepLink()
    val openHint = stringResource(R.string.notif_feed_open_cd)

    CzCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (deepLink != null) {
                    Modifier.semantics { contentDescription = openHint }
                } else {
                    Modifier
                },
            ),
        onClick = deepLink?.let { { onOpen(it) } },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(colors.ember.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = notification.kind.icon(),
                    contentDescription = null,
                    tint = colors.ember,
                    modifier = Modifier.size(18.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = notification.audienceText(),
                        color = colors.ember,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatSentAt(notification.sentAt),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
                Text(
                    text = notification.title,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (notification.body.isNotBlank()) {
                    Text(
                        text = notification.body,
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (deepLink != null) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun AppNotification.audienceText(): String {
    val roleRaw = role
    val resolvedRole = roleRaw?.let { UserRole.fromWire(it) }
    return when {
        resolvedRole != null && roleRaw.equals(resolvedRole.rawValue, ignoreCase = true) ->
            resolvedRole.displayName
        topic == NotificationTopics.globalAnnouncement -> stringResource(R.string.notif_audience_everyone)
        else -> stringResource(kind.labelRes())
    }
}

private fun AppNotificationKind.icon(): ImageVector = when (this) {
    AppNotificationKind.Announcement -> Icons.Rounded.Campaign
    AppNotificationKind.Badge -> Icons.Rounded.WorkspacePremium
    AppNotificationKind.ChatMessage -> Icons.AutoMirrored.Rounded.Chat
    AppNotificationKind.ChatMention -> Icons.Rounded.AlternateEmail
    AppNotificationKind.Poll -> Icons.Rounded.BarChart
    AppNotificationKind.Registration -> Icons.Rounded.PersonAdd
    AppNotificationKind.ScheduleReminder -> Icons.Rounded.Schedule
    AppNotificationKind.TeamUpdate -> Icons.Rounded.Groups
    AppNotificationKind.Transportation -> Icons.Rounded.DirectionsCar
    AppNotificationKind.Unknown -> Icons.Rounded.Notifications
}

private fun AppNotificationKind.labelRes(): Int = when (this) {
    AppNotificationKind.Announcement -> R.string.notif_cat_announcements_title
    AppNotificationKind.Badge -> R.string.badges_badge
    AppNotificationKind.ChatMessage, AppNotificationKind.ChatMention -> R.string.notif_cat_chat_title
    AppNotificationKind.Poll -> R.string.notif_channel_poll
    AppNotificationKind.Registration -> R.string.notif_channel_registration
    AppNotificationKind.ScheduleReminder -> R.string.notif_cat_reminders_title
    AppNotificationKind.TeamUpdate -> R.string.notif_cat_team_title
    AppNotificationKind.Transportation -> R.string.camping_transportation
    AppNotificationKind.Unknown -> R.string.notif_feed_title
}

private fun formatSentAt(date: Date): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(date)

@Preview
@Composable
private fun AppNotificationFeedScreenPreview() {
    CampzoneTheme {
        AppNotificationFeedScreen(
            uiState = AppNotificationFeedUiState.Loaded(
                listOf(
                    AppNotification(
                        id = "1",
                        appId = AppNotification.APP_ID,
                        kind = AppNotificationKind.Announcement,
                        title = "Arrival gate update",
                        body = "Use the north parking entrance when you arrive.",
                        topic = NotificationTopics.globalAnnouncement,
                        sentAt = Date(),
                        announcementId = "arrival",
                    ),
                    AppNotification(
                        id = "2",
                        appId = AppNotification.APP_ID,
                        kind = AppNotificationKind.Announcement,
                        title = "Leader briefing",
                        body = "Leaders meet at the main tent before opening worship.",
                        topic = NotificationTopics.roleTopic(UserRole.Leader.rawValue),
                        sentAt = Date(),
                        role = UserRole.Leader.rawValue,
                    ),
                ),
            ),
            onBack = {},
            onRetry = {},
            onOpen = {},
        )
    }
}
