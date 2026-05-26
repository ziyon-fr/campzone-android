package fr.ziyon.campzone.ui.teams

import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HolidayVillage
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.Team
import fr.ziyon.campzone.data.teams.FakeTeamService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ── Symbol catalogue (matches iOS wire values exactly) ────────────────────────

private data class TeamSymbol(val wireKey: String, val icon: ImageVector)

private val teamSymbols = listOf(
    TeamSymbol("shield.lefthalf.filled", Icons.Outlined.Security),
    TeamSymbol("flame.fill", Icons.Outlined.LocalFireDepartment),
    TeamSymbol("paperplane.fill", Icons.Outlined.Send),
    TeamSymbol("bolt.fill", Icons.Outlined.FlashOn),
    TeamSymbol("leaf.fill", Icons.Outlined.Eco),
    TeamSymbol("sparkles", Icons.Outlined.AutoAwesome),
    TeamSymbol("star.fill", Icons.Outlined.Star),
    TeamSymbol("flag.fill", Icons.Outlined.Flag),
    TeamSymbol("mountain.2.fill", Icons.Outlined.Landscape),
    TeamSymbol("tent.2.fill", Icons.Outlined.HolidayVillage),
    TeamSymbol("binoculars.fill", Icons.Outlined.Groups),
    TeamSymbol("pawprint.fill", Icons.Outlined.Pets),
    TeamSymbol("compass.drawing", Icons.Outlined.Explore),
    TeamSymbol("tree.fill", Icons.Outlined.Park),
    TeamSymbol("map.fill", Icons.Outlined.Map),
    TeamSymbol("target", Icons.Outlined.TrackChanges),
)

fun symbolIcon(wireKey: String): ImageVector =
    teamSymbols.firstOrNull { it.wireKey == wireKey }?.icon ?: Icons.Outlined.Groups

// ── Color presets ─────────────────────────────────────────────────────────────

private val colorPresets = listOf(
    "#D9432F", "#2364AA", "#2A9D8F", "#E9C46A",
    "#264653", "#F4A261", "#8338EC", "#06D6A0",
    "#EF476F", "#118AB2", "#073B4C", "#FFB703",
)

private fun String.toArgb(): Int = runCatching {
    android.graphics.Color.parseColor(this)
}.getOrDefault(android.graphics.Color.GRAY)

private fun Int.toHex(): String =
    String.format("#%06X", 0xFFFFFF and this)

// ── Route ─────────────────────────────────────────────────────────────────────

