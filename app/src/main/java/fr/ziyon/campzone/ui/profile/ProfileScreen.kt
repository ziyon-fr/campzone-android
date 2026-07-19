package fr.ziyon.campzone.ui.profile

import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import fr.ziyon.campzone.core.designsystem.CzColors
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
import fr.ziyon.campzone.data.church.SDAChurch
import fr.ziyon.campzone.data.profile.UserProfile
import fr.ziyon.campzone.ui.common.AllergiesEditor
import fr.ziyon.campzone.ui.common.ChurchPickerSheet
import java.text.DateFormat

@Composable
fun ProfileScreen(
    authenticatedUser: AuthenticatedUser,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateBack: (() -> Unit)? = null,
    onOpenDataExport: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val photoUploadFailed = stringResource(R.string.profile_photo_upload_failed)
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "image/jpeg"
            val extension = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(mimeType)
                ?: "jpg"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error(photoUploadFailed)
            viewModel.uploadPhoto(
                bytes = bytes,
                mimeType = mimeType,
                fileExtension = extension,
            )
        }.onFailure {
            viewModel.reportPhotoError(photoUploadFailed)
        }
    }

    LaunchedEffect(authenticatedUser.uid) {
        viewModel.load(authenticatedUser)
    }

    ProfileContent(
        state = uiState,
        authenticatedUser = authenticatedUser,
        onRetry = { viewModel.load(authenticatedUser) },
        onSave = viewModel::save,
        onDismissMessages = viewModel::dismissMessages,
        onDisplayNameChange = viewModel::updateDisplayName,
        onAgeChange = viewModel::updateAgeText,
        onGenderChange = viewModel::updateGender,
        onChurchChange = viewModel::updateChurch,
        onEmailChange = viewModel::updateEmail,
        onPreferredLanguageChange = viewModel::updatePreferredLanguage,
        onLanguageToggle = viewModel::toggleLanguage,
        onChurchQueryChange = viewModel::updateChurchQuery,
        onLoadChurches = viewModel::loadChurches,
        onRoleChange = viewModel::updateRole,
        onPhoneChange = viewModel::updatePhone,
        onProfessionChange = viewModel::updateProfession,
        onEducationChange = viewModel::updateEducation,
        onPathfinderRankChange = viewModel::updatePathfinderRank,
        onSkillsChange = viewModel::updateSkillsText,
        onAllergiesChange = viewModel::updateAllergies,
        onChangePhoto = { photoPicker.launch("image/*") },
        onRequestDeletion = { viewModel.requestAccountDeletion(onSuccess = onSignOut) },
        onCancelDeletion = viewModel::cancelAccountDeletion,
        onNavigateBack = onNavigateBack,
        onOpenDataExport = onOpenDataExport,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileContent(
    state: ProfileUiState,
    authenticatedUser: AuthenticatedUser,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    onDismissMessages: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onAgeChange: (String) -> Unit,
    onGenderChange: (UserGender?) -> Unit,
    onChurchChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPreferredLanguageChange: (String) -> Unit,
    onLanguageToggle: (String) -> Unit,
    onChurchQueryChange: (String) -> Unit,
    onLoadChurches: () -> Unit,
    onRoleChange: (UserRole) -> Unit,
    onPhoneChange: (String) -> Unit,
    onProfessionChange: (String) -> Unit,
    onEducationChange: (String) -> Unit,
    onPathfinderRankChange: (String) -> Unit,
    onSkillsChange: (String) -> Unit,
    onAllergiesChange: (List<String>) -> Unit,
    onChangePhoto: () -> Unit,
    onRequestDeletion: () -> Unit,
    onCancelDeletion: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    onOpenDataExport: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CzLoadingView(message = stringResource(R.string.profile_loading))
        }
        return
    }

    if (state.loadedUser == null && state.errorMessage != null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CzErrorState(
                title = stringResource(R.string.profile_could_not_save),
                message = state.errorMessage,
                retryLabel = stringResource(R.string.common_retry),
                onRetry = onRetry,
            )
        }
        
        return
    }

    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showReactivateDialog by rememberSaveable { mutableStateOf(false) }
    var showChurchPicker by rememberSaveable { mutableStateOf(false) }
    var showLanguagePicker by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.czColors
    val haptics = LocalHapticFeedback.current
    val churchSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val languageSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val loadedUser = state.loadedUser
    val profileName = state.form.displayName.takeUnless { it.isBlank() }
        ?: loadedUser?.preferredDisplayName
        ?: authenticatedUser.preferredDisplayName

    LaunchedEffect(showChurchPicker) {
        if (showChurchPicker) onLoadChurches()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(CzSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.base),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onNavigateBack != null) {
                    TextButton(onClick = onNavigateBack) {
                        Text(stringResource(R.string.common_back))
                    }
                }
                Text(
                    text = stringResource(R.string.profile_title),
                    modifier = Modifier.weight(1f),
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center
                )
                CzButton(
                    text = stringResource(R.string.common_save),
                    onClick = onSave,
                    enabled = loadedUser != null && !state.isSaving,
                    loading = state.isSaving,
                    variant = CzButtonVariant.Primary,
                )
            }

            ProfileAvatarHeader(
                name = profileName,
                photoUrl = loadedUser?.photoUrl ?: authenticatedUser.photoUrl,
                roleLabel = state.form.role.localizedName(),
                isUploadingPhoto = state.isUploadingPhoto,
                photoError = state.photoError,
                onChangePhoto = onChangePhoto,
            )

        AnimatedVisibility(visible = state.saveMessage != null) {
            ProfileMessageBanner(
                message = stringResource(R.string.profile_changes_saved),
                tone = ProfileMessageTone.Success,
                onDismiss = onDismissMessages,
            )
        }

        AnimatedVisibility(visible = state.errorMessage != null) {
            ProfileMessageBanner(
                message = state.errorMessage.orEmpty(),
                tone = ProfileMessageTone.Error,
                onDismiss = onDismissMessages,
            )
        }

        AnimatedVisibility(visible = state.deletionMessage != null) {
            ProfileMessageBanner(
                message = if (state.isPendingDeletion) {
                    stringResource(R.string.profile_account_scheduled_for_deletion)
                } else {
                    stringResource(R.string.profile_account_reactivated)
                },
                tone = ProfileMessageTone.Success,
                onDismiss = onDismissMessages,
            )
        }

        AnimatedVisibility(visible = state.deletionError != null) {
            ProfileMessageBanner(
                message = state.deletionError.orEmpty(),
                tone = ProfileMessageTone.Error,
                onDismiss = onDismissMessages,
            )
        }

        if (state.validationErrors.isNotEmpty()) {
            ProfileValidationBanner(errors = state.validationErrors)
        }

        ProfileSection(
            title = stringResource(R.string.profile_identity),
            icon = Icons.Rounded.Person,
        ) {
            CzTextField(
                value = state.form.displayName,
                onValueChange = onDisplayNameChange,
                label = stringResource(R.string.profile_full_name),
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { ProfileFieldIcon(Icons.Rounded.Person) },
            )
            CzTextField(
                value = state.form.ageText,
                onValueChange = onAgeChange,
                label = stringResource(R.string.profile_age),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                leadingIcon = { ProfileFieldIcon(Icons.Rounded.Info) },
            )
            ProfileDropdown(
                label = stringResource(R.string.profile_gender),
                selectedLabel = (state.form.gender ?: UserGender.PreferNotToSay).localizedName(),
                options = UserGender.entries.map { it to it.localizedName() },
                onSelected = onGenderChange,
                icon = Icons.Rounded.Wc,
            )
            ProfileReadOnlyRow(
                label = stringResource(R.string.profile_age_group),
                value = state.form.ageText.toIntOrNull()
                    ?.let { CampingAgeGroup.fromAge(it).localizedName() }
                    ?: stringResource(R.string.profile_add_age),
                icon = Icons.Rounded.Info,
            )
            CzTextField(
                value = state.form.email,
                onValueChange = onEmailChange,
                label = stringResource(R.string.profile_email),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                leadingIcon = { ProfileFieldIcon(Icons.Rounded.Language) },
            )
        }

        ProfileSection(
            title = stringResource(R.string.profile_church),
            icon = Icons.Rounded.Groups,
        ) {
            ProfileActionRow(
                icon = Icons.Rounded.Groups,
                label = state.form.church.ifBlank { stringResource(R.string.profile_select_church) },
                isPlaceholder = state.form.church.isBlank(),
                onClick = {
                    haptics.profileImpact()
                    showChurchPicker = true
                },
            )
        }

        ProfileSection(
            title = stringResource(R.string.profile_languages),
            icon = Icons.Rounded.Language,
            footer = stringResource(R.string.profile_languages_footer),
        ) {
            ProfileDropdown(
                label = stringResource(R.string.profile_preferred_language),
                selectedLabel = languageLabel(state.form.preferredLanguageCode),
                options = PreferredLanguage.entries.map { it.wireValue to it.localizedName() },
                onSelected = onPreferredLanguageChange,
                icon = Icons.Rounded.Language,
            )
            ProfileActionRow(
                icon = Icons.Rounded.Translate,
                label = displayLanguageList(state.form.languageCodes),
                isPlaceholder = state.form.languageCodes.isEmpty(),
                onClick = {
                    haptics.profileImpact()
                    showLanguagePicker = true
                },
            )
        }

        ProfileSection(
            title = stringResource(R.string.profile_role),
            icon = Icons.Rounded.Badge,
        ) {
            if (state.canEditRole) {
                ProfileDropdown(
                    label = stringResource(R.string.profile_role),
                    selectedLabel = state.form.role.localizedName(),
                    options = UserRole.selfAssignableRoles
                        .sortedBy { it.ordinal }
                        .map { it to it.localizedName() },
                    onSelected = onRoleChange,
                    icon = Icons.Rounded.Badge,
                )
            } else {
                ProfileReadOnlyRow(
                    label = stringResource(R.string.profile_assigned_role),
                    value = state.form.role.localizedName(),
                    icon = Icons.Rounded.Badge,
                )
                Text(
                    text = stringResource(R.string.profile_role_managed_by_admins),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        ProfileSection(
            title = stringResource(R.string.profile_background),
            icon = Icons.Rounded.WorkspacePremium,
            footer = stringResource(R.string.profile_skills_footer),
        ) {
            CzTextField(
                value = state.form.phone,
                onValueChange = onPhoneChange,
                label = stringResource(R.string.profile_phone),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                leadingIcon = { ProfileFieldIcon(Icons.Rounded.Phone) },
            )
            CzTextField(
                value = state.form.profession,
                onValueChange = onProfessionChange,
                label = stringResource(R.string.profile_profession),
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { ProfileFieldIcon(Icons.Rounded.Work) },
            )
            CzTextField(
                value = state.form.education,
                onValueChange = onEducationChange,
                label = stringResource(R.string.profile_education),
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { ProfileFieldIcon(Icons.Rounded.School) },
            )
            CzTextField(
                value = state.form.pathfinderRank,
                onValueChange = onPathfinderRankChange,
                label = stringResource(R.string.profile_pathfinder_rank),
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { ProfileFieldIcon(Icons.Rounded.Star) },
            )
            CzTextField(
                value = state.form.skillsText,
                onValueChange = onSkillsChange,
                label = stringResource(R.string.profile_skills),
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { ProfileFieldIcon(Icons.Rounded.Settings) },
            )
        }

        ProfileSection(
            title = stringResource(R.string.profile_allergies),
            icon = Icons.Rounded.WarningAmber,
            footer = stringResource(R.string.profile_allergies_footer),
        ) {
            AllergiesEditor(
                selected = state.form.allergies,
                onSelectedChange = onAllergiesChange,
            )
        }

        ProfileAccountSection(
            state = state,
            onOpenDataExport = onOpenDataExport,
            onShowDeleteDialog = { showDeleteDialog = true },
            onShowReactivateDialog = { showReactivateDialog = true },
        )
    }

    if (state.isSaving) {
        ProfileSavingOverlay()
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.profile_delete_account_title)) },
            text = { Text(stringResource(R.string.profile_delete_account_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onRequestDeletion()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                ) {
                    Text(stringResource(R.string.profile_delete_account))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_keep_account))
                }
            },
        )
    }

    if (showReactivateDialog) {
        AlertDialog(
            onDismissRequest = { showReactivateDialog = false },
            title = { Text(stringResource(R.string.profile_reactivate_title)) },
            text = { Text(stringResource(R.string.profile_reactivate_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReactivateDialog = false
                        onCancelDeletion()
                    },
                ) {
                    Text(stringResource(R.string.profile_reactivate))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReactivateDialog = false }) {
                    Text(stringResource(R.string.profile_keep_deletion_scheduled))
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
                query = state.churchQuery,
                groups = state.filteredChurchGroups,
                selectedChurch = state.form.church,
                isLoading = state.isLoadingChurches,
                errorMessage = state.churchError,
                onQueryChange = onChurchQueryChange,
                onSelectChurch = { church ->
                    onChurchChange(church.name)
                    showChurchPicker = false
                },
                onClear = {
                    onChurchChange("")
                    showChurchPicker = false
                },
            )
        }
    }

    if (showLanguagePicker) {
        ModalBottomSheet(
            sheetState = languageSheetState,
            onDismissRequest = { showLanguagePicker = false },
        ) {
            LanguagePickerSheet(
                selectedCodes = state.form.languageCodes,
                onToggle = onLanguageToggle,
            )
        }
    }
    }
}

