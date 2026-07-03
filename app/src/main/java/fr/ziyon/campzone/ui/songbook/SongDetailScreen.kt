package fr.ziyon.campzone.ui.songbook

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTypeScale
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.ChordLine
import fr.ziyon.campzone.data.model.Song
import fr.ziyon.campzone.data.model.SongAudio
import fr.ziyon.campzone.data.model.SongLyricsPart
import fr.ziyon.campzone.data.model.SongLyricsPartKind
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Locale
import kotlin.math.roundToInt

private enum class SongDisplayMode(@param:androidx.annotation.StringRes val titleRes: Int) {
    Lyrics(R.string.songbook_lyrics),
    Chords(R.string.songbook_chords),
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
    val playingAudioId by viewModel.playingAudioId.collectAsState()
    val isAudioPlaying by viewModel.isAudioPlaying.collectAsState()
    val playbackPositionMs by viewModel.playbackPositionMs.collectAsState()
    val playbackDurationMs by viewModel.playbackDurationMs.collectAsState()
    val song = viewModel.songById(songId, campingId)
    val isActive = playingSongId == songId

    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.loadIfNeeded(campingId, authenticatedUser)
    }

    SongDetailScreen(
        song = song,
        userId = authenticatedUser.uid,
        isActive = isActive,
        isPlaying = isActive && isAudioPlaying,
        playingAudioId = playingAudioId.takeIf { playingSongId == songId },
        playbackPositionMs = playbackPositionMs.takeIf { isActive } ?: 0L,
        playbackDurationMs = playbackDurationMs.takeIf { isActive } ?: 0L,
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
        onPlayTrack = { track ->
            song?.let { viewModel.playAudio(it, track) }
        },
        onSeekAudio = viewModel::seekAudioTo,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(
    song: Song?,
    userId: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    playingAudioId: String?,
    playbackPositionMs: Long,
    playbackDurationMs: Long,
    canManage: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleAudio: () -> Unit,
    onPlayTrack: (SongAudio) -> Unit,
    onSeekAudio: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(SongDisplayMode.Lyrics) }
    val context = LocalContext.current
    val textSizePreferences = remember(context) {
        context.applicationContext.getSharedPreferences("songbook_preferences", android.content.Context.MODE_PRIVATE)
    }
    var textSize by remember(textSizePreferences) {
        mutableFloatStateOf(textSizePreferences.getFloat("songbook.textSize", 20f))
    }
    val updateTextSize: (Float) -> Unit = { next ->
        val bounded = next.coerceIn(14f, 30f)
        textSize = bounded
        textSizePreferences.edit().putFloat("songbook.textSize", bounded).apply()
    }

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.songbook_music).uppercase(),
                        style = CzTypeScale.caption2.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textTertiary,
                    )
                },
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
                                updateTextSize(textSize - 1f)
                                menuOpen = false
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.songbook_larger_text)) }, leadingIcon = { Icon(Icons.Rounded.TextIncrease, null) }, onClick = {
                                updateTextSize(textSize + 1f)
                                menuOpen = false
                            })
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (userId?.let(song::isFavoritedBy) == true) {
                                                R.string.songbook_remove_favorite
                                            } else {
                                                R.string.songbook_add_favorite
                                            },
                                        ),
                                    )
                                },
                                leadingIcon = { Icon(if (userId?.let(song::isFavoritedBy) == true) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null) },
                                onClick = {
                                    menuOpen = false
                                    onToggleFavorite()
                                },
                            )
                            if (song.hasAlternativeAudio) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.songbook_voice_kits), fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = { Icon(Icons.Rounded.QueueMusic, null) },
                                    enabled = false,
                                    onClick = {},
                                )
                                song.orderedAudioFiles.forEach { track ->
                                    val selected = playingAudioId == track.id
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                track.displayName.ifBlank { stringResource(track.trackType.displayNameRes) },
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                if (selected) Icons.Rounded.Check else Icons.Rounded.PlayArrow,
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            menuOpen = false
                                            onPlayTrack(track)
                                        },
                                    )
                                }
                            }
                            if (song.youtubeLink.isNotBlank()) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.songbook_watch_youtube)) }, leadingIcon = { Icon(Icons.Rounded.SlowMotionVideo, null) }, onClick = {
                                    menuOpen = false
                                    openUrl(context, song.youtubeLink)
                                })
                            }
                            if (song.pdfLink.isNotBlank()) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.songbook_open_sheet_pdf)) }, leadingIcon = { Icon(Icons.Rounded.Description, null) }, onClick = {
                                    menuOpen = false
                                    openUrl(context, song.pdfLink)
                                })
                            }
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
                colors = TopAppBarDefaults.topAppBarColors(
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
            val modes = availableDisplayModes(song)
            val currentMode = if (mode in modes) mode else SongDisplayMode.Lyrics
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
            ) {
                SongDetailHeader(
                    song = song,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    nowPlayingLabel = song.orderedAudioFiles
                        .firstOrNull { it.id == playingAudioId }
                        ?.let { track ->
                            track.displayName.ifBlank { stringResource(track.trackType.displayNameRes) }
                        },
                    playbackPositionMs = playbackPositionMs,
                    playbackDurationMs = playbackDurationMs,
                    onPlay = onToggleAudio,
                    onSeek = onSeekAudio,
                    modifier = Modifier.padding(horizontal = CzSpacing.lg, vertical = CzSpacing.md),
                )

                if (modes.size > 1) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CzSpacing.lg),
                    ) {
                        modes.forEachIndexed { index, item ->
                            SegmentedButton(
                                selected = currentMode == item,
                                onClick = { mode = item },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                                label = { Text(stringResource(item.titleRes)) },
                            )
                        }
                    }
                }

                when (currentMode) {
                    SongDisplayMode.Lyrics -> LyricsPanel(song = song, textSize = textSize)
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

private fun availableDisplayModes(song: Song): List<SongDisplayMode> = buildList {
    add(SongDisplayMode.Lyrics)
    val hasChords = song.chordSheet.lines.isNotEmpty() || song.chords.isNotBlank()
    if (hasChords) add(SongDisplayMode.Chords)
}

@Composable
private fun SongDetailHeader(
    song: Song,
    isActive: Boolean,
    isPlaying: Boolean,
    nowPlayingLabel: String?,
    playbackPositionMs: Long,
    playbackDurationMs: Long,
    onPlay: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(CzRadius.xxl), clip = false)
            .clip(RoundedCornerShape(CzRadius.xxl))
            .background(
                Brush.linearGradient(
                    listOf(colors.espresso, colors.espressoDeep),
                ),
            )
            .border(BorderStroke(1.dp, Color.Black.copy(alpha = 0.20f)), RoundedCornerShape(CzRadius.xxl))
            .padding(horizontal = CzSpacing.base, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                Text(
                    text = song.title,
                    style = CzTypeScale.title2.copy(fontWeight = FontWeight.Medium, fontFamily = FontFamily.Serif),
                    color = colors.cream,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (song.artist.isNotBlank()) {
                    Text(
                        text = song.artist,
                        style = CzTypeScale.caption,
                        color = colors.cream.copy(alpha = 0.66f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (song.hasAlternativeAudio && nowPlayingLabel != null) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.cream.copy(alpha = 0.14f))
                            .padding(horizontal = CzSpacing.sm, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = colors.cream,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = nowPlayingLabel,
                            style = CzTypeScale.caption2.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.cream,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (song.mainAudio != null) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .shadow(10.dp, CircleShape, clip = false)
                        .clip(CircleShape)
                        .background(colors.accent)
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.songbook_pause else R.string.songbook_play),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = song.mainAudio != null && isActive,
            enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(220)),
            exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(140)),
        ) {
            HeroPlaybackProgressBar(
                currentTimeMs = playbackPositionMs,
                durationMs = playbackDurationMs,
                onSeek = onSeek,
            )
        }
    }
}

