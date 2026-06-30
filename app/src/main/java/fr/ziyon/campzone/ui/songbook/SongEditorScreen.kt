package fr.ziyon.campzone.ui.songbook

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTypeScale
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.Song
import fr.ziyon.campzone.data.model.SongLyricsPart
import fr.ziyon.campzone.data.model.SongLyricsPartKind
import fr.ziyon.campzone.data.model.SongAudioTrackType

@Composable
fun SongEditorRoute(
    campingId: String,
    songId: String?,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: SongbookViewModel,
) {
    val form by viewModel.form.collectAsState()
    val canManage by viewModel.canManageSongbook.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    val trackTypeEditorAudioId by viewModel.trackTypeEditorAudioId.collectAsState()
    val existingSong = songId?.let { viewModel.songById(it, campingId) }

    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.loadIfNeeded(campingId, authenticatedUser)
    }
    LaunchedEffect(songId, existingSong?.id) {
        if (songId == null) {
            viewModel.prepareNewSong(campingId)
        } else if (existingSong != null) {
            viewModel.prepareEditingSong(existingSong)
        }
    }

    SongEditorScreen(
        form = form,
        isEditing = songId != null,
        canManage = canManage,
        isSaving = isSaving,
        operationError = operationError,
        existingSong = existingSong,
        audioTracks = viewModel.orderedFormAudio(),
        trackTypeEditorAudioId = trackTypeEditorAudioId,
        availableTrackTypes = viewModel.availableTrackTypes(trackTypeEditorAudioId),
        onBack = onBack,
        onUpdateForm = viewModel::updateForm,
        onAddAudio = viewModel::addPendingAudio,
        onAddRemoteAudio = viewModel::addRemoteAudio,
        onRemoveAudio = viewModel::removeAudio,
        onEditAudioType = viewModel::editAudioTrackType,
        onDismissAudioTypeEditor = viewModel::dismissAudioTrackTypeEditor,
        onUpdateAudioType = viewModel::updateAudioTrackType,
        onStartLyricsPartEditor = viewModel::startLyricsPartEditor,
        onLyricsPartKindChange = viewModel::updateLyricsPartKind,
        onLyricsPartNumberChange = viewModel::updateLyricsPartNumber,
        onLyricsPartTitleChange = viewModel::updateLyricsPartTitle,
        onLyricsPartTextChange = viewModel::updateLyricsPartText,
        onSaveLyricsPart = viewModel::saveLyricsPart,
        onCancelLyricsPartEditing = viewModel::cancelLyricsPartEditing,
        onRemoveLyricsPart = viewModel::removeLyricsPart,
        onMoveLyricsPart = viewModel::moveLyricsPart,
        onSave = { viewModel.saveSong(campingId, onSuccess = onSaved) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongEditorScreen(
    form: SongEditorForm,
    isEditing: Boolean,
    canManage: Boolean,
    isSaving: Boolean,
    operationError: String?,
    existingSong: Song?,
    audioTracks: List<SongbookAudioTrackItem>,
    trackTypeEditorAudioId: String?,
    availableTrackTypes: List<SongAudioTrackType>,
    onBack: () -> Unit,
    onUpdateForm: ((SongEditorForm) -> SongEditorForm) -> Unit,
    onAddAudio: (String, String, ByteArray) -> Boolean,
    onAddRemoteAudio: (String) -> Boolean,
    onRemoveAudio: (String) -> Unit,
    onEditAudioType: (String) -> Unit,
    onDismissAudioTypeEditor: () -> Unit,
    onUpdateAudioType: (String, SongAudioTrackType, String) -> Boolean,
    onStartLyricsPartEditor: (String?) -> Unit,
    onLyricsPartKindChange: (SongLyricsPartKind) -> Unit,
    onLyricsPartNumberChange: (Int) -> Unit,
    onLyricsPartTitleChange: (String) -> Unit,
    onLyricsPartTextChange: (String) -> Unit,
    onSaveLyricsPart: () -> Unit,
    onCancelLyricsPartEditing: () -> Unit,
    onRemoveLyricsPart: (String) -> Unit,
    onMoveLyricsPart: (String, SongMoveDirection) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val context = LocalContext.current
    val defaultAudioName = stringResource(R.string.songbook_default_audio_name)
    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        uris.forEach { uri ->
            val name = context.displayNameFor(uri) ?: uri.lastPathSegment ?: defaultAudioName
            val type = context.contentResolver.getType(uri) ?: contentTypeForName(name)
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) onAddAudio(name, type, bytes)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(if (isEditing) R.string.songbook_edit_song else R.string.songbook_new_song),
                        style = CzTypeScale.headline,
                        color = colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_close), tint = colors.error)
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = !isSaving && form.title.isNotBlank()) {
                        if (isSaving) {
                            CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.padding(end = CzSpacing.xs))
                        }
                        Text(stringResource(R.string.common_save), color = if (!isSaving && form.title.isNotBlank()) colors.accent else colors.textSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    scrolledContainerColor = colors.background,
                ),
                windowInsets = WindowInsets(),
            )
        },
    ) { innerPadding ->
        if (!canManage) {
            CzEmptyState(
                title = stringResource(R.string.songbook_restricted_title),
                message = stringResource(R.string.songbook_restricted_message),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(CzSpacing.xl),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CzSpacing.base, vertical = CzSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
            ) {
                EditorSectionHeader(stringResource(R.string.songbook_song_info), Icons.Rounded.MusicNote)
                EditorCard {
                    SongTextField(
                        value = form.title,
                        onValueChange = { value -> onUpdateForm { it.copy(title = value) } },
                        placeholder = stringResource(R.string.common_title),
                        capitalization = KeyboardCapitalization.Words,
                    )
                    DividerInset()
                    SongTextField(
                        value = form.artist,
                        onValueChange = { value -> onUpdateForm { it.copy(artist = value) } },
                        placeholder = stringResource(R.string.songbook_artist),
                        capitalization = KeyboardCapitalization.Words,
                    )
                    DividerInset()
                    SongTextField(
                        value = form.composer,
                        onValueChange = { value -> onUpdateForm { it.copy(composer = value) } },
                        placeholder = stringResource(R.string.songbook_composer),
                        capitalization = KeyboardCapitalization.Words,
                    )
                    DividerInset()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(CzSpacing.base),
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, tint = colors.accent)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.songbook_theme_song_toggle), style = CzTypeScale.subhead.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                            Text(stringResource(R.string.songbook_theme_song_desc), style = CzTypeScale.caption, color = colors.textSecondary)
                        }
                        Switch(
                            checked = form.isPinnedTheme,
                            onCheckedChange = { checked -> onUpdateForm { it.copy(isPinnedTheme = checked) } },
                        )
                    }
                    DividerInset()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CzSpacing.base, vertical = CzSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                    ) {
                        Icon(Icons.Rounded.Link, contentDescription = null, tint = colors.accent)
                        TextField(
                            value = form.remoteAudioUrl,
                            onValueChange = { value -> onUpdateForm { it.copy(remoteAudioUrl = value) } },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.songbook_remote_audio_url)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            colors = transparentTextFieldColors(),
                        )
                        TextButton(
                            onClick = { onAddRemoteAudio(form.remoteAudioUrl) },
                            enabled = form.remoteAudioUrl.isNotBlank(),
                        ) {
                            Text(stringResource(R.string.common_add), color = colors.accent)
                        }
                    }
                }

                EditorSectionHeader(stringResource(R.string.songbook_audio), Icons.Rounded.Headphones)
                EditorCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(CzSpacing.base),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null, tint = colors.accent)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.songbook_local_file), style = CzTypeScale.subhead.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                            Text(stringResource(R.string.songbook_audio_types), style = CzTypeScale.caption, color = colors.textSecondary)
                        }
                        TextButton(onClick = { audioPicker.launch("audio/*") }) {
                            Text(stringResource(R.string.songbook_choose), color = colors.accent)
                        }
                    }
                }

                if (audioTracks.isNotEmpty()) {
                    EditorSectionHeader(stringResource(R.string.songbook_attached_audio), Icons.Rounded.Headphones)
                    EditorCard {
                        audioTracks.forEachIndexed { index, audio ->
                            AudioRow(
                                title = audio.fileName,
                                trackLabel = audio.displayName.ifBlank { stringResource(audio.type.displayNameRes) },
                                subtitle = if (audio.isPending) {
                                    stringResource(R.string.songbook_ready_upload)
                                } else {
                                    stringResource(R.string.songbook_audio_attached)
                                },
                                onEdit = { onEditAudioType(audio.id) },
                                onDelete = { onRemoveAudio(audio.id) },
                            )
                            if (index != audioTracks.lastIndex) DividerInset()
                        }
                    }
                    Text(
                        text = stringResource(R.string.songbook_voice_kits_hint),
                        style = CzTypeScale.caption2,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(horizontal = CzSpacing.sm),
                    )
                }

                EditorSectionHeader(
                    stringResource(if (form.isEditingLyricsPart) R.string.songbook_edit_lyrics_title else R.string.songbook_add_lyrics),
                    Icons.Rounded.TextFields,
                )
                LyricsPartEditorCard(
                    form = form,
                    onKindChange = onLyricsPartKindChange,
                    onNumberChange = onLyricsPartNumberChange,
                    onTitleChange = onLyricsPartTitleChange,
                    onTextChange = onLyricsPartTextChange,
                    onSave = onSaveLyricsPart,
                    onCancel = onCancelLyricsPartEditing,
                )

                if (form.lyricsParts.isNotEmpty()) {
                    EditorSectionHeader(stringResource(R.string.songbook_lyrics_structure), Icons.Rounded.TextFields)
                    EditorCard {
                        form.lyricsParts.forEachIndexed { index, part ->
                            LyricsPreviewRow(
                                part = part,
                                index = index,
                                isEditing = form.editingLyricsPartId == part.id,
                                canMoveUp = index > 0,
                                canMoveDown = index < form.lyricsParts.lastIndex,
                                onEdit = { onStartLyricsPartEditor(part.id) },
                                onDelete = { onRemoveLyricsPart(part.id) },
                                onMoveUp = { onMoveLyricsPart(part.id, SongMoveDirection.Up) },
                                onMoveDown = { onMoveLyricsPart(part.id, SongMoveDirection.Down) },
                            )
                            if (index != form.lyricsParts.lastIndex) DividerInset()
                        }
                    }
                }

                EditorSectionHeader(stringResource(R.string.songbook_chord_sheet), Icons.Rounded.MusicNote)
                EditorCard {
                    TextField(
                        value = form.chordSheetText,
                        onValueChange = { value -> onUpdateForm { it.copy(chordSheetText = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6,
                        placeholder = { Text(stringResource(R.string.songbook_chord_placeholder), color = colors.textSecondary) },
                        textStyle = CzTypeScale.body.copy(color = colors.textPrimary, fontFamily = FontFamily.Monospace),
                        colors = transparentTextFieldColors(),
                    )
                }

                EditorSectionHeader(stringResource(R.string.songbook_links), Icons.Rounded.Link)
                EditorCard {
                    SongTextField(
                        value = form.pdfLink,
                        onValueChange = { value -> onUpdateForm { it.copy(pdfLink = value) } },
                        placeholder = stringResource(R.string.songbook_pdf_url),
                        keyboardType = KeyboardType.Uri,
                    )
                    DividerInset()
                    SongTextField(
                        value = form.youtubeLink,
                        onValueChange = { value -> onUpdateForm { it.copy(youtubeLink = value) } },
                        placeholder = stringResource(R.string.songbook_youtube_link),
                        keyboardType = KeyboardType.Uri,
                    )
                }

                if (form.validationErrors.isNotEmpty() || operationError != null) {
                    EditorCard(background = colors.error.copy(alpha = 0.06f)) {
                        form.validationErrors.forEach { error ->
                            Text(
                                stringResource(error.messageRes),
                                style = CzTypeScale.caption,
                                color = colors.error,
                                modifier = Modifier.padding(CzSpacing.md),
                            )
                        }
                        if (operationError != null) {
                            Text(operationError, style = CzTypeScale.caption, color = colors.error, modifier = Modifier.padding(CzSpacing.md))
                        }
                    }
                }

                CzButton(
                    text = stringResource(if (isEditing) R.string.songbook_save_changes else R.string.songbook_add_song_action),
                    onClick = onSave,
                    variant = CzButtonVariant.Primary,
                    loading = isSaving,
                    enabled = form.title.isNotBlank() && !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(CzSpacing.xxl))
            }
        }
    }

    val editingTrack = audioTracks.firstOrNull { it.id == trackTypeEditorAudioId }
    if (editingTrack != null) {
        AudioTrackTypeDialog(
            track = editingTrack,
            availableTrackTypes = availableTrackTypes,
            onDismiss = onDismissAudioTypeEditor,
            onSave = { type, customName -> onUpdateAudioType(editingTrack.id, type, customName) },
        )
    }
}

