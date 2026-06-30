package fr.ziyon.campzone.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzAvatar
import fr.ziyon.campzone.core.designsystem.CzAvatarSize
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.chat.ChatAudioRecorder
import fr.ziyon.campzone.data.chat.ChatMentionScanner
import fr.ziyon.campzone.data.chat.ChatMessageDraft
import fr.ziyon.campzone.data.chat.MentionCandidate
import fr.ziyon.campzone.data.model.ChatAttachmentKind
import fr.ziyon.campzone.data.model.ChatMention
import fr.ziyon.campzone.data.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Chat input bar mirroring the iOS `ChatComposer`: text with WhatsApp/Teams
 * @mentions, an image attachment, tap-to-record voice notes with a
 * review-before-send step, and an inline edit mode for the user's own messages.
 */
@Composable
fun ChatComposer(
    draft: ChatMessageDraft,
    isEditing: Boolean,
    replyingTo: ChatMessage?,
    isSending: Boolean,
    isUploading: Boolean,
    mentionCandidates: List<MentionCandidate>,
    recorder: ChatAudioRecorder,
    onDraftChange: (text: String, mentions: List<ChatMention>) -> Unit,
    onSend: () -> Unit,
    onCommitEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onCancelReply: () -> Unit,
    onSendImage: (bytes: ByteArray, mimeType: String, fileExtension: String) -> Unit,
    onSendVoice: (bytes: ByteArray, durationSeconds: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var fieldValue by remember { mutableStateOf(TextFieldValue(draft.text)) }
    var recordedVoice by remember { mutableStateOf<ChatAudioRecorder.RecordedVoice?>(null) }
    var elapsed by remember { mutableDoubleStateOf(0.0) }

    // Sync the field when the draft changes externally (send clears it, begin-edit fills it).
    LaunchedEffect(draft.text) {
        if (draft.text != fieldValue.text) {
            fieldValue = TextFieldValue(draft.text, TextRange(draft.text.length))
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } ?: return@launch
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val ext = when (mime) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/heic", "image/heif" -> "heic"
                else -> "jpg"
            }
            onSendImage(bytes, mime, ext)
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        recorder.permissionDenied = !granted
        if (granted) recorder.start()
    }

    fun startRecording() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) recorder.start() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Tick the elapsed timer + auto-stop at the max duration while recording.
    LaunchedEffect(recorder.isRecording) {
        while (recorder.isRecording) {
            elapsed = recorder.elapsedSeconds()
            if (elapsed * 1000 >= ChatAudioRecorder.MAX_DURATION_MS) {
                recordedVoice = recorder.stop()
                break
            }
            delay(200)
        }
    }

    val activeQuery = remember(fieldValue, recorder.isRecording, recordedVoice) {
        if (recorder.isRecording || recordedVoice != null) {
            null
        } else {
            ChatMentionScanner.activeQuery(fieldValue.text, fieldValue.selection.start)
        }
    }
    val suggestions = remember(activeQuery, mentionCandidates) {
        val q = activeQuery?.query?.trim().orEmpty()
        when {
            activeQuery == null -> emptyList()
            q.isEmpty() -> mentionCandidates
            else -> mentionCandidates.filter {
                it.displayName.contains(q, ignoreCase = true) ||
                    (it.isEveryone && "everyone".startsWith(q.lowercase()))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .imePadding()
            .padding(horizontal = CzSpacing.md, vertical = CzSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        if (activeQuery != null) {
            MentionPicker(
                candidates = suggestions,
                query = activeQuery.query,
                onSelect = { candidate ->
                    val result = ChatMentionScanner.insertion(
                        candidate = candidate,
                        text = fieldValue.text,
                        atIndex = activeQuery.atIndex,
                        queryLength = activeQuery.query.length,
                    ) ?: return@MentionPicker
                    fieldValue = TextFieldValue(result.text, TextRange(result.caret))
                    onDraftChange(result.text, draft.mentions + result.mention)
                },
            )
        }

        if (replyingTo != null && !recorder.isRecording && recordedVoice == null) {
            ComposerReplyPreview(message = replyingTo, onCancel = onCancelReply)
        }
        if (isEditing) {
            EditBanner(onCancel = onCancelEdit)
        }
        if (recorder.permissionDenied) {
            MicDeniedNotice()
        }

        when {
            recorder.isRecording -> RecordingBar(
                elapsedSeconds = elapsed,
                onCancel = { recorder.cancel() },
                onStop = { recordedVoice = recorder.stop() },
            )
            recordedVoice != null -> ReviewBar(
                durationSeconds = recordedVoice!!.durationSeconds,
                isUploading = isUploading,
                onDiscard = {
                    recordedVoice?.file?.delete()
                    recordedVoice = null
                },
                onSend = {
                    val voice = recordedVoice ?: return@ReviewBar
                    scope.launch {
                        val bytes = withContext(Dispatchers.IO) { voice.file.readBytes() }
                        onSendVoice(bytes, voice.durationSeconds)
                        voice.file.delete()
                        recordedVoice = null
                    }
                },
            )
            else -> InputBar(
                fieldValue = fieldValue,
                draft = draft,
                isEditing = isEditing,
                isSending = isSending,
                isUploading = isUploading,
                mentionColor = colors.accent,
                onValueChange = { newValue ->
                    fieldValue = newValue
                    onDraftChange(newValue.text, draft.mentions)
                },
                onAttach = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onSend = onSend,
                onCommitEdit = onCommitEdit,
                onStartRecording = ::startRecording,
            )
        }
    }
}

@Composable
private fun InputBar(
    fieldValue: TextFieldValue,
    draft: ChatMessageDraft,
    isEditing: Boolean,
    isSending: Boolean,
    isUploading: Boolean,
    mentionColor: Color,
    onValueChange: (TextFieldValue) -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    onCommitEdit: () -> Unit,
    onStartRecording: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val transformation = remember(draft.mentions, mentionColor) {
        mentionVisualTransformation(draft.mentions, mentionColor)
    }
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        if (!isEditing) {
            CircleIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.chat_attach_photo),
                background = colors.accent.copy(alpha = 0.12f),
                tint = colors.accent,
                enabled = !isUploading,
                onClick = onAttach,
            )
        }
        BasicTextField(
            value = fieldValue,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .background(colors.surface, RoundedCornerShape(CzRadius.lg))
                .padding(horizontal = CzSpacing.md, vertical = CzSpacing.xs),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            visualTransformation = transformation,
            maxLines = 5,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (fieldValue.text.isBlank()) {
                        Text(
                            stringResource(
                                if (isEditing) R.string.chat_edit_placeholder else R.string.chat_message_placeholder,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textSecondary,
                        )
                    }
                    innerTextField()
                }
            },
        )
        val showSend = isEditing || draft.isValid || isSending
        when {
            isEditing -> CircleIconButton(
                icon = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.chat_save_edit),
                background = colors.accent,
                tint = Color.White,
                enabled = draft.isValid && !isSending,
                loading = isSending,
                onClick = onCommitEdit,
            )
            showSend -> CircleIconButton(
                icon = Icons.Rounded.Send,
                contentDescription = stringResource(R.string.chat_send),
                background = colors.accent,
                tint = Color.White,
                enabled = draft.isValid && !isSending,
                loading = isSending,
                onClick = onSend,
            )
            else -> CircleIconButton(
                icon = Icons.Rounded.Mic,
                contentDescription = stringResource(R.string.chat_record_voice),
                background = colors.accent,
                tint = Color.White,
                enabled = !isUploading,
                onClick = onStartRecording,
            )
        }
    }
}