@Composable
private fun HeroPlaybackProgressBar(
    currentTimeMs: Long,
    durationMs: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val label = stringResource(R.string.songbook_playback_position)
    val hasDuration = durationMs > 0L
    val progress = if (hasDuration) {
        (currentTimeMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val displayedProgress = if (isDragging) dragProgress else progress
    val displayTimeMs = if (isDragging && hasDuration) {
        (durationMs * dragProgress).toLong().coerceIn(0L, durationMs)
    } else {
        currentTimeMs
    }

    fun ratioFrom(x: Float): Float = if (widthPx > 0f) (x / widthPx).coerceIn(0f, 1f) else 0f
    val seekModifier = if (hasDuration) {
        Modifier
            .pointerInput(durationMs, widthPx) {
                detectTapGestures { offset ->
                    onSeek(ratioFrom(offset.x))
                }
            }
            .pointerInput(durationMs, widthPx, progress) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragProgress = ratioFrom(offset.x).takeIf { widthPx > 0f } ?: progress
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragProgress = ratioFrom(change.position.x)
                    },
                    onDragEnd = {
                        onSeek(dragProgress)
                        isDragging = false
                    },
                    onDragCancel = { isDragging = false },
                )
            }
    } else {
        Modifier
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .onSizeChanged { widthPx = it.width.toFloat() }
                .semantics { contentDescription = label }
                .then(seekModifier),
        ) {
            val trackHeight = 4.dp.toPx()
            val centerY = size.height / 2f
            val radius = trackHeight / 2f
            val knobRadius = (if (isDragging) 15.dp else 11.dp).toPx() / 2f
            val knobCenterX = if (size.width > knobRadius * 2f) {
                (size.width * displayedProgress).coerceIn(knobRadius, size.width - knobRadius)
            } else {
                size.width * displayedProgress
            }
            val cream = colors.cream

            drawRoundRect(
                color = cream.copy(alpha = 0.16f),
                topLeft = Offset(0f, centerY - radius),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(radius, radius),
            )
            if (hasDuration) {
                val fillWidth = (size.width * displayedProgress).coerceIn(0f, size.width)
                drawRoundRect(
                    color = cream.copy(alpha = 0.92f),
                    topLeft = Offset(0f, centerY - radius),
                    size = Size(fillWidth, trackHeight),
                    cornerRadius = CornerRadius(radius, radius),
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.25f),
                    radius = knobRadius,
                    center = Offset(knobCenterX, centerY + 1.dp.toPx()),
                )
                drawCircle(
                    color = cream,
                    radius = knobRadius,
                    center = Offset(knobCenterX, centerY),
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val timeStyle = CzTypeScale.caption2.copy(fontFamily = FontFamily.Monospace)
            Text(
                text = playbackTimeString(displayTimeMs),
                style = timeStyle,
                color = colors.cream.copy(alpha = 0.66f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (hasDuration) playbackTimeString(durationMs) else "--:--",
                style = timeStyle,
                color = colors.cream.copy(alpha = 0.66f),
            )
        }
    }
}