@Composable
private fun EditorSectionHeader(title: String, icon: ImageVector) {
    val colors = MaterialTheme.czColors
    Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.padding(start = CzSpacing.xs))
        Text(title, style = CzTypeScale.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.textSecondary)
    }
}

@Composable
private fun EditorCard(
    background: Color = MaterialTheme.czColors.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.lg))
            .background(background),
        content = content,
    )
}

@Composable
private fun SongTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val colors = MaterialTheme.czColors
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(placeholder, color = colors.textSecondary) },
        textStyle = CzTypeScale.body.copy(color = colors.textPrimary),
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType),
        colors = transparentTextFieldColors(),
    )
}

@Composable
private fun AudioRow(
    title: String,
    trackLabel: String,
    subtitle: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Icon(Icons.Rounded.Headphones, contentDescription = null, tint = colors.accent)
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onEdit)
                .padding(vertical = CzSpacing.xs),
        ) {
            Text(title, style = CzTypeScale.subhead.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(trackLabel, style = CzTypeScale.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.accent)
            Text(subtitle, style = CzTypeScale.caption2, color = colors.textSecondary)
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.songbook_edit_voice_kit), tint = colors.accent)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.songbook_remove_audio), tint = colors.error)
        }
    }
}

@Composable
private fun AudioTrackTypeDialog(
    track: SongbookAudioTrackItem,
    availableTrackTypes: List<SongAudioTrackType>,
    onDismiss: () -> Unit,
    onSave: (SongAudioTrackType, String) -> Boolean,
) {
    val colors = MaterialTheme.czColors
    var selectedType by remember(track.id) { mutableStateOf(track.type) }
    var customName by remember(track.id) { mutableStateOf(track.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.songbook_choose_voice_kit)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            ) {
                Text(track.fileName, style = CzTypeScale.caption, color = colors.textSecondary)
                availableTrackTypes.forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(CzRadius.md))
                            .clickable { selectedType = type }
                            .background(if (selectedType == type) colors.accent.copy(alpha = 0.10f) else Color.Transparent)
                            .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(type.displayNameRes), modifier = Modifier.weight(1f), color = colors.textPrimary)
                        if (selectedType == type) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.accent)
                    }
                }
                if (selectedType == SongAudioTrackType.Other) {
                    TextField(
                        value = customName,
                        onValueChange = { customName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.songbook_custom_track_name)) },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selectedType, customName) }) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun LyricsPartEditorCard(
    form: SongEditorForm,
    onKindChange: (SongLyricsPartKind) -> Unit,
    onNumberChange: (Int) -> Unit,
    onTitleChange: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    EditorCard {
        LyricsPartKindPicker(
            selectedKind = form.selectedLyricsPartKind,
            onKindChange = onKindChange,
        )
        DividerInset()
        PartNumberStepper(
            value = form.selectedLyricsPartNumber,
            onValueChange = onNumberChange,
        )
        if (form.selectedLyricsPartKind == SongLyricsPartKind.Custom) {
            DividerInset()
            SongTextField(
                value = form.selectedLyricsPartTitle,
                onValueChange = onTitleChange,
                placeholder = stringResource(R.string.songbook_custom_title),
                capitalization = KeyboardCapitalization.Words,
            )
        }
        DividerInset()
        TextField(
            value = form.lyricsPartText,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 5,
            placeholder = { Text(stringResource(R.string.songbook_lyrics_placeholder), color = colors.textSecondary) },
            textStyle = CzTypeScale.body.copy(color = colors.textPrimary),
            colors = transparentTextFieldColors(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CzSpacing.base, vertical = CzSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (form.isEditingLyricsPart) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.common_cancel), color = colors.textSecondary, style = CzTypeScale.caption)
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onSave,
                enabled = form.lyricsPartText.trim().isNotEmpty(),
            ) {
                Icon(
                    if (form.isEditingLyricsPart) Icons.Rounded.Edit else Icons.Rounded.Add,
                    contentDescription = null,
                    tint = if (form.lyricsPartText.trim().isNotEmpty()) colors.accent else colors.textSecondary,
                )
                Text(
                    stringResource(if (form.isEditingLyricsPart) R.string.songbook_update_lyrics else R.string.songbook_add_lyrics),
                    color = if (form.lyricsPartText.trim().isNotEmpty()) colors.accent else colors.textSecondary,
                    style = CzTypeScale.caption.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

@Composable
private fun LyricsPartKindPicker(
    selectedKind: SongLyricsPartKind,
    onKindChange: (SongLyricsPartKind) -> Unit,
) {
    val colors = MaterialTheme.czColors
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(CzSpacing.base),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.songbook_type), style = CzTypeScale.caption, color = colors.textSecondary)
            Text(
                stringResource(selectedKind.displayNameRes),
                style = CzTypeScale.subhead.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
            )
        }
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(stringResource(R.string.songbook_change), color = colors.accent)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SongLyricsPartKind.entries.forEach { kind ->
                    DropdownMenuItem(
                        text = { Text(stringResource(kind.displayNameRes)) },
                        onClick = {
                            expanded = false
                            onKindChange(kind)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PartNumberStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(CzSpacing.base),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.songbook_part), style = CzTypeScale.caption, color = colors.textSecondary)
            Text(
                value.toString(),
                style = CzTypeScale.subhead.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
            )
        }
        TextButton(onClick = { onValueChange(value - 1) }, enabled = value > 1) {
            Text("-", color = if (value > 1) colors.accent else colors.textSecondary)
        }
        TextButton(onClick = { onValueChange(value + 1) }, enabled = value < 24) {
            Text("+", color = if (value < 24) colors.accent else colors.textSecondary)
        }
    }
}

