package fr.ziyon.campzone.ui.songbook

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTypeScale
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.ChordLine
import fr.ziyon.campzone.data.model.Song

private enum class SongDisplayMode(val title: String) {
    Lyrics("Lyrics"),
    Sheet("Sheet"),
    Chords("Chords"),
}

@Composable
fun SongDetailRoute(
    campingId: String,
    songId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit,
    viewModel: SongbookViewModel,
) {
    val canManage by viewModel.canManageSongbook.collectAsState()
    val playingSongId by viewModel.playingSongId.collectAsState()
    val song = viewModel.songById(songId, campingId)

    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.loadIfNeeded(campingId, authenticatedUser)
    }

    SongDetailScreen(
        song = song,
        userId = authenticatedUser.uid,
        isPlaying = playingSongId == songId,
        canManage = canManage,
        onBack = onBack,
        onEdit = {
            song?.let {
                viewModel.prepareEditingSong(it)
                onOpenEditor(it.id)
            }
        },
        onDelete = {
            viewModel.deleteSong(songId, campingId, onDeleted = onBack)
        },
        onPin = {
            viewModel.setPinnedTheme(songId, campingId)
        },
        onToggleFavorite = {
            viewModel.toggleFavorite(songId, campingId, authenticatedUser.uid)
        },
        onToggleAudio = {
            song?.let(viewModel::toggleAudio)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(
    song: Song?,
    userId: String?,
    isPlaying: Boolean,
    canManage: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleAudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(SongDisplayMode.Lyrics) }
    var textSize by remember { mutableFloatStateOf(18f) }
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.songbook_song_title), style = CzTypeScale.headline, color = colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = colors.textPrimary)
                    }
                },
                actions = {
                    if (song != null) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.songbook_options_cd), tint = colors.textPrimary)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.songbook_smaller_text)) }, leadingIcon = { Icon(Icons.Rounded.Remove, null) }, onClick = {
                                textSize = (textSize - 1f).coerceAtLeast(14f)
                                menuOpen = false
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.songbook_larger_text)) }, leadingIcon = { Icon(Icons.Rounded.TextIncrease, null) }, onClick = {
                                textSize = (textSize + 1f).coerceAtMost(30f)
                                menuOpen = false
                            })
                            if (canManage) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.common_edit)) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = {
                                    menuOpen = false
                                    onEdit()
                                })
                                if (!song.isPinnedTheme) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.songbook_set_theme)) }, leadingIcon = { Icon(Icons.Rounded.Mic, null) }, onClick = {
                                        menuOpen = false
                                        onPin()
                                    })
                                }
                                DropdownMenuItem(text = { Text(stringResource(R.string.common_delete), color = colors.error) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = colors.error) }, onClick = {
                                    menuOpen = false
                                    confirmDelete = true
                                })
                            }
                        }
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
        if (song == null) {
            CzEmptyState(
                title = stringResource(R.string.songbook_not_found_title),
                message = stringResource(R.string.songbook_not_found_message),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(CzSpacing.xl),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
            ) {
                SongDetailHeader(
                    song = song,
                    isFavorite = userId?.let(song::isFavoritedBy) == true,
                    isPlaying = isPlaying,
                    onFavorite = onToggleFavorite,
                    onPlay = onToggleAudio,
                    onWatch = { openUrl(context, song.youtubeLink) },
                    modifier = Modifier.padding(horizontal = CzSpacing.lg, vertical = CzSpacing.md),
                )

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CzSpacing.lg),
                ) {
                    SongDisplayMode.entries.forEachIndexed { index, item ->
                        SegmentedButton(
                            selected = mode == item,
                            onClick = { mode = item },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = SongDisplayMode.entries.size),
                            label = { Text(item.title) },
                        )
                    }
                }

                when (mode) {
                    SongDisplayMode.Lyrics -> LyricsPanel(song = song, textSize = textSize)
                    SongDisplayMode.Sheet -> SheetPanel(song = song, onOpen = { openUrl(context, song.pdfLink) })
                    SongDisplayMode.Chords -> ChordsPanel(song = song, textSize = textSize)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.songbook_delete_title)) },
            text = { Text(stringResource(R.string.songbook_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) { Text(stringResource(R.string.common_delete), color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun SongDetailHeader(
    song: Song,
    isFavorite: Boolean,
    isPlaying: Boolean,
    onFavorite: () -> Unit,
    onPlay: () -> Unit,
    onWatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.lg), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(CzRadius.lg))
                    .background(
                        Brush.linearGradient(
                            if (song.isPinnedTheme) {
                                listOf(colors.amber.copy(alpha = 0.3f), colors.ember.copy(alpha = 0.2f))
                            } else {
                                listOf(colors.ember.copy(alpha = 0.2f), colors.ember.copy(alpha = 0.1f))
                            },
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (song.isPinnedTheme) Icons.Rounded.Mic else Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = if (song.isPinnedTheme) colors.amber else colors.ember,
                    modifier = Modifier.size(40.dp),
                )
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                Text(song.title, style = CzTypeScale.title2.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, maxLines = 2)
                if (song.artist.isNotBlank()) {
                    Text(song.artist, style = CzTypeScale.body, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(song.subtitleText(), style = CzTypeScale.caption, color = colors.textSecondary)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onFavorite) {
                Icon(
                    if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                    tint = colors.error,
                )
            }
            if (song.audio != null) {
                IconButton(onClick = onPlay) {
                    Icon(
                        if (isPlaying) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = colors.ember,
                    )
                }
            }
            if (song.youtubeLink.isNotBlank()) {
                TextButton(onClick = onWatch) {
                    Icon(Icons.Rounded.SlowMotionVideo, contentDescription = null, tint = colors.ember, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.songbook_watch_youtube), color = colors.ember, style = CzTypeScale.caption.copy(fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

@Composable
private fun LyricsPanel(song: Song, textSize: Float) {
    val colors = MaterialTheme.czColors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xl),
    ) {
        if (song.lyricsParts.isEmpty()) {
            Text(song.lyrics, fontSize = textSize.sp, color = colors.textPrimary, lineHeight = (textSize + 6).sp)
        } else {
            song.lyricsParts.forEach { part ->
                Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                    Text(part.displayTitle(), fontSize = (textSize * 0.8f).sp, color = colors.ember, fontWeight = FontWeight.Bold)
                    Text(part.text, fontSize = textSize.sp, color = colors.textPrimary, lineHeight = (textSize + 6).sp)
                }
            }
        }
        if (song.composer.isNotBlank()) {
            Text(stringResource(R.string.songbook_composed_by, song.composer), style = CzTypeScale.caption, color = colors.textSecondary, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SheetPanel(song: Song, onOpen: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (song.pdfLink.isBlank()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                Icon(Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.czColors.textSecondary, modifier = Modifier.size(36.dp))
                Text(stringResource(R.string.songbook_no_sheet), style = CzTypeScale.body, color = MaterialTheme.czColors.textPrimary)
                Text(stringResource(R.string.songbook_no_sheet_message), style = CzTypeScale.caption, color = MaterialTheme.czColors.textSecondary)
            }
        } else {
            CzButton(
                text = stringResource(R.string.songbook_open_sheet),
                onClick = onOpen,
                variant = CzButtonVariant.Primary,
            )
        }
    }
}

@Composable
private fun ChordsPanel(song: Song, textSize: Float) {
    val colors = MaterialTheme.czColors
    val lines = song.chordSheet.lines.ifEmpty { ChordProParser.parse(song.chords).lines }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        if (lines.isEmpty() || lines.all { it.chords.isEmpty() && it.text.isBlank() }) {
            Text(
                text = song.chords.ifBlank { "No chord sheet available." },
                fontFamily = FontFamily.Monospace,
                fontSize = textSize.sp,
                color = if (song.chords.isBlank()) colors.textSecondary else colors.textPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CzRadius.md))
                    .background(colors.surface)
                    .padding(CzSpacing.md),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CzRadius.lg))
                    .background(colors.surface)
                    .padding(CzSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            ) {
                lines.forEach { line ->
                    RenderedChordLine(line = line, textSize = textSize)
                }
            }
        }
    }
}

@Composable
private fun RenderedChordLine(line: ChordLine, textSize: Float) {
    val colors = MaterialTheme.czColors
    if (line.isSectionHeader) {
        Text(
            text = line.text,
            fontSize = (textSize - 1).coerceAtLeast(14f).sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.ember,
            modifier = Modifier.padding(top = CzSpacing.xs),
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val chordLine = ChordProParser.renderedChordLine(line)
            if (chordLine.isNotBlank()) {
                Text(
                    text = chordLine,
                    fontFamily = FontFamily.Monospace,
                    fontSize = (textSize - 2).coerceAtLeast(13f).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ember,
                )
            }
            Text(
                text = line.text.ifBlank { " " },
                fontFamily = FontFamily.Monospace,
                fontSize = textSize.sp,
                color = if (line.text.isBlank()) Color.Transparent else colors.textPrimary,
            )
        }
    }
}

private fun Song.subtitleText(): String = when {
    isPinnedTheme -> "Theme song"
    audio != null -> "Audio available"
    chordSheet.lines.isNotEmpty() || chords.isNotBlank() -> "Lyrics and chords"
    else -> "Lyrics only"
}