private fun playbackTimeString(milliseconds: Long): String {
    val totalSeconds = ((milliseconds.coerceAtLeast(0L) + 500L) / 1_000L).toInt()
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

@Composable
private fun LyricsPanel(song: Song, textSize: Float) {
    val colors = MaterialTheme.czColors
    SelectionContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CzSpacing.xl, vertical = CzSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.xxl),
        ) {
            if (song.lyricsParts.isEmpty()) {
                Text(
                    song.lyrics,
                    fontFamily = FontFamily.Serif,
                    fontSize = textSize.sp,
                    color = colors.textPrimary,
                    lineHeight = (textSize * 1.34f).sp,
                )
            } else {
                song.lyricsParts.forEach { part ->
                    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                        LyricsPartLabel(part)
                        Text(
                            part.text,
                            fontFamily = FontFamily.Serif,
                            fontSize = textSize.sp,
                            color = colors.textPrimary,
                            lineHeight = (textSize * 1.34f).sp,
                        )
                    }
                }
            }
            if (song.composer.isNotBlank()) {
                Text(
                    stringResource(R.string.songbook_composed_by, song.composer),
                    style = CzTypeScale.caption,
                    color = colors.textSecondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun LyricsPartLabel(part: SongLyricsPart) {
    val colors = MaterialTheme.czColors
    Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (part.kind == SongLyricsPartKind.Custom && part.title.isNotBlank()) {
                part.title.uppercase()
            } else {
                stringResource(part.kind.displayNameRes).uppercase()
            },
            style = CzTypeScale.caption2.copy(fontWeight = FontWeight.Bold),
            color = colors.accent,
        )
        if (part.kind != SongLyricsPartKind.Custom) {
            Text(
                text = part.number.toString(),
                style = CzTypeScale.caption2.copy(fontWeight = FontWeight.Bold),
                color = colors.accent,
                modifier = Modifier
                    .defaultMinSize(minWidth = 18.dp, minHeight = 17.dp)
                    .background(colors.accent.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 5.dp),
            )
        }
    }
}

