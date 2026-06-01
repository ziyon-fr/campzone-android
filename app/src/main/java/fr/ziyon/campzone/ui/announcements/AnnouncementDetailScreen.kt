package fr.ziyon.campzone.ui.announcements

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.outlined.Cabin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTypeScale
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.Announcement
import fr.ziyon.campzone.data.model.AnnouncementAttachment
import fr.ziyon.campzone.data.model.AnnouncementAttachmentKind
import fr.ziyon.campzone.data.model.AnnouncementAudienceScope
import io.noties.markwon.Markwon
import java.util.Date

// ── Route ─────────────────────────────────────────────────────────────────────

@Composable
fun AnnouncementDetailRoute(
    viewModel: AnnouncementViewModel,
    announcementId: String,
    authenticatedUser: AuthenticatedUser,
    permissionUser: PermissionUser?,
    evaluator: AppPermissionEvaluator,
    onBack: () -> Unit,
    onOpenComposer: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    val announcement = viewModel.announcementById(announcementId)

    val canManage = remember(announcement, permissionUser) {
        announcement?.let { viewModel.canManageAnnouncement(it, permissionUser, evaluator) } ?: false
    }

    AnnouncementDetailScreen(
        announcement = announcement,
        uiState = uiState,
        isSaving = isSaving,
        canManage = canManage,
        onBack = onBack,
        onEdit = {
            announcement?.let {
                viewModel.prepareEdit(it)
                onOpenComposer()
            }
        },
        onDelete = {
            viewModel.deleteAnnouncement(announcementId, onSuccess = onBack)
        },
        onRetry = viewModel::load,
    )
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementDetailScreen(
    announcement: Announcement?,
    uiState: AnnouncementsUiState,
    isSaving: Boolean,
    canManage: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    var showOptions by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(R.string.announcements_title), style = CzTypeScale.headline, color = colors.textPrimary)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = colors.textPrimary,
                        )
                    }
                },
                actions = {
                    if (canManage && announcement != null) {
                        IconButton(onClick = { showOptions = true }) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.announcements_options_cd),
                                tint = colors.textPrimary,
                            )
                        }
                        DropdownMenu(
                            expanded = showOptions,
                            onDismissRequest = { showOptions = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_edit), style = CzTypeScale.body) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Edit, contentDescription = null)
                                },
                                onClick = {
                                    showOptions = false
                                    onEdit()
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.common_delete), style = CzTypeScale.body, color = colors.error)
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Description,
                                        contentDescription = null,
                                        tint = colors.error,
                                    )
                                },
                                onClick = {
                                    showOptions = false
                                    showDeleteConfirm = true
                                },
                            )
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
        if (isSaving) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.ember)
            }
        } else if (announcement != null) {
            AnnouncementDetailContent(
                announcement = announcement,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                when (uiState) {
                    is AnnouncementsUiState.Loading -> CircularProgressIndicator(color = colors.ember)
                    is AnnouncementsUiState.Error -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                    ) {
                        Text(stringResource(R.string.common_failed_to_load), style = CzTypeScale.body, color = colors.error)
                        Text(uiState.message, style = CzTypeScale.caption, color = colors.textSecondary)
                        TextButton(onClick = onRetry) {
                            Text(stringResource(R.string.common_retry), color = colors.ember)
                        }
                    }
                    else -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                    ) {
                        Icon(
                            Icons.Rounded.Campaign,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            "Announcement not found",
                            style = CzTypeScale.body,
                            color = colors.textPrimary,
                        )
                        Text(
                            "This announcement may have been removed or is not available yet.",
                            style = CzTypeScale.caption,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.announcements_delete_title)) },
            text = {
                Text(
                    stringResource(R.string.announcements_delete_message),
                    style = CzTypeScale.body,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                ) {
                    Text(stringResource(R.string.common_delete), color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

// ── Detail content ────────────────────────────────────────────────────────────

@Composable
private fun AnnouncementDetailContent(
    announcement: Announcement,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    var expandedImageUrl by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xl),
    ) {
        // ── Header card ───────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CzRadius.lg))
                .background(colors.surface)
                .padding(CzSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Text(
                text = announcement.title,
                style = CzTypeScale.title2,
                color = colors.textPrimary,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            ) {
                if (!announcement.authorPhotoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = announcement.authorPhotoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(colors.amber.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = announcement.authorName.take(1).uppercase(),
                            style = CzTypeScale.caption2,
                            color = colors.amber,
                        )
                    }
                }
                Text(
                    text = announcement.authorName,
                    style = CzTypeScale.caption,
                    color = colors.textSecondary,
                )
                Text("·", style = CzTypeScale.caption, color = colors.textSecondary)
                Text(
                    text = announcement.createdDateText,
                    style = CzTypeScale.caption,
                    color = colors.textSecondary,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            ) {
                Icon(
                    if (announcement.audienceScope == AnnouncementAudienceScope.Camping)
                        Icons.Outlined.Cabin else Icons.Rounded.People,
                    contentDescription = null,
                    tint = colors.ember,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = announcement.audienceText,
                    style = CzTypeScale.caption,
                    color = colors.textSecondary,
                )
            }

            if (announcement.wasEdited) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                ) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = colors.amber,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = "Edited ${announcement.updatedDateText}",
                        style = CzTypeScale.caption2,
                        color = colors.amber,
                    )
                }
            }
        }

        // ── Body card ─────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CzRadius.lg))
                .background(colors.surface)
                .padding(CzSpacing.md),
        ) {
            MarkdownBody(
                text = announcement.body,
                textColor = colors.textPrimary.toArgb(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ── Attachments ───────────────────────────────────────────────────────
        if (announcement.attachments.isNotEmpty()) {
            AttachmentsSection(
                attachments = announcement.attachments,
                onImageClick = { url -> expandedImageUrl = url },
            )
        }

        Spacer(Modifier.height(CzSpacing.xxl))
    }

    // Full-screen image viewer
    val imgUrl = expandedImageUrl
    if (imgUrl != null) {
        FullScreenImageViewer(
            imageUrl = imgUrl,
            onDismiss = { expandedImageUrl = null },
        )
    }
}

// ── Markdown body ─────────────────────────────────────────────────────────────

@Composable
internal fun MarkdownBody(text: String, textColor: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val markwon: Markwon = remember { Markwon.create(context) }
    val spanned: android.text.Spanned = remember(text) { markwon.toMarkdown(text) }

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                textSize = 15f
                setLineSpacing(4f, 1f)
            }
        },
        update = { tv ->
            tv.setTextColor(textColor)
            markwon.setParsedMarkdown(tv, spanned)
        },
        modifier = modifier,
    )
}

