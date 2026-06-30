package fr.ziyon.campzone.ui.songbook

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTypeScale
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.Song
import fr.ziyon.campzone.data.model.SongAudio
import fr.ziyon.campzone.data.songbook.FakeSongbookService

@Composable
fun SongbookRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenSong: (String) -> Unit,
    onOpenEditor: (String?) -> Unit,
    viewModel: SongbookViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val canManage by viewModel.canManageSongbook.collectAsState()
    val searchText by viewModel.searchText.collectAsState()
    val favoritesOnly by viewModel.showsFavoritesOnly.collectAsState()
    val playingSongId by viewModel.playingSongId.collectAsState()
    val playingAudioId by viewModel.playingAudioId.collectAsState()
    val isAudioPlaying by viewModel.isAudioPlaying.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()

    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.loadIfNeeded(campingId, authenticatedUser)
    }

    SongbookScreen(
        campingId = campingId,
        userId = authenticatedUser.uid,
        uiState = uiState,
        canManage = canManage,
        searchText = searchText,
        favoritesOnly = favoritesOnly,
        playingSongId = playingSongId,
        playingAudioId = playingAudioId,
        isAudioPlaying = isAudioPlaying,
        visibleSongs = viewModel.visibleSongs(campingId, authenticatedUser.uid),
        pinnedSong = viewModel.pinnedSong(campingId),
        operationError = operationError,
        operationMessage = operationMessage,
        onBack = onBack,
        onSearchChange = viewModel::updateSearch,
        onToggleFavoritesOnly = viewModel::toggleFavoritesOnly,
        onOpenSong = onOpenSong,
        onAddSong = {
            viewModel.prepareNewSong(campingId)
            onOpenEditor(null)
        },
        onEditSong = { song ->
            viewModel.prepareEditingSong(song)
            onOpenEditor(song.id)
        },
        onMoveSong = { songId, direction -> viewModel.moveSong(songId, direction, campingId) },
        onPinSong = { songId -> viewModel.setPinnedTheme(songId, campingId) },
        onDeleteSong = { song -> viewModel.deleteSong(song.id, campingId) },
        onToggleFavorite = { songId -> viewModel.toggleFavorite(songId, campingId, authenticatedUser.uid) },
        onToggleAudio = viewModel::toggleAudio,
        onPlayTrack = viewModel::playAudio,
        onRetry = { viewModel.load(campingId, authenticatedUser) },
        onClearMessage = viewModel::clearOperationMessage,
        onClearError = viewModel::clearOperationError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongbookScreen(
    campingId: String,
    userId: String?,
    uiState: SongbookUiState,
    canManage: Boolean,
    searchText: String,
    favoritesOnly: Boolean,
    playingSongId: String?,
    playingAudioId: String?,
    isAudioPlaying: Boolean,
    visibleSongs: List<Song>,
    pinnedSong: Song?,
    operationError: String?,
    operationMessage: String?,
    onBack: () -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onOpenSong: (String) -> Unit,
    onAddSong: () -> Unit,
    onEditSong: (Song) -> Unit,
    onMoveSong: (String, SongMoveDirection) -> Unit,
    onPinSong: (String) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleAudio: (Song) -> Unit,
    onPlayTrack: (Song, SongAudio) -> Unit,
    onRetry: () -> Unit,
    onClearMessage: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var songPendingDeletion by remember { mutableStateOf<Song?>(null) }
    val loadedTitle = when (uiState) {
        is SongbookUiState.Loaded -> uiState.campingTitle
        is SongbookUiState.Empty -> uiState.campingTitle
        else -> stringResource(R.string.songbook_title)
    }
    val songCount = when (uiState) {
        is SongbookUiState.Loaded -> uiState.songs.size
        else -> visibleSongs.size
    }

    LaunchedEffect(operationMessage) {
        if (operationMessage != null) {
            snackbarHostState.showSnackbar(operationMessage, duration = SnackbarDuration.Short)
            onClearMessage()
        }
    }
    LaunchedEffect(operationError) {
        if (operationError != null) {
            snackbarHostState.showSnackbar(operationError, duration = SnackbarDuration.Long)
            onClearError()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            loadedTitle,
                            style = CzTypeScale.headline,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            pluralStringResource(R.plurals.songbook_song_count, songCount, songCount),
                            style = CzTypeScale.caption,
                            color = colors.textTertiary,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = colors.textPrimary)
                    }
                },
                actions = {
                    if (canManage) {
                        IconButton(onClick = onAddSong) {
                            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.songbook_add_song), tint = colors.accent)
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
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (uiState) {
                SongbookUiState.Loading -> CzLoadingView(
                    modifier = Modifier.fillMaxSize(),
                    message = stringResource(R.string.songbook_loading),
                )

                is SongbookUiState.Error -> CzErrorState(
                    title = stringResource(R.string.songbook_error_title),
                    message = uiState.message,
                    onRetry = onRetry,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(CzSpacing.xl),
                )

                is SongbookUiState.Empty -> CzEmptyState(
                    title = stringResource(R.string.songbook_empty_title),
                    message = stringResource(R.string.songbook_empty_message),
                    action = if (canManage) {
                        {
                            CzButton(
                                text = stringResource(R.string.songbook_add_song),
                                onClick = onAddSong,
                                variant = CzButtonVariant.Primary,
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(CzSpacing.xl),
                )

                is SongbookUiState.Loaded -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = CzSpacing.lg,
                        end = CzSpacing.lg,
                        top = CzSpacing.md,
                        bottom = CzSpacing.xxxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        SongbookHeader(
                            searchText = searchText,
                            onSearchChange = onSearchChange,
                        )
                    }

                    if (pinnedSong != null) {
                        item {
                            FeaturedSongCard(
                                song = pinnedSong,
                                isFavorite = pinnedSong.isFavoritedBy(userId.orEmpty()),
                                isPlaying = playingSongId == pinnedSong.id && isAudioPlaying,
                                onPrimary = {
                                    if (pinnedSong.audio != null) onToggleAudio(pinnedSong)
                                    else onOpenSong(pinnedSong.id)
                                },
                                onToggleFavorite = { onToggleFavorite(pinnedSong.id) },
                            )
                        }
                    }

                    item {
                        SongListSection(
                            songs = visibleSongs,
                            userId = userId,
                            playingSongId = playingSongId,
                            playingAudioId = playingAudioId,
                            isAudioPlaying = isAudioPlaying,
                            canManage = canManage,
                            onOpenSong = onOpenSong,
                            onToggleFavorite = onToggleFavorite,
                            onToggleAudio = onToggleAudio,
                            onPlayTrack = onPlayTrack,
                            onWatch = { song -> openUrl(context, song.youtubeLink) },
                            onEdit = onEditSong,
                            onMoveSong = onMoveSong,
                            onPinSong = onPinSong,
                            onDelete = { song -> songPendingDeletion = song },
                        )
                    }
                }
            }
        }
    }

    songPendingDeletion?.let { song ->
        AlertDialog(
            onDismissRequest = { songPendingDeletion = null },
            title = { Text(stringResource(R.string.songbook_delete_title)) },
            text = { Text(stringResource(R.string.songbook_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        songPendingDeletion = null
                        onDeleteSong(song)
                    },
                ) { Text(stringResource(R.string.common_delete), color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { songPendingDeletion = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun SongbookHeader(
    searchText: String,
    onSearchChange: (String) -> Unit,
) {
    val colors = MaterialTheme.czColors
    TextField(
        value = searchText,
        onValueChange = onSearchChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.songbook_search_placeholder), color = colors.textSecondary) },
        textStyle = CzTypeScale.body.copy(color = colors.textPrimary),
        shape = RoundedCornerShape(CzRadius.md),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = colors.accent,
        ),
    )
}

@Composable
private fun FeaturedSongCard(
    song: Song,
    isFavorite: Boolean,
    isPlaying: Boolean,
    onPrimary: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val hasAudio = song.audio != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.xxl))
            .background(
                Brush.linearGradient(
                    listOf(colors.espresso, colors.espressoDeep),
                ),
            )
            .border(BorderStroke(1.dp, Color.Black.copy(alpha = 0.25f)), RoundedCornerShape(CzRadius.xxl))
            .padding(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.base),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Mic, contentDescription = null, tint = colors.gold, modifier = Modifier.size(16.dp))
            Text(
                stringResource(R.string.songbook_theme_song).uppercase(),
                style = CzTypeScale.caption2.copy(fontWeight = FontWeight.Bold),
                color = colors.gold,
            )
        }

        Text(
            text = song.artist.ifBlank { song.title },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = CzSpacing.xs),
            style = CzTypeScale.title2.copy(fontWeight = FontWeight.Medium, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif),
            color = colors.cream,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(colors.cream.copy(alpha = 0.10f))
                    .border(BorderStroke(1.dp, colors.cream.copy(alpha = 0.16f)), CircleShape)
                    .clickable(onClick = onPrimary)
                    .padding(vertical = CzSpacing.sm + 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    if (hasAudio) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = colors.cream,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = when {
                            hasAudio && isPlaying -> stringResource(R.string.songbook_pause)
                            hasAudio -> stringResource(R.string.songbook_listen)
                            else -> stringResource(R.string.songbook_open_lyrics_chords)
                        },
                        style = CzTypeScale.subhead.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.cream,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.cream.copy(alpha = 0.10f))
                    .border(BorderStroke(1.dp, colors.cream.copy(alpha = 0.16f)), CircleShape)
                    .clickable(onClick = onToggleFavorite),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isFavorite) stringResource(R.string.songbook_remove_favorite) else stringResource(R.string.songbook_add_favorite),
                    tint = if (isFavorite) colors.accent else colors.cream,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun SongListSection(
    songs: List<Song>,
    userId: String?,
    playingSongId: String?,
    playingAudioId: String?,
    isAudioPlaying: Boolean,
    canManage: Boolean,
    onOpenSong: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleAudio: (Song) -> Unit,
    onPlayTrack: (Song, SongAudio) -> Unit,
    onWatch: (Song) -> Unit,
    onEdit: (Song) -> Unit,
    onMoveSong: (String, SongMoveDirection) -> Unit,
    onPinSong: (String) -> Unit,
    onDelete: (Song) -> Unit,
) {
    val colors = MaterialTheme.czColors
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        Text(
            text = stringResource(R.string.songbook_all_songs).uppercase(),
            style = CzTypeScale.caption2.copy(fontWeight = FontWeight.Bold),
            color = colors.textTertiary,
            modifier = Modifier.padding(horizontal = CzSpacing.xs),
        )

        if (songs.isEmpty()) {
            EmptySongFilterCard(message = stringResource(R.string.songbook_no_search_results))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CzRadius.xxl))
                    .background(colors.card)
                    .border(BorderStroke(1.dp, colors.divider), RoundedCornerShape(CzRadius.xxl)),
            ) {
                songs.forEachIndexed { index, song ->
                    SongRow(
                        song = song,
                        index = index + 1,
                        isFavorite = userId?.let(song::isFavoritedBy) == true,
                        isPlaying = playingSongId == song.id && isAudioPlaying,
                        playingAudioId = playingAudioId.takeIf { playingSongId == song.id },
                        canManage = canManage,
                        onOpen = { onOpenSong(song.id) },
                        onFavorite = { onToggleFavorite(song.id) },
                        onAudio = { onToggleAudio(song) },
                        onPlayTrack = { track -> onPlayTrack(song, track) },
                        onWatch = { onWatch(song) },
                        onEdit = { onEdit(song) },
                        onMoveUp = { onMoveSong(song.id, SongMoveDirection.Up) },
                        onMoveDown = { onMoveSong(song.id, SongMoveDirection.Down) },
                        onPin = { onPinSong(song.id) },
                        onDelete = { onDelete(song) },
                    )
                    if (index != songs.lastIndex) {
                        HorizontalDivider(
                            color = colors.divider,
                            modifier = Modifier.padding(start = CzSpacing.base + 20.dp + CzSpacing.md + 48.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    index: Int,
    isFavorite: Boolean,
    isPlaying: Boolean,
    playingAudioId: String?,
    canManage: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onAudio: () -> Unit,
    onPlayTrack: (SongAudio) -> Unit,
    onWatch: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CzSpacing.base, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpen),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = index.toString(),
                style = CzTypeScale.subhead.copy(fontWeight = FontWeight.Medium),
                color = colors.textTertiary,
                modifier = Modifier.size(width = 20.dp, height = 24.dp),
            )

            SongArtwork(song = song, size = 48.dp, isPlaying = isPlaying)

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = song.title,
                    style = CzTypeScale.callout.copy(fontWeight = FontWeight.Medium),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    if (song.audio != null) {
                        Box(
                            Modifier
                                .size(5.dp)
                                .background(colors.accent, CircleShape),
                        )
                    }
                    Text(
                        text = song.subtitleText(),
                        style = CzTypeScale.caption,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.songbook_options_cd), tint = colors.textTertiary)
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (song.audio != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (isPlaying) R.string.songbook_pause else R.string.songbook_play)) },
                        leadingIcon = { Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null) },
                        onClick = {
                            showMenu = false
                            onAudio()
                        },
                    )
                }
                if (song.youtubeLink.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.songbook_watch_youtube)) },
                        leadingIcon = { Icon(Icons.Rounded.SlowMotionVideo, null) },
                        onClick = {
                            showMenu = false
                            onWatch()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(if (isFavorite) R.string.songbook_remove_favorite else R.string.songbook_add_favorite)) },
                    leadingIcon = { Icon(if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null) },
                    onClick = {
                        showMenu = false
                        onFavorite()
                    },
                )
                if (song.hasAlternativeAudio) {
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.songbook_voice_kits), fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                        enabled = false,
                        onClick = {},
                    )
                    song.orderedAudioFiles.forEach { track ->
                        val selected = playingAudioId == track.id
                        DropdownMenuItem(
                            text = {
                                Text(track.displayName.ifBlank { stringResource(track.trackType.displayNameRes) })
                            },
                            leadingIcon = {
                                Icon(
                                    if (selected) Icons.Rounded.Check else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onPlayTrack(track)
                            },
                        )
                    }
                }
                if (canManage) {
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(stringResource(R.string.common_edit)) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = {
                        showMenu = false
                        onEdit()
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.songbook_move_up)) }, leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null) }, onClick = {
                        showMenu = false
                        onMoveUp()
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.songbook_move_down)) }, leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null) }, onClick = {
                        showMenu = false
                        onMoveDown()
                    })
                    if (!song.isPinnedTheme) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.songbook_set_theme)) }, leadingIcon = { Icon(Icons.Rounded.Mic, null) }, onClick = {
                            showMenu = false
                            onPin()
                        })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_delete), color = colors.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = colors.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun SongArtwork(
    song: Song,
    size: androidx.compose.ui.unit.Dp,
    isPlaying: Boolean,
) {
    val colors = MaterialTheme.czColors
    val isDarkMode = isSystemInDarkTheme()
    val palette = artworkPalettes[((song.orderIndex % artworkPalettes.size) + artworkPalettes.size) % artworkPalettes.size]
    val shape = RoundedCornerShape(size * 0.21f)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)

            .border(BorderStroke(1.dp, Brush.linearGradient(palette)), shape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                    ),
                ),
        )
        when {
            isPlaying -> SongWaveBars(
                barColor = if (isDarkMode) Color.White.copy(alpha = 0.9f) else colors.accent.copy(alpha = 0.9f),
            )
            song.isPinnedTheme -> Icon(
                Icons.Rounded.Mic,
                contentDescription = null,
                tint = if (isDarkMode) Color.White.copy(alpha = 0.92f) else colors.accent.copy(alpha = 0.92f),
                modifier = Modifier.size(size * 0.34f),
            )
        }
    }
}