@Composable
private fun ChordsPanel(song: Song, textSize: Float) {
    val colors = MaterialTheme.czColors
    val lines = song.chordSheet.lines.ifEmpty { ChordProParser.parse(song.chords).lines }
    val uniqueChords = remember(lines) {
        lines.flatMap { line -> line.chords.map { it.chord } }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    var transposeOffset by remember(song.id) { mutableStateOf(0) }
    var scrollSpeed by remember(song.id) { mutableFloatStateOf(1f) }
    var isFullScreen by remember(song.id) { mutableStateOf(false) }
    val keyName = song.chordSheet.originalKey.ifBlank { "C" }

    ChordsPerformanceContent(
        lines = lines,
        uniqueChords = uniqueChords,
        rawChords = song.chords,
        textSize = textSize,
        keyName = keyName,
        transposeOffset = transposeOffset,
        onTranspose = { delta -> transposeOffset = (transposeOffset + delta).coerceIn(-12, 12) },
        scrollSpeed = scrollSpeed,
        onSpeedChange = { scrollSpeed = it },
        isFullScreen = false,
        onEnterFullScreen = { isFullScreen = true },
    )

    if (isFullScreen) {
        Dialog(
            onDismissRequest = { isFullScreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background),
            ) {
                ChordsPerformanceContent(
                    lines = lines,
                    uniqueChords = uniqueChords,
                    rawChords = song.chords,
                    textSize = textSize,
                    keyName = keyName,
                    transposeOffset = transposeOffset,
                    onTranspose = { delta -> transposeOffset = (transposeOffset + delta).coerceIn(-12, 12) },
                    scrollSpeed = scrollSpeed,
                    onSpeedChange = { scrollSpeed = it },
                    isFullScreen = true,
                    onExitFullScreen = { isFullScreen = false },
                )
            }
        }
    }
}

