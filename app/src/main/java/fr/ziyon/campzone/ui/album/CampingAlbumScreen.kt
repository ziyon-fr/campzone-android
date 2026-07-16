package fr.ziyon.campzone.ui.album

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.AlbumSettings
import fr.ziyon.campzone.data.model.MediaItem
import fr.ziyon.campzone.data.model.MediaKind
import java.io.File
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CampingAlbumRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    canViewAlbum: Boolean,
    canManageAlbum: Boolean,
    canManageAlbumSettings: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()

    LaunchedEffect(campingId, canViewAlbum) {
        if (canViewAlbum) viewModel.loadIfNeeded(campingId)
    }

    CampingAlbumScreen(
        campingId = campingId,
        authenticatedUser = authenticatedUser,
        uiState = uiState,
        canViewAlbum = canViewAlbum,
        canManageAlbum = canManageAlbum,
        canManageAlbumSettings = canManageAlbumSettings,
        isUploading = isUploading,
        isRefreshing = isRefreshing,
        operationMessage = operationMessage,
        onBack = onBack,
        onRetry = { viewModel.load(campingId) },
        onRefresh = { viewModel.refresh(campingId) },
        onUploadFile = viewModel::uploadMediaFile,
        onAddExternalVideo = viewModel::addExternalVideo,
        onDelete = viewModel::deleteMedia,
        onUpdateCaption = viewModel::updateCaption,
        onSetRoleAllowed = viewModel::setRoleAllowed,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampingAlbumScreen(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    uiState: AlbumUiState,
    canViewAlbum: Boolean,
    canManageAlbum: Boolean,
    canManageAlbumSettings: Boolean,
    isUploading: Boolean,
    isRefreshing: Boolean,
    operationMessage: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onUploadFile: (String, MediaKind, File, String, String, String, AuthenticatedUser) -> Unit,
    onAddExternalVideo: (String, String, String?, String, AuthenticatedUser) -> Unit,
    onDelete: (String, String) -> Unit,
    onUpdateCaption: (String, String, String) -> Unit,
    onSetRoleAllowed: (String, UserRole, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val context = LocalContext.current
    val uploadScope = rememberCoroutineScope()
    var selectedItem by remember { mutableStateOf<MediaItem?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showExternalVideoDialog by remember { mutableStateOf(false) }
    var uploadCaption by remember { mutableStateOf("") }
    val settings = (uiState as? AlbumUiState.Loaded)?.settings ?: AlbumSettings()
    val canUpload = canManageAlbum || (canViewAlbum && settings.allows(authenticatedUser.role))
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        val kind = if (mimeType.startsWith("video/")) MediaKind.Video else MediaKind.Photo
        val extension = extensionFor(uri, mimeType)
        val caption = uploadCaption
        uploadCaption = ""
        uploadScope.launch {
            val file = cacheAlbumUpload(context, uri, extension) ?: return@launch
            onUploadFile(campingId, kind, file, mimeType.ifBlank { kind.defaultMimeType }, extension, caption, authenticatedUser)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.album_title), color = colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (canManageAlbumSettings) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.album_permissions))
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
        floatingActionButton = {
            if (canUpload) {
                Box {
                    FloatingActionButton(
                        onClick = { showAddMenu = true },
                        containerColor = colors.ember,
                        contentColor = Color.White,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.album_add_media))
                    }
                    DropdownMenu(
                        expanded = showAddMenu,
                        onDismissRequest = { showAddMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.album_upload_from_library)) },
                            leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, contentDescription = null) },
                            onClick = {
                                showAddMenu = false
                                mediaPicker.launch(arrayOf("image/*", "video/*"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.album_add_external_video)) },
                            leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                            onClick = {
                                showAddMenu = false
                                showExternalVideoDialog = true
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            if (isUploading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.ember,
                )
            }
            if (!operationMessage.isNullOrBlank()) {
                Text(operationMessage, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            if (canUpload) {
                OutlinedTextField(
                    value = uploadCaption,
                    onValueChange = { uploadCaption = it },
                    label = { Text(stringResource(R.string.album_caption_next)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            AlbumContent(
                uiState = uiState,
                canViewAlbum = canViewAlbum,
                canUpload = canUpload,
                isRefreshing = isRefreshing,
                onRetry = onRetry,
                onRefresh = onRefresh,
                onOpen = { selectedItem = it },
                modifier = Modifier.weight(1f),
            )
        }
    }

    selectedItem?.let { item ->
        val media = (uiState as? AlbumUiState.Loaded)?.media.orEmpty()
        FullScreenGalleryDialog(
            media = media,
            initialItemId = item.id,
            currentUserId = authenticatedUser.uid,
            canManageAlbum = canManageAlbum,
            onDismiss = { selectedItem = null },
            onOpenExternal = { current ->
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(current.playbackUrl)))
            },
            onDelete = { current ->
                onDelete(campingId, current.id)
                selectedItem = null
            },
            onUpdateCaption = { current, caption ->
                onUpdateCaption(campingId, current.id, caption)
            },
        )
    }

    if (showExternalVideoDialog && canUpload) {
        ExternalVideoDialog(
            onDismiss = { showExternalVideoDialog = false },
            onSave = { videoUrl, thumbnailUrl, caption ->
                onAddExternalVideo(campingId, videoUrl, thumbnailUrl, caption, authenticatedUser)
                showExternalVideoDialog = false
            },
        )
    }

    if (showSettings && canManageAlbumSettings) {
        AlbumSettingsDialog(
            settings = settings,
            onDismiss = { showSettings = false },
            onSetRoleAllowed = { role, allowed -> onSetRoleAllowed(campingId, role, allowed) },
        )
    }
}

@Composable
private fun FullScreenGalleryDialog(
    media: List<MediaItem>,
    initialItemId: String,
    currentUserId: String,
    canManageAlbum: Boolean,
    onDismiss: () -> Unit,
    onOpenExternal: (MediaItem) -> Unit,
    onDelete: (MediaItem) -> Unit,
    onUpdateCaption: (MediaItem, String) -> Unit,
) {
    if (media.isEmpty()) return
    val initialPage = media.indexOfFirst { it.id == initialItemId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { media.size })
    val current = media[pagerState.currentPage.coerceIn(media.indices)]
    val canEdit = canManageAlbum || current.uploaderId == currentUserId
    var caption by remember(current.id) { mutableStateOf(current.caption) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(top = CzSpacing.lg, bottom = CzSpacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = CzSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_close), tint = Color.White)
                }
                Text(
                    text = "${pagerState.currentPage + 1} / ${media.size}",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onOpenExternal(current) }) {
                    Text(stringResource(R.string.common_open), color = Color.White)
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                key = { media[it].id },
            ) { page ->
                val item = media[page]
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MediaPreviewImage(
                        item = item,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                    if (item.kind == MediaKind.Video) {
                        IconButton(onClick = { onOpenExternal(item) }, modifier = Modifier.size(72.dp)) {
                            Icon(
                                if (item.opensExternally) Icons.Rounded.Link else Icons.Rounded.PlayCircle,
                                null,
                                tint = Color.White,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = CzSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.album_uploaded_by, current.uploaderName),
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (canEdit) {
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        label = { Text(stringResource(R.string.album_caption)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                        TextButton(onClick = { onUpdateCaption(current, caption.trim()) }) {
                            Icon(Icons.Rounded.Edit, null)
                            Text(stringResource(R.string.common_save))
                        }
                        TextButton(onClick = { onDelete(current) }) {
                            Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.czColors.error)
                            Text(stringResource(R.string.common_delete), color = MaterialTheme.czColors.error)
                        }
                    }
                } else if (current.caption.isNotBlank()) {
                    Text(current.caption, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun AlbumContent(
    uiState: AlbumUiState,
    canViewAlbum: Boolean,
    canUpload: Boolean,
    isRefreshing: Boolean,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!canViewAlbum) {
        CzEmptyState(
            title = stringResource(R.string.album_participants_only_title),
            message = stringResource(R.string.album_participants_only_message),
            icon = { Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(36.dp)) },
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        when (uiState) {
            AlbumUiState.Loading -> CzLoadingView(
                modifier = Modifier.fillMaxSize(),
                message = stringResource(R.string.album_loading),
            )

            is AlbumUiState.Error -> CzErrorState(
                title = stringResource(R.string.album_error_title),
                message = uiState.message,
                onRetry = onRetry,
                retryLabel = stringResource(R.string.common_retry),
                modifier = Modifier.fillMaxSize(),
            )

            is AlbumUiState.Loaded -> {
                if (uiState.media.isEmpty()) {
                    CzEmptyState(
                        title = stringResource(R.string.album_empty_title),
                        message = if (canUpload) {
                            stringResource(R.string.album_empty_upload_message)
                        } else {
                            stringResource(R.string.album_empty_view_message)
                        },
                        icon = {
                            Icon(
                                Icons.Rounded.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 112.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = CzSpacing.xxxl),
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                    ) {
                        items(uiState.media, key = { it.id }) { item ->
                            MediaTile(
                                item = item,
                                onClick = { onOpen(item) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaTile(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(CzRadius.md))
            .background(MaterialTheme.czColors.surface)
            .clickable(onClick = onClick),
    ) {
        MediaPreviewImage(
            item = item,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.kind == MediaKind.Video) {
            Icon(
                if (item.opensExternally) Icons.Rounded.Link else Icons.Rounded.PlayCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp),
            )
        }
        if (item.caption.isNotBlank()) {
            Text(
                item.caption,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(MaterialTheme.czColors.night.copy(alpha = 0.68f))
                    .padding(horizontal = CzSpacing.xs, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun MediaPreviewImage(
    item: MediaItem,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
) {
    val thumbnail = item.displayThumbnailUrl
    Box(
        modifier = modifier.background(MaterialTheme.czColors.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnail != null) {
            AsyncImage(
                model = thumbnail,
                contentDescription = item.caption.ifBlank { stringResource(R.string.album_media_cd) },
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            Icon(
                if (item.kind == MediaKind.Video) Icons.Rounded.Link else Icons.Rounded.PhotoLibrary,
                contentDescription = item.caption.ifBlank { stringResource(R.string.album_media_cd) },
                tint = MaterialTheme.czColors.textSecondary,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun MediaDetailDialog(
    item: MediaItem,
    canDelete: Boolean,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit,
    onDelete: () -> Unit,
    onUpdateCaption: (String) -> Unit,
) {
    var caption by remember(item.id) { mutableStateOf(item.caption) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item.kind == MediaKind.Video) stringResource(R.string.album_video) else stringResource(R.string.album_photo)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
                MediaPreviewImage(
                    item = item,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(CzRadius.md)),
                    contentScale = ContentScale.Crop,
                )
                Text(stringResource(R.string.album_uploaded_by, item.uploaderName), style = MaterialTheme.typography.bodySmall)
                if (canEdit) {
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        label = { Text(stringResource(R.string.album_caption)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (item.caption.isNotBlank()) {
                    Text(item.caption)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                TextButton(onClick = onOpenExternal) { Text(stringResource(R.string.common_open)) }
                if (canEdit) {
                    TextButton(onClick = { onUpdateCaption(caption) }) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(stringResource(R.string.common_save))
                    }
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                if (canDelete) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(stringResource(R.string.common_delete), color = MaterialTheme.czColors.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            }
        },
    )
}

@Composable
private fun ExternalVideoDialog(
    onDismiss: () -> Unit,
    onSave: (String, String?, String) -> Unit,
) {
    var videoUrl by remember { mutableStateOf("") }
    var thumbnailUrl by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    val normalizedVideoUrl = normalizedWebUrlOrNull(videoUrl)
    val normalizedThumbnailUrl = normalizedWebUrlOrNull(thumbnailUrl)
    val videoUrlInvalid = videoUrl.isNotBlank() && normalizedVideoUrl == null
    val thumbnailUrlInvalid = thumbnailUrl.isNotBlank() && normalizedThumbnailUrl == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.album_external_video_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
                OutlinedTextField(
                    value = videoUrl,
                    onValueChange = { videoUrl = it },
                    label = { Text(stringResource(R.string.album_video_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = videoUrlInvalid,
                    supportingText = {
                        if (videoUrlInvalid) {
                            Text(stringResource(R.string.album_invalid_video_url))
                        }
                    },
                )
                OutlinedTextField(
                    value = thumbnailUrl,
                    onValueChange = { thumbnailUrl = it },
                    label = { Text(stringResource(R.string.album_thumbnail_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = thumbnailUrlInvalid,
                    supportingText = {
                        if (thumbnailUrlInvalid) {
                            Text(stringResource(R.string.album_invalid_thumbnail_url))
                        }
                    },
                )
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text(stringResource(R.string.album_caption)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = normalizedVideoUrl != null && !thumbnailUrlInvalid,
                onClick = { onSave(normalizedVideoUrl.orEmpty(), normalizedThumbnailUrl, caption) },
            ) {
                Text(stringResource(R.string.common_save))
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
private fun AlbumSettingsDialog(
    settings: AlbumSettings,
    onDismiss: () -> Unit,
    onSetRoleAllowed: (UserRole, Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.album_permissions)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                Text(
                    stringResource(R.string.album_permissions_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.czColors.textSecondary,
                )
                UserRole.entries.forEach { role ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(role.displayName, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Checkbox(
                            checked = settings.allowedUploadRoles.contains(role),
                            onCheckedChange = { checked -> onSetRoleAllowed(role, checked) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) } },
    )
}

private val MediaKind.defaultMimeType: String
    get() = when (this) {
        MediaKind.Photo -> "image/jpeg"
        MediaKind.Video -> "video/mp4"
    }

private fun extensionFor(uri: Uri, mimeType: String): String {
    val fromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
    if (!fromMime.isNullOrBlank()) return fromMime
    val path = uri.lastPathSegment.orEmpty()
    return path.substringAfterLast('.', missingDelimiterValue = "").ifBlank {
        if (mimeType.startsWith("video/")) "mp4" else "jpg"
    }
}

private suspend fun cacheAlbumUpload(context: Context, uri: Uri, extension: String): File? =
    withContext(Dispatchers.IO) {
        val uploadDirectory = File(context.cacheDir, "album-uploads").apply { mkdirs() }
        val safeExtension = extension.filter { it.isLetterOrDigit() }.ifBlank { "bin" }
        val file = File(uploadDirectory, "album-${UUID.randomUUID()}.$safeExtension")
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output, bufferSize = 512 * 1024) }
            } ?: error("Could not open album media.")
            file
        }.getOrElse {
            file.delete()
            null
        }
    }

private fun normalizedWebUrlOrNull(value: String): String? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return null
    val lower = trimmed.lowercase()
    if ("://" in lower && !lower.startsWith("http://") && !lower.startsWith("https://")) {
        return null
    }
    val candidate = if (lower.startsWith("http://") || lower.startsWith("https://")) trimmed else "https://$trimmed"
    val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    return candidate.takeIf {
        (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    }
}

@Preview
@Composable
private fun CampingAlbumPreview() {
    CampzoneTheme {
        CampingAlbumScreen(
            campingId = "camp-1",
            authenticatedUser = AuthenticatedUser(
                uid = "u1",
                email = "maria@example.com",
                displayName = "Maria",
                photoUrl = null,
                role = UserRole.Admin,
                church = "Paris Central",
                age = 30,
                preferredLanguage = "en",
                gender = null,
                onboardingCompleted = true,
            ),
            uiState = AlbumUiState.Loaded(
                media = listOf(
                    MediaItem(
                        id = "m1",
                        campingId = "camp-1",
                        kind = MediaKind.Photo,
                        secureUrl = "https://res.cloudinary.com/demo/image/upload/sample.jpg",
                        publicId = "sample",
                        uploaderId = "u1",
                        uploaderName = "Maria",
                        caption = "Day one sunset",
                        uploadedAt = Date(),
                    ),
                ),
                settings = AlbumSettings(),
            ),
            canViewAlbum = true,
            canManageAlbum = true,
            canManageAlbumSettings = true,
            isUploading = false,
            isRefreshing = false,
            operationMessage = null,
            onBack = {},
            onRetry = {},
            onRefresh = {},
            onUploadFile = { _, _, _, _, _, _, _ -> },
            onAddExternalVideo = { _, _, _, _, _ -> },
            onDelete = { _, _ -> },
            onUpdateCaption = { _, _, _ -> },
            onSetRoleAllowed = { _, _, _ -> },
        )
    }
}