@Composable
private fun RecordingBar(
    elapsedSeconds: Double,
    onCancel: () -> Unit,
    onStop: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        IconButton(onClick = onCancel) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.chat_cancel_recording),
                tint = colors.error,
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .background(colors.surface, RoundedCornerShape(CzRadius.lg))
                .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(colors.error, CircleShape),
            )
            Text(formatClock(elapsedSeconds), color = colors.textPrimary)
            Text(
                stringResource(R.string.chat_recording),
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
        }
        CircleIconButton(
            icon = Icons.Rounded.Stop,
            contentDescription = stringResource(R.string.chat_stop_recording),
            background = colors.accent,
            tint = Color.White,
            onClick = onStop,
        )
    }
}

@Composable
private fun ReviewBar(
    durationSeconds: Double,
    isUploading: Boolean,
    onDiscard: () -> Unit,
    onSend: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        IconButton(onClick = onDiscard) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.chat_discard_voice),
                tint = colors.error,
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .background(colors.surface, RoundedCornerShape(CzRadius.lg))
                .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = colors.accent)
            Text(
                stringResource(R.string.chat_voice_message_duration, formatClock(durationSeconds)),
                color = colors.textPrimary,
            )
        }
        CircleIconButton(
            icon = Icons.Rounded.Send,
            contentDescription = stringResource(R.string.chat_send_voice),
            background = colors.accent,
            tint = Color.White,
            enabled = !isUploading,
            loading = isUploading,
            onClick = onSend,
        )
    }
}

@Composable
private fun ComposerReplyPreview(message: ChatMessage, onCancel: () -> Unit) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(CzRadius.md))
            .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.chat_replying_to, message.senderName),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.accent,
            )
            Text(
                replyPreviewText(message),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.chat_cancel_reply))
        }
    }
}

