package fr.ziyon.campzone.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzAvatar
import fr.ziyon.campzone.core.designsystem.CzAvatarSize
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.chat.ChatAudioRecorder
import fr.ziyon.campzone.data.chat.MentionCandidate
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.ChatAttachmentKind
import fr.ziyon.campzone.data.model.ChatMention
import fr.ziyon.campzone.data.model.ChatMessage
import fr.ziyon.campzone.data.model.ContentReportReason
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.Team
import fr.ziyon.campzone.ui.camping.registrations.permissionContext

private sealed interface ChatScope {
    val campingId: String
    val teamId: String?

    data class Camping(override val campingId: String) : ChatScope {
        override val teamId: String? = null
    }

    data class Team(override val campingId: String, val team: String) : ChatScope {
        override val teamId: String = team
    }
}

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
        ChatLoading(modifier)
        return
    }
    val evaluator = remember { AppPermissionEvaluator() }
    val permissionUser = remember(authenticatedUser) {
        PermissionUser(authenticatedUser.role, authenticatedUser.uid, authenticatedUser.church)
    }
    val canModerate = evaluator.canModerateCampingChat(permissionUser, camping.permissionContext())
    val approved = remember(attendees) {
        attendees.filter { it.registrationStatus == RegistrationApprovalStatus.Approved }
    }
    val isApprovedParticipant = approved.any {
        it.userId == authenticatedUser.uid ||
            it.guardianId == authenticatedUser.uid ||
            it.id == authenticatedUser.uid
    }
    val people = remember(approved, authenticatedUser.uid) {
        campingMentionPeople(approved, authenticatedUser.uid)
    }
    val mentionableUserIds = remember(people) { people.map { it.id } }
    val candidates = listOf(everyoneCandidate()) + people

    ChatConversation(
        viewModel = viewModel,
        scope = ChatScope.Camping(campingId),
        title = stringResource(R.string.chat_camp_title),
        restrictedMessage = stringResource(R.string.chat_camp_restricted_message),
        emptyMessage = stringResource(R.string.chat_camp_empty_message),
        canAccess = isApprovedParticipant || canModerate,
        canModerate = canModerate,
        sender = authenticatedUser,
        mentionCandidates = candidates,
        mentionableUserIds = mentionableUserIds,
        header = null,
        onBack = onBack,
        modifier = modifier,
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
        ChatLoading(modifier)
        return
    }
    val evaluator = remember { AppPermissionEvaluator() }
    val permissionUser = remember(authenticatedUser) {
        PermissionUser(authenticatedUser.role, authenticatedUser.uid, authenticatedUser.church)
    }
    val canModerate = evaluator.canModerateTeamChat(permissionUser, camping.permissionContext())
    val isTeamMember = team.members.any { it.userId == authenticatedUser.uid }
    val people = remember(team, authenticatedUser.uid) {
        team.members
            .asSequence()
            .filter { it.userId.isNotBlank() && it.userId != authenticatedUser.uid }
            .distinctBy { it.userId }
            .map { MentionCandidate(it.userId, it.displayName, it.church, it.photoUrl, isEveryone = false) }
            .sortedBy { it.displayName.lowercase() }
            .toList()
    }
    val mentionableUserIds = remember(people) { people.map { it.id } }
    val candidates = listOf(everyoneCandidate()) + people

    ChatConversation(
        viewModel = viewModel,
        scope = ChatScope.Team(campingId, teamId),
        title = team.name.ifBlank { stringResource(R.string.chat_team_title) },
        restrictedMessage = stringResource(R.string.chat_team_restricted_message, team.name),
        emptyMessage = stringResource(R.string.chat_team_empty_message),
        canAccess = isTeamMember || canModerate,
        canModerate = canModerate,
        sender = authenticatedUser,
        mentionCandidates = candidates,
        mentionableUserIds = mentionableUserIds,
        header = { TeamChatHeader(team) },
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatConversation(
    viewModel: ChatViewModel,
    scope: ChatScope,
    title: String,
    restrictedMessage: String,
    emptyMessage: String,
    canAccess: Boolean,
    canModerate: Boolean,
    sender: AuthenticatedUser,
    mentionCandidates: List<MentionCandidate>,
    mentionableUserIds: List<String>,
    header: (@Composable () -> Unit)?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val context = LocalContext.current
    val recorder = remember { ChatAudioRecorder(context) }

    val state by viewModel.uiState.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val isUploading by viewModel.isUploadingAttachment.collectAsState()
    val editingId by viewModel.editingMessageId.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var blockedSheetVisible by remember { mutableStateOf(false) }
    var reportingMessage by remember { mutableStateOf<ChatMessage?>(null) }

    LaunchedEffect(canAccess, scope.campingId, scope.teamId, sender.uid) {
        if (canAccess) viewModel.start(scope.campingId, scope.teamId, sender.uid)
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { blockedSheetVisible = true }) {
                        Icon(Icons.Rounded.Block, stringResource(R.string.chat_blocked_users))
                    }
                },
                windowInsets = WindowInsets(),
            )
        },
        bottomBar = {
            if (canAccess) {
                ChatComposer(
                    draft = draft,
                    isEditing = editingId != null,
                    isSending = isSending,
                    isUploading = isUploading,
                    mentionCandidates = mentionCandidates,
                    recorder = recorder,
                    onDraftChange = viewModel::updateDraft,
                    onSend = { viewModel.send(scope.campingId, scope.teamId, sender, mentionableUserIds) },
                    onCommitEdit = { viewModel.commitEdit(scope.campingId, scope.teamId) },
                    onCancelEdit = viewModel::cancelEditing,
                    onSendImage = { bytes, mime, ext ->
                        viewModel.sendImage(bytes, mime, ext, "", scope.campingId, scope.teamId, sender)
                    },
                    onSendVoice = { bytes, duration ->
                        viewModel.sendVoice(bytes, duration, scope.campingId, scope.teamId, sender)
                    },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            header?.invoke()
            if (!canAccess) {
                CzEmptyState(
                    title = stringResource(R.string.chat_restricted_title),
                    message = restrictedMessage,
                    modifier = Modifier.fillMaxSize().padding(CzSpacing.lg),
                )
            } else {
                ChatTimeline(
                    state = state,
                    emptyMessage = emptyMessage,
                    currentUserId = sender.uid,
                    canModerate = canModerate,
                    onRetry = { viewModel.retry(scope.campingId, scope.teamId, sender.uid) },
                    onTogglePin = viewModel::togglePinned,
                    onDelete = { viewModel.softDelete(it, sender.uid) },
                    onReport = { reportingMessage = it },
                    onBlock = {
                        viewModel.setBlocked(true, sender.uid, it.senderId, it.senderName)
                    },
                    onEdit = viewModel::beginEditing,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    LaunchedEffect(operationMessage) {
        operationMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearOperationMessage()
        }
    }
    LaunchedEffect(operationError) {
        operationError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearOperationError()
        }
    }

    reportingMessage?.let { message ->
        ReportMessageDialog(
            onDismiss = { reportingMessage = null },
            onSubmit = { reason, note ->
                viewModel.reportMessage(message, sender.uid, reason, note)
                reportingMessage = null
            },
        )
    }

    if (blockedSheetVisible) {
        val loaded = state as? ChatUiState.Loaded
        BlockedUsersSheet(
            blocked = loaded?.blockedUsers.orEmpty(),
            onUnblock = { viewModel.setBlocked(false, sender.uid, it.blockedUserId, it.displayName) },
            onDismiss = { blockedSheetVisible = false },
        )
    }
}

@Composable
private fun ChatTimeline(
    state: ChatUiState,
    emptyMessage: String,
    currentUserId: String,
    canModerate: Boolean,
    onRetry: () -> Unit,
    onTogglePin: (ChatMessage) -> Unit,
    onDelete: (ChatMessage) -> Unit,
    onReport: (ChatMessage) -> Unit,
    onBlock: (ChatMessage) -> Unit,
    onEdit: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is ChatUiState.Loading -> CzLoadingView(
            modifier = modifier.padding(CzSpacing.lg),
            message = stringResource(R.string.chat_loading),
        )
        is ChatUiState.Error -> CzErrorState(
            title = stringResource(R.string.chat_error_title),
            message = state.message,
            onRetry = onRetry,
            retryLabel = stringResource(R.string.common_retry),
            modifier = modifier.padding(CzSpacing.lg),
        )
        is ChatUiState.Loaded -> {
            val messages = state.visibleMessages
            if (messages.isEmpty()) {
                CzEmptyState(
                    title = stringResource(R.string.chat_empty_title),
                    message = emptyMessage,
                    modifier = modifier.padding(CzSpacing.lg),
                )
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(messages.size) {
                    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
                }
                LazyColumn(
                    modifier = modifier,
                    state = listState,
                    contentPadding = PaddingValues(vertical = CzSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                ) {
                    items(messages.size, key = { messages[it].id }) { index ->
                        val message = messages[index]
                        ChatMessageRow(
                            message = message,
                            isCurrentUser = message.senderId == currentUserId,
                            canModerate = canModerate,
                            currentUserId = currentUserId,
                            onTogglePin = { onTogglePin(message) },
                            onDelete = { onDelete(message) },
                            onReport = { onReport(message) },
                            onBlock = { onBlock(message) },
                            onEdit = { onEdit(message) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatMessageRow(
    message: ChatMessage,
    isCurrentUser: Boolean,
    canModerate: Boolean,
    currentUserId: String,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    onEdit: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val notifiesMe = !isCurrentUser && message.notifies(currentUserId)
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CzSpacing.md),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isCurrentUser) {
            CzAvatar(
                imageUrl = message.senderPhotoUrl,
                contentDescription = stringResource(R.string.chat_sender_avatar, message.senderName),
                initials = message.senderName.take(2),
                size = CzAvatarSize.Small,
            )
            Spacer(Modifier.size(CzSpacing.sm))
        }
        Column(horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start) {
            MessageMetadata(message, isCurrentUser, notifiesMe)
            Box {
                MessageBubble(
                    message = message,
                    isCurrentUser = isCurrentUser,
                    notifiesMe = notifiesMe,
                    currentUserId = currentUserId,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { if (!message.isDeleted) menuExpanded = true },
                        ),
                )
                MessageContextMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    message = message,
                    isCurrentUser = isCurrentUser,
                    canModerate = canModerate,
                    currentUserId = currentUserId,
                    onTogglePin = onTogglePin,
                    onDelete = onDelete,
                    onReport = onReport,
                    onBlock = onBlock,
                    onEdit = onEdit,
                )
            }
        }
        if (isCurrentUser) {
            Spacer(Modifier.size(CzSpacing.sm))
            CzAvatar(
                imageUrl = message.senderPhotoUrl,
                contentDescription = stringResource(R.string.chat_sender_avatar, message.senderName),
                initials = message.senderName.take(2),
                size = CzAvatarSize.Small,
            )
        }
    }
}

@Composable
private fun MessageMetadata(message: ChatMessage, isCurrentUser: Boolean, notifiesMe: Boolean) {
    val colors = MaterialTheme.czColors
    Row(
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isCurrentUser) {
            Text(
                message.senderName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
        }
        if (notifiesMe) {
            Text(
                stringResource(R.string.chat_mentioned_you),
                style = MaterialTheme.typography.labelSmall,
                color = colors.ember,
            )
        }
        if (message.pinned) {
            Icon(
                Icons.Rounded.PushPin,
                contentDescription = stringResource(R.string.chat_pinned),
                tint = colors.ember,
                modifier = Modifier.size(12.dp),
            )
        }
        if (message.isEdited && !message.isDeleted) {
            Text(
                stringResource(R.string.chat_edited),
                style = MaterialTheme.typography.labelSmall,
                fontStyle = FontStyle.Italic,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isCurrentUser: Boolean,
    notifiesMe: Boolean,
    currentUserId: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val background = when {
        message.isDeleted -> colors.textSecondary.copy(alpha = 0.12f)
        notifiesMe -> colors.ember.copy(alpha = 0.10f)
        isCurrentUser -> colors.ember
        else -> colors.surface
    }
    val attachment = message.attachment
    if (!message.isDeleted && attachment != null && attachment.kind == ChatAttachmentKind.Image) {
        Column(modifier = modifier, horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start) {
            ChatImageBubble(attachment)
            if (message.hasText) {
                Spacer(Modifier.size(4.dp))
                Box(
                    Modifier.background(background, RoundedCornerShape(CzRadius.lg))
                        .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
                ) {
                    Text(
                        bubbleText(message, isCurrentUser, currentUserId, colors.textPrimary, Color.White, colors.ember),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    } else if (!message.isDeleted && attachment != null && attachment.kind == ChatAttachmentKind.Audio) {
        Box(
            modifier.background(background, RoundedCornerShape(CzRadius.lg))
                .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
        ) {
            ChatVoiceNoteView(attachment = attachment, isCurrentUser = isCurrentUser)
        }
    } else {
        Box(
            modifier.background(background, RoundedCornerShape(CzRadius.lg))
                .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
        ) {
            if (message.isDeleted) {
                Text(
                    stringResource(R.string.chat_message_removed),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = colors.textSecondary,
                )
            } else {
                Text(
                    bubbleText(message, isCurrentUser, currentUserId, colors.textPrimary, Color.White, colors.ember),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun bubbleText(
    message: ChatMessage,
    isCurrentUser: Boolean,
    currentUserId: String,
    primary: Color,
    onEmber: Color,
    ember: Color,
): AnnotatedString {
    val base = if (isCurrentUser) onEmber else primary
    return buildAnnotatedString {
        withStyle(SpanStyle(color = base)) { append(message.text) }
        message.mentions.forEach { mention ->
            if (mention.offset < 0 || mention.endOffset > message.text.length) return@forEach
            val color = if (isCurrentUser) onEmber else ember
            addStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold), mention.offset, mention.endOffset)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    message: ChatMessage,
    isCurrentUser: Boolean,
    canModerate: Boolean,
    currentUserId: String,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    onEdit: () -> Unit,
) {
    androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (isCurrentUser && message.isEditable(currentUserId)) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_edit)) },
                onClick = { onDismiss(); onEdit() },
            )
        }
        androidx.compose.material3.DropdownMenuItem(
            text = { Text(stringResource(R.string.chat_report)) },
            onClick = { onDismiss(); onReport() },
        )
        if (!isCurrentUser) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_block_sender)) },
                onClick = { onDismiss(); onBlock() },
            )
        }
        if (canModerate) {
            androidx.compose.material3.DropdownMenuItem(
                text = {
                    Text(stringResource(if (message.pinned) R.string.chat_unpin else R.string.chat_pin))
                },
                onClick = { onDismiss(); onTogglePin() },
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_remove)) },
                onClick = { onDismiss(); onDelete() },
            )
        }
    }
}

@Composable
private fun ChatImageBubble(attachment: fr.ziyon.campzone.data.model.ChatAttachment) {
    var fullScreen by remember { mutableStateOf(false) }
    val ratio = if ((attachment.width ?: 0) > 0 && (attachment.height ?: 0) > 0) {
        attachment.width!!.toFloat() / attachment.height!!.toFloat()
    } else {
        3f / 4f
    }
    coil.compose.AsyncImage(
        model = attachment.url,
        contentDescription = stringResource(R.string.chat_photo),
        modifier = Modifier
            .widthIn(max = 240.dp)
            .heightIn(max = 280.dp)
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(CzRadius.lg))
            .clickable { fullScreen = true },
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
    )
    if (fullScreen) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { fullScreen = false }) {
            coil.compose.AsyncImage(
                model = attachment.url,
                contentDescription = stringResource(R.string.chat_photo),
                modifier = Modifier.fillMaxSize().clickable { fullScreen = false },
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockedUsersSheet(
    blocked: List<fr.ziyon.campzone.data.model.BlockedUser>,
    onUnblock: (fr.ziyon.campzone.data.model.BlockedUser) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg)) {
            Text(
                stringResource(R.string.chat_blocked_users),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Spacer(Modifier.size(CzSpacing.md))
            if (blocked.isEmpty()) {
                Text(stringResource(R.string.chat_no_blocked_users), color = colors.textSecondary)
            } else {
                blocked.forEach { user ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = CzSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                    ) {
                        CzAvatar(
                            imageUrl = null,
                            contentDescription = user.displayName,
                            initials = user.displayName.take(2),
                            size = CzAvatarSize.Small,
                        )
                        Text(user.displayName, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onUnblock(user) }) {
                            Text(stringResource(R.string.chat_unblock), color = colors.ember)
                        }
                    }
                }
            }
            Spacer(Modifier.size(CzSpacing.lg))
        }
    }
}

