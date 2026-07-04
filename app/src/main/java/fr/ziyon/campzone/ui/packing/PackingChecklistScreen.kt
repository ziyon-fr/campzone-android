package fr.ziyon.campzone.ui.packing

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Note
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.packing.PackingCategorySnapshot
import fr.ziyon.campzone.data.packing.PackingChecklistSnapshot
import fr.ziyon.campzone.data.packing.PackingCustomItem
import fr.ziyon.campzone.data.packing.PackingItemRowState

@Composable
fun PackingChecklistRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
    viewModel: PackingChecklistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(campingId, authenticatedUser.uid) { viewModel.load(campingId, authenticatedUser) }
    PackingChecklistScreen(
        state = state,
        onBack = onBack,
        onOpenEditor = onOpenEditor,
        onToggle = viewModel::toggle,
        onMarkAll = viewModel::markAllReady,
        onClear = viewModel::clearChecklist,
        onSaveNotes = viewModel::saveNotes,
        onAddItem = viewModel::addCustomItem,
        onEditItem = viewModel::editCustomItem,
        onDeleteItem = viewModel::deleteCustomItem,
        onPrepareShare = viewModel::prepareShare,
        onRevokeShares = viewModel::revokeAllShares,
        onRetry = { viewModel.load(campingId, authenticatedUser) },
        onClearFeedback = viewModel::clearFeedback,
    )
}

