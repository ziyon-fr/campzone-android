package fr.ziyon.campzone.ui.songbook

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SlowMotionVideo
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
        onToggleFavorite = { songId -> viewModel.toggleFavorite(songId, campingId, authenticatedUser.uid) },
        onToggleAudio = viewModel::toggleAudio,
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
    onToggleFavorite: (String) -> Unit,
    onToggleAudio: (Song) -> Unit,
    onRetry: () -> Unit,
    onClearMessage: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

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
                title = { Text(stringResource(R.string.songbook_title), style = CzTypeScale.headline, color = colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = colors.textPrimary)
                    }
                },
                actions = {
                    if (canManage) {
                        IconButton(onClick = onAddSong) {
                            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.songbook_add_song), tint = colors.ember)
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
                                text = "Add Song",
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
                            campingTitle = uiState.campingTitle,
                            songCount = uiState.songs.size,
                            searchText = searchText,
                            favoritesOnly = favoritesOnly,
                            onSearchChange = onSearchChange,
                            onToggleFavoritesOnly = onToggleFavoritesOnly,
                        )
                    }

                    if (pinnedSong != null) {
                        item {
                            FeaturedSongCard(
                                song = pinnedSong,
                                isFavorite = pinnedSong.isFavoritedBy(userId.orEmpty()),
                                isPlaying = playingSongId == pinnedSong.id,
                                canManage = canManage,
                                onOpen = { onOpenSong(pinnedSong.id) },
                                onEdit = { onEditSong(pinnedSong) },
                                onToggleFavorite = { onToggleFavorite(pinnedSong.id) },
                                onToggleAudio = { onToggleAudio(pinnedSong) },
                                onWatch = { openUrl(context, pinnedSong.youtubeLink) },
                            )
                        }
                    }

                    if (visibleSongs.isEmpty()) {
                        item {
                            EmptySongFilterCard(
                                message = if (favoritesOnly) "No favorite songs yet." else "No songs match these filters.",
                            )
                        }
                    } else {
                        item {
                            SongListCard(
                                songs = visibleSongs,
                                userId = userId,
                                playingSongId = playingSongId,
                                canManage = canManage,
                                onOpenSong = onOpenSong,
                                onToggleFavorite = onToggleFavorite,
                                onToggleAudio = onToggleAudio,
                                onWatch = { song -> openUrl(context, song.youtubeLink) },
                                onEdit = onEditSong,
                                onMoveSong = onMoveSong,
                                onPinSong = onPinSong,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongbookHeader(
    campingTitle: String,
    songCount: Int,
    searchText: String,
    favoritesOnly: Boolean,
    onSearchChange: (String) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = campingTitle,
                    style = CzTypeScale.subhead.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$songCount ${if (songCount == 1) "song" else "songs"}",
                    style = CzTypeScale.caption,
                    color = colors.textSecondary,
                )
            }
            TextButton(onClick = onToggleFavoritesOnly) {
                Icon(
                    imageVector = if (favoritesOnly) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    tint = if (favoritesOnly) colors.error else colors.textSecondary,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    "Favorites",
                    color = if (favoritesOnly) colors.error else colors.textSecondary,
                    style = CzTypeScale.caption.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }

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
                cursorColor = colors.ember,
            ),
        )
    }
}

@Composable
private fun FeaturedSongCard(
    song: Song,
    isFavorite: Boolean,
    isPlaying: Boolean,
    canManage: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleAudio: () -> Unit,
    onWatch: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.xl))
            .background(Brush.linearGradient(listOf(colors.amber.copy(alpha = 0.9f), colors.ember)))
            .padding(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Mic, contentDescription = null, tint = Color.White.copy(alpha = 0.86f), modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.songbook_theme_song), style = CzTypeScale.caption.copy(fontWeight = FontWeight.Bold), color = Color.White.copy(alpha = 0.86f))
            Spacer(Modifier.weight(1f))
            if (isPlaying) {
                Icon(Icons.Rounded.SlowMotionVideo, contentDescription = null, tint = Color.White)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            Text(song.title, style = CzTypeScale.title2.copy(fontWeight = FontWeight.Bold), color = Color.White)
            if (song.artist.isNotBlank()) {
                Text(song.artist, style = CzTypeScale.subhead, color = Color.White.copy(alpha = 0.82f))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            if (song.audio != null) {
                IconAction(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    onClick = onToggleAudio,
                )
            }
            if (song.youtubeLink.isNotBlank()) {
                IconAction(Icons.Rounded.SlowMotionVideo, "Watch on YouTube", Color.White, onWatch)
            }
            IconAction(
                icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                tint = Color.White,
                onClick = onToggleFavorite,
            )
            Spacer(Modifier.weight(1f))
            if (canManage) {
                IconAction(Icons.Rounded.Edit, "Edit", Color.White, onEdit)
            }
        }
    }
}