@Composable
private fun ChordsPerformanceContent(
    lines: List<ChordLine>,
    uniqueChords: List<String>,
    rawChords: String,
    textSize: Float,
    keyName: String,
    transposeOffset: Int,
    onTranspose: (Int) -> Unit,
    scrollSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    isFullScreen: Boolean,
    onEnterFullScreen: (() -> Unit)? = null,
    onExitFullScreen: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.czColors
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val topInset = if (isFullScreen) safeDrawingPadding.calculateTopPadding().coerceAtLeast(CzSpacing.lg) else 0.dp
    val bottomInset = if (isFullScreen) safeDrawingPadding.calculateBottomPadding().coerceAtLeast(CzSpacing.sm) else 0.dp
    val fullScreenTopReserve = if (isFullScreen) topInset + 58.dp else 0.dp
    var isAutoScrolling by remember(isFullScreen) { mutableStateOf(false) }
    val pixelsPerSecond = with(density) { 28.dp.toPx() } * scrollSpeed

    FullScreenSystemBarsEffect(enabled = isFullScreen)

    LaunchedEffect(isAutoScrolling, pixelsPerSecond, scrollState.maxValue) {
        var lastFrameNanos: Long? = null
        var pendingScrollPixels = 0f

        while (isAutoScrolling) {
            if (scrollState.maxValue <= 0 || scrollState.value >= scrollState.maxValue) {
                isAutoScrolling = false
                break
            }

            val frameNanos = withFrameNanos { it }
            val elapsedSeconds = lastFrameNanos
                ?.let { ((frameNanos - it) / 1_000_000_000f).coerceIn(0f, 0.1f) }
                ?: (1f / 60f)
            lastFrameNanos = frameNanos

            pendingScrollPixels += pixelsPerSecond * elapsedSeconds
            if (pendingScrollPixels < 1f) continue

            scrollState.scrollBy(pendingScrollPixels)
            pendingScrollPixels = 0f
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = CzSpacing.lg)
                    .padding(
                        top = CzSpacing.md + fullScreenTopReserve,
                        bottom = CzSpacing.lg,
                    ),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
            ) {
                TransposeBar(
                    keyName = keyName,
                    transposeOffset = transposeOffset,
                    onTranspose = onTranspose,
                )

                if (uniqueChords.isEmpty()) {
                    Text(
                        text = rawChords.ifBlank { stringResource(R.string.songbook_no_chord_sheet) },
                        fontFamily = FontFamily.Monospace,
                        fontSize = textSize.sp,
                        color = if (rawChords.isBlank()) colors.textSecondary else colors.textPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(CzRadius.md))
                            .background(colors.surface)
                            .padding(CzSpacing.md),
                    )
                } else {
                    ChordStrip(
                        chords = uniqueChords,
                        transposeOffset = transposeOffset,
                        originalKey = keyName,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                        lines.forEach { line ->
                            RenderedChordLine(
                                line = line,
                                textSize = textSize,
                                transposeOffset = transposeOffset,
                                originalKey = keyName,
                            )
                        }
                    }
                }
            }

            AutoScrollBar(
                isAutoScrolling = isAutoScrolling,
                scrollSpeed = scrollSpeed,
                onToggle = { isAutoScrolling = !isAutoScrolling },
                onSpeedChange = onSpeedChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CzSpacing.base)
                    .padding(bottom = CzSpacing.sm + bottomInset),
                showFullScreenButton = !isFullScreen && onEnterFullScreen != null,
                onEnterFullScreen = {
                    isAutoScrolling = false
                    onEnterFullScreen?.invoke()
                },
            )
        }

        if (isFullScreen && onExitFullScreen != null) {
            IconButton(
                onClick = {
                    isAutoScrolling = false
                    onExitFullScreen()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = topInset + CzSpacing.sm, end = CzSpacing.lg)
                    .size(46.dp)
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(colors.card)
                    .border(BorderStroke(1.dp, colors.divider), CircleShape),
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.songbook_exit_full_screen_performance),
                    tint = colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun FullScreenSystemBarsEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        if (!enabled) {
            onDispose {}
        } else {
            val window = view.context.findActivity()?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            val previousBehavior = controller?.systemBarsBehavior
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                controller?.show(WindowInsetsCompat.Type.systemBars())
                if (previousBehavior != null) {
                    controller.systemBarsBehavior = previousBehavior
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun TransposeBar(
    keyName: String,
    transposeOffset: Int,
    onTranspose: (Int) -> Unit,
) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.lg))
            .background(colors.card)
            .border(BorderStroke(1.dp, colors.divider), RoundedCornerShape(CzRadius.lg))
            .padding(horizontal = CzSpacing.base, vertical = CzSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                stringResource(R.string.songbook_key).uppercase(),
                style = CzTypeScale.caption2.copy(fontWeight = FontWeight.Bold),
                color = colors.textTertiary,
            )
            Text(
                keyName,
                fontFamily = FontFamily.Monospace,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accent,
            )
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(30.dp)
                .background(colors.divider),
        )
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(CzRadius.sm))
                .background(colors.background)
                .border(BorderStroke(1.dp, colors.divider), RoundedCornerShape(CzRadius.sm)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onTranspose(-1) }) {
                Icon(Icons.Rounded.Remove, contentDescription = null, tint = colors.accent)
            }
            Text(
                transposeOffset.formatTranspose(),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (transposeOffset == 0) colors.textSecondary else colors.accent,
                modifier = Modifier.padding(horizontal = CzSpacing.sm),
            )
            IconButton(onClick = { onTranspose(1) }) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.accent)
            }
        }
    }
}

@Composable
private fun ChordStrip(
    chords: List<String>,
    transposeOffset: Int,
    originalKey: String,
) {
    val colors = MaterialTheme.czColors
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        Text(
            stringResource(R.string.songbook_chords_in_song).uppercase(),
            style = CzTypeScale.caption2.copy(fontWeight = FontWeight.Bold),
            color = colors.textTertiary,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            chords.forEach { chord ->
                Text(
                    text = transposeChordSymbol(chord, transposeOffset, originalKey),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(CzRadius.sm))
                        .background(colors.accent.copy(alpha = 0.12f))
                        .border(BorderStroke(1.dp, colors.accent.copy(alpha = 0.18f)), RoundedCornerShape(CzRadius.sm))
                        .padding(horizontal = CzSpacing.md, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun AutoScrollBar(
    isAutoScrolling: Boolean,
    scrollSpeed: Float,
    onToggle: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    showFullScreenButton: Boolean = false,
    onEnterFullScreen: () -> Unit = {},
) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = modifier
            .shadow(16.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(colors.card)
            .border(BorderStroke(1.dp, colors.divider), CircleShape)
            .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (isAutoScrolling) colors.accent else colors.surface)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isAutoScrolling) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(if (isAutoScrolling) R.string.songbook_stop_auto_scroll else R.string.songbook_start_auto_scroll),
                tint = if (isAutoScrolling) Color.White else colors.accent,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.songbook_auto_scroll),
                    style = CzTypeScale.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    String.format(Locale.US, "%.1fx", scrollSpeed),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent,
                )
            }
            CompactAutoScrollSlider(
                value = scrollSpeed,
                onValueChange = onSpeedChange,
            )
        }
        if (showFullScreenButton) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(BorderStroke(1.dp, colors.divider), CircleShape)
                    .clickable(onClick = onEnterFullScreen),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Fullscreen,
                    contentDescription = stringResource(R.string.songbook_enter_full_screen_performance),
                    tint = colors.accent,
                )
            }
        }
    }
}