private sealed interface ItemDialogState {
    data class Add(val categoryId: String?) : ItemDialogState
    data class Edit(val item: PackingCustomItem) : ItemDialogState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackingChecklistScreen(
    state: PackingChecklistUiState,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
    onToggle: (String) -> Unit,
    onMarkAll: () -> Unit,
    onClear: () -> Unit,
    onSaveNotes: (String) -> Unit,
    onAddItem: (String, String?) -> Unit,
    onEditItem: (String, String, String?) -> Unit,
    onDeleteItem: (String) -> Unit,
    onPrepareShare: () -> Unit,
    onRevokeShares: () -> Unit,
    onRetry: () -> Unit,
    onClearFeedback: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var clearConfirmation by remember { mutableStateOf(false) }
    var revokeConfirmation by remember { mutableStateOf(false) }
    var itemDialog by remember { mutableStateOf<ItemDialogState?>(null) }
    val context = LocalContext.current

    LaunchedEffect(state.preparedShareUrl) {
        state.preparedShareUrl?.let { url ->
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }, null))
            onClearFeedback()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.czColors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.packing_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back)) } },
                actions = {
                    if (state.canEdit) IconButton(onClick = onOpenEditor) { Icon(Icons.Rounded.Settings, stringResource(R.string.packing_edit_list)) }
                    if (state.snapshot?.hasItems == true) Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreHoriz, stringResource(R.string.packing_checklist_options)) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.packing_add_item)) },
                                leadingIcon = { Icon(Icons.Rounded.Add, null) },
                                onClick = { menuOpen = false; itemDialog = ItemDialogState.Add(null) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.packing_mark_all_ready)) },
                                leadingIcon = { Icon(Icons.Rounded.Check, null) },
                                onClick = { menuOpen = false; onMarkAll() },
                            )
                            if (state.progress?.customItems?.isNotEmpty() == true) DropdownMenuItem(
                                text = { Text(stringResource(R.string.packing_share_items)) },
                                leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                onClick = { menuOpen = false; onPrepareShare() },
                            )
                            if (state.ownedShareCount > 0) DropdownMenuItem(
                                text = { Text(stringResource(R.string.packing_revoke_links), color = MaterialTheme.czColors.error) },
                                leadingIcon = { Icon(Icons.Rounded.LinkOff, null, tint = MaterialTheme.czColors.error) },
                                onClick = { menuOpen = false; revokeConfirmation = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.packing_clear), color = MaterialTheme.czColors.error) },
                                leadingIcon = { Icon(Icons.Rounded.RestartAlt, null, tint = MaterialTheme.czColors.error) },
                                onClick = { menuOpen = false; clearConfirmation = true },
                            )
                        }
                    }
                }, windowInsets = WindowInsets()
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null && state.template == null -> CzErrorState(
                title = stringResource(R.string.packing_load_error),
                message = state.error,
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            state.snapshot == null -> PackingNotReadyState(
                canEdit = state.canEdit,
                onCreate = onOpenEditor,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> PackingLoadedContent(
                snapshot = state.snapshot,
                progressItems = state.progress?.customItems.orEmpty(),
                message = state.message,
                error = state.error,
                saving = state.saving,
                onToggle = onToggle,
                onAdd = { itemDialog = ItemDialogState.Add(if (it == PackingChecklistSnapshot.GeneralCategoryId) null else it) },
                onEdit = { id -> state.progress?.customItems?.firstOrNull { it.id == id }?.let { itemDialog = ItemDialogState.Edit(it) } },
                onDelete = onDeleteItem,
                onSaveNotes = onSaveNotes,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }

    if (clearConfirmation) AlertDialog(
        onDismissRequest = { clearConfirmation = false },
        title = { Text(stringResource(R.string.packing_clear_title)) },
        text = { Text(stringResource(R.string.packing_clear_message)) },
        confirmButton = { TextButton(onClick = { clearConfirmation = false; onClear() }) { Text(stringResource(R.string.packing_clear), color = MaterialTheme.czColors.error) } },
        dismissButton = { TextButton(onClick = { clearConfirmation = false }) { Text(stringResource(R.string.packing_keep_progress)) } },
    )

    if (revokeConfirmation) AlertDialog(
        onDismissRequest = { revokeConfirmation = false },
        title = { Text(stringResource(R.string.packing_revoke_title)) },
        text = { Text(stringResource(R.string.packing_revoke_message)) },
        confirmButton = {
            TextButton(onClick = { revokeConfirmation = false; onRevokeShares() }) {
                Text(stringResource(R.string.packing_revoke_links), color = MaterialTheme.czColors.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { revokeConfirmation = false }) {
                Text(stringResource(R.string.packing_keep_links))
            }
        },
    )

    itemDialog?.let { dialog ->
        val initial = (dialog as? ItemDialogState.Edit)?.item
        PackingItemDialog(
            initialTitle = initial?.title.orEmpty(),
            initialCategoryId = initial?.categoryId ?: (dialog as? ItemDialogState.Add)?.categoryId,
            categories = state.template?.sortedCategories.orEmpty().map { it.id to it.title },
            onDismiss = { itemDialog = null },
            onSave = { title, categoryId ->
                if (initial == null) onAddItem(title, categoryId) else onEditItem(initial.id, title, categoryId)
                itemDialog = null
            },
        )
    }
}

@Composable
private fun PackingNotReadyState(canEdit: Boolean, onCreate: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(CzSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Checklist, null, tint = MaterialTheme.czColors.textSecondary, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(CzSpacing.lg))
        Text(stringResource(R.string.packing_not_ready), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.czColors.textPrimary)
        Text(
            stringResource(if (canEdit) R.string.packing_not_ready_leader else R.string.packing_not_ready_participant),
            color = MaterialTheme.czColors.textSecondary,
            modifier = Modifier.padding(vertical = CzSpacing.sm),
        )
        if (canEdit) CzButton(stringResource(R.string.packing_create), onCreate, modifier = Modifier.padding(top = CzSpacing.md))
    }
}

@Composable
private fun PackingLoadedContent(
    snapshot: PackingChecklistSnapshot,
    progressItems: List<PackingCustomItem>,
    message: String?,
    error: String?,
    saving: Boolean,
    onToggle: (String) -> Unit,
    onAdd: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSaveNotes: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var notes by remember(snapshot.campingId) { mutableStateOf(snapshot.notes) }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        if (saving) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        message?.let { FeedbackText(it, MaterialTheme.czColors.success) }
        error?.let { FeedbackText(it, MaterialTheme.czColors.error) }
        PackingProgressHeader(snapshot)
        snapshot.categories.forEach { category ->
            PackingCategoryCard(category, onToggle, onAdd, onEdit, onDelete)
        }
        Text(stringResource(R.string.packing_personal_notes), fontWeight = FontWeight.SemiBold, color = MaterialTheme.czColors.textSecondary)
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            placeholder = { Text(stringResource(R.string.packing_notes_hint)) },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Note, null) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = packingCardColor(),
                unfocusedContainerColor = packingCardColor(),
                disabledContainerColor = packingCardColor(),
            ),
        )
        TextButton(onClick = { onSaveNotes(notes) }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.packing_save_notes)) }
    }
}

@Composable
private fun FeedbackText(text: String, color: Color) = Text(
    text,
    color = color,
    modifier = Modifier.fillMaxWidth().background(color.copy(alpha = 0.08f), RoundedCornerShape(CzRadius.md)).padding(CzSpacing.md),
)

