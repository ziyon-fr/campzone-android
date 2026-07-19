package fr.ziyon.campzone.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.BackHand
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
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
import fr.ziyon.campzone.data.model.CampingStaffRole
import fr.ziyon.campzone.data.model.ChatAttachmentKind
import fr.ziyon.campzone.data.model.ChatMention
import fr.ziyon.campzone.data.model.ChatReactionSummary
import fr.ziyon.campzone.data.model.ChatMessage
import fr.ziyon.campzone.data.model.ChatReplyReference
import fr.ziyon.campzone.data.model.ContentReportReason
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.Team
import fr.ziyon.campzone.ui.camping.registrations.permissionContext
import fr.ziyon.campzone.ui.teams.symbolIcon
import fr.ziyon.campzone.ui.teams.toComposeColor
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale

private data class ChatActionContext(
    val message: ChatMessage,
    val bubbleCenterY: Float?,
)

private sealed interface ChatScope {
    val campingId: String
    val teamId: String?
    val staffRoleId: String?

    data class Camping(override val campingId: String) : ChatScope {
        override val teamId: String? = null
        override val staffRoleId: String? = null
    }

    data class Team(override val campingId: String, val team: String) : ChatScope {
        override val teamId: String = team
        override val staffRoleId: String? = null
    }