@Composable
private fun ProfileAvatarHeader(
    name: String,
    photoUrl: String?,
    roleLabel: String,
    isUploadingPhoto: Boolean,
    photoError: String?,
    onChangePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = CzSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            CzAvatar(
                imageUrl = photoUrl,
                contentDescription = name,
                initials = name,
                size = CzAvatarSize.Large,
                modifier = Modifier.then(
                    if (isUploadingPhoto) {
                        Modifier.background(Color.Black.copy(alpha = 0.18f), CircleShape)
                    } else {
                        Modifier
                    },
                ),
            )
            if (isUploadingPhoto) {
                Box(
                    modifier = Modifier
                        .size(CzAvatarSize.Large.value)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.36f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .size(26.dp)
                    .clickable(enabled = !isUploadingPhoto, onClick = onChangePhoto),
                shape = CircleShape,
                color = MaterialTheme.czColors.primary,
                contentColor = MaterialTheme.czColors.onPrimary,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.background),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Camera,
                        contentDescription = null,
                        tint = CzColors.TextPrimaryDark,
                        modifier = modifier.size(18.dp),
                    )

                }
            }
        }
        Text(
            text = name.ifBlank { stringResource(R.string.profile_your_profile) },
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        CzBadge(text = roleLabel, tone = CzBadgeTone.Primary)
        if (photoError != null) {
            Text(
                text = photoError,
                color = MaterialTheme.czColors.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ProfileSavingOverlay(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(CzRadius.lg),
            color = MaterialTheme.czColors.surface,
            contentColor = MaterialTheme.czColors.textPrimary,
            border = BorderStroke(1.dp, MaterialTheme.czColors.divider),
        ) {
            Column(
                modifier = Modifier.padding(CzSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.czColors.primary,
                    strokeWidth = 2.dp,
                )
                Text(
                    text = stringResource(R.string.profile_saving),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ProfileSection(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.czColors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CzRadius.lg),
            color = colors.surface,
            contentColor = colors.textPrimary,
            border = BorderStroke(1.dp, colors.divider),
        ) {
            Column(
                modifier = Modifier.padding(CzSpacing.base),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
                content = content,
            )
        }
        if (footer != null) {
            Text(
                text = footer,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ProfileReadOnlyRow(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CzSpacing.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.base),
    ) {
        ProfileFieldIcon(icon)
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProfileFieldIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.czColors.primary,
        modifier = modifier.size(18.dp),
    )
}

@Composable
private fun ProfileActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaceholder: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CzSpacing.minTouchTarget)
            .clip(RoundedCornerShape(CzRadius.md))
            .border(1.dp, MaterialTheme.czColors.divider, RoundedCornerShape(CzRadius.md))
            .clickable(onClick = onClick)
            .padding(horizontal = CzSpacing.base, vertical = CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.base),
    ) {
        ProfileFieldIcon(icon)
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = if (isPlaceholder) MaterialTheme.czColors.textSecondary else MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun <T> ProfileDropdown(
    label: String,
    selectedLabel: String,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
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
                    .clip(RoundedCornerShape(CzRadius.md))
                    .background(Color.Transparent)
                    .border(1.dp, MaterialTheme.czColors.divider, RoundedCornerShape(CzRadius.md))
                    .clickable { expanded = true }
                    .padding(horizontal = CzSpacing.base, vertical = CzSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.base),
            ) {
                if (icon != null) {
                    ProfileFieldIcon(icon)
                }
                Text(
                    text = selectedLabel,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size( 14.dp)
                )

            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (value, optionLabel) ->
                    DropdownMenuItem(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguagePickerRow(
    selectedCodes: List<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Text(
            text = stringResource(R.string.profile_spoken_languages),
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            PreferredLanguage.entries.forEach { language ->
                FilterChip(
                    selected = selectedCodes.contains(language.wireValue),
                    onClick = { onToggle(language.wireValue) },
                    label = { Text(language.localizedName()) },
                )
            }
        }
    }
}

@Composable
private fun LanguagePickerSheet(
    selectedCodes: List<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val languages = mutableListOf<Pair<PreferredLanguage, String>>()
    for (language in PreferredLanguage.entries) {
        val label = language.localizedName()
        if (query.isBlank() ||
            label.contains(query, ignoreCase = true) ||
            language.wireValue.contains(query, ignoreCase = true)
        ) {
            languages += language to label
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .padding(CzSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Text(
            text = stringResource(R.string.profile_languages),
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.titleLarge,
        )
        CzTextField(
            value = query,
            onValueChange = { query = it },
            label = stringResource(R.string.profile_search_language),
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { ProfileFieldIcon(Icons.Rounded.Search) },
        )
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            languages.forEach { (language, label) ->
                val isSelected = selectedCodes.contains(language.wireValue)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CzRadius.md))
                        .background(
                            if (isSelected) {
                                MaterialTheme.czColors.primary.copy(alpha = 0.14f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable { onToggle(language.wireValue) }
                        .padding(CzSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        color = if (isSelected) {
                            MaterialTheme.czColors.textPrimary
                        } else {
                            MaterialTheme.czColors.textSecondary
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    )
                    if (isSelected) {
                        Text(
                            text = "OK",
                            color = MaterialTheme.czColors.primary,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileValidationBanner(
    errors: List<ProfileValidationError>,
    modifier: Modifier = Modifier,
) {
    ProfileMessageContainer(
        modifier = modifier,
        tone = ProfileMessageTone.Error,
    ) {
        Text(
            text = stringResource(R.string.profile_fix_following),
            style = MaterialTheme.typography.titleSmall,
        )
        errors.forEach { error ->
            Text(
                text = "- ${error.localizedMessage()}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ProfileMessageBanner(
    message: String,
    tone: ProfileMessageTone,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ProfileMessageContainer(modifier = modifier, tone = tone) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_ok))
            }
        }
    }
}

@Composable
private fun ProfileMessageContainer(
    tone: ProfileMessageTone,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val color = when (tone) {
        ProfileMessageTone.Error -> MaterialTheme.czColors.error
        ProfileMessageTone.Success -> MaterialTheme.czColors.success
        ProfileMessageTone.Warning -> MaterialTheme.czColors.warning
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.md))
            .background(color.copy(alpha = 0.12f))
            .padding(CzSpacing.base),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        val columnScope = this
        CompositionLocalProvider(LocalContentColor provides color) {
            columnScope.content()
        }
    }
}

@Composable
private fun ProfileAccountSection(
    state: ProfileUiState,
    onOpenDataExport: (() -> Unit)?,
    onShowDeleteDialog: () -> Unit,
    onShowReactivateDialog: () -> Unit,
) {
    ProfileSection(
        title = stringResource(R.string.profile_account),
        icon = Icons.Rounded.Key,
        footer = stringResource(R.string.profile_delete_account_footer),
    ) {
        if (onOpenDataExport != null) {
            ProfileActionRow(
                icon = Icons.Rounded.DownloadForOffline,
                label = stringResource(R.string.profile_export_my_data),
                onClick = onOpenDataExport,
            )
        }
        if (state.isPendingDeletion) {
            ProfileMessageContainer(tone = ProfileMessageTone.Warning) {
                Text(
                    text = stringResource(R.string.profile_deletion_scheduled),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = deletionScheduleText(state),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            CzButton(
                text = stringResource(R.string.profile_reactivate_account),
                onClick = onShowReactivateDialog,
                enabled = !state.isProcessingDeletion,
                loading = state.isProcessingDeletion,
                variant = CzButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            CzButton(
                text = stringResource(R.string.profile_delete_account),
                onClick = onShowDeleteDialog,
                enabled = !state.isProcessingDeletion,
                loading = state.isProcessingDeletion,
                variant = CzButtonVariant.Destructive,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun deletionScheduleText(state: ProfileUiState): String {
    val days = state.deletionDaysRemaining
    val endsAt = state.loadedUser?.deletionGraceEnds
    return when {
        days != null && endsAt != null -> pluralStringResource(
            R.plurals.profile_deletion_scheduled_with_date,
            days,
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(endsAt),
            days,
        )
        days != null -> pluralStringResource(R.plurals.profile_deletion_scheduled_with_days, days, days)
        else -> stringResource(R.string.profile_deletion_scheduled_generic)
    }
}

private enum class ProfileMessageTone {
    Error,
    Success,
    Warning,
}

@Composable
private fun ProfileValidationError.localizedMessage(): String =
    stringResource(
        when (this) {
            ProfileValidationError.DisplayNameRequired -> R.string.profile_display_name_required
            ProfileValidationError.AgeRequired -> R.string.profile_age_required
            ProfileValidationError.AgeOutOfRange -> R.string.profile_age_out_of_range
            ProfileValidationError.ChurchRequired -> R.string.profile_church_required
            ProfileValidationError.GenderRequired -> R.string.profile_gender_required
            ProfileValidationError.EmailRequired -> R.string.profile_email_required
            ProfileValidationError.EmailInvalid -> R.string.profile_email_invalid
            ProfileValidationError.PreferredLanguageRequired -> R.string.profile_preferred_language_required
            ProfileValidationError.LanguageRequired -> R.string.profile_language_required
            ProfileValidationError.RoleNotSelfAssignable -> R.string.profile_role_not_self_assignable
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
private fun UserRole.localizedName(): String =
    stringResource(
        when (this) {
            UserRole.User -> R.string.role_user
            UserRole.Adult -> R.string.role_adult
            UserRole.YouthDirector -> R.string.role_youth_director
            UserRole.Pastor -> R.string.role_pastor
            UserRole.GameMaster -> R.string.role_game_master
            UserRole.Leader -> R.string.role_leader
            UserRole.Photographer -> R.string.role_photographer
            UserRole.Admin -> R.string.role_admin
        },
    )

@Composable
private fun languageLabel(code: String): String =
    PreferredLanguage.fromWire(code)?.localizedName() ?: code

@Composable
private fun displayLanguageList(codes: List<String>): String {
    if (codes.isEmpty()) return stringResource(R.string.profile_spoken_languages)

    val labels = mutableListOf<String>()
    for (code in codes) {
        labels += languageLabel(code)
    }
    return labels.joinToString(", ")
}

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

private fun androidx.compose.ui.hapticfeedback.HapticFeedback.profileImpact() {
    performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
}

@Preview(showBackground = true)
@Composable
private fun ProfileContentPreview() {
    val user = UserProfile(
        uid = "preview",
        displayName = "Campzone Guest",
        age = 22,
        gender = UserGender.PreferNotToSay,
        church = "Paris Central SDA",
        skills = listOf("Singing", "First Aid"),
        profession = "Designer",
        education = "Bachelor",
        pathfinderRank = "Guide",
        phone = "+33 6 00 00 00 00",
        email = "user@campzone.app",
        preferredLanguage = "fr",
        languages = listOf("pt", "fr"),
        role = UserRole.User,
        photoUrl = null,
        photoPublicId = null,
        onboardingCompleted = true,
    )
    CampzoneTheme {
        ProfileContent(
            state = ProfileUiState(
                isLoading = false,
                loadedUser = user,
                form = ProfileFormState(
                    displayName = user.displayName,
                    ageText = "22",
                    gender = user.gender,
                    church = user.church,
                    skillsText = "Singing, First Aid",
                    profession = user.profession,
                    education = user.education,
                    pathfinderRank = user.pathfinderRank,
                    phone = user.phone,
                    email = user.email,
                    preferredLanguageCode = user.preferredLanguage,
                    languageCodes = user.languages,
                    role = user.role,
                ),
                canEditRole = true,
            ),
            authenticatedUser = AuthenticatedUser(
                uid = user.uid,
                email = user.email,
                displayName = user.displayName,
                photoUrl = null,
                role = user.role,
                church = user.church,
                age = user.age,
                preferredLanguage = user.preferredLanguage,
                gender = user.gender,
                onboardingCompleted = true,
            ),
            onRetry = {},
            onSave = {},
            onDismissMessages = {},
            onDisplayNameChange = {},
            onAgeChange = {},
            onGenderChange = {},
            onChurchChange = {},
            onEmailChange = {},
            onPreferredLanguageChange = {},
            onLanguageToggle = {},
            onChurchQueryChange = {},
            onLoadChurches = {},
            onRoleChange = {},
            onPhoneChange = {},
            onProfessionChange = {},
            onEducationChange = {},
            onPathfinderRankChange = {},
            onSkillsChange = {},
            onAllergiesChange = {},
            onChangePhoto = {},
            onRequestDeletion = {},
            onCancelDeletion = {},
        )
    }
}