@Composable
private fun PackingProgressHeader(snapshot: PackingChecklistSnapshot) {
    Surface(color = packingCardColor(), shape = RoundedCornerShape(CzRadius.xl)) {
        Row(Modifier.fillMaxWidth().padding(CzSpacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.lg)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                CircularProgressIndicator(
                    progress = { snapshot.progress.coerceAtLeast(0.001f) },
                    color = if (snapshot.isComplete) MaterialTheme.czColors.success else MaterialTheme.czColors.accent,
                    trackColor = MaterialTheme.czColors.divider,
                    strokeWidth = 7.dp,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(if (snapshot.isComplete) "✓" else "${(snapshot.progress * 100).toInt()}%", fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.packing_progress_prompt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.czColors.textSecondary)
                Text(pluralStringResource(R.plurals.packing_items_ready, snapshot.totalItems, snapshot.checkedItems, snapshot.totalItems), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.czColors.textPrimary)
                snapshot.campName?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.czColors.textSecondary, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun PackingCategoryCard(
    category: PackingCategorySnapshot,
    onToggle: (String) -> Unit,
    onAdd: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Surface(color = packingCardColor(), shape = RoundedCornerShape(CzRadius.xl)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = CzSpacing.lg, vertical = CzSpacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(CzRadius.md)).background((if (category.isComplete) MaterialTheme.czColors.success else MaterialTheme.czColors.accent).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Checklist, null, tint = if (category.isComplete) MaterialTheme.czColors.success else MaterialTheme.czColors.accent) }
                Text(category.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.czColors.textPrimary, modifier = Modifier.weight(1f))
                Text("${category.checkedCount}/${category.totalCount}", style = MaterialTheme.typography.labelMedium, color = if (category.isComplete) MaterialTheme.czColors.success else MaterialTheme.czColors.textSecondary)
            }
            HorizontalDivider(color = MaterialTheme.czColors.divider)
            category.rows.forEach { row ->
                PackingItemRow(row, { onToggle(row.id) }, { onEdit(row.id) }, { onDelete(row.id) })
                HorizontalDivider(color = MaterialTheme.czColors.divider, modifier = Modifier.padding(start = 56.dp))
            }
            TextButton(onClick = { onAdd(category.id) }, modifier = Modifier.padding(horizontal = CzSpacing.sm)) {
                Icon(Icons.Rounded.Add, null)
                Text(stringResource(R.string.packing_add_item), modifier = Modifier.padding(start = CzSpacing.xs))
            }
        }
    }
}

@Composable
private fun PackingItemRow(row: PackingItemRowState, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val stateDescription = stringResource(if (row.isChecked) R.string.packing_state_packed else R.string.packing_state_not_packed)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Checkbox
                this.stateDescription = stateDescription
            }
            .clickable(onClick = onToggle)
            .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Box(
            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(if (row.isChecked) MaterialTheme.czColors.success else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = if (row.isChecked) Icons.Rounded.Check else Icons.Rounded.CheckBoxOutlineBlank,
            null, modifier = Modifier.size(16.dp)) }
        Text(
            row.title,
            color = if (row.isChecked) MaterialTheme.czColors.textSecondary else MaterialTheme.czColors.textPrimary,
            textDecoration = if (row.isChecked) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
        if (row.isCustom) Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.MoreHoriz, stringResource(R.string.packing_item_options)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.common_edit)) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text(stringResource(R.string.common_delete)) }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun packingCardColor(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.czColors.surface else Color.White

@Composable
private fun PackingItemDialog(
    initialTitle: String,
    initialCategoryId: String?,
    categories: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var categoryId by remember { mutableStateOf(initialCategoryId) }
    var categoryMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initialTitle.isBlank()) R.string.packing_add_item else R.string.packing_edit_item)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
            OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.packing_item)) }, singleLine = true)
            Box {
                TextButton(onClick = { categoryMenu = true }) { Text(categories.firstOrNull { it.first == categoryId }?.second ?: stringResource(R.string.packing_my_items)) }
                DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.packing_my_items)) }, onClick = { categoryId = null; categoryMenu = false })
                    categories.forEach { category -> DropdownMenuItem(text = { Text(category.second) }, onClick = { categoryId = category.first; categoryMenu = false }) }
                }
            }
        } },
        confirmButton = { TextButton(onClick = { onSave(title, categoryId) }, enabled = title.isNotBlank()) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