    data class StaffRole(override val campingId: String, val role: String) : ChatScope {
        override val teamId: String? = null
        override val staffRoleId: String = role
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

@Composable
fun StaffRoleChatRoute(
    campingId: String,
    staffRoleId: String,
    camping: Camping?,
    role: CampingStaffRole?,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    if (camping == null || role == null) {
        ChatLoading(modifier)
        return
    }
    val evaluator = remember { AppPermissionEvaluator() }
    val permissionUser = remember(authenticatedUser) {
        PermissionUser(authenticatedUser.role, authenticatedUser.uid, authenticatedUser.church)
    }
    val canModerate = evaluator.canManageStaffRoles(permissionUser, camping.permissionContext())
    val isMember = role.containsUser(authenticatedUser.uid)
    val people = remember(role, authenticatedUser.uid) {
        role.members
            .asSequence()
            .filter { it.userId.isNotBlank() && it.userId != authenticatedUser.uid }
            .distinctBy { it.userId }
            .map { MentionCandidate(it.userId, it.displayName, it.church, it.photoUrl, isEveryone = false) }
            .sortedBy { it.displayName.lowercase() }
            .toList()
    }
    val mentionableUserIds = remember(people) { people.map { it.id } }

    ChatConversation(
        viewModel = viewModel,
        scope = ChatScope.StaffRole(campingId, staffRoleId),
        title = role.name.ifBlank { stringResource(R.string.staff_roles_title) },
        restrictedMessage = stringResource(R.string.staff_role_chat_restricted, role.name),
        emptyMessage = stringResource(R.string.staff_role_chat_empty),
        canAccess = role.chatEnabled && (isMember || canModerate),
        canModerate = canModerate,
        sender = authenticatedUser,
        mentionCandidates = listOf(everyoneCandidate()) + people,
        mentionableUserIds = mentionableUserIds,
        header = { StaffRoleChatHeader(role) },
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
    val clipboard = LocalClipboardManager.current
    val recorder = remember { ChatAudioRecorder(context) }
    val coroutineScope = rememberCoroutineScope()

    val state by viewModel.uiState.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val isUploading by viewModel.isUploadingAttachment.collectAsState()
    val editingId by viewModel.editingMessageId.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var blockedSheetVisible by remember { mutableStateOf(false) }
    var reportingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var actionContext by remember { mutableStateOf<ChatActionContext?>(null) }
    var overlayContext by remember { mutableStateOf<ChatActionContext?>(null) }
    val copiedMessage = stringResource(R.string.chat_copied)

    LaunchedEffect(actionContext) {
        if (actionContext != null) overlayContext = actionContext
    }
    LaunchedEffect(actionContext, overlayContext) {
        if (actionContext == null && overlayContext != null) {
            delay(220)
            if (actionContext == null) overlayContext = null
        }
    }
    val blurRadius by animateFloatAsState(
        targetValue = if (actionContext != null) 48f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "chatBlur",
    )

    LaunchedEffect(canAccess, scope.campingId, scope.teamId, scope.staffRoleId, sender.uid) {
        if (canAccess) viewModel.start(scope.campingId, scope.teamId, sender.uid, scope.staffRoleId)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = if (blurRadius > 0.5f) BlurEffect(blurRadius, blurRadius, TileMode.Clamp) else null
                }
        ) {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                actions = {
                    IconButton(onClick = { blockedSheetVisible = true }) {
                        Icon(
                            Icons.Rounded.MoreHoriz,
                            contentDescription = stringResource(R.string.chat_more_actions),
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                windowInsets = WindowInsets()
            )
            header?.invoke()
            if (!canAccess) {
                CzEmptyState(
                    title = stringResource(R.string.chat_restricted_title),
                    message = restrictedMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(CzSpacing.lg),
                )
            } else {
                ChatTimeline(
                    state = state,
                    emptyMessage = emptyMessage,
                    currentUserId = sender.uid,
                    onRetry = {
                        viewModel.retry(scope.campingId, scope.teamId, sender.uid, scope.staffRoleId)
                    },
                    onReact = { message, emoji -> viewModel.toggleReaction(message, emoji, sender.uid) },
                    onReply = viewModel::beginReply,
                    onShowActions = { message, bubbleCenterY ->
                        actionContext = ChatActionContext(message, bubbleCenterY)
                    },
                    bottomContentPadding = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                ChatComposer(
                    draft = draft,
                    isEditing = editingId != null,
                    replyingTo = replyingTo,
                    isSending = isSending,
                    isUploading = isUploading,
                    mentionCandidates = mentionCandidates,
                    recorder = recorder,
                    onDraftChange = viewModel::updateDraft,
                    onSend = {
                        viewModel.send(
                            scope.campingId,
                            scope.teamId,
                            sender,
                            mentionableUserIds,
                            scope.staffRoleId,
                        )
                    },
                    onCommitEdit = {
                        viewModel.commitEdit(scope.campingId, scope.teamId, scope.staffRoleId)
                    },
                    onCancelEdit = viewModel::cancelEditing,
                    onCancelReply = viewModel::cancelReply,
                    onSendImage = { bytes, mime, ext ->
                        viewModel.sendImage(
                            bytes,
                            mime,
                            ext,
                            "",
                            scope.campingId,
                            scope.teamId,
                            sender,
                            scope.staffRoleId,
                        )
                    },
                    onSendVoice = { bytes, duration ->
                        viewModel.sendVoice(
                            bytes,
                            duration,
                            scope.campingId,
                            scope.teamId,
                            sender,
                            scope.staffRoleId,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (canAccess) 88.dp else CzSpacing.md),
        )

        AnimatedVisibility(
            visible = actionContext != null,
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(200)),
        ) {
            overlayContext?.let { context ->
            val message = context.message
            ChatMessageActionOverlay(
                message = message,
                anchorCenterY = context.bubbleCenterY,
                isCurrentUser = message.senderId == sender.uid,
                canModerate = canModerate,
                currentUserId = sender.uid,
                onDismiss = { actionContext = null },
                onReact = { emoji ->
                    viewModel.toggleReaction(message, emoji, sender.uid)
                    actionContext = null
                },
                onReply = {
                    viewModel.beginReply(message)
                    actionContext = null
                },
                onCopy = {
                    clipboard.setText(AnnotatedString(message.text))
                    coroutineScope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                    actionContext = null
                },
                onEdit = {
                    viewModel.beginEditing(message)
                    actionContext = null
                },
                onReport = {
                    reportingMessage = message
                    actionContext = null
                },
                onBlock = {
                    viewModel.setBlocked(true, sender.uid, message.senderId, message.senderName)
                    actionContext = null
                },
                onPinToggle = {
                    viewModel.togglePinned(message)
                    actionContext = null
                },
                onDelete = {
                    viewModel.softDelete(message, sender.uid)
                    actionContext = null
                },
            )
            }
        } // AnimatedVisibility
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
    onRetry: () -> Unit,
    onReact: (ChatMessage, String) -> Unit,
    onReply: (ChatMessage) -> Unit,
    onShowActions: (ChatMessage, Float?) -> Unit,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
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
                val coroutineScope = rememberCoroutineScope()
                val density = LocalDensity.current
                // Mirror iOS: treat the timeline as "at bottom" while the newest
                // message is within this distance of the viewport's bottom edge so
                // the jump-to-latest button doesn't flicker on tiny scrolls.
                val bottomThresholdPx = with(density) { 120.dp.toPx() }
                var highlightedMessageId by remember { mutableStateOf<String?>(null) }
                var previousCount by remember { mutableStateOf(0) }
                var unseenCount by remember { mutableStateOf(0) }
                val isAtBottom by remember(listState, bottomThresholdPx) {
                    derivedStateOf {
                        val info = listState.layoutInfo
                        val lastVisible = info.visibleItemsInfo.lastOrNull()
                            ?: return@derivedStateOf true
                        if (lastVisible.index < info.totalItemsCount - 1) {
                            return@derivedStateOf false
                        }
                        val bottomEdge = lastVisible.offset + lastVisible.size
                        val viewportBottom = info.viewportEndOffset - info.afterContentPadding
                        (bottomEdge - viewportBottom) <= bottomThresholdPx
                    }
                }
                LaunchedEffect(messages.size) {
                    val old = previousCount
                    previousCount = messages.size
                    if (messages.isEmpty()) return@LaunchedEffect
                    val sentByMe = messages.lastOrNull()?.senderId == currentUserId
                    when {
                        old == 0 -> listState.scrollToItem(messages.lastIndex)
                        messages.size > old && (isAtBottom || sentByMe) -> {
                            listState.animateScrollToItem(messages.lastIndex)
                            unseenCount = 0
                        }
                        messages.size > old -> unseenCount += messages.size - old
                    }
                }
                LaunchedEffect(isAtBottom) {
                    if (isAtBottom) unseenCount = 0
                }
                Box(modifier = modifier) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(
                            top = CzSpacing.md,
                            bottom = CzSpacing.md + bottomContentPadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                    ) {
                        items(messages.size, key = { messages[it].id }) { index ->
                            val message = messages[index]
                            Column(
                                modifier = Modifier.animateItem(),
                                verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                            ) {
                                val date = message.createdAt
                                if (date != null && shouldShowDateSeparator(date, messages.getOrNull(index - 1)?.createdAt)) {
                                    ChatDateSeparator(date)
                                }
                                ChatMessageRow(
                                    message = message,
                                    isCurrentUser = message.senderId == currentUserId,
                                    currentUserId = currentUserId,
                                    isHighlighted = highlightedMessageId == message.id,
                                    onReply = { onReply(message) },
                                    onOpenReply = { messageId ->
                                        val targetIndex = messages.indexOfFirst { it.id == messageId }
                                        if (targetIndex >= 0) {
                                            coroutineScope.launch {
                                                listState.animateScrollToItem(targetIndex)
                                                highlightedMessageId = messageId
                                                delay(1_500)
                                                if (highlightedMessageId == messageId) highlightedMessageId = null
                                            }
                                        }
                                    },
                                    onReact = { emoji -> onReact(message, emoji) },
                                    onShowActions = { bubbleCenterY -> onShowActions(message, bubbleCenterY) },
                                )
                            }
                        }
                    }
                    if (!isAtBottom) {
                        ChatScrollToBottomButton(
                            unseenCount = unseenCount,
                            onClick = {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(messages.lastIndex)
                                    unseenCount = 0
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = CzSpacing.md, bottom = bottomContentPadding + CzSpacing.sm),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatDateSeparator(date: Date) {
    val colors = MaterialTheme.czColors
    val today = stringResource(R.string.chat_today)
    val yesterday = stringResource(R.string.chat_yesterday)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CzSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = chatDateLabel(date, today, yesterday),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.textSecondary,
            modifier = Modifier
                .background(colors.surface, RoundedCornerShape(CzRadius.full))
                .padding(horizontal = CzSpacing.md, vertical = CzSpacing.xs),
        )
    }
}

@Composable
private fun ChatScrollToBottomButton(
    unseenCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    Box(modifier = modifier) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = colors.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(0.5.dp, colors.divider),
            // Mirror iOS' subtle shadow (black @ 12%) instead of the platform
            // elevation overlay, which casts a soft offset disc beneath the
            // circle that reads as a stray "round shape" under the button.
            modifier = Modifier
                .size(40.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.12f),
                ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.chat_scroll_to_latest),
                    tint = colors.textPrimary,
                )
            }
        }
        if (unseenCount > 0) {
            Text(
                text = if (unseenCount > 99) "99+" else unseenCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .background(colors.accent, RoundedCornerShape(CzRadius.full))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatMessageRow(
    message: ChatMessage,
    isCurrentUser: Boolean,
    currentUserId: String,
    isHighlighted: Boolean = false,
    onReply: () -> Unit = {},
    onOpenReply: (String) -> Unit = {},
    onReact: (String) -> Unit,
    onShowActions: (Float?) -> Unit,
) {
    val notifiesMe = !isCurrentUser && message.notifies(currentUserId)
    val density = LocalDensity.current
    val swipeTriggerPx = with(density) { 56.dp.toPx() }
    val swipeMaxPx = with(density) { 72.dp.toPx() }
    var dragOffset by remember(message.id) { mutableStateOf(0f) }
    var bubbleCenterY by remember(message.id) { mutableStateOf<Float?>(null) }
    val animatedDragOffset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "chat-reply-drag",
    )
    val replyIndicatorAlpha = (abs(animatedDragOffset) / swipeTriggerPx).coerceIn(0f, 1f)
    val clusterSwipeModifier = Modifier
        .offset { IntOffset(animatedDragOffset.roundToInt(), 0) }
        .pointerInput(message.id, isCurrentUser, message.isDeleted) {
            if (!message.isDeleted) {
                var totalX = 0f
                var totalY = 0f
                var trackingSwipe = false
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        totalY = 0f
                        trackingSwipe = false
                    },
                    onDragCancel = { dragOffset = 0f },
                    onDragEnd = {
                        val triggered = abs(dragOffset) >= swipeTriggerPx
                        dragOffset = 0f
                        if (triggered) onReply()
                    },
                    onDrag = { change, dragAmount ->
                        totalX += dragAmount.x
                        totalY += dragAmount.y
                        if (!trackingSwipe &&
                            abs(totalX) > abs(totalY) * 1.5f &&
                            abs(totalX) > 8f
                        ) {
                            trackingSwipe = true
                        }
                        if (trackingSwipe) {
                            change.consume()
                            val inward = if (isCurrentUser) min(0f, totalX) else max(0f, totalX)
                            dragOffset = inward.coerceIn(-swipeMaxPx, swipeMaxPx)
                        }
                    },
                )
            }
        }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CzSpacing.sm)
            .background(
                if (isHighlighted) MaterialTheme.czColors.accent.copy(alpha = 0.12f) else Color.Transparent,
                RoundedCornerShape(CzRadius.md),
            )
            .padding(horizontal = CzSpacing.xs, vertical = 2.dp),
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
            Box(
                contentAlignment = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                if (replyIndicatorAlpha > 0f) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Reply,
                        contentDescription = null,
                        tint = MaterialTheme.czColors.accent,
                        modifier = Modifier
                            .padding(horizontal = CzSpacing.sm)
                            .alpha(replyIndicatorAlpha)
                            .size(22.dp),
                    )
                }
                Column(
                    horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start,
                    modifier = clusterSwipeModifier,
                ) {
                    MessageBubble(
                        message = message,
                        isCurrentUser = isCurrentUser,
                        notifiesMe = notifiesMe,
                        currentUserId = currentUserId,
                        onOpenReply = onOpenReply,
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .onGloballyPositioned { coordinates ->
                                val position = coordinates.positionInRoot()
                                bubbleCenterY = position.y + coordinates.size.height / 2f
                            }
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { if (!message.isDeleted) onShowActions(bubbleCenterY) },
                            ),
                    )
                    ReactionSummaryRow(
                        summaries = message.reactionSummaries(currentUserId),
                        isCurrentUser = isCurrentUser,
                        onReact = onReact,
                    )
                }
            }
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
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
            )
        }
        if (notifiesMe) {
            Text(
                stringResource(R.string.chat_mentioned_you),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                modifier = Modifier
                    .background(colors.surface, RoundedCornerShape(CzRadius.full))
                    .padding(horizontal = CzSpacing.sm, vertical = 2.dp),
            )
        }
        if (message.pinned) {
            Icon(
                Icons.Rounded.PushPin,
                contentDescription = stringResource(R.string.chat_pinned),
                tint = colors.accent,
                modifier = Modifier.size(12.dp),
            )
        }
        message.createdAt?.let { date ->
            Text(
                formatMessageTime(date),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
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
    onOpenReply: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val background = when {
        message.isDeleted -> colors.textSecondary.copy(alpha = 0.12f)
        notifiesMe -> colors.accent.copy(alpha = 0.10f)
        isCurrentUser -> colors.accent
        else -> colors.surface
    }
    Column(modifier = modifier, horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start) {
        if (!message.isDeleted) {
            message.replyTo?.let { reply ->
                MessageReplyReferenceView(reply, isCurrentUser, onOpenReply)
                Spacer(Modifier.size(4.dp))
            }
        }
        val attachment = message.attachment
        if (!message.isDeleted && attachment != null && attachment.kind == ChatAttachmentKind.Image) {
            ChatImageBubble(attachment)
            if (message.hasText) {
                Spacer(Modifier.size(4.dp))
                Box(
                    Modifier.background(background, RoundedCornerShape(CzRadius.lg))
                        .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
                ) {
                    Text(
                        bubbleText(message, isCurrentUser, currentUserId, colors.textPrimary, Color.White, colors.accent),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else if (!message.isDeleted && attachment != null && attachment.kind == ChatAttachmentKind.Audio) {
            Box(
                Modifier.background(background, RoundedCornerShape(CzRadius.lg))
                    .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
            ) {
                ChatVoiceNoteView(attachment = attachment, isCurrentUser = isCurrentUser)
            }
        } else {
            Box(
                Modifier.background(background, RoundedCornerShape(CzRadius.lg))
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
                        bubbleText(message, isCurrentUser, currentUserId, colors.textPrimary, Color.White, colors.accent),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageReplyReferenceView(
    reply: fr.ziyon.campzone.data.model.ChatReplyReference,
    isCurrentUser: Boolean,
    onOpenReply: (String) -> Unit,
) {
    val colors = MaterialTheme.czColors
    val preview = reply.textPreview
        ?: when (reply.mediaType) {
            ChatAttachmentKind.Image -> stringResource(R.string.chat_photo)
            ChatAttachmentKind.Audio -> stringResource(R.string.chat_voice_message)
            null -> stringResource(R.string.chat_message_removed)
        }
    val blockBackground = if (isCurrentUser) colors.surface else colors.accent.copy(alpha = 0.08f)
    val senderColor = if (isCurrentUser) colors.textPrimary.copy(alpha = 0.5f) else colors.accent
    val previewColor = if (isCurrentUser) colors.textSecondary.copy(alpha = 0.5f) else colors.textSecondary
    Column(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .background(blockBackground, RoundedCornerShape(CzRadius.sm))
            .clickable { onOpenReply(reply.messageId) }
            .padding(horizontal = CzSpacing.sm, vertical = CzSpacing.xs),
    ) {
        Text(
            reply.senderName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = senderColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            preview,
            style = MaterialTheme.typography.bodySmall,
            color = previewColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReactionSummaryRow(
    summaries: List<ChatReactionSummary>,
    isCurrentUser: Boolean,
    onReact: (String) -> Unit,
) {
    if (summaries.isEmpty()) return
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        summaries.forEach { summary ->
            ReactionChip(summary = summary, isCurrentUser = isCurrentUser, onClick = { onReact(summary.emoji) })
        }
    }
}

@Composable
private fun ReactionChip(summary: ChatReactionSummary, isCurrentUser: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.czColors
    val background = if (summary.reactedByCurrentUser) {
        colors.accent.copy(alpha = if (isCurrentUser) 0.18f else 0.14f)
    } else {
        colors.surface
    }
    Row(
        modifier = Modifier
            .heightIn(min = 24.dp)
            .background(background, RoundedCornerShape(CzRadius.full))
            .border(
                width = 1.dp,
                color = if (summary.reactedByCurrentUser) colors.accent.copy(alpha = 0.50f) else Color.Transparent,
                shape = RoundedCornerShape(CzRadius.full),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = CzSpacing.sm, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = summary.emoji,
            style = MaterialTheme.typography.labelMedium,
        )
        if (summary.count > 1) {
            Text(
                text = "${summary.count}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (summary.reactedByCurrentUser) colors.accent else colors.textSecondary,
            )
        }
    }
}

private val ChatQuickReactions = listOf("\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDE4F", "\uD83D\uDC4F")
private val ChatExtendedReactions = listOf(
    "\uD83D\uDD25", "\uD83C\uDF89", "\uD83D\uDE0D", "\uD83E\uDD14",
    "\uD83D\uDE0E", "\uD83E\uDD73", "\uD83D\uDE05", "\uD83D\uDE4C",
    "\uD83D\uDCAA", "\u2705", "\uD83D\uDC40", "\uD83D\uDCAF",
    "\uD83D\uDE34", "\uD83E\uDD1D", "\uD83E\uDEF6", "\uD83C\uDF1F",
)

@Composable
private fun ChatReactionRail(
    selectedEmoji: String?,
    onReact: (String) -> Unit,
) {
    val colors = MaterialTheme.czColors
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .shadow(8.dp, RoundedCornerShape(CzRadius.xxl))
            .background(colors.card, RoundedCornerShape(CzRadius.xxl))
            .padding(horizontal = CzSpacing.sm, vertical = CzSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            ChatQuickReactions.forEach { emoji ->
                ReactionRailButton(emoji = emoji, selected = selectedEmoji == emoji, onClick = { onReact(emoji) })
            }
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.size(40.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(if (expanded) "-" else "+", style = MaterialTheme.typography.headlineSmall, color = colors.textSecondary)
            }
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ChatExtendedReactions.chunked(8).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        row.forEach { emoji ->
                            ReactionRailButton(
                                emoji = emoji,
                                selected = selectedEmoji == emoji,
                                onClick = { onReact(emoji) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactionRailButton(emoji: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.czColors
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .background(
                if (selected) colors.accent.copy(alpha = 0.18f) else Color.Transparent,
                CircleShape,
            ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.titleLarge,
        )
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
    val linkColor = if (isCurrentUser) onEmber else ember
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
    )
    return buildAnnotatedString {
        withStyle(SpanStyle(color = base)) {
            var cursor = 0
            ChatUrlRegex.findAll(message.text).forEach { match ->
                append(message.text.substring(cursor, match.range.first))
                val raw = match.value
                val url = if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw
                withLink(LinkAnnotation.Url(url, linkStyles)) { append(raw) }
                cursor = match.range.last + 1
            }
            append(message.text.substring(cursor))
        }
        message.mentions.forEach { mention ->
            if (mention.offset < 0 || mention.endOffset > message.text.length) return@forEach
            val color = if (isCurrentUser) onEmber else ember
            addStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold), mention.offset, mention.endOffset)
        }
    }
}

private val ChatUrlRegex = Regex("""(?i)\b(?:https?://|www\.)[^\s<>()]+""")

@Composable
private fun ChatMessageActionOverlay(
    message: ChatMessage,
    anchorCenterY: Float?,
    isCurrentUser: Boolean,
    canModerate: Boolean,
    currentUserId: String,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    onPinToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val density = LocalDensity.current

    // Staggered entrance animation state
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val railAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(200),
        label = "railAlpha",
    )
    val bubbleAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(220, delayMillis = 60),
        label = "bubbleAlpha",
    )
    val bubbleScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.88f,
        animationSpec = tween(300, delayMillis = 60, easing = FastOutSlowInEasing),
        label = "bubbleScale",
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(240, delayMillis = 100),
        label = "cardAlpha",
    )
    val cardSlide by animateFloatAsState(
        targetValue = if (appeared) 0f else 24f,
        animationSpec = tween(300, delayMillis = 100, easing = FastOutSlowInEasing),
        label = "cardSlide",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.35f))
            .clickable(onClick = onDismiss),
    ) {
        val topOffset = with(density) {
            val containerHeight = maxHeight.toPx()
            val selectedCenter = anchorCenterY ?: (containerHeight / 2f)
            val desiredTop = selectedCenter - 118.dp.toPx()
            val minTop = 8.dp.toPx()
            val maxTop = max(minTop, containerHeight - 360.dp.toPx())
            desiredTop.coerceIn(minTop, maxTop).toDp()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .offset(y = topOffset)
                .padding(horizontal = CzSpacing.lg),
            horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.graphicsLayer { alpha = railAlpha }) {
                ChatReactionRail(
                    selectedEmoji = message.reaction(currentUserId),
                    onReact = onReact,
                )
            }

            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = bubbleAlpha
                    scaleX = bubbleScale
                    scaleY = bubbleScale
                },
            ) {
                Surface(
                    shape = RoundedCornerShape(CzRadius.lg),
                    color = if (isCurrentUser) colors.accent else colors.card,
                    tonalElevation = 0.dp,
                    shadowElevation = 16.dp,
                    modifier = Modifier.widthIn(max = 280.dp),
                ) {
                    MessageBubble(
                        message = message,
                        isCurrentUser = isCurrentUser,
                        notifiesMe = !isCurrentUser && message.notifies(currentUserId),
                        currentUserId = currentUserId,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = cardAlpha }
                    .offset(y = cardSlide.dp),
            ) {
                ChatActionCard(
                    message = message,
                    isCurrentUser = isCurrentUser,
                    canModerate = canModerate,
                    currentUserId = currentUserId,
                    onReply = onReply,
                    onCopy = onCopy,
                    onEdit = onEdit,
                    onReport = onReport,
                    onBlock = onBlock,
                    onPinToggle = onPinToggle,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun ChatActionCard(
    message: ChatMessage,
    isCurrentUser: Boolean,
    canModerate: Boolean,
    currentUserId: String,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    onPinToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    Surface(
        modifier = Modifier.width(260.dp),
        color = colors.card,
        shape = RoundedCornerShape(CzRadius.xxl),
        tonalElevation = 0.dp,
        shadowElevation = 20.dp,
    ) {
        Column {
            ChatActionRow(
                label = stringResource(R.string.chat_reply),
                icon = Icons.AutoMirrored.Rounded.Reply,
                onClick = onReply,
            )
            if (message.hasText) {
                ChatActionDivider()
                ChatActionRow(
                    label = stringResource(R.string.chat_copy),
                    icon = Icons.Rounded.ContentCopy,
                    onClick = onCopy,
                )
            }
            if (isCurrentUser && message.isEditable(currentUserId)) {
                ChatActionDivider()
                ChatActionRow(
                    label = stringResource(R.string.chat_edit),
                    icon = Icons.Rounded.Edit,
                    onClick = onEdit,
                )
            }
            ChatActionDivider()
            ChatActionRow(
                label = stringResource(R.string.chat_report),
                icon = Icons.Rounded.Flag,
                onClick = onReport,
            )
            if (!isCurrentUser) {
                ChatActionDivider()
                ChatActionRow(
                    label = stringResource(R.string.chat_block_user, message.senderName),
                    icon = Icons.Rounded.BackHand,
                    destructive = true,
                    onClick = onBlock,
                )
            }
            if (canModerate) {
                ChatActionDivider()
                ChatActionRow(
                    label = stringResource(if (message.pinned) R.string.chat_unpin else R.string.chat_pin),
                    icon = Icons.Rounded.PushPin,
                    onClick = onPinToggle,
                )
                ChatActionDivider()
                ChatActionRow(
                    label = stringResource(R.string.chat_remove),
                    icon = Icons.Rounded.Delete,
                    destructive = true,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun ChatActionDivider() {
    HorizontalDivider(color = MaterialTheme.czColors.divider)
}

@Composable
private fun ChatActionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val tint = if (destructive) colors.error else colors.textPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CzSpacing.lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
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
                            Text(stringResource(R.string.chat_unblock), color = colors.accent)
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
    val teamColor = team.colorHex.toComposeColor() ?: colors.ember
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(teamColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = symbolIcon(team.symbolName),
                contentDescription = null,
                tint = teamColor,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = stringResource(R.string.chat_private_team_chat).uppercase(LocalLocale.current.platformLocale),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(12.dp),
        )
    }
    HorizontalDivider()
}

@Composable
private fun StaffRoleChatHeader(role: CampingStaffRole) {
    val colors = MaterialTheme.czColors
    val roleColor = role.colorHex.toComposeColor() ?: colors.ember
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(roleColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = symbolIcon(role.symbolName),
                contentDescription = null,
                tint = roleColor,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = stringResource(R.string.staff_role_private_chat).uppercase(LocalLocale.current.platformLocale),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(12.dp),
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

private fun shouldShowDateSeparator(date: Date, previous: Date?): Boolean {
    if (previous == null) return true
    val calendar = Calendar.getInstance()
    calendar.time = date
    val year = calendar.get(Calendar.YEAR)
    val day = calendar.get(Calendar.DAY_OF_YEAR)
    calendar.time = previous
    return year != calendar.get(Calendar.YEAR) || day != calendar.get(Calendar.DAY_OF_YEAR)
}

private fun chatDateLabel(date: Date, today: String, yesterday: String): String {
    val calendar = Calendar.getInstance()
    val targetYear: Int
    val targetDay: Int
    calendar.time = date
    targetYear = calendar.get(Calendar.YEAR)
    targetDay = calendar.get(Calendar.DAY_OF_YEAR)

    calendar.time = Date()
    val todayYear = calendar.get(Calendar.YEAR)
    val todayDay = calendar.get(Calendar.DAY_OF_YEAR)
    if (targetYear == todayYear && targetDay == todayDay) return today

    calendar.add(Calendar.DAY_OF_YEAR, -1)
    val yesterdayYear = calendar.get(Calendar.YEAR)
    val yesterdayDay = calendar.get(Calendar.DAY_OF_YEAR)
    if (targetYear == yesterdayYear && targetDay == yesterdayDay) return yesterday

    return DateFormat.getDateInstance(DateFormat.LONG).format(date)
}

private fun formatMessageTime(date: Date): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)

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
                isCurrentUser = false, currentUserId = "me",
                onReact = {}, onShowActions = { _ -> },
            )
            ChatMessageRow(
                message = ChatMessage(
                    id = "2", campingId = "c", senderId = "lead", senderName = "Camp Office",
                    text = "Hey @me can you confirm?", createdAt = java.util.Date(),
                    mentions = listOf(ChatMention("me", "me", offset = 4, length = 3)),
                    pinned = true,
                    reactions = mapOf("me" to "\uD83D\uDC4D", "other" to "\u2764\uFE0F"),
                ),
                isCurrentUser = false, currentUserId = "me",
                onReact = {}, onShowActions = { _ -> },
            )
            ChatMessageRow(
                message = ChatMessage(
                    id = "3", campingId = "c", senderId = "me", senderName = "Me",
                    text = "On my way!", createdAt = java.util.Date(), editedAt = java.util.Date(),
                    replyTo = ChatReplyReference(
                        messageId = "2",
                        senderId = "lead",
                        senderName = "Camp Office",
                        textPreview = "Hey @me can you confirm?",
                    ),
                ),
                isCurrentUser = true, currentUserId = "me",
                onReact = {}, onShowActions = { _ -> },
            )
        }
    }
}
