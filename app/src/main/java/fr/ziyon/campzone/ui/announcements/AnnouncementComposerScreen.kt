package fr.ziyon.campzone.ui.announcements

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTypeScale
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.AnnouncementAttachment
import fr.ziyon.campzone.data.model.AnnouncementAttachmentKind
import fr.ziyon.campzone.data.model.AnnouncementAudienceScope
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.PendingAnnouncementAttachment

// ── Route ─────────────────────────────────────────────────────────────────────

@Composable
fun AnnouncementComposerRoute(
    viewModel: AnnouncementViewModel,
    authenticatedUser: AuthenticatedUser,
    permissionUser: PermissionUser?,
    evaluator: AppPermissionEvaluator,
    onBack: () -> Unit,
) {
    val form by viewModel.form.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    val isEditing by viewModel.editingId.collectAsState()
    val availableCampings = remember(permissionUser, viewModel.campings.collectAsState().value) {
        viewModel.availableCampingsForCompose(permissionUser, evaluator)
    }

    AnnouncementComposerScreen(
        form = form,
        isSaving = isSaving,
        operationError = operationError,
        isEditing = isEditing != null,
        availableCampings = availableCampings,
        canCreateGlobal = evaluator.can(
            permissionUser,
            fr.ziyon.campzone.core.permissions.AppPermission.CreateAnnouncements,
        ),
        onBack = onBack,
        onUpdateForm = viewModel::updateForm,
        onRemoveExisting = viewModel::removeExistingAttachment,
        onRemovePending = viewModel::removePendingAttachment,
        onAddAttachment = { uri, kind, name ->
            viewModel.addAttachmentFromUri(uri, kind, name)
        },
        onPublish = {
            viewModel.saveAnnouncement(authenticatedUser, onSuccess = onBack)
        },
    )
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementComposerScreen(
    form: AnnouncementComposerForm,
    isSaving: Boolean,
    operationError: String?,
    isEditing: Boolean,
    availableCampings: List<Camping>,
    canCreateGlobal: Boolean,
    onBack: () -> Unit,
    onUpdateForm: ((AnnouncementComposerForm) -> AnnouncementComposerForm) -> Unit,
    onRemoveExisting: (String) -> Unit,
    onRemovePending: (String) -> Unit,
    onAddAttachment: (Uri, AnnouncementAttachmentKind, String) -> Unit,
    onPublish: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    var bodyPreviewMode by remember { mutableStateOf(false) }
    val defaultImageName = stringResource(R.string.announcements_default_image_name)
    val defaultPdfName = stringResource(R.string.announcements_default_pdf_name)

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment ?: defaultImageName
            onAddAttachment(uri, AnnouncementAttachmentKind.Image, name)
        }
    }
    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment ?: defaultPdfName
            onAddAttachment(uri, AnnouncementAttachmentKind.Pdf, name)
        }
    }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (isEditing) R.string.announcements_edit_title else R.string.announcements_compose_title,
                        ),
                        style = CzTypeScale.headline,
                        color = colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_cancel_cd),
                            tint = colors.textPrimary,
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onPublish,
                        enabled = form.isValid && !isSaving,
                    ) {
                        Text(
                            stringResource(R.string.common_publish),
                            style = CzTypeScale.headline,
                            color = if (form.isValid && !isSaving) colors.ember else colors.textSecondary,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.background,
                ),
                windowInsets = WindowInsets(),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CzSpacing.base, vertical = CzSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
        ) {
            // ── Validation errors ─────────────────────────────────────────────
            if (form.validationErrors.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CzRadius.lg))
                        .background(colors.error.copy(alpha = 0.08f))
                        .padding(CzSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                ) {
                    Text(
                        stringResource(R.string.announcements_validation_header),
                        style = CzTypeScale.subhead,
                        color = colors.error,
                    )
                    form.validationErrors.forEach { error ->
                        Text(
                            "• ${stringResource(error.messageRes)}",
                            style = CzTypeScale.caption,
                            color = colors.error,
                        )
                    }
                }
            }

            // ── Content section ───────────────────────────────────────────────
            ComposerSectionHeader(title = stringResource(R.string.common_content), icon = Icons.Rounded.TextFields)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CzRadius.lg))
                    .background(colors.surface),
            ) {
                // Title field
                TextField(
                    value = form.title,
                    onValueChange = { onUpdateForm { f -> f.copy(title = it) } },
                    placeholder = {
                        Text(stringResource(R.string.common_title), style = CzTypeScale.body, color = colors.textSecondary)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = CzTypeScale.body.copy(color = colors.textPrimary),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = colors.ember,
                    ),
                )

                HorizontalDivider(
                    color = colors.divider,
                    modifier = Modifier.padding(horizontal = CzSpacing.base),
                )

                // Write / Preview segmented toggle
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CzSpacing.base, vertical = CzSpacing.xs),
                ) {
                    SegmentedButton(
                        selected = !bodyPreviewMode,
                        onClick = { bodyPreviewMode = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        label = { Text(stringResource(R.string.common_write), style = CzTypeScale.caption) },
                    )
                    SegmentedButton(
                        selected = bodyPreviewMode,
                        onClick = { bodyPreviewMode = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        label = { Text(stringResource(R.string.common_preview), style = CzTypeScale.caption) },
                    )
                }

                HorizontalDivider(
                    color = colors.divider,
                    modifier = Modifier.padding(horizontal = CzSpacing.base),
                )

                // Body - Write or Preview
                if (bodyPreviewMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(CzSpacing.md),
                    ) {
                        if (form.body.isBlank()) {
                            Text(
                                stringResource(R.string.announcements_preview_empty),
                                style = CzTypeScale.body,
                                color = colors.textSecondary,
                            )
                        } else {
                            MarkdownBody(
                                text = form.body,
                                textColor = colors.textPrimary.toArgb(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                } else {
                    TextField(
                        value = form.body,
                        onValueChange = { onUpdateForm { f -> f.copy(body = it) } },
                        placeholder = {
                            Text(
                                stringResource(R.string.announcements_body_placeholder),
                                style = CzTypeScale.body,
                                color = colors.textSecondary,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5,
                        maxLines = 20,
                        textStyle = CzTypeScale.body.copy(color = colors.textPrimary),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = colors.ember,
                        ),
                    )
                }
            }

            // ── Audience section ──────────────────────────────────────────────
            ComposerSectionHeader(title = stringResource(R.string.common_audience), icon = Icons.Rounded.People)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CzRadius.lg))
                    .background(colors.surface)
                    .padding(horizontal = CzSpacing.base, vertical = CzSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            ) {
                if (canCreateGlobal) {
                    AudienceScopePicker(
                        selected = form.audienceScopeRawValue,
                        onSelect = { rawValue ->
                            val newScope = AnnouncementAudienceScope.fromWire(rawValue)
                            onUpdateForm { f ->
                                f.copy(
                                    audienceScopeRawValue = rawValue,
                                    campingId = if (newScope == AnnouncementAudienceScope.App) null else f.campingId,
                                    notificationTargetRoleRawValue = if (newScope == AnnouncementAudienceScope.App) null
                                    else f.notificationTargetRoleRawValue,
                                )
                            }
                        },
                    )
                }

                if (form.audienceScope == AnnouncementAudienceScope.Camping) {
                    if (availableCampings.isEmpty()) {
                        Text(
                            stringResource(R.string.announcements_no_campings_available),
                            style = CzTypeScale.caption,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(vertical = CzSpacing.xs),
                        )
                    } else {
                        CampingPicker(
                            selected = form.campingId,
                            campings = availableCampings,
                            onSelect = { id ->
                                onUpdateForm { f ->
                                    f.copy(
                                        campingId = id,
                                        campingTitle = availableCampings.firstOrNull { it.id == id }?.title,
                                    )
                                }
                            },
                        )

                        RolePicker(
                            selected = form.notificationTargetRoleRawValue,
                            onSelect = { role ->
                                onUpdateForm { f -> f.copy(notificationTargetRoleRawValue = role) }
                            },
                        )
                    }
                }
            }

            // ── Attachments section ───────────────────────────────────────────
            ComposerSectionHeader(title = stringResource(R.string.common_attachments), icon = Icons.Rounded.AttachFile)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CzRadius.lg))
                    .background(colors.surface),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (form.existingAttachments.isEmpty() && form.pendingAttachments.isEmpty()) {
                    Row(
                        modifier = Modifier.padding(CzSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                    ) {
                        Icon(
                            Icons.Rounded.AttachFile,
                            contentDescription = null,
                            tint = colors.textSecondary,
                        )
                        Text(
                            stringResource(R.string.announcements_no_attachments),
                            style = CzTypeScale.body,
                            color = colors.textSecondary,
                        )
                    }
                } else {
                    form.existingAttachments.forEach { attachment ->
                        ComposerAttachmentRow(
                            name = attachment.fileName,
                            kind = attachment.kind,
                            isPending = false,
                            onRemove = { onRemoveExisting(attachment.id) },
                        )
                    }
                    form.pendingAttachments.forEach { attachment ->
                        ComposerAttachmentRow(
                            name = attachment.fileName,
                            kind = attachment.kind,
                            isPending = true,
                            onRemove = { onRemovePending(attachment.id) },
                        )
                    }
                }

                HorizontalDivider(color = colors.divider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .padding(CzSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                ) {
                    Icon(Icons.Rounded.Photo, contentDescription = null, tint = colors.ember)
                    Text(stringResource(R.string.common_add_image), style = CzTypeScale.body, color = colors.ember)
                }
                HorizontalDivider(color = colors.divider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pdfPicker.launch("application/pdf") }
                        .padding(CzSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                ) {
                    Icon(Icons.Rounded.Description, contentDescription = null, tint = colors.ember)
                    Text(stringResource(R.string.common_add_pdf), style = CzTypeScale.body, color = colors.ember)
                }
            }

            // ── Operation error ───────────────────────────────────────────────
            val err = operationError
            if (err != null) {
                Text(err, style = CzTypeScale.caption, color = colors.error)
            }

            Spacer(Modifier.height(CzSpacing.xxl))
        }
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
private fun ComposerSectionHeader(title: String, icon: ImageVector) {
    val colors = MaterialTheme.czColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Icon(icon, contentDescription = null, tint = colors.ember, modifier = Modifier.size(12.dp))
        Text(
            text = title.uppercase(),
            style = CzTypeScale.caption.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textSecondary,
        )
    }
}

