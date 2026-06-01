package fr.ziyon.campzone.ui.album

import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import java.util.Date

@Composable
fun CampingAlbumRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    canViewAlbum: Boolean,
    canManageAlbum: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
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
        isUploading = isUploading,
        operationMessage = operationMessage,
        onBack = onBack,
        onRetry = { viewModel.load(campingId) },
        onUpload = viewModel::uploadMedia,
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
    isUploading: Boolean,
    operationMessage: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onUpload: (String, MediaKind, ByteArray, String, String, String, AuthenticatedUser) -> Unit,
    onDelete: (String, String) -> Unit,
    onUpdateCaption: (String, String, String) -> Unit,
    onSetRoleAllowed: (String, UserRole, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val context = LocalContext.current
    var selectedItem by remember { mutableStateOf<MediaItem?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var uploadCaption by remember { mutableStateOf("") }
    val settings = (uiState as? AlbumUiState.Loaded)?.settings ?: AlbumSettings()
    val canUpload = canManageAlbum || (canViewAlbum && settings.allows(authenticatedUser.role))
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        val kind = if (mimeType.startsWith("video/")) MediaKind.Video else MediaKind.Photo
        val extension = extensionFor(uri, mimeType)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        onUpload(campingId, kind, bytes, mimeType.ifBlank { kind.defaultMimeType }, extension, uploadCaption, authenticatedUser)
        uploadCaption = ""
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
                    if (canManageAlbum) {
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
                FloatingActionButton(
                    onClick = { mediaPicker.launch(arrayOf("image/*", "video/*")) },
                    containerColor = colors.ember,
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.album_add_media))
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
                onRetry = onRetry,
                onOpen = { selectedItem = it },
                modifier = Modifier.weight(1f),
            )
        }
    }

    selectedItem?.let { item ->
        MediaDetailDialog(
            item = item,
            canDelete = canManageAlbum || item.uploaderId == authenticatedUser.uid,
            canEdit = canManageAlbum || item.uploaderId == authenticatedUser.uid,
            onDismiss = { selectedItem = null },
            onOpenExternal = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.secureUrl)))
            },
            onDelete = {
                onDelete(campingId, item.id)
                selectedItem = null
            },
            onUpdateCaption = { caption ->
                onUpdateCaption(campingId, item.id, caption)
                selectedItem = item.copy(caption = caption.trim())
            },
        )
    }

    if (showSettings) {
        AlbumSettingsDialog(
            settings = settings,
            onDismiss = { showSettings = false },
            onSetRoleAllowed = { role, allowed -> onSetRoleAllowed(campingId, role, allowed) },
        )
    }
}

@Composable
private fun AlbumContent(
    uiState: AlbumUiState,
    canViewAlbum: Boolean,
    canUpload: Boolean,
    onRetry: () -> Unit,
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
    when (uiState) {
        AlbumUiState.Loading -> CzLoadingView(
            modifier = modifier.fillMaxSize(),
            message = stringResource(R.string.album_loading),
        )

        is AlbumUiState.Error -> CzErrorState(
            title = stringResource(R.string.album_error_title),
            message = uiState.message,
            onRetry = onRetry,
            retryLabel = stringResource(R.string.common_retry),
            modifier = modifier.fillMaxSize(),
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
                    modifier = modifier.fillMaxSize(),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = CzSpacing.xxxl),
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                ) {
                    items(uiState.media, key = { it.id }) { item ->
                        MediaTile(item = item, onClick = { onOpen(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaTile(item: MediaItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(118.dp)
            .clip(RoundedCornerShape(CzRadius.md))
            .background(MaterialTheme.czColors.surface)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = item.thumbnailUrl ?: item.secureUrl,
            contentDescription = item.caption.ifBlank { stringResource(R.string.album_media_cd) },
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (item.kind == MediaKind.Video) {
            Icon(
                Icons.Rounded.PlayCircle,
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
                AsyncImage(
                    model = item.thumbnailUrl ?: item.secureUrl,
                    contentDescription = item.caption.ifBlank { stringResource(R.string.album_media_cd) },
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
            isUploading = false,
            operationMessage = null,
            onBack = {},
            onRetry = {},
            onUpload = { _, _, _, _, _, _, _ -> },
            onDelete = { _, _ -> },
            onUpdateCaption = { _, _, _ -> },
            onSetRoleAllowed = { _, _, _ -> },
        )
    }
}