@Composable
private fun SongWaveBars(
    modifier: Modifier = Modifier,
    barColor: Color = Color.White.copy(alpha = 0.9f),
) {
    val transition = rememberInfiniteTransition(label = "song-wave")
    Row(
        modifier = modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val height by transition.animateFloat(
                initialValue = 5f,
                targetValue = 16f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500, delayMillis = index * 160),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar-$index",
            )
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = height.dp)
                    .clip(CircleShape)
                    .background(barColor),
            )
        }
    }
}

private val artworkPalettes = listOf(
    listOf(Color(0xFF1E0533), Color(0xFF5B21B6), Color(0xFF8B5CF6)),
    listOf(Color(0xFF0A0E2E), Color(0xFF1E40AF), Color(0xFF3B82F6)),
    listOf(Color(0xFF071A0F), Color(0xFF166534), Color(0xFF22C55E)),
    listOf(Color(0xFF1A0508), Color(0xFF9B1C1C), Color(0xFFEF4444)),
)

@Composable
private fun EmptySongFilterCard(message: String) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.md))
            .background(colors.surface)
            .padding(CzSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        Text(message, style = CzTypeScale.caption, color = colors.textSecondary)
    }
}

@Composable
internal fun Song.subtitleText(): String = when {
    isPinnedTheme -> stringResource(R.string.songbook_theme_song)
    audio != null -> stringResource(R.string.songbook_audio_available)
    chordSheet.lines.isNotEmpty() || chords.isNotBlank() -> stringResource(R.string.songbook_lyrics_and_chords_available)
    else -> stringResource(R.string.songbook_lyrics_only_available)
}