// ── Audience pickers ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudienceScopePicker(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val colors = MaterialTheme.czColors
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        AnnouncementAudienceScope.App.rawValue to stringResource(R.string.announcements_audience_all_app),
        AnnouncementAudienceScope.Camping.rawValue to stringResource(R.string.common_camping),
    )
    val selectedLabel = options.firstOrNull { it.first == selected }?.second
        ?: stringResource(R.string.announcements_audience_all_app)

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        TextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            label = { Text(stringResource(R.string.common_audience), style = CzTypeScale.caption, color = colors.textSecondary) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = colors.ember,
            ),
            textStyle = CzTypeScale.body.copy(color = colors.textPrimary),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label, style = CzTypeScale.body) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampingPicker(
    selected: String?,
    campings: List<Camping>,
    onSelect: (String) -> Unit,
) {
    val colors = MaterialTheme.czColors
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = campings.firstOrNull { it.id == selected }?.title ?: stringResource(R.string.announcements_select_camping)

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        TextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            label = { Text(stringResource(R.string.common_camping), style = CzTypeScale.caption, color = colors.textSecondary) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = colors.ember,
            ),
            textStyle = CzTypeScale.body.copy(color = colors.textPrimary),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            campings.forEach { camping ->
                DropdownMenuItem(
                    text = { Text(camping.title, style = CzTypeScale.body) },
                    onClick = {
                        onSelect(camping.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RolePicker(
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    val colors = MaterialTheme.czColors
    var expanded by remember { mutableStateOf(false) }
    val roles = UserRole.entries
    val selectedLabel = selected?.let {
        stringResource(UserRole.fromWire(it).displayNameRes())
    } ?: stringResource(R.string.common_everyone_in_camping)

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        TextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            label = { Text(stringResource(R.string.common_role), style = CzTypeScale.caption, color = colors.textSecondary) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = colors.ember,
            ),
            textStyle = CzTypeScale.body.copy(color = colors.textPrimary),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_everyone_in_camping), style = CzTypeScale.body) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            roles.forEach { role ->
                DropdownMenuItem(
                    text = { Text(stringResource(role.displayNameRes()), style = CzTypeScale.body) },
                    onClick = {
                        onSelect(role.rawValue)
                        expanded = false
                    },
                )
            }
        }
    }
}

// ── Attachment row ────────────────────────────────────────────────────────────

@Composable
private fun ComposerAttachmentRow(
    name: String,
    kind: AnnouncementAttachmentKind,
    isPending: Boolean,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CzSpacing.md, vertical = CzSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Icon(
            if (kind == AnnouncementAttachmentKind.Image) Icons.Rounded.Photo else Icons.Rounded.Description,
            contentDescription = null,
            tint = colors.ember,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = CzTypeScale.body,
                color = colors.textPrimary,
                maxLines = 1,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(kind.labelRes()),
                    style = CzTypeScale.caption,
                    color = colors.textSecondary,
                )
                if (isPending) {
                    Text(
                        stringResource(R.string.announcements_pending_upload),
                        style = CzTypeScale.caption,
                        color = colors.amber,
                    )
                }
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.common_remove_attachment),
                tint = colors.error,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@StringRes
private fun AnnouncementAttachmentKind.labelRes(): Int = when (this) {
    AnnouncementAttachmentKind.Image -> R.string.announcements_attachment_image
    AnnouncementAttachmentKind.Pdf -> R.string.announcements_attachment_pdf
}

@StringRes
private fun UserRole.displayNameRes(): Int = when (this) {
    UserRole.Guest -> R.string.role_guest
    UserRole.User -> R.string.role_user
    UserRole.Adult -> R.string.role_adult
    UserRole.YouthDirector -> R.string.role_youth_director
    UserRole.Pastor -> R.string.role_pastor
    UserRole.GameMaster -> R.string.role_game_master
    UserRole.Leader -> R.string.role_leader
    UserRole.Photographer -> R.string.role_photographer
    UserRole.Admin -> R.string.role_admin
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun AnnouncementComposerPreview() {
    CampzoneTheme {
        AnnouncementComposerScreen(
            form = AnnouncementComposerForm(),
            isSaving = false,
            operationError = null,
            isEditing = false,
            availableCampings = emptyList(),
            canCreateGlobal = true,
            onBack = {},
            onUpdateForm = {},
            onRemoveExisting = {},
            onRemovePending = {},
            onAddAttachment = { _, _, _ -> },
            onPublish = {},
        )
    }
}
