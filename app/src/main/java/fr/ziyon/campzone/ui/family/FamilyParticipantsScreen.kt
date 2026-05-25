package fr.ziyon.campzone.ui.family

import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzAvatar
import fr.ziyon.campzone.core.designsystem.CzAvatarSize
import fr.ziyon.campzone.core.designsystem.CzBadge
import fr.ziyon.campzone.core.designsystem.CzBadgeTone
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzCard
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTextField
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.CampingAgeGroup
import fr.ziyon.campzone.data.auth.PreferredLanguage
import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.church.ChurchGroup
import fr.ziyon.campzone.data.family.ChildParticipant
import fr.ziyon.campzone.data.family.FamilyRelationship
import fr.ziyon.campzone.ui.common.ChurchPickerSheet

@Composable
fun FamilyParticipantsScreen(
    authenticatedUser: AuthenticatedUser,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FamilyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(authenticatedUser.uid) {
        viewModel.load(authenticatedUser)
    }

    val editorOpen = uiState.editor != null
    BackHandler(enabled = editorOpen) { viewModel.closeEditor() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            !uiState.canManageFamily -> RestrictedState(onNavigateBack = onNavigateBack)
            uiState.editor != null -> ChildEditorContent(
                editor = uiState.editor!!,
                authenticatedUser = authenticatedUser,
                onClose = viewModel::closeEditor,
                onSave = { viewModel.save(authenticatedUser, onSaved = {}) },
                onConfirmDuplicate = { viewModel.confirmDuplicateSave(authenticatedUser, onSaved = {}) },
                onCancelDuplicate = viewModel::cancelDuplicate,
                onForm = viewModel::updateForm,
                onChangePhoto = viewModel::uploadPhoto,
                onPhotoError = viewModel::reportPhotoError,
                onRemovePhoto = viewModel::removePhoto,
                churchQuery = uiState.churchQuery,
                churchGroups = uiState.filteredChurchGroups,
                isLoadingChurches = uiState.isLoadingChurches,
                churchError = uiState.churchError,
                onChurchQueryChange = viewModel::updateChurchQuery,
                onLoadChurches = viewModel::loadChurches,
                onSelectChurch = viewModel::selectChurch,
            )
            else -> FamilyListContent(
                uiState = uiState,
                authenticatedUser = authenticatedUser,
                onNavigateBack = onNavigateBack,
                onAdd = { viewModel.openEditor(childId = null, user = authenticatedUser) },
                onEdit = { child -> viewModel.openEditor(childId = child.id, user = authenticatedUser) },
                onDelete = { child -> viewModel.deleteChild(child, authenticatedUser) },
                onRetry = { viewModel.reload(authenticatedUser) },
            )
        }

        FamilyFeedbackBanner(
            feedback = uiState.feedback,
            onDismiss = viewModel::dismissFeedback,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun RestrictedState(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        FamilyHeader(title = stringResource(R.string.family_title), onNavigateBack = onNavigateBack)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CzEmptyState(
                title = stringResource(R.string.family_restricted_title),
                message = stringResource(R.string.family_restricted_message),
            )
        }
    }
}