@Composable
private fun replyPreviewText(message: ChatMessage): String = when {
    message.isDeleted -> stringResource(R.string.chat_message_removed)
    message.hasText -> message.text
    message.attachment?.kind == ChatAttachmentKind.Image -> stringResource(R.string.chat_photo)
    message.attachment?.kind == ChatAttachmentKind.Audio -> stringResource(R.string.chat_voice_message)
    else -> stringResource(R.string.chat_message_placeholder)
}

@Composable
private fun EditBanner(onCancel: () -> Unit) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.accent.copy(alpha = 0.10f), RoundedCornerShape(CzRadius.md))
            .padding(horizontal = CzSpacing.md, vertical = CzSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Text(
            stringResource(R.string.chat_editing_banner),
            color = colors.textPrimary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.chat_cancel_editing),
                tint = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun MicDeniedNotice() {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.warning.copy(alpha = 0.10f), RoundedCornerShape(CzRadius.md))
            .padding(horizontal = CzSpacing.md, vertical = CzSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Icon(Icons.Rounded.MicOff, contentDescription = null, tint = colors.warning)
        Text(
            stringResource(R.string.chat_mic_denied),
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    background: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(40.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(if (enabled) background else background.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = tint, strokeWidth = 2.dp)
            } else {
                Icon(icon, contentDescription = contentDescription, tint = tint)
            }
        }
    }
}

/** Floating @mention suggestion list shown above the composer (iOS MentionPicker). */
@Composable
fun MentionPicker(
    candidates: List<MentionCandidate>,
    query: String,
    onSelect: (MentionCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background, RoundedCornerShape(CzRadius.lg)),
    ) {
        Text(
            text = if (query.isBlank()) {
                stringResource(R.string.chat_mention_header)
            } else {
                stringResource(R.string.chat_mention_header_query, query)
            },
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = CzSpacing.md, vertical = CzSpacing.xs),
        )
        if (candidates.isEmpty()) {
            Text(
                stringResource(R.string.chat_mention_empty),
                color = colors.textSecondary,
                modifier = Modifier.padding(CzSpacing.md),
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 264.dp)) {
                items(candidates, key = { it.id }) { candidate ->
                    MentionRow(candidate = candidate, onClick = { onSelect(candidate) })
                }
            }
        }
    }
}

@Composable
private fun MentionRow(candidate: MentionCandidate, onClick: () -> Unit) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        CzAvatar(
            imageUrl = candidate.photoUrl,
            contentDescription = candidate.displayName,
            initials = candidate.displayName.take(2),
            size = CzAvatarSize.Small,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                candidate.displayName,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                candidate.subtitle,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
        if (candidate.isEveryone) {
            Icon(Icons.Rounded.Campaign, contentDescription = null, tint = colors.accent)
        }
    }
}

private fun mentionVisualTransformation(
    mentions: List<ChatMention>,
    color: Color,
): VisualTransformation = VisualTransformation { text ->
    val resolved = ChatMentionScanner.resolve(mentions, text.text)
    val annotated = buildAnnotatedString {
        append(text.text)
        resolved.forEach { mention ->
            addStyle(
                SpanStyle(color = color, fontWeight = FontWeight.SemiBold),
                mention.offset,
                mention.endOffset,
            )
        }
    }
    TransformedText(annotated, OffsetMapping.Identity)
}

private fun formatClock(seconds: Double): String {
    val total = seconds.roundToInt()
    return "%d:%02d".format(total / 60, total % 60)
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun ChatComposerPreview() {
    fr.ziyon.campzone.core.designsystem.CampzoneTheme {
        ChatComposer(
            draft = ChatMessageDraft(text = "Hey @Léa, ready for dinner?"),
            isEditing = false,
            replyingTo = null,
            isSending = false,
            isUploading = false,
            mentionCandidates = listOf(
                MentionCandidate("everyone", "Everyone", "Notify all participants", isEveryone = true),
                MentionCandidate("lea", "Léa Müller", "Lausanne SDA"),
            ),
            recorder = ChatAudioRecorder(LocalContext.current),
            onDraftChange = { _, _ -> },
            onSend = {},
            onCommitEdit = {},
            onCancelEdit = {},
            onCancelReply = {},
            onSendImage = { _, _, _ -> },
            onSendVoice = { _, _ -> },
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun MentionPickerPreview() {
    fr.ziyon.campzone.core.designsystem.CampzoneTheme {
        MentionPicker(
            candidates = listOf(
                MentionCandidate("everyone", "Everyone", "Notify all participants", isEveryone = true),
                MentionCandidate("lea", "Léa Müller", "Lausanne SDA"),
                MentionCandidate("david", "David Chen", "Paris 17e"),
            ),
            query = "",
            onSelect = {},
        )
    }
}
