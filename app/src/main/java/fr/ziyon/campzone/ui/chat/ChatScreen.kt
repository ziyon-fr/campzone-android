package fr.ziyon.campzone.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzAvatar
import fr.ziyon.campzone.core.designsystem.CzAvatarSize
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.BlockedUser
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.ChatMessage
import fr.ziyon.campzone.data.model.ContentReportReason
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.Team
import fr.ziyon.campzone.data.chat.previewChatMessages
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun CampingChatRoute(
    campingId: String,
    camping: Camping?,
    attendees: List<CampingAttendee>,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    if (camping == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CzLoadingView(message = stringResource(R.string.chat_loading))
        }
        return
    }

    val scope = ChatScope.Camping(campingId)
    val permissionUser = remember(authenticatedUser) {
        PermissionUser(authenticatedUser.role, authenticatedUser.uid, authenticatedUser.church)
    }
    val evaluator = remember { AppPermissionEvaluator() }
    val permissionContext = camping.permissionContext()
    val canModerate = evaluator.canModerateCampingChat(permissionUser, permissionContext)
    val isApprovedParticipant = attendees.any {
        it.registrationStatus == RegistrationApprovalStatus.Approved &&
            (it.userId == authenticatedUser.uid ||
                it.guardianId == authenticatedUser.uid ||
                it.id == authenticatedUser.uid)
    }
    val canAccess = isApprovedParticipant || canModerate

    ChatRouteContent(
        scope = scope,
        title = stringResource(R.string.chat_camp_title),
        restrictedMessage = stringResource(R.string.chat_camp_restricted_message),
        canAccess = canAccess,
        canModerate = canModerate,
        authenticatedUser = authenticatedUser,
        onBack = onBack,
        modifier = modifier,
        viewModel = viewModel,
        header = null,
    )
}

@Composable
fun TeamChatRoute(
    campingId: String,
    teamId: String,
    camping: Camping?,
    team: Team?,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    if (camping == null || team == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CzLoadingView(message = stringResource(R.string.chat_loading))
        }
        return
    }

    val scope = ChatScope.Team(campingId, teamId)
    val permissionUser = remember(authenticatedUser) {
        PermissionUser(authenticatedUser.role, authenticatedUser.uid, authenticatedUser.church)
    }
    val evaluator = remember { AppPermissionEvaluator() }
    val permissionContext = camping.permissionContext()
    val canModerate = evaluator.canModerateTeamChat(permissionUser, permissionContext)
    val isTeamMember = team.members.any { it.userId == authenticatedUser.uid }
    val canAccess = isTeamMember || canModerate

    ChatRouteContent(
        scope = scope,
        title = team.name.takeUnless { it.isBlank() } ?: stringResource(R.string.chat_team_title),
        restrictedMessage = stringResource(R.string.chat_team_restricted_message, team.name),
        canAccess = canAccess,
        canModerate = canModerate,
        authenticatedUser = authenticatedUser,
        onBack = onBack,
        modifier = modifier,
        viewModel = viewModel,
        header = { TeamChatHeader(team) },
    )
}