@Composable
fun TeamEditorRoute(
    campingId: String,
    teamId: String?,
    camping: Camping?,
    authenticatedUser: AuthenticatedUser,
    viewModel: TeamViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val evaluator = remember { AppPermissionEvaluator() }
    val permissionUser = PermissionUser(
        role = authenticatedUser.role,
        userId = authenticatedUser.uid,
        church = authenticatedUser.church,
    )
    val campingCtx = camping?.let { c ->
        CampingPermissionContext(
            organizerLevelType = c.organizerLevel.type.wireValue,
            organizerLevelValue = c.organizerLevel.value,
            createdByUid = c.createdByUid,
        )
    }
    val canManageTeams = campingCtx != null && evaluator.canManageTeams(permissionUser, campingCtx)

    val form by viewModel.form.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isUploadingPhoto by viewModel.isUploadingPhoto.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    val editingTeamId by viewModel.editingTeamId.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(campingId) { viewModel.loadIfNeeded(campingId) }
    DisposableEffect(campingId) {
        viewModel.startObserving(campingId)
        onDispose { viewModel.stopObserving() }
    }

    // Prepare the form once data is ready (handles both shared and fresh ViewModel)
    LaunchedEffect(teamId, uiState) {
        if (teamId != null) {
            if (editingTeamId != teamId) {
                val team = viewModel.team(teamId, campingId)
                if (team != null) viewModel.prepareEdit(team)
            }
        } else if (editingTeamId != null) {
            viewModel.prepareNew(campingId)
        }
    }

    TeamEditorScreen(
        isNewTeam = teamId == null,
        canManageTeams = canManageTeams,
        form = form,
        isSaving = isSaving,
        isUploadingPhoto = isUploadingPhoto,
        operationError = operationError,
        campingId = campingId,
        onFormUpdate = viewModel::updateForm,
        onSave = { viewModel.saveTeam(campingId, onBack) },
        onBack = onBack,
        onUploadPhoto = { bytes, mimeType, ext -> viewModel.uploadPhoto(bytes, mimeType, ext) },
        onRemovePhoto = viewModel::removePhoto,
        onClearError = viewModel::clearOperationError,
    )
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamEditorScreen(
    isNewTeam: Boolean,
    canManageTeams: Boolean,
    form: TeamForm,
    isSaving: Boolean,
    isUploadingPhoto: Boolean,
    operationError: String?,
    campingId: String,
    onFormUpdate: ((TeamForm) -> TeamForm) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onUploadPhoto: (ByteArray, String, String) -> Unit,
    onRemovePhoto: () -> Unit,
    onClearError: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@launch
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
            onUploadPhoto(bytes, mimeType, ext)
        }
    }

    val title = if (isNewTeam) stringResource(R.string.teams_new_team)
    else stringResource(R.string.teams_edit_team)

    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onSave,
                        enabled = form.isValid && !isSaving && !isUploadingPhoto,
                    ) {
                        Text(
                            stringResource(R.string.common_save),
                            color = if (form.isValid && !isSaving) colors.ember else colors.textSecondary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                windowInsets = WindowInsets()
            )
        },
    ) { innerPadding ->
        if (!canManageTeams) {
            RestrictedContent(modifier = Modifier.padding(innerPadding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            // Validation errors
            if (form.validationErrors.isNotEmpty()) {
                ValidationBanner(errors = form.validationErrors)
            }

            // Operation error
            if (operationError != null) {
                OperationErrorBanner(message = operationError, onDismiss = onClearError)
            }

            // Saving / uploading overlay hint
            if (isSaving || isUploadingPhoto) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CzSpacing.md),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = colors.ember,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(CzSpacing.sm))
                    Text(
                        if (isUploadingPhoto) stringResource(R.string.teams_uploading_photo)
                        else stringResource(R.string.teams_saving),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }

            // Identity section
            EditorSection(
                header = { EditorSectionHeader(stringResource(R.string.teams_editor_identity)) },
            ) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { v -> onFormUpdate { it.copy(name = v) } },
                    label = { Text(stringResource(R.string.teams_editor_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors(colors),
                )
                OutlinedTextField(
                    value = form.slogan,
                    onValueChange = { v -> onFormUpdate { it.copy(slogan = v) } },
                    label = { Text(stringResource(R.string.teams_editor_slogan_hint)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors(colors),
                )
            }

            // Logo section
            EditorSection(
                header = { EditorSectionHeader(stringResource(R.string.teams_editor_logo)) },
            ) {
                LogoPickerRow(
                    photoUrl = form.photoUrl,
                    isUploading = isUploadingPhoto,
                    onPickPhoto = { photoLauncher.launch("image/*") },
                    onRemovePhoto = onRemovePhoto,
                )
            }

            // Preview card (shown when name is non-empty)
            if (form.name.isNotBlank() || form.photoUrl != null) {
                EditorSection(
                    header = { EditorSectionHeader(stringResource(R.string.teams_editor_preview)) },
                ) {
                    TeamPreviewCard(form = form)
                }
            }

            // Symbol section
            EditorSection(
                header = { EditorSectionHeader(stringResource(R.string.teams_editor_symbol)) },
            ) {
                SymbolPickerGrid(
                    selection = form.symbolName,
                    teamColor = form.colorHex.toComposeColor() ?: colors.ember,
                    onSelect = { key -> onFormUpdate { it.copy(symbolName = key) } },
                )
            }

            // Color section
            EditorSection(
                header = { EditorSectionHeader(stringResource(R.string.teams_editor_color)) },
            ) {
                ColorPickerRow(
                    selectedHex = form.colorHex,
                    onSelect = { hex -> onFormUpdate { it.copy(colorHex = hex) } },
                )
            }
        }
    }
}

// ── Section wrapper ───────────────────────────────────────────────────────────

@Composable
private fun EditorSection(
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.czColors
    Column(modifier = Modifier.fillMaxWidth()) {
        header()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CzSpacing.md)
                .background(colors.surface, RoundedCornerShape(CzRadius.lg))
                .padding(CzSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            content()
        }
    }
}

@Composable
private fun EditorSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.czColors.textSecondary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = CzSpacing.lg, vertical = CzSpacing.xs),
    )
}

// ── Logo picker ───────────────────────────────────────────────────────────────

@Composable
private fun LogoPickerRow(
    photoUrl: String?,
    isUploading: Boolean,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
) {
    val colors = MaterialTheme.czColors

    if (photoUrl != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .border(1.dp, colors.divider, CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.teams_editor_current_logo),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                )
                Text(
                    stringResource(R.string.teams_editor_logo_from_cloudinary),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            TextButton(onClick = onRemovePhoto) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = colors.error,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.teams_editor_remove_photo),
                    color = colors.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CzRadius.md))
                .clickable(enabled = !isUploading, onClick = onPickPhoto)
                .border(1.dp, colors.divider, RoundedCornerShape(CzRadius.md))
                .padding(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            if (isUploading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = colors.ember,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    Icons.Outlined.Photo,
                    contentDescription = null,
                    tint = colors.ember,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                if (isUploading) stringResource(R.string.teams_uploading_photo)
                else stringResource(R.string.teams_editor_pick_photo),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUploading) colors.textSecondary else colors.ember,
            )
        }
    }
}

// ── Preview card ──────────────────────────────────────────────────────────────