@Composable
private fun SongListCard(
    songs: List<Song>,
    userId: String?,
    playingSongId: String?,
    canManage: Boolean,
    onOpenSong: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleAudio: (Song) -> Unit,
    onWatch: (Song) -> Unit,
    onEdit: (Song) -> Unit,
    onMoveSong: (String, SongMoveDirection) -> Unit,
    onPinSong: (String) -> Unit,
) {
    val colors = MaterialTheme.czColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.lg))
            .background(colors.surface),
    ) {
        songs.forEachIndexed { index, song ->
            SongRow(
                song = song,
                isFavorite = userId?.let(song::isFavoritedBy) == true,
                isPlaying = playingSongId == song.id,
                canManage = canManage,
                onOpen = { onOpenSong(song.id) },
                onFavorite = { onToggleFavorite(song.id) },
                onAudio = { onToggleAudio(song) },
                onWatch = { onWatch(song) },
                onEdit = { onEdit(song) },
                onMoveUp = { onMoveSong(song.id, SongMoveDirection.Up) },
                onMoveDown = { onMoveSong(song.id, SongMoveDirection.Down) },
                onPin = { onPinSong(song.id) },
            )
            if (index != songs.lastIndex) {
                HorizontalDivider(
                    color = colors.divider,
                    modifier = Modifier.padding(start = 68.dp),
                )
            }
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    isFavorite: Boolean,
    isPlaying: Boolean,
    canManage: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onAudio: () -> Unit,
    onWatch: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onPin: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    var showMenu by remember { mutableStateOf(false) }
    val reorderThreshold = with(LocalDensity.current) { 52.dp.toPx() }
    val reorderModifier = if (canManage) {
        Modifier.pointerInput(song.id) {
            var dragOffset = 0f
            detectVerticalDragGestures(
                onDragEnd = { dragOffset = 0f },
                onDragCancel = { dragOffset = 0f },
                onVerticalDrag = { _, dragAmount ->
                    dragOffset += dragAmount
                    when {
                        dragOffset >= reorderThreshold -> {
                            onMoveDown()
                            dragOffset = 0f
                        }
                        dragOffset <= -reorderThreshold -> {
                            onMoveUp()
                            dragOffset = 0f
                        }
                    }
                },
            )
        }
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(reorderModifier)
            .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (song.isPinnedTheme) colors.amber.copy(alpha = 0.2f) else colors.ember.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when {
                        isPlaying -> Icons.Rounded.SlowMotionVideo
                        song.isPinnedTheme -> Icons.Rounded.Mic
                        else -> Icons.Rounded.MusicNote
                    },
                    contentDescription = null,
                    tint = if (song.isPinnedTheme) colors.amber else colors.ember,
                )
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = song.title,
                    style = CzTypeScale.subhead.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(song.subtitleText(), style = CzTypeScale.caption, color = colors.textSecondary, maxLines = 1)
            }

            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.textSecondary.copy(alpha = 0.55f))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (song.audio != null) {
                TextIconAction(if (isPlaying) "Pause" else "Play", if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, onAudio)
            }
            if (song.youtubeLink.isNotBlank()) {
                TextIconAction("Watch", Icons.Rounded.SlowMotionVideo, onWatch)
            }
            IconAction(
                icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                tint = if (isFavorite) colors.error else colors.textSecondary,
                onClick = onFavorite,
            )
            Spacer(Modifier.weight(1f))
            if (canManage) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.songbook_options_cd), tint = colors.textSecondary)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
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
                    }
                }
            }
        }
    }
}

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
private fun TextIconAction(text: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = MaterialTheme.czColors
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null, tint = colors.ember, modifier = Modifier.size(16.dp))
        Text(text, style = CzTypeScale.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.ember)
    }
}

@Composable
private fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}

private fun Song.subtitleText(): String = when {
    isPinnedTheme -> "Theme song"
    audio != null -> "Audio available"
    chordSheet.lines.isNotEmpty() || chords.isNotBlank() -> "Lyrics and chords"
    else -> "Lyrics only"
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
            onToggleFavorite = {},
            onToggleAudio = {},
            onRetry = {},
            onClearMessage = {},
            onClearError = {},
        )
    }
}
