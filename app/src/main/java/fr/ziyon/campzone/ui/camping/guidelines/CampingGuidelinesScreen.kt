package fr.ziyon.campzone.ui.camping.guidelines

import android.widget.TextView
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import io.noties.markwon.Markwon
import java.util.Date

@Composable
fun CampingGuidelinesRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CampingGuidelinesViewModel = hiltViewModel(),
) {
    LaunchedEffect(campingId) { viewModel.load(campingId, authenticatedUser) }
    val state by viewModel.uiState.collectAsState()

    CampingGuidelinesScreen(
        state = state,
        campingId = campingId,
        onBack = onBack,
        onRetry = { viewModel.load(campingId, authenticatedUser) },
        onSave = { body, onSuccess -> viewModel.saveGuidelines(campingId, body, onSuccess) },
        onDelete = { viewModel.deleteGuidelines(campingId) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampingGuidelinesScreen(
    state: CampingGuidelinesUiState,
    campingId: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSave: (body: String, onSuccess: () -> Unit) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var draftBody by rememberSaveable { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val camping = state.camping

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.czColors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.camping_guidelines),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.czColors.textPrimary,
                    )
                },
                navigationIcon = {
                    if (isEditing) {
                        TextButton(
                            onClick = {
                                val original = camping?.guidelines.orEmpty()
                                if (draftBody == original) {
                                    isEditing = false
                                } else {
                                    showDiscardDialog = true
                                }
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.common_cancel),
                                color = MaterialTheme.czColors.textSecondary,
                            )
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                                tint = MaterialTheme.czColors.textPrimary,
                            )
                        }
                    }
                },
                actions = {
                    if (isEditing) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(end = CzSpacing.md),
                                color = MaterialTheme.czColors.ember,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            TextButton(
                                onClick = {
                                    val trimmed = draftBody.trim()
                                    onSave(trimmed) { isEditing = false }
                                },
                            ) {
                                Text(
                                    text = stringResource(R.string.common_save),
                                    color = MaterialTheme.czColors.ember,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    } else if (state.canEditGuidelines && camping != null) {
                        GuidelinesOverflowMenu(
                            hasContent = camping.guidelines.isNotBlank(),
                            onEdit = {
                                draftBody = camping.guidelines
                                isEditing = true
                            },
                            onDelete = { showDeleteDialog = true },
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.czColors.background,
                ),
                windowInsets = WindowInsets()
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> CzLoadingView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                message = stringResource(R.string.camping_loading),
            )

            state.errorMessage != null || camping == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CzErrorState(
                    title = stringResource(R.string.camping_guidelines_unavailable),
                    message = state.errorMessage,
                    onRetry = onRetry,
                    retryLabel = stringResource(R.string.common_retry),
                )
            }

            isEditing -> GuidelinesEditMode(
                draftBody = draftBody,
                isSaving = state.isSaving,
                onDraftChange = { draftBody = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            else -> GuidelinesReadMode(
                camping = camping,
                canEdit = state.canEditGuidelines,
                onCreateClick = {
                    draftBody = ""
                    isEditing = true
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.camping_guidelines_delete_title)) },
            text = { Text(stringResource(R.string.camping_guidelines_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.common_delete),
                        color = MaterialTheme.czColors.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.camping_guidelines_discard_title)) },
            text = { Text(stringResource(R.string.camping_guidelines_discard_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        isEditing = false
                        draftBody = ""
                    },
                ) {
                    Text(
                        text = stringResource(R.string.camping_guidelines_discard_action),
                        color = MaterialTheme.czColors.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.camping_guidelines_keep_editing))
                }
            },
        )
    }
}

// ── Read mode ─────────────────────────────────────────────────────────────────

@Composable
private fun GuidelinesReadMode(
    camping: Camping,
    canEdit: Boolean,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        GuidelinesHeader(camping = camping)

        if (camping.guidelines.isBlank()) {
            GuidelinesEmptyState(canEdit = canEdit, onCreateClick = onCreateClick)
        } else {
            GuidelinesMarkdownCard(text = camping.guidelines)
        }
    }
}