@Composable
private fun LyricsPreviewRow(
    part: SongLyricsPart,
    index: Int,
    isEditing: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(CzSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            Text(
                part.displayTitle(),
                style = CzTypeScale.subhead.copy(fontWeight = FontWeight.SemiBold),
                color = if (part.kind == SongLyricsPartKind.Chorus) colors.accent else colors.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("#${index + 1}", style = CzTypeScale.caption, color = colors.textSecondary)
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Rounded.ArrowUpward, contentDescription = stringResource(R.string.songbook_move_lyrics_up), tint = if (canMoveUp) colors.textSecondary else colors.divider)
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Rounded.ArrowDownward, contentDescription = stringResource(R.string.songbook_move_lyrics_down), tint = if (canMoveDown) colors.textSecondary else colors.divider)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.songbook_edit_lyrics), tint = colors.accent)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.songbook_delete_lyrics), tint = colors.error)
            }
        }
        Text(
            part.text,
            style = CzTypeScale.caption,
            color = colors.textSecondary,
            maxLines = if (isEditing) 10 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DividerInset() {
    HorizontalDivider(
        color = MaterialTheme.czColors.divider,
        modifier = Modifier.padding(horizontal = CzSpacing.base),
    )
}

@Composable
private fun transparentTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = MaterialTheme.czColors.accent,
)

private fun android.content.Context.displayNameFor(uri: Uri): String? =
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }

private fun contentTypeForName(fileName: String): String = when {
    fileName.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
    fileName.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
    fileName.endsWith(".aac", ignoreCase = true) -> "audio/aac"
    fileName.endsWith(".wav", ignoreCase = true) -> "audio/wav"
    else -> "application/octet-stream"
}