internal fun openUrl(context: android.content.Context, url: String) {
    val trimmed = url.trim()
    if (trimmed.isBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)))
    }.recoverCatching { error ->
        if (error is ActivityNotFoundException) throw error else throw error
    }
}

@Preview(showBackground = true)
@Composable
private fun SongbookScreenPreview() {
    val songs = FakeSongbookService.previewSongs()
    CampzoneTheme {
        SongbookScreen(
            campingId = "summer-camp-2026",
            userId = "preview-user",
            uiState = SongbookUiState.Loaded(songs, "Summer Camp 2026"),
            canManage = true,
            searchText = "",
            favoritesOnly = false,
            playingSongId = null,
            playingAudioId = null,
            isAudioPlaying = false,
            visibleSongs = songs,
            pinnedSong = songs.first(),
            operationError = null,
            operationMessage = null,
            onBack = {},
            onSearchChange = {},
            onToggleFavoritesOnly = {},
            onOpenSong = {},
            onAddSong = {},
            onEditSong = {},
            onMoveSong = { _, _ -> },
            onPinSong = {},
            onDeleteSong = {},
            onToggleFavorite = {},
            onToggleAudio = {},
            onPlayTrack = { _, _ -> },
            onRetry = {},
            onClearMessage = {},
            onClearError = {},
        )
    }
}