@Composable
private fun ReportMessageDialog(
    onDismiss: () -> Unit,
    onSubmit: (ContentReportReason, String) -> Unit,
) {
    var reason by remember { mutableStateOf(ContentReportReason.Inappropriate) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_report_title)) },
        text = {
            Column {
                ReportReasons.forEach { (value, labelRes) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { reason = value }
                            .padding(vertical = CzSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = reason == value,
                            onClick = { reason = value },
                        )
                        Text(stringResource(labelRes))
                    }
                }
                Spacer(Modifier.size(CzSpacing.sm))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.chat_report_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(reason, note) }) {
                Text(stringResource(R.string.chat_report_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun TeamChatHeader(team: Team) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CzSpacing.lg, vertical = CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Text(
            stringResource(R.string.chat_private_team_chat),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
    }
    HorizontalDivider()
}

@Composable
private fun ChatLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CzLoadingView(message = stringResource(R.string.chat_loading))
    }
}

@Composable
private fun everyoneCandidate(): MentionCandidate = MentionCandidate(
    id = ChatMention.EVERYONE_TOKEN,
    displayName = stringResource(R.string.chat_mention_everyone),
    subtitle = stringResource(R.string.chat_mention_everyone_subtitle),
    isEveryone = true,
)

private fun campingMentionPeople(
    approved: List<CampingAttendee>,
    currentUserId: String,
): List<MentionCandidate> {
    val seen = mutableSetOf<String>()
    return approved
        .mapNotNull { attendee ->
            val notificationId = attendee.guardianId?.trim()?.takeUnless { it.isBlank() }
                ?: attendee.userId.trim()
            if (notificationId.isBlank() || notificationId == currentUserId || !seen.add(notificationId)) {
                null
            } else {
                MentionCandidate(notificationId, attendee.displayName, attendee.church, attendee.photoUrl, false)
            }
        }
        .sortedBy { it.displayName.lowercase() }
}