@Composable
private fun ChatRouteContent(
    scope: ChatScope,
    title: String,
    restrictedMessage: String,
    canAccess: Boolean,
    canModerate: Boolean,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    modifier: Modifier,
    viewModel: ChatViewModel,
    header: (@Composable () -> Unit)?,
) {
    val state by viewModel.uiState.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()

    LaunchedEffect(canAccess, scope, authenticatedUser.uid) {
        if (canAccess) {
            viewModel.start(scope.campingId, scope.teamId, authenticatedUser.uid)
        }
    }

    ChatScreen(
        title = title,
        state = if (canAccess) state else ChatUiState.Loaded(emptyList(), emptyList()),
        scope = scope,
        currentUser = authenticatedUser,
        canAccess = canAccess,
        canModerate = canModerate,
        restrictedMessage = restrictedMessage,
        draft = draft,
        isSending = isSending,
        operationError = operationError,
        operationMessage = operationMessage,
        onDraftChange = viewModel::updateDraft,
        onSend = { viewModel.send(scope.campingId, scope.teamId, authenticatedUser) },
        onRetry = { viewModel.retry(scope.campingId, scope.teamId, authenticatedUser.uid) },
        onBack = onBack,
        onTogglePin = viewModel::togglePinned,
        onDelete = { message -> viewModel.softDelete(message, authenticatedUser.uid) },
        onReport = { message, reason, note ->
            viewModel.reportMessage(message, authenticatedUser.uid, reason, note)
        },
        onBlock = { message ->
            viewModel.setBlocked(
                blocked = true,
                currentUserId = authenticatedUser.uid,
                blockedUserId = message.senderId,
                displayName = message.senderName,
            )
        },
        onUnblock = { blocked ->
            viewModel.setBlocked(
                blocked = false,
                currentUserId = authenticatedUser.uid,
                blockedUserId = blocked.blockedUserId,
                displayName = blocked.displayName,
            )
        },
        onDismissError = viewModel::clearOperationError,
        onDismissMessage = viewModel::clearOperationMessage,
        modifier = modifier,
        header = header,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    title: String,
    state: ChatUiState,
    scope: ChatScope,
    currentUser: AuthenticatedUser,
    canAccess: Boolean,
    canModerate: Boolean,
    restrictedMessage: String,
    draft: String,
    isSending: Boolean,
    operationError: String?,
    operationMessage: String?,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onTogglePin: (ChatMessage) -> Unit,
    onDelete: (ChatMessage) -> Unit,
    onReport: (ChatMessage, ContentReportReason, String) -> Unit,
    onBlock: (ChatMessage) -> Unit,
    onUnblock: (BlockedUser) -> Unit,
    onDismissError: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    var blockedSheetVisible by rememberSaveable { mutableStateOf(false) }
    var reportingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    val colors = MaterialTheme.czColors

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(),
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { blockedSheetVisible = true }) {
                        Icon(Icons.Rounded.Block, stringResource(R.string.chat_blocked_users))
                    }
                }, windowInsets = WindowInsets()
            )
        },
        bottomBar = {
            if (canAccess) {
                ChatComposer(
                    draft = draft,
                    isSending = isSending,
                    onDraftChange = onDraftChange,
                    onSend = onSend,
                    modifier = Modifier.imePadding(),
                )
            }
        },

    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (header != null && canAccess) header()
            when {
                !canAccess -> RestrictedChat(restrictedMessage)
                else -> ChatTimeline(
                    state = state,
                    scope = scope,
                    currentUserId = currentUser.uid,
                    canModerate = canModerate,
                    onRetry = onRetry,
                    onTogglePin = onTogglePin,
                    onDelete = onDelete,
                    onReport = { reportingMessage = it },
                    onBlock = onBlock,
                    modifier = Modifier.weight(1f),
                )
            }
            operationError?.let {
                ChatBanner(text = it, color = colors.error, onDismiss = onDismissError)
            }
            operationMessage?.let {
                ChatBanner(text = it, color = colors.success, onDismiss = onDismissMessage)
            }
        }
    }

    reportingMessage?.let { message ->
        ReportMessageDialog(
            message = message,
            onDismiss = { reportingMessage = null },
            onSubmit = { reason, note ->
                onReport(message, reason, note)
                reportingMessage = null
            },
        )
    }

    if (blockedSheetVisible) {
        val blockedUsers = (state as? ChatUiState.Loaded)?.blockedUsers.orEmpty()
        BlockedUsersDialog(
            blockedUsers = blockedUsers,
            onDismiss = { blockedSheetVisible = false },
            onUnblock = onUnblock,
        )
    }
}