@Composable
private fun TeamPreviewCard(form: TeamForm) {
    val colors = MaterialTheme.czColors
    val teamColor = form.colorHex.toComposeColor() ?: colors.ember

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(teamColor.copy(alpha = 0.15f))
                .border(2.dp, teamColor, CircleShape),
        ) {
            if (form.photoUrl != null) {
                AsyncImage(
                    model = form.photoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                )
            } else {
                Icon(
                    symbolIcon(form.symbolName),
                    contentDescription = null,
                    tint = teamColor,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Column {
            Text(
                form.name.ifBlank { "Team name" },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (form.name.isBlank()) colors.textSecondary else colors.textPrimary,
            )
            if (form.slogan.isNotBlank()) {
                Text(
                    form.slogan,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

// ── Symbol picker grid ────────────────────────────────────────────────────────

@Composable
private fun SymbolPickerGrid(
    selection: String,
    teamColor: Color,
    onSelect: (String) -> Unit,
) {
    val colors = MaterialTheme.czColors
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier
            .fillMaxWidth()
            .height((4 * 68).dp),
        contentPadding = PaddingValues(0.dp),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        userScrollEnabled = false,
    ) {
        items(teamSymbols) { symbol ->
            val isSelected = symbol.wireKey == selection
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(CzRadius.md))
                    .background(if (isSelected) teamColor else colors.background)
                    .border(
                        1.5.dp,
                        if (isSelected) teamColor else colors.divider,
                        RoundedCornerShape(CzRadius.md),
                    )
                    .clickable { onSelect(symbol.wireKey) },
            ) {
                Icon(
                    symbol.icon,
                    contentDescription = symbol.wireKey.replace(".", " "),
                    tint = if (isSelected) Color.White else colors.textSecondary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

// ── Color picker ──────────────────────────────────────────────────────────────

@Composable
private fun ColorPickerRow(
    selectedHex: String,
    onSelect: (String) -> Unit,
) {
    val colors = MaterialTheme.czColors
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        // Selected color swatch + label
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(CzRadius.sm))
                    .background(selectedHex.toComposeColor() ?: colors.ember)
                    .border(1.dp, colors.divider, RoundedCornerShape(CzRadius.sm)),
            )
            Text(
                selectedHex.uppercase(),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = colors.textSecondary,
            )
        }
        // Preset grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier
                .fillMaxWidth()
                .height((2 * 52).dp),
            contentPadding = PaddingValues(0.dp),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            userScrollEnabled = false,
        ) {
            items(colorPresets) { hex ->
                val color = hex.toComposeColor() ?: Color.Gray
                val isSelected = hex.equals(selectedHex, ignoreCase = true)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            if (isSelected) 3.dp else 0.dp,
                            Color.White,
                            CircleShape,
                        )
                        .clickable { onSelect(hex) },
                ) {}
            }
        }
    }
}

// ── Banners ───────────────────────────────────────────────────────────────────

@Composable
private fun ValidationBanner(errors: List<String>) {
    val colors = MaterialTheme.czColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CzSpacing.md)
            .background(colors.error.copy(alpha = 0.08f), RoundedCornerShape(CzRadius.lg))
            .padding(CzSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Text(
            "Please fix the following:",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.error,
        )
        errors.forEach { error ->
            Text(
                "• $error",
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
            )
        }
    }
}

@Composable
private fun OperationErrorBanner(message: String, onDismiss: () -> Unit) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CzSpacing.md)
            .background(colors.error.copy(alpha = 0.08f), RoundedCornerShape(CzRadius.lg))
            .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = colors.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.common_dismiss), color = colors.error)
        }
    }
}

@Composable
private fun RestrictedContent(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.czColors
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(CzSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(CzSpacing.md))
        Text(
            stringResource(R.string.teams_restricted_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(CzSpacing.xs))
        Text(
            stringResource(R.string.teams_restricted_message),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun textFieldColors(colors: fr.ziyon.campzone.core.designsystem.CzColorPalette) =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.ember,
        unfocusedBorderColor = colors.divider,
        focusedLabelColor = colors.ember,
        cursorColor = colors.ember,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        unfocusedLabelColor = colors.textSecondary,
    )

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview
@Composable
private fun PreviewTeamEditorNew() {
    CampzoneTheme {
        TeamEditorScreen(
            isNewTeam = true,
            canManageTeams = true,
            form = TeamForm(name = "Lions", slogan = "Courage in service", colorHex = "#D9432F"),
            isSaving = false,
            isUploadingPhoto = false,
            operationError = null,
            campingId = "preview",
            onFormUpdate = {},
            onSave = {},
            onBack = {},
            onUploadPhoto = { _, _, _ -> },
            onRemovePhoto = {},
            onClearError = {},
        )
    }
}

@Preview
@Composable
private fun PreviewTeamEditorRestricted() {
    CampzoneTheme {
        TeamEditorScreen(
            isNewTeam = true,
            canManageTeams = false,
            form = TeamForm(),
            isSaving = false,
            isUploadingPhoto = false,
            operationError = null,
            campingId = "preview",
            onFormUpdate = {},
            onSave = {},
            onBack = {},
            onUploadPhoto = { _, _, _ -> },
            onRemovePhoto = {},
            onClearError = {},
        )
    }
}