@Composable
private fun FamilyListContent(
    uiState: FamilyUiState,
    authenticatedUser: AuthenticatedUser,
    onNavigateBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (ChildParticipant) -> Unit,
    onDelete: (ChildParticipant) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        FamilyHeader(
            title = stringResource(R.string.family_title),
            onNavigateBack = onNavigateBack,
            actionLabel = stringResource(R.string.family_add_participant),
            onAction = onAdd,
        )

        Column(
            modifier = Modifier.padding(horizontal = CzSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
        ) {
            CzCard {
                Text(
                    text = stringResource(R.string.family_intro_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.czColors.primary,
                )
                Text(
                    text = stringResource(R.string.family_intro_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.czColors.textSecondary,
                )
            }

            when (val listState = uiState.listState) {
                FamilyListState.Loading -> CzLoadingView(message = stringResource(R.string.family_loading))
                is FamilyListState.Error -> CzErrorState(
                    title = stringResource(R.string.family_error_title),
                    message = listState.message,
                    retryLabel = stringResource(R.string.common_retry),
                    onRetry = onRetry,
                )
                FamilyListState.Empty -> CzEmptyState(
                    title = stringResource(R.string.family_empty_title),
                    message = stringResource(R.string.family_empty_message),
                    action = {
                        CzButton(
                            text = stringResource(R.string.family_add_participant),
                            onClick = onAdd,
                            variant = CzButtonVariant.Primary,
                        )
                    },
                )
                is FamilyListState.Loaded -> listState.children.forEach { child ->
                    ChildParticipantCard(
                        child = child,
                        onEdit = { onEdit(child) },
                        onDelete = { onDelete(child) },
                        deleteEnabled = !uiState.isDeleting,
                    )
                }
            }

            Box(modifier = Modifier.padding(bottom = CzSpacing.xxl))
        }
    }
}

@Composable
private fun ChildParticipantCard(
    child: ChildParticipant,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    deleteEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    CzCard(modifier = modifier.fillMaxWidth(), onClick = onEdit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.base),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CzAvatar(
                imageUrl = child.photoUrl,
                contentDescription = child.displayName,
                initials = child.displayName,
                size = CzAvatarSize.Medium,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = child.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.czColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${stringResource(R.string.family_years, child.age)} · ${child.ageGroup.localizedName()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.czColors.textSecondary,
                )
            }
            IconButton(onClick = onDelete, enabled = deleteEnabled) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.family_delete),
                    tint = MaterialTheme.czColors.error,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = CzSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CzBadge(text = child.relationshipDisplayName, tone = CzBadgeTone.Secondary)
            CzBadge(text = child.preferredLanguage.uppercase(), tone = CzBadgeTone.Neutral)
            Text(
                text = child.church,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.czColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChildEditorContent(
    editor: ChildEditorState,
    authenticatedUser: AuthenticatedUser,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onConfirmDuplicate: () -> Unit,
    onCancelDuplicate: () -> Unit,
    onForm: (ChildFormState.() -> ChildFormState) -> Unit,
    onChangePhoto: (ByteArray, String, String) -> Unit,
    onPhotoError: (String) -> Unit,
    onRemovePhoto: () -> Unit,
    churchQuery: String,
    churchGroups: List<ChurchGroup>,
    isLoadingChurches: Boolean,
    churchError: String?,
    onChurchQueryChange: (String) -> Unit,
    onLoadChurches: () -> Unit,
    onSelectChurch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showChurchPicker by remember { mutableStateOf(false) }
    val churchSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(showChurchPicker) { if (showChurchPicker) onLoadChurches() }
    val photoUploadFailed = stringResource(R.string.family_photo_upload_failed)
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "image/jpeg"
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error(photoUploadFailed)
            onChangePhoto(bytes, mimeType, extension)
        }.onFailure { onPhotoError(photoUploadFailed) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        FamilyHeader(
            title = if (editor.isEditing) {
                stringResource(R.string.family_editor_edit_title)
            } else {
                stringResource(R.string.family_editor_add_title)
            },
            onNavigateBack = onClose,
            actionLabel = stringResource(R.string.common_save),
            onAction = onSave,
            actionLoading = editor.isSaving,
            actionEnabled = !editor.isSaving && !editor.isUploadingPhoto,
        )

        Column(
            modifier = Modifier.padding(horizontal = CzSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
        ) {
            if (editor.validationErrors.isNotEmpty()) {
                ChildValidationBanner(editor.validationErrors)
            }

            EditorSection(title = stringResource(R.string.family_section_photo)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.base),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CzAvatar(
                        imageUrl = editor.form.photoUrl,
                        contentDescription = editor.form.displayName,
                        initials = editor.form.displayName.ifBlank { "?" },
                        size = CzAvatarSize.Large,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        CzButton(
                            text = if (editor.hasPhoto) {
                                stringResource(R.string.family_photo_change)
                            } else {
                                stringResource(R.string.family_photo_choose)
                            },
                            onClick = { photoPicker.launch("image/*") },
                            enabled = !editor.isUploadingPhoto && !editor.isSaving,
                            loading = editor.isUploadingPhoto,
                            variant = CzButtonVariant.Outline,
                            leadingIcon = {
                                Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                        )
                        if (editor.hasPhoto) {
                            TextButton(onClick = onRemovePhoto, enabled = !editor.isUploadingPhoto) {
                                Text(
                                    text = stringResource(R.string.family_photo_remove),
                                    color = MaterialTheme.czColors.error,
                                )
                            }
                        }
                    }
                }
                if (editor.photoError != null) {
                    Text(
                        text = editor.photoError,
                        color = MaterialTheme.czColors.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            EditorSection(title = stringResource(R.string.family_section_identity)) {
                CzTextField(
                    value = editor.form.displayName,
                    onValueChange = { value -> onForm { copy(displayName = value) } },
                    label = stringResource(R.string.family_full_name),
                    modifier = Modifier.fillMaxWidth(),
                )
                CzTextField(
                    value = editor.form.ageText,
                    onValueChange = { value -> onForm { copy(ageText = value.filter(Char::isDigit).take(2)) } },
                    label = stringResource(R.string.family_age),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                FamilyDropdown(
                    label = stringResource(R.string.family_gender),
                    selectedLabel = editor.form.gender.localizedName(),
                    options = UserGender.entries.map { it to it.localizedName() },
                    onSelected = { value -> onForm { copy(gender = value) } },
                )
                ReadOnlyRow(
                    label = stringResource(R.string.family_age_group),
                    value = editor.form.ageOrNull?.let { CampingAgeGroup.fromAge(it).localizedName() }
                        ?: stringResource(R.string.family_add_age),
                )
            }

            EditorSection(
                title = stringResource(R.string.family_section_relationship),
                footer = stringResource(R.string.family_relationship_footer),
            ) {
                FamilyDropdown(
                    label = stringResource(R.string.family_relationship),
                    selectedLabel = editor.form.relationship.localizedName(),
                    options = FamilyRelationship.entries.map { it to it.localizedName() },
                    onSelected = { value -> onForm { copy(relationship = value) } },
                )
                if (editor.form.relationship.requiresCustomLabel) {
                    CzTextField(
                        value = editor.form.customRelationshipLabel,
                        onValueChange = { value -> onForm { copy(customRelationshipLabel = value) } },
                        label = stringResource(R.string.family_relationship_custom),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            EditorSection(title = stringResource(R.string.family_section_church_language)) {
                Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                    Text(
                        text = stringResource(R.string.family_church),
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = CzSpacing.minTouchTarget)
                            .border(1.dp, MaterialTheme.czColors.divider, RoundedCornerShape(CzRadius.md))
                            .clickable { showChurchPicker = true }
                            .padding(horizontal = CzSpacing.base, vertical = CzSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.base),
                    ) {
                        Text(
                            text = editor.form.church.ifBlank { stringResource(R.string.church_picker_title) },
                            modifier = Modifier.weight(1f),
                            color = if (editor.form.church.isBlank()) {
                                MaterialTheme.czColors.textSecondary
                            } else {
                                MaterialTheme.czColors.textPrimary
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowForwardIos,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                FamilyDropdown(
                    label = stringResource(R.string.family_preferred_language),
                    selectedLabel = editor.form.preferredLanguage.localizedName(),
                    options = PreferredLanguage.entries.map { it to it.localizedName() },
                    onSelected = { value -> onForm { copy(preferredLanguage = value) } },
                )
            }

            EditorSection(title = stringResource(R.string.family_section_emergency)) {
                CzTextField(
                    value = editor.form.emergencyContactName,
                    onValueChange = { value -> onForm { copy(emergencyContactName = value) } },
                    label = stringResource(R.string.family_contact_name),
                    modifier = Modifier.fillMaxWidth(),
                )
                CzTextField(
                    value = editor.form.emergencyContactPhone,
                    onValueChange = { value -> onForm { copy(emergencyContactPhone = value) } },
                    label = stringResource(R.string.family_contact_phone),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                CzTextField(
                    value = editor.form.medicalNotes,
                    onValueChange = { value -> onForm { copy(medicalNotes = value) } },
                    label = stringResource(R.string.family_medical_notes),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                )
            }

            EditorSection(title = stringResource(R.string.family_section_consent)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.base),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.family_consent_text),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.czColors.textPrimary,
                    )
                    Switch(
                        checked = editor.form.hasGuardianConsent,
                        onCheckedChange = { value -> onForm { copy(hasGuardianConsent = value) } },
                    )
                }
            }

            if (editor.errorMessage != null) {
                Text(
                    text = editor.errorMessage,
                    color = MaterialTheme.czColors.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Box(modifier = Modifier.padding(bottom = CzSpacing.xxl))
        }
    }

    val duplicate = editor.pendingDuplicate
    if (duplicate != null) {
        AlertDialog(
            onDismissRequest = onCancelDuplicate,
            title = { Text(stringResource(R.string.family_duplicate_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.family_duplicate_message,
                        duplicate.existing.displayName,
                        duplicate.existing.age,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmDuplicate) {
                    Text(stringResource(R.string.family_duplicate_add_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelDuplicate) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showChurchPicker) {
        ModalBottomSheet(
            sheetState = churchSheetState,
            onDismissRequest = { showChurchPicker = false },
        ) {
            ChurchPickerSheet(
                query = churchQuery,
                groups = churchGroups,
                selectedChurch = editor.form.church,
                isLoading = isLoadingChurches,
                errorMessage = churchError,
                onQueryChange = onChurchQueryChange,
                onSelectChurch = { church ->
                    onSelectChurch(church.name)
                    showChurchPicker = false
                },
                onClear = {
                    onSelectChurch("")
                    showChurchPicker = false
                },
            )
        }
    }
}

@Composable
private fun FamilyHeader(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionLoading: Boolean = false,
    actionEnabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(CzSpacing.xl),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.base),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onNavigateBack) {
            Text(stringResource(R.string.common_back))
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.headlineSmall,
        )
        if (actionLabel != null && onAction != null) {
            CzButton(
                text = actionLabel,
                onClick = onAction,
                enabled = actionEnabled,
                loading = actionLoading,
                variant = CzButtonVariant.Primary,
            )
        }
    }
}

@Composable
private fun EditorSection(
    title: String,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Text(
            text = title,
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.labelLarge,
        )
        CzCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
                content = content,
            )
        }
        if (footer != null) {
            Text(
                text = footer,
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ReadOnlyRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CzSpacing.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            color = MaterialTheme.czColors.primary,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun <T> FamilyDropdown(
    label: String,
    selectedLabel: String,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Text(
            text = label,
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = CzSpacing.minTouchTarget)
                    .border(1.dp, MaterialTheme.czColors.divider, RoundedCornerShape(CzRadius.md))
                    .clickable { expanded = true }
                    .padding(horizontal = CzSpacing.base, vertical = CzSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.base),
            ) {
                Text(
                    text = selectedLabel,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (value, optionLabel) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(optionLabel) },
                        onClick = {
                            expanded = false
                            onSelected(value)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChildValidationBanner(
    errors: List<ChildValidationError>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.czColors.error.copy(alpha = 0.12f),
                RoundedCornerShape(CzRadius.md),
            )
            .padding(CzSpacing.base),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Text(
            text = stringResource(R.string.family_fix_following),
            color = MaterialTheme.czColors.error,
            style = MaterialTheme.typography.titleSmall,
        )
        errors.forEach { error ->
            Text(
                text = "- ${error.localizedMessage()}",
                color = MaterialTheme.czColors.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun FamilyFeedbackBanner(
    feedback: FamilyFeedback?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (feedback == null) return
    val isError = feedback is FamilyFeedback.PermissionDenied || feedback is FamilyFeedback.Failure
    val message = when (feedback) {
        FamilyFeedback.Saved -> stringResource(R.string.family_saved)
        FamilyFeedback.Removed -> stringResource(R.string.family_removed)
        FamilyFeedback.PermissionDenied -> stringResource(R.string.family_restricted_message)
        is FamilyFeedback.Failure -> feedback.message
    }
    val tone = if (isError) MaterialTheme.czColors.error else MaterialTheme.czColors.success
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(CzSpacing.base),
        shape = RoundedCornerShape(CzRadius.md),
        color = tone.copy(alpha = 0.12f),
        contentColor = tone,
    ) {
        Row(
            modifier = Modifier.padding(CzSpacing.base),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Text(text = message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) }
        }
    }
}

@Composable
private fun ChildValidationError.localizedMessage(): String =
    stringResource(
        when (this) {
            ChildValidationError.DisplayNameRequired -> R.string.family_error_name_required
            ChildValidationError.AgeRequired -> R.string.family_error_age_required
            ChildValidationError.AgeOutOfRange -> R.string.family_error_age_range
            ChildValidationError.ChurchRequired -> R.string.family_error_church_required
            ChildValidationError.EmergencyContactRequired -> R.string.family_error_contact_required
            ChildValidationError.EmergencyPhoneRequired -> R.string.family_error_phone_required
            ChildValidationError.RelationshipLabelRequired -> R.string.family_error_relationship_required
            ChildValidationError.GuardianConsentRequired -> R.string.family_error_consent_required
        },
    )

@Composable
private fun UserGender.localizedName(): String =
    stringResource(
        when (this) {
            UserGender.Female -> R.string.gender_female
            UserGender.Male -> R.string.gender_male
            UserGender.PreferNotToSay -> R.string.gender_prefer_not_to_say
        },
    )

@Composable
private fun CampingAgeGroup.localizedName(): String =
    stringResource(
        when (this) {
            CampingAgeGroup.Kids -> R.string.age_group_kids
            CampingAgeGroup.Youth -> R.string.age_group_youth
            CampingAgeGroup.Adult -> R.string.age_group_adult
        },
    )

@Composable
private fun FamilyRelationship.localizedName(): String =
    stringResource(
        when (this) {
            FamilyRelationship.Parent -> R.string.relationship_parent
            FamilyRelationship.StepParent -> R.string.relationship_step_parent
            FamilyRelationship.LegalGuardian -> R.string.relationship_legal_guardian
            FamilyRelationship.Grandparent -> R.string.relationship_grandparent
            FamilyRelationship.Sibling -> R.string.relationship_sibling
            FamilyRelationship.Aunt -> R.string.relationship_aunt
            FamilyRelationship.Uncle -> R.string.relationship_uncle
            FamilyRelationship.Cousin -> R.string.relationship_cousin
            FamilyRelationship.Friend -> R.string.relationship_friend
            FamilyRelationship.Other -> R.string.relationship_other
        },
    )

@Composable
private fun PreferredLanguage.localizedName(): String =
    stringResource(
        when (this) {
            PreferredLanguage.English -> R.string.language_english
            PreferredLanguage.Mandarin -> R.string.language_mandarin
            PreferredLanguage.Hindi -> R.string.language_hindi
            PreferredLanguage.Spanish -> R.string.language_spanish
            PreferredLanguage.French -> R.string.language_french
            PreferredLanguage.Arabic -> R.string.language_arabic
            PreferredLanguage.Bengali -> R.string.language_bengali
            PreferredLanguage.Portuguese -> R.string.language_portuguese
            PreferredLanguage.Russian -> R.string.language_russian
            PreferredLanguage.Urdu -> R.string.language_urdu
            PreferredLanguage.Indonesian -> R.string.language_indonesian
            PreferredLanguage.German -> R.string.language_german
            PreferredLanguage.Japanese -> R.string.language_japanese
            PreferredLanguage.Swahili -> R.string.language_swahili
            PreferredLanguage.Marathi -> R.string.language_marathi
            PreferredLanguage.Telugu -> R.string.language_telugu
            PreferredLanguage.Turkish -> R.string.language_turkish
            PreferredLanguage.Tamil -> R.string.language_tamil
            PreferredLanguage.Vietnamese -> R.string.language_vietnamese
            PreferredLanguage.Korean -> R.string.language_korean
            PreferredLanguage.Italian -> R.string.language_italian
            PreferredLanguage.Thai -> R.string.language_thai
            PreferredLanguage.Gujarati -> R.string.language_gujarati
            PreferredLanguage.Persian -> R.string.language_persian
            PreferredLanguage.Polish -> R.string.language_polish
            PreferredLanguage.Ukrainian -> R.string.language_ukrainian
            PreferredLanguage.Malay -> R.string.language_malay
            PreferredLanguage.Kannada -> R.string.language_kannada
            PreferredLanguage.Oromo -> R.string.language_oromo
            PreferredLanguage.Romanian -> R.string.language_romanian
        },
    )

@Preview(showBackground = true)
@Composable
private fun FamilyParticipantsScreenPreview() {
    CampzoneTheme {
        FamilyListContent(
            uiState = FamilyUiState(
                canManageFamily = true,
                listState = FamilyListState.Loaded(
                    listOf(
                        ChildParticipant(
                            id = "1",
                            guardianId = "preview",
                            displayName = "Ana Santos",
                            age = 10,
                            gender = UserGender.Female,
                            church = "Paris Central SDA",
                            preferredLanguage = "fr",
                            emergencyContactName = "Maria",
                            emergencyContactPhone = "+33 1 00 00 00 00",
                            relationship = FamilyRelationship.Parent,
                        ),
                    ),
                ),
            ),
            authenticatedUser = AuthenticatedUser(
                uid = "preview",
                email = "p@example.com",
                displayName = "Preview",
                photoUrl = null,
                role = UserRole.Adult,
                church = "Paris Central SDA",
                age = 40,
                preferredLanguage = "fr",
                gender = null,
                onboardingCompleted = true,
            ),
            onNavigateBack = {},
            onAdd = {},
            onEdit = {},
            onDelete = {},
            onRetry = {},
        )
    }
}