private val ReportReasons = listOf(
    ContentReportReason.Inappropriate to R.string.chat_report_reason_inappropriate,
    ContentReportReason.Spam to R.string.chat_report_reason_spam,
    ContentReportReason.Misinformation to R.string.chat_report_reason_misinformation,
    ContentReportReason.Harassment to R.string.chat_report_reason_harassment,
    ContentReportReason.Other to R.string.chat_report_reason_other,
)

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun ChatMessageRowPreview() {
    fr.ziyon.campzone.core.designsystem.CampzoneTheme {
        Column {
            ChatMessageRow(
                message = ChatMessage(
                    id = "1", campingId = "c", senderId = "other", senderName = "Ana Silva",
                    text = "Welcome to camp! Dinner is at 18:30.", createdAt = java.util.Date(),
                ),
                isCurrentUser = false, canModerate = true, currentUserId = "me",
                onTogglePin = {}, onDelete = {}, onReport = {}, onBlock = {}, onEdit = {},
            )
            ChatMessageRow(
                message = ChatMessage(
                    id = "2", campingId = "c", senderId = "lead", senderName = "Camp Office",
                    text = "Hey @me can you confirm?", createdAt = java.util.Date(),
                    mentions = listOf(ChatMention("me", "me", offset = 4, length = 3)),
                    pinned = true,
                ),
                isCurrentUser = false, canModerate = false, currentUserId = "me",
                onTogglePin = {}, onDelete = {}, onReport = {}, onBlock = {}, onEdit = {},
            )
            ChatMessageRow(
                message = ChatMessage(
                    id = "3", campingId = "c", senderId = "me", senderName = "Me",
                    text = "On my way!", createdAt = java.util.Date(), editedAt = java.util.Date(),
                ),
                isCurrentUser = true, canModerate = false, currentUserId = "me",
                onTogglePin = {}, onDelete = {}, onReport = {}, onBlock = {}, onEdit = {},
            )
        }
    }
}
