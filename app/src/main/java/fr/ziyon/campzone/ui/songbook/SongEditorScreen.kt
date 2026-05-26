package fr.ziyon.campzone.ui.songbook

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTypeScale
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.Song

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
        onBack = onBack,
        onUpdateForm = viewModel::updateForm,
        onAddAudio = viewModel::addPendingAudio,
        onRemoveAudio = viewModel::removeAudio,
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
    onBack: () -> Unit,
    onUpdateForm: ((SongEditorForm) -> SongEditorForm) -> Unit,
    onAddAudio: (String, String, ByteArray) -> Boolean,
    onRemoveAudio: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val context = LocalContext.current
    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        uris.forEach { uri ->
            val name = context.displayNameFor(uri) ?: uri.lastPathSegment ?: "Song audio.mp3"
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
                        if (isEditing) "Edit Song" else "New Song",
                        style = CzTypeScale.headline,
                        color = colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close", tint = colors.error)
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = !isSaving && form.title.isNotBlank()) {
                        if (isSaving) {
                            CircularProgressIndicator(color = colors.ember, strokeWidth = 2.dp, modifier = Modifier.padding(end = CzSpacing.xs))
                        }
                        Text("Save", color = if (!isSaving && form.title.isNotBlank()) colors.ember else colors.textSecondary)
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
        if (!canManage) {
            CzEmptyState(
                title = "Restricted",
                message = "Only admins can manage the songbook.",
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
                EditorSectionHeader("Song Info", Icons.Rounded.MusicNote)
                EditorCard {
                    SongTextField(
                        value = form.title,
                        onValueChange = { value -> onUpdateForm { it.copy(title = value) } },
                        placeholder = "Title",
                        capitalization = KeyboardCapitalization.Words,
                    )
                    DividerInset()
                    SongTextField(
                        value = form.artist,
                        onValueChange = { value -> onUpdateForm { it.copy(artist = value) } },
                        placeholder = "Artist",
                        capitalization = KeyboardCapitalization.Words,
                    )
                    DividerInset()
                    SongTextField(
                        value = form.composer,
                        onValueChange = { value -> onUpdateForm { it.copy(composer = value) } },
                        placeholder = "Composer",
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
                        Icon(Icons.Rounded.Mic, contentDescription = null, tint = colors.amber)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Theme Song", style = CzTypeScale.subhead.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                            Text("Pin as the camp's featured song", style = CzTypeScale.caption, color = colors.textSecondary)
                        }
                        Switch(
                            checked = form.isPinnedTheme,
                            onCheckedChange = { checked -> onUpdateForm { it.copy(isPinnedTheme = checked) } },
                        )
                    }
                }

                EditorSectionHeader("Audio", Icons.Rounded.Headphones)
                EditorCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(CzSpacing.base),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null, tint = colors.ember)
                        Column(Modifier.weight(1f)) {
                            Text("Local File", style = CzTypeScale.subhead.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                            Text("MP3, M4A, AAC, or WAV", style = CzTypeScale.caption, color = colors.textSecondary)
                        }
                        TextButton(onClick = { audioPicker.launch("audio/*") }) {
                            Text("Choose", color = colors.ember)
                        }
                    }
                }

                if (form.existingAudioFiles.isNotEmpty() || form.pendingAudioFiles.isNotEmpty()) {
                    EditorSectionHeader("Attached Audio", Icons.Rounded.Headphones)
                    EditorCard {
                        form.existingAudioFiles.forEachIndexed { index, audio ->
                            AudioRow(title = audio.fileName, subtitle = audio.kind.wireValue.uppercase(), onDelete = { onRemoveAudio(audio.id) })
                            if (index != form.existingAudioFiles.lastIndex || form.pendingAudioFiles.isNotEmpty()) DividerInset()
                        }
                        form.pendingAudioFiles.forEachIndexed { index, audio ->
                            AudioRow(title = audio.fileName, subtitle = "Ready to upload", onDelete = { onRemoveAudio(audio.id) })
                            if (index != form.pendingAudioFiles.lastIndex) DividerInset()
                        }
                    }
                }

                EditorSectionHeader("Lyrics", Icons.Rounded.TextFields)
                EditorCard {
                    TextField(
                        value = form.lyrics,
                        onValueChange = { value -> onUpdateForm { it.copy(lyrics = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5,
                        placeholder = { Text("Write lyrics here...", color = colors.textSecondary) },
                        textStyle = CzTypeScale.body.copy(color = colors.textPrimary),
                        colors = transparentTextFieldColors(),
                    )
                }

                EditorSectionHeader("Chord Sheet", Icons.Rounded.MusicNote)
                EditorCard {
                    TextField(
                        value = form.chordSheetText,
                        onValueChange = { value -> onUpdateForm { it.copy(chordSheetText = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6,
                        placeholder = { Text("[G] Let every voice rise [D] together", color = colors.textSecondary) },
                        textStyle = CzTypeScale.body.copy(color = colors.textPrimary, fontFamily = FontFamily.Monospace),
                        colors = transparentTextFieldColors(),
                    )
                }

                EditorSectionHeader("Links", Icons.Rounded.Link)
                EditorCard {
                    SongTextField(
                        value = form.pdfLink,
                        onValueChange = { value -> onUpdateForm { it.copy(pdfLink = value) } },
                        placeholder = "PDF URL",
                        keyboardType = KeyboardType.Uri,
                    )
                    DividerInset()
                    SongTextField(
                        value = form.youtubeLink,
                        onValueChange = { value -> onUpdateForm { it.copy(youtubeLink = value) } },
                        placeholder = "YouTube Link",
                        keyboardType = KeyboardType.Uri,
                    )
                }

                if (form.validationErrors.isNotEmpty() || operationError != null) {
                    EditorCard(background = colors.error.copy(alpha = 0.06f)) {
                        form.validationErrors.forEach { error ->
                            Text(error, style = CzTypeScale.caption, color = colors.error, modifier = Modifier.padding(CzSpacing.md))
                        }
                        if (operationError != null) {
                            Text(operationError, style = CzTypeScale.caption, color = colors.error, modifier = Modifier.padding(CzSpacing.md))
                        }
                    }
                }

                CzButton(
                    text = if (isEditing) "Save changes" else "Add song",
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
}

@Composable
private fun EditorSectionHeader(title: String, icon: ImageVector) {
    val colors = MaterialTheme.czColors
    Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = colors.ember, modifier = Modifier.padding(start = CzSpacing.xs))
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
private fun AudioRow(title: String, subtitle: String, onDelete: () -> Unit) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Icon(Icons.Rounded.Headphones, contentDescription = null, tint = colors.ember)
        Column(Modifier.weight(1f)) {
            Text(title, style = CzTypeScale.subhead.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = CzTypeScale.caption, color = colors.textSecondary)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, contentDescription = "Remove audio", tint = colors.error)
        }
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
    cursorColor = MaterialTheme.czColors.ember,
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