// ── Full-screen image viewer ──────────────────────────────────────────────────

@Composable
private fun FullScreenImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            // Close button - top-right
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.common_close_image),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

// ── Attachments section ───────────────────────────────────────────────────────

@Composable
private fun AttachmentsSection(
    attachments: List<AnnouncementAttachment>,
    onImageClick: (String) -> Unit,
) {
    val colors = MaterialTheme.czColors
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            Icon(
                Icons.Rounded.AttachFile,
                contentDescription = null,
                tint = colors.ember,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "Attachments",
                style = CzTypeScale.caption,
                color = colors.textSecondary,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CzRadius.lg))
                .background(colors.surface),
        ) {
            attachments.forEachIndexed { idx, attachment ->
                AttachmentRow(
                    attachment = attachment,
                    onImageClick = onImageClick,
                )
                if (idx < attachments.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = CzSpacing.md),
                        color = colors.divider,
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentRow(
    attachment: AnnouncementAttachment,
    onImageClick: (String) -> Unit,
) {
    val colors = MaterialTheme.czColors
    val context = LocalContext.current

    when (attachment.kind) {
        AnnouncementAttachmentKind.Image -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CzSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                ) {
                    Icon(
                        Icons.Rounded.Photo,
                        contentDescription = null,
                        tint = colors.ember,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = attachment.fileName,
                        style = CzTypeScale.subhead,
                        color = colors.textPrimary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (attachment.downloadUrl.isNotBlank()) {
                    AsyncImage(
                        model = attachment.downloadUrl,
                        contentDescription = attachment.fileName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(CzRadius.md))
                            .height(200.dp)
                            .clickable { onImageClick(attachment.downloadUrl) },
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }

        AnnouncementAttachmentKind.Pdf -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (attachment.downloadUrl.isNotBlank()) {
                            Modifier.clickable {
                                openPdf(context, attachment.downloadUrl)
                            }
                        } else Modifier
                    )
                    .padding(CzSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            ) {
                Icon(
                    Icons.Rounded.Description,
                    contentDescription = null,
                    tint = colors.ember,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = attachment.fileName,
                    style = CzTypeScale.subhead,
                    color = if (attachment.downloadUrl.isNotBlank()) colors.textPrimary else colors.textSecondary,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                )
                if (attachment.downloadUrl.isNotBlank()) {
                    Spacer(Modifier.width(CzSpacing.xs))
                    Icon(
                        Icons.Rounded.OpenInNew,
                        contentDescription = stringResource(R.string.common_open_pdf),
                        tint = colors.ember,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun openPdf(context: android.content.Context, url: String) {
    val uri = Uri.parse(url)
    // Try dedicated PDF viewer first
    val pdfIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(pdfIntent)
        return
    } catch (e: ActivityNotFoundException) {
        android.util.Log.d("AnnouncementDetail", "No PDF viewer app installed: ${e.message}")
    }
    // Fallback: open URL in browser
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: Exception) {
        android.util.Log.d("AnnouncementDetail", "Could not open PDF URL: ${e.message}")
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun AnnouncementDetailPreview() {
    CampzoneTheme {
        AnnouncementDetailScreen(
            announcement = Announcement(
                id = "preview",
                title = "Packing list published",
                body = "Leaders have published the **first checklist** for summer camp.\n\n- Clothing\n- Sleeping bag",
                authorName = "Campzone Team",
                createdAt = Date(),
                updatedAt = Date(),
            ),
            uiState = AnnouncementsUiState.Loading,
            isSaving = false,
            canManage = true,
            onBack = {},
            onEdit = {},
            onDelete = {},
            onRetry = {},
        )
    }
}