@Composable
private fun ChatTimeline(
    state: ChatUiState,
    scope: ChatScope,
    currentUserId: String,
    canModerate: Boolean,
    onRetry: () -> Unit,
    onTogglePin: (ChatMessage) -> Unit,
    onDelete: (ChatMessage) -> Unit,
    onReport: (ChatMessage) -> Unit,
    onBlock: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val loaded = state as? ChatUiState.Loaded
    val visibleMessages = loaded?.visibleMessages.orEmpty()

    LaunchedEffect(visibleMessages.size) {
        if (visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.lastIndex)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        when (state) {
            ChatUiState.Loading -> CzLoadingView(
                modifier = Modifier.align(Alignment.Center),
                message = stringResource(R.string.chat_loading),
            )
            is ChatUiState.Error -> CzErrorState(
                title = stringResource(R.string.chat_error_title),
                message = state.message,
                onRetry = onRetry,
                retryLabel = stringResource(R.string.common_retry),
                modifier = Modifier.align(Alignment.Center),
            )
            is ChatUiState.Loaded -> {
                if (visibleMessages.isEmpty()) {
                    CzEmptyState(
                        title = stringResource(R.string.chat_empty_title),
                        message = if (scope is ChatScope.Team) {
                            stringResource(R.string.chat_team_empty_message)
                        } else {
                            stringResource(R.string.chat_camp_empty_message)
                        },
                        icon = {
                            Icon(
                                Icons.Rounded.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.czColors.primary,
                                modifier = Modifier.size(32.dp),
                            )
                        },
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = CzSpacing.base,
                            vertical = CzSpacing.md,
                        ),
                        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
                    ) {
                        if (state.pinnedMessages.isNotEmpty()) {
                            item(key = "pinned") {
                                PinnedMessagesStrip(state.pinnedMessages)
                            }
                        }
                        items(visibleMessages, key = { it.id }) { message ->
                            ChatMessageRow(
                                message = message,
                                isCurrentUser = message.senderId == currentUserId,
                                canModerate = canModerate,
                                onTogglePin = { onTogglePin(message) },
                                onDelete = { onDelete(message) },
                                onReport = { onReport(message) },
                                onBlock = { onBlock(message) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinnedMessagesStrip(messages: List<ChatMessage>) {
    val colors = MaterialTheme.czColors
    val removedText = stringResource(R.string.chat_message_removed)
    Surface(
        color = colors.primary.copy(alpha = 0.10f),
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
        Column(
            modifier = Modifier.padding(CzSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PushPin, null, tint = colors.primary, modifier = Modifier.size(16.dp))
                Text(
                    text = stringResource(R.string.chat_pinned),
                    color = colors.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            messages.take(2).forEach { message ->
                Text(
                    text = "${message.senderName}: ${message.displayText(removedText)}",
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ChatMessageRow(
    message: ChatMessage,
    isCurrentUser: Boolean,
    canModerate: Boolean,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
) {
    val alignment = if (isCurrentUser) Alignment.End else Alignment.Start
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isCurrentUser) {
            ChatAvatar(message)
            Spacer(Modifier.width(CzSpacing.sm))
        }
        Column(
            modifier = Modifier.fillMaxWidth(0.78f),
            horizontalAlignment = alignment,
            verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            MessageMetadata(message, isCurrentUser)
            Row(
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                verticalAlignment = Alignment.Top,
            ) {
                if (isCurrentUser) {
                    MessageActions(
                        message = message,
                        isCurrentUser = true,
                        canModerate = canModerate,
                        onTogglePin = onTogglePin,
                        onDelete = onDelete,
                        onReport = onReport,
                        onBlock = onBlock,
                    )
                }
                MessageBubble(message = message, isCurrentUser = isCurrentUser)
                if (!isCurrentUser) {
                    MessageActions(
                        message = message,
                        isCurrentUser = false,
                        canModerate = canModerate,
                        onTogglePin = onTogglePin,
                        onDelete = onDelete,
                        onReport = onReport,
                        onBlock = onBlock,
                    )
                }
            }
        }
        if (isCurrentUser) {
            Spacer(Modifier.width(CzSpacing.sm))
            ChatAvatar(message)
        }
    }
}

@Composable
private fun ChatAvatar(message: ChatMessage) {
    CzAvatar(
        imageUrl = message.senderPhotoUrl,
        contentDescription = stringResource(R.string.chat_sender_avatar, message.senderName),
        initials = message.senderName.initials(),
        size = CzAvatarSize.Small,
    )
}

@Composable
private fun MessageMetadata(message: ChatMessage, isCurrentUser: Boolean) {
    val colors = MaterialTheme.czColors
    Row(
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isCurrentUser) {
            Text(
                text = message.senderName,
                color = colors.textPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (message.pinned) {
            Icon(Icons.Rounded.PushPin, null, tint = colors.primary, modifier = Modifier.size(12.dp))
        }
        Text(
            text = message.createdAt?.let(chatTimeFormatter::format).orEmpty(),
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, isCurrentUser: Boolean) {
    val colors = MaterialTheme.czColors
    val isDeleted = message.isDeleted
    val displayText = message.displayText(stringResource(R.string.chat_message_removed))
    val background = when {
        isDeleted -> colors.textSecondary.copy(alpha = 0.12f)
        isCurrentUser -> colors.primary
        else -> colors.surface
    }
    val textColor = when {
        isDeleted -> colors.textSecondary
        isCurrentUser -> colors.onPrimary
        else -> colors.textPrimary
    }

    Text(
        text = displayText,
        color = textColor,
        style = MaterialTheme.typography.bodyMedium,
        fontStyle = if (isDeleted) FontStyle.Italic else FontStyle.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(CzRadius.lg))
            .background(background)
            .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm)
            .semantics {
                contentDescription = "${message.senderName}: $displayText"
            },
    )
}

@Composable
private fun MessageActions(
    message: ChatMessage,
    isCurrentUser: Boolean,
    canModerate: Boolean,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
) {
    if (message.isDeleted) return
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Rounded.MoreVert,
                stringResource(R.string.chat_message_options),
                tint = MaterialTheme.czColors.textSecondary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_report)) },
                leadingIcon = { Icon(Icons.Rounded.Flag, null) },
                onClick = {
                    expanded = false
                    onReport()
                },
            )
            if (!isCurrentUser) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_block_sender)) },
                    leadingIcon = { Icon(Icons.Rounded.Block, null) },
                    onClick = {
                        expanded = false
                        onBlock()
                    },
                )
            }
            if (canModerate) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (message.pinned) stringResource(R.string.chat_unpin)
                            else stringResource(R.string.chat_pin)
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
                    onClick = {
                        expanded = false
                        onTogglePin()
                    },
                )
            }
            if (isCurrentUser || canModerate) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_remove)) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun ChatComposer(
    draft: String,
    isSending: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val canSend = draft.trim().isNotEmpty() && !isSending
    Surface(color = colors.background) {
        Column(modifier = modifier.fillMaxWidth()) {
            HorizontalDivider(color = colors.divider)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CzSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.chat_message_placeholder)) },
                    minLines = 1,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                    supportingText = {
                        Text(
                            text = stringResource(R.string.chat_character_count, draft.length, ChatMessage.CLIENT_TEXT_CAP),
                            color = colors.textSecondary,
                        )
                    },
                    shape = RoundedCornerShape(CzRadius.lg),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.divider,
                        cursorColor = colors.primary,
                    ),
                )
                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(CzRadius.full))
                        .background(if (canSend) colors.primary else colors.surface),
                ) {
                    Icon(
                        Icons.Outlined.Send,
                        stringResource(R.string.chat_send),
                        tint = if (canSend) colors.onPrimary else colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RestrictedChat(message: String) {
    CzEmptyState(
        title = stringResource(R.string.chat_restricted_title),
        message = message,
        icon = {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.czColors.primary,
                modifier = Modifier.size(32.dp),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(CzSpacing.xl),
    )
}

@Composable
private fun TeamChatHeader(team: Team) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Groups, null, tint = colors.primary, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.chat_private_team_chat),
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = team.name,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Rounded.Lock, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ChatBanner(text: String, color: Color, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f))
            .clickable(onClick = onDismiss)
            .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.common_ok),
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ReportMessageDialog(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onSubmit: (ContentReportReason, String) -> Unit,
) {
    var selectedReason by rememberSaveable(message.id) {
        mutableStateOf(ContentReportReason.Inappropriate)
    }
    var note by rememberSaveable(message.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_report_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                ContentReportReason.entries.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(CzRadius.sm))
                            .clickable { selectedReason = reason }
                            .padding(vertical = CzSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason },
                        )
                        Text(reason.displayText())
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(240) },
                    label = { Text(stringResource(R.string.chat_report_note)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(selectedReason, note) }) {
                Text(stringResource(R.string.chat_report_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun BlockedUsersDialog(
    blockedUsers: List<BlockedUser>,
    onDismiss: () -> Unit,
    onUnblock: (BlockedUser) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_blocked_users)) },
        text = {
            if (blockedUsers.isEmpty()) {
                Text(stringResource(R.string.chat_no_blocked_users))
            } else {
                Column(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                ) {
                    blockedUsers.forEach { blocked ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = blocked.displayName,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = { onUnblock(blocked) }) {
                                Icon(Icons.Rounded.Undo, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(CzSpacing.xs))
                                Text(stringResource(R.string.chat_unblock))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_ok))
            }
        },
    )
}