@Composable
private fun CompactAutoScrollSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0.2f..3f,
    step: Float = 0.1f,
) {
    val colors = MaterialTheme.czColors
    val label = stringResource(R.string.songbook_auto_scroll)
    var widthPx by remember { mutableFloatStateOf(0f) }

    fun valueAt(x: Float): Float {
        if (widthPx <= 0f) return value
        val fraction = (x / widthPx).coerceIn(0f, 1f)
        val raw = valueRange.start + (valueRange.endInclusive - valueRange.start) * fraction
        val stepped = valueRange.start + (((raw - valueRange.start) / step).roundToInt() * step)
        return stepped.coerceIn(valueRange.start, valueRange.endInclusive)
    }

    val inputModifier = Modifier
        .pointerInput(widthPx, valueRange, step) {
            detectTapGestures { offset -> onValueChange(valueAt(offset.x)) }
        }
        .pointerInput(widthPx, valueRange, step) {
            detectDragGestures { change, _ ->
                change.consume()
                onValueChange(valueAt(change.position.x))
            }
        }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .semantics { contentDescription = label }
            .then(inputModifier),
    ) {
        val thumbRadius = 7.dp.toPx()
        val trackStart = thumbRadius
        val trackEnd = (size.width - thumbRadius).coerceAtLeast(trackStart)
        val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)
        val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        val centerY = size.height / 2f
        val trackHeight = 6.dp.toPx()
        val trackRadius = trackHeight / 2f
        val thumbX = trackStart + trackWidth * fraction

        drawRoundRect(
            color = colors.textPrimary.copy(alpha = 0.14f),
            topLeft = Offset(trackStart, centerY - trackRadius),
            size = Size(trackWidth, trackHeight),
            cornerRadius = CornerRadius(trackRadius, trackRadius),
        )
        drawRoundRect(
            color = colors.accent.copy(alpha = 0.92f),
            topLeft = Offset(trackStart, centerY - trackRadius),
            size = Size((thumbX - trackStart).coerceIn(0f, trackWidth), trackHeight),
            cornerRadius = CornerRadius(trackRadius, trackRadius),
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.16f),
            radius = thumbRadius + 1.dp.toPx(),
            center = Offset(thumbX, centerY + 1.dp.toPx()),
        )
        drawCircle(
            color = colors.textPrimary,
            radius = thumbRadius,
            center = Offset(thumbX, centerY),
        )
    }
}

@Composable
private fun RenderedChordLine(
    line: ChordLine,
    textSize: Float,
    transposeOffset: Int,
    originalKey: String,
) {
    val colors = MaterialTheme.czColors
    if (line.isSectionHeader) {
        Text(
            text = line.text,
            fontSize = (textSize - 1).coerceAtLeast(14f).sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.accent,
            modifier = Modifier.padding(top = CzSpacing.xs),
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val chordLine = renderedChordLine(line, transposeOffset, originalKey)
            if (chordLine.isNotBlank()) {
                Text(
                    text = chordLine,
                    fontFamily = FontFamily.Monospace,
                    fontSize = (textSize - 2).coerceAtLeast(13f).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent,
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

private fun renderedChordLine(line: ChordLine, transposeOffset: Int, originalKey: String): String {
    val sorted = line.chords.sortedWith(compareBy({ it.position }, { it.chord }))
    val output = StringBuilder()
    var cursor = 0
    sorted.forEach { chord ->
        val target = chord.position.coerceAtLeast(cursor)
        while (cursor < target) {
            output.append(' ')
            cursor += 1
        }
        val display = transposeChordSymbol(chord.chord, transposeOffset, originalKey)
        output.append(display)
        cursor += display.length
    }
    return output.toString().trimEnd()
}

private fun Int.formatTranspose(): String = when {
    this > 0 -> "+$this"
    else -> toString()
}

private fun transposeChordSymbol(symbol: String, semitones: Int, originalKey: String): String {
    return ChordSymbolParser.transpose(symbol, semitones, originalKey)
}
