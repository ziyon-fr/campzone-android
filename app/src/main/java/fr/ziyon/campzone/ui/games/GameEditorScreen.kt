@file:android.annotation.SuppressLint("ViewModelConstructorInComposable")

package fr.ziyon.campzone.ui.games

import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.games.FakeGameService
import fr.ziyon.campzone.data.games.previewGame
import fr.ziyon.campzone.data.model.PointRule
import fr.ziyon.campzone.data.model.PointRuleTarget
import fr.ziyon.campzone.data.model.PointRuleVisibility
import fr.ziyon.campzone.data.model.VenuePoint
import fr.ziyon.campzone.data.model.GameInstructionAttachment
import fr.ziyon.campzone.data.camping.PreviewCampingService
import fr.ziyon.campzone.data.teams.FakeTeamService
import fr.ziyon.campzone.ui.venuemap.VenuePinGlyph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun GameEditorRoute(
    campingId: String,
    gameId: String?,
    authenticatedUser: AuthenticatedUser,
    viewModel: GameViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val instructionImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val targetGameId = viewModel.editingGameId ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
            viewModel.uploadInstructionImage(bytes, mimeType, ext, targetGameId, campingId)
        }
    }
    LaunchedEffect(campingId) {
        viewModel.loadIfNeeded(campingId)
        viewModel.loadVenuePoints(campingId)
    }

    LaunchedEffect(gameId, uiState) {
        if (gameId != null && viewModel.editingGameId != gameId) {
            val existing = viewModel.game(gameId, campingId)
            if (existing != null) {
                viewModel.prepareEditingGame(existing)
                viewModel.prepareInstructions(existing.id, campingId)
            }
        } else if (gameId == null && viewModel.editingGameId == null) {
            viewModel.prepareNewGame()
        }
    }

    GameEditorScreen(
        isEditing = gameId != null,
        form = viewModel.form,
        venuePoints = viewModel.venuePointsFor(campingId),
        instructionsForm = viewModel.instructionsForm,
        isSavingInstructions = viewModel.isSavingInstructions,
        isUploadingInstructionImage = viewModel.isUploadingInstructionImage,
        instructionsError = viewModel.instructionsError,
        validationErrors = viewModel.validationErrors,
        isSaving = viewModel.isSaving,
        onFormChange = viewModel::updateForm,
        onInstructionsChange = viewModel::updateInstructionsForm,
        onPickInstructionImage = { instructionImageLauncher.launch("image/*") },
        onRemoveInstructionAttachment = { attachment ->
            val targetGameId = viewModel.editingGameId
            if (targetGameId != null) {
                scope.launch {
                    viewModel.removeInstructionAttachment(attachment, targetGameId, campingId)
                }
            }
        },
        onSave = {
            val scope = kotlinx.coroutines.MainScope()
            scope.launch {
                val saved = viewModel.saveGame(campingId, authenticatedUser.uid)
                if (saved != null) {
                    viewModel.saveInstructions(saved.id, campingId)
                    onSaved()
                }
            }
        },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameEditorScreen(
    isEditing: Boolean,
    form: GameForm,
    venuePoints: List<VenuePoint>,
    instructionsForm: GameInstructionsForm,
    isSavingInstructions: Boolean,
    isUploadingInstructionImage: Boolean,
    instructionsError: String?,
    validationErrors: List<GameValidationError>,
    isSaving: Boolean,
    onFormChange: ((GameForm) -> GameForm) -> Unit,
    onInstructionsChange: ((GameInstructionsForm) -> GameInstructionsForm) -> Unit,
    onPickInstructionImage: () -> Unit,
    onRemoveInstructionAttachment: (GameInstructionAttachment) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    var showRuleSheet by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<PointRule?>(null) }

    if (showRuleSheet) {
        PointRuleEditorSheet(
            rule = editingRule,
            onSave = { rule ->
                onFormChange { f ->
                    val updated = f.pointRules.toMutableList()
                    val idx = updated.indexOfFirst { it.id == rule.id }
                    if (idx >= 0) updated[idx] = rule else updated.add(rule)
                    f.copy(pointRules = updated)
                }
                editingRule = null
                showRuleSheet = false
            },
            onDismiss = { editingRule = null; showRuleSheet = false },
        )
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (isEditing) stringResource(R.string.games_edit_title) else stringResource(R.string.games_new_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.common_back), tint = colors.textPrimary)
                    }
                },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(end = CzSpacing.sm), color = colors.ember, strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = onSave) {
                            Text(stringResource(R.string.common_save), color = colors.ember, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                windowInsets = WindowInsets()
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
        ) {
            Surface(color = colors.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(CzSpacing.lg), verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
                    Text(stringResource(R.string.games_section_info), style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
                    OutlinedTextField(
                        value = form.name,
                        onValueChange = { onFormChange { f -> f.copy(name = it) } },
                        label = { Text(stringResource(R.string.games_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        isError = validationErrors.contains(GameValidationError.NameRequired),
                        supportingText = if (validationErrors.contains(GameValidationError.NameRequired)) {
                            { Text(stringResource(R.string.games_validation_name_required)) }
                        } else null,
                    )
                    OutlinedTextField(
                        value = form.rules,
                        onValueChange = { onFormChange { f -> f.copy(rules = it) } },
                        label = { Text(stringResource(R.string.games_rules_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    )
                }
            }

            GameLocationsEditorCard(
                venuePoints = venuePoints,
                selectedIds = form.venuePointIds,
                visibleToAll = form.locationVisibleToAll,
                onTogglePoint = { pointId ->
                    onFormChange { f ->
                        val updated = if (pointId in f.venuePointIds) {
                            f.venuePointIds - pointId
                        } else {
                            f.venuePointIds + pointId
                        }
                        f.copy(venuePointIds = updated.distinct())
                    }
                },
                onVisibilityChange = { visible ->
                    onFormChange { f -> f.copy(locationVisibleToAll = visible) }
                },
            )

            GameInstructionsEditorCard(
                form = instructionsForm,
                isSaving = isSavingInstructions,
                isUploading = isUploadingInstructionImage,
                error = instructionsError,
                onChange = onInstructionsChange,
                onPickImage = onPickInstructionImage,
                onRemoveAttachment = onRemoveInstructionAttachment,
            )

            Surface(color = colors.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(CzSpacing.lg), verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.games_point_rules), style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
                        TextButton(onClick = { editingRule = null; showRuleSheet = true }) {
                            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                            Text(stringResource(R.string.games_add_rule), color = colors.ember)
                        }
                    }
                    if (validationErrors.contains(GameValidationError.PointRulesEmpty)) {
                        Text(stringResource(R.string.games_validation_rules_required), style = MaterialTheme.typography.bodySmall, color = colors.error)
                    }
                    if (form.pointRules.isEmpty()) {
                        Text(stringResource(R.string.games_no_rules_hint), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    } else {
                        form.pointRules.forEachIndexed { index, rule ->
                            RuleEditorRow(
                                rule = rule,
                                onEdit = { editingRule = rule; showRuleSheet = true },
                                onDelete = { onFormChange { f -> f.copy(pointRules = f.pointRules.filter { it.id != rule.id }) } },
                            )
                            if (index < form.pointRules.size - 1) {
                                HorizontalDivider(color = colors.divider)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(CzSpacing.xxl))
        }
    }
}

@Composable
private fun GameInstructionsEditorCard(
    form: GameInstructionsForm,
    isSaving: Boolean,
    isUploading: Boolean,
    error: String?,
    onChange: ((GameInstructionsForm) -> GameInstructionsForm) -> Unit,
    onPickImage: () -> Unit,
    onRemoveAttachment: (GameInstructionAttachment) -> Unit,
) {
    val colors = MaterialTheme.czColors
    Surface(color = colors.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CzSpacing.lg), verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.games_instructions_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textSecondary,
                )
                TextButton(onClick = onPickImage, enabled = !isUploading && !isSaving) {
                    if (isUploading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = colors.ember, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp), tint = colors.ember)
                    }
                    Text(
                        if (isUploading) {
                            stringResource(R.string.games_instructions_uploading)
                        } else {
                            stringResource(R.string.games_instructions_add_image)
                        },
                        color = colors.ember,
                    )
                }
            }
            OutlinedTextField(
                value = form.title,
                onValueChange = { value -> onChange { it.copy(title = value) } },
                label = { Text(stringResource(R.string.games_instructions_title_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = { value -> onChange { it.copy(description = value) } },
                label = { Text(stringResource(R.string.games_instructions_description_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 7,
            )
            error?.takeIf { it.isNotBlank() }?.let {
                Text(
                    stringResource(R.string.games_instructions_error_prefix, it),
                    color = colors.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (form.images.isEmpty()) {
                Text(
                    stringResource(R.string.games_instructions_empty_attachments),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                form.images.forEach { attachment ->
                    InstructionAttachmentRow(
                        attachment = attachment,
                        onRemove = { onRemoveAttachment(attachment) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InstructionAttachmentRow(
    attachment: GameInstructionAttachment,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = attachment.url,
            contentDescription = attachment.displayName,
            modifier = Modifier.size(56.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                attachment.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            Text(
                attachment.kind.name.lowercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.Delete, stringResource(R.string.common_delete), tint = colors.error)
        }
    }
}

@Composable
private fun GameLocationsEditorCard(
    venuePoints: List<VenuePoint>,
    selectedIds: List<String>,
    visibleToAll: Boolean,
    onTogglePoint: (String) -> Unit,
    onVisibilityChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.czColors
    Surface(color = colors.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CzSpacing.lg), verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            Text(stringResource(R.string.games_locations_title), style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
            if (venuePoints.isEmpty()) {
                Text(
                    stringResource(R.string.games_locations_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            } else {
                venuePoints.forEach { point ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VenuePinGlyph(
                            category = point.category,
                            iconName = point.resolvedIconName,
                            diameter = 28.dp,
                        )
                        Text(
                            text = point.name,
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Checkbox(
                            checked = point.id in selectedIds,
                            onCheckedChange = { onTogglePoint(point.id) },
                        )
                    }
                }
                HorizontalDivider(color = colors.divider)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.games_locations_visible_all),
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.games_locations_visible_all_hint),
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Switch(
                        checked = visibleToAll,
                        onCheckedChange = onVisibilityChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleEditorRow(rule: PointRule, onEdit: () -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.czColors
    Surface(onClick = onEdit, color = colors.surface, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(vertical = CzSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.name, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                Text(
                    "${rule.appliesTo.displayName} · ${rule.visibility.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
            Text(
                if (rule.points >= 0) "+${rule.points}" else "${rule.points}",
                style = MaterialTheme.typography.titleSmall,
                color = if (rule.points >= 0) colors.success else colors.error,
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.common_delete), tint = colors.error, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointRuleEditorSheet(
    rule: PointRule?,
    onSave: (PointRule) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(rule?.name ?: "") }
    var pointsText by remember { mutableStateOf(rule?.points?.toString() ?: "") }
    var reason by remember { mutableStateOf(rule?.reason ?: "") }
    var category by remember { mutableStateOf(rule?.category ?: "") }
    var appliesTo by remember { mutableStateOf(rule?.appliesTo ?: PointRuleTarget.Team) }
    var visibility by remember { mutableStateOf(rule?.visibility ?: PointRuleVisibility.Immediate) }

    val isValid = name.isNotBlank() && pointsText.toIntOrNull() != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CzSpacing.lg)
                .padding(bottom = CzSpacing.xxxl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { scope.launch { sheetState.hide(); onDismiss() } }) {
                    Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
                }
                Text(
                    if (rule == null) stringResource(R.string.games_new_rule) else stringResource(R.string.games_edit_rule),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                TextButton(
                    onClick = {
                        val pts = pointsText.toIntOrNull() ?: return@TextButton
                        val saved = PointRule(
                            id = rule?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            points = pts,
                            reason = reason.trim(),
                            category = category.trim().takeUnless { it.isBlank() },
                            appliesTo = appliesTo,
                            visibility = visibility,
                        )
                        onSave(saved)
                    },
                    enabled = isValid,
                ) { Text(stringResource(R.string.common_save), color = if (isValid) colors.ember else colors.textSecondary) }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.games_rule_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = pointsText,
                onValueChange = { pointsText = it },
                label = { Text(stringResource(R.string.games_rule_points_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text(stringResource(R.string.games_rule_reason_label)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text(stringResource(R.string.games_rule_category_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.games_rule_applies_to), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PointRuleTarget.entries.forEachIndexed { index, target ->
                    SegmentedButton(
                        selected = appliesTo == target,
                        onClick = { appliesTo = target },
                        shape = SegmentedButtonDefaults.itemShape(index, PointRuleTarget.entries.size),
                        label = { Text(target.displayName, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            Text(stringResource(R.string.games_rule_visibility), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PointRuleVisibility.entries.forEachIndexed { index, vis ->
                    SegmentedButton(
                        selected = visibility == vis,
                        onClick = { visibility = vis },
                        shape = SegmentedButtonDefaults.itemShape(index, PointRuleVisibility.entries.size),
                        label = { Text(vis.displayName, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GameEditorScreenPreview() {
    CampzoneTheme {
        val vm = GameViewModel(
            FakeGameService(games = listOf(previewGame())),
            FakeTeamService(),
            PreviewCampingService(),
            fr.ziyon.campzone.data.teams.FakeTeamNotificationDispatcher(),
                fr.ziyon.campzone.data.media.PreviewMediaUploader,
                fr.ziyon.campzone.data.media.PreviewMediaUploader,
                fr.ziyon.campzone.data.venuemap.FakeVenueMapService(),
            )
        GameEditorRoute(
            campingId = "preview-camp",
            gameId = null,
            authenticatedUser = AuthenticatedUser(
                uid = "uid", email = "a@b.com", displayName = "Admin", photoUrl = null,
                role = UserRole.Admin, church = "Central SDA", age = 30,
                preferredLanguage = "en", gender = null, onboardingCompleted = true,
            ),
            viewModel = vm,
            onBack = {},
            onSaved = {},
        )
    }
}