@Composable
private fun GuidelinesHeader(camping: Camping) {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CzSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.czColors.ember.copy(alpha = 0.25f),
                                MaterialTheme.czColors.amber.copy(alpha = 0.18f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.czColors.ember,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = camping.title,
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.camping_guidelines_subtitle),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun GuidelinesMarkdownCard(text: String) {
    val context = LocalContext.current
    val markwon = remember { Markwon.create(context) }
    val spanned = remember(text) { markwon.toMarkdown(text) }
    val textColor = MaterialTheme.czColors.textPrimary.toArgb()

    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(CzSpacing.md),
        )
    }
}

@Composable
private fun GuidelinesEmptyState(canEdit: Boolean, onCreateClick: () -> Unit) {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CzSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = null,
                tint = MaterialTheme.czColors.textSecondary,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = stringResource(R.string.camping_guidelines_empty_title),
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (canEdit) {
                    stringResource(R.string.camping_guidelines_empty_admin)
                } else {
                    stringResource(R.string.camping_guidelines_empty_user)
                },
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            if (canEdit) {
                Spacer(modifier = Modifier.height(CzSpacing.sm))
                CzButton(
                    text = stringResource(R.string.camping_guidelines_create),
                    onClick = onCreateClick,
                    variant = CzButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }
    }
}

// ── Edit mode ─────────────────────────────────────────────────────────────────

@Composable
private fun GuidelinesEditMode(
    draftBody: String,
    isSaving: Boolean,
    onDraftChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = null,
                tint = MaterialTheme.czColors.ember,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(R.string.camping_guidelines_format_hint),
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        TextField(
            value = draftBody,
            onValueChange = onDraftChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = CzSpacing.lg),
            placeholder = {
                Text(
                    text = stringResource(R.string.camping_guidelines_placeholder),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.czColors.textPrimary,
            ),
            shape = RoundedCornerShape(CzRadius.lg),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.czColors.surface,
                unfocusedContainerColor = MaterialTheme.czColors.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            enabled = !isSaving,
            singleLine = false,
        )

        Spacer(modifier = Modifier.height(CzSpacing.md))
    }
}

// ── Overflow menu ─────────────────────────────────────────────────────────────

@Composable
private fun GuidelinesOverflowMenu(
    hasContent: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.camping_guidelines_options),
                tint = MaterialTheme.czColors.textPrimary,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (hasContent) {
                            stringResource(R.string.camping_guidelines_edit)
                        } else {
                            stringResource(R.string.camping_guidelines_create)
                        },
                    )
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Edit, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            if (hasContent) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.common_delete),
                            color = MaterialTheme.czColors.error,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.czColors.error,
                        )
                    },
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun GuidelinesReadPreview() {
    CampzoneTheme {
        CampingGuidelinesScreen(
            state = CampingGuidelinesUiState(
                isLoading = false,
                camping = previewCamping().copy(
                    guidelines = "## Packing list\n- Sleeping bag\n- Flashlight\n- Bible\n\n## Rules\nRespect each other.",
                ),
                canEditGuidelines = true,
            ),
            campingId = "preview",
            onBack = {},
            onRetry = {},
            onSave = { _, _ -> },
            onDelete = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GuidelinesEmptyAdminPreview() {
    CampzoneTheme {
        CampingGuidelinesScreen(
            state = CampingGuidelinesUiState(
                isLoading = false,
                camping = previewCamping(),
                canEditGuidelines = true,
            ),
            campingId = "preview",
            onBack = {},
            onRetry = {},
            onSave = { _, _ -> },
            onDelete = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GuidelinesEmptyUserPreview() {
    CampzoneTheme {
        CampingGuidelinesScreen(
            state = CampingGuidelinesUiState(
                isLoading = false,
                camping = previewCamping(),
                canEditGuidelines = false,
            ),
            campingId = "preview",
            onBack = {},
            onRetry = {},
            onSave = { _, _ -> },
            onDelete = {},
        )
    }
}

private fun previewCamping() = Camping(
    id = "summer-2026",
    title = "Summer Pathfinder Camp",
    description = "A great camp experience.",
    startDate = Date(),
    endDate = Date(),
    organizerLevel = OrganizerLevel(OrganizerType.Church, "Paris Central SDA"),
    location = "Camp Forêt, France",
    registrationStatus = CampingRegistrationStatus.Open,
)