private sealed interface ChatScope {
    val campingId: String
    val teamId: String?

    data class Camping(override val campingId: String) : ChatScope {
        override val teamId: String? = null
    }

    data class Team(
        override val campingId: String,
        override val teamId: String,
    ) : ChatScope
}

private fun Camping.permissionContext(): CampingPermissionContext =
    CampingPermissionContext(
        organizerLevelType = organizerLevel.type.wireValue,
        organizerLevelValue = organizerLevel.value,
        createdByUid = createdByUid,
    )

private fun ChatMessage.displayText(removedText: String): String =
    if (isDeleted) removedText else text

private fun String.initials(): String =
    split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .joinToString("")
        .ifBlank { "CZ" }

@Composable
private fun ContentReportReason.displayText(): String =
    when (this) {
        ContentReportReason.Inappropriate -> stringResource(R.string.chat_report_reason_inappropriate)
        ContentReportReason.Spam -> stringResource(R.string.chat_report_reason_spam)
        ContentReportReason.Misinformation -> stringResource(R.string.chat_report_reason_misinformation)
        ContentReportReason.Harassment -> stringResource(R.string.chat_report_reason_harassment)
        ContentReportReason.Other -> stringResource(R.string.chat_report_reason_other)
    }

private val chatTimeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

@Preview
@Composable
private fun ChatScreenPreview() {
    CampzoneTheme {
        ChatScreen(
            title = "Camp Chat",
            state = ChatUiState.Loaded(previewChatMessages(), emptyList()),
            scope = ChatScope.Camping("preview-camping"),
            currentUser = previewUser,
            canAccess = true,
            canModerate = true,
            restrictedMessage = "",
            draft = "See you soon",
            isSending = false,
            operationError = null,
            operationMessage = null,
            onDraftChange = {},
            onSend = {},
            onRetry = {},
            onBack = {},
            onTogglePin = {},
            onDelete = {},
            onReport = { _, _, _ -> },
            onBlock = {},
            onUnblock = {},
            onDismissError = {},
            onDismissMessage = {},
        )
    }
}

private val previewUser = AuthenticatedUser(
    uid = "preview-user",
    email = "preview@campzone.local",
    displayName = "Preview Camper",
    photoUrl = null,
    role = fr.ziyon.campzone.core.permissions.UserRole.Admin,
    church = "Central SDA",
    age = 28,
    preferredLanguage = "en",
    gender = null,
    onboardingCompleted = true,
)
