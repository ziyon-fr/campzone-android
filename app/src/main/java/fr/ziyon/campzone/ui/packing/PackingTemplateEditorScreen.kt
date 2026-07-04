package fr.ziyon.campzone.ui.packing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.packing.PackingCategory
import fr.ziyon.campzone.data.packing.PackingItem

@Composable
fun PackingTemplateEditorRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    viewModel: PackingChecklistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(campingId, authenticatedUser.uid) { viewModel.load(campingId, authenticatedUser) }
    PackingTemplateEditorScreen(
        state = state,
        onBack = onBack,
        onLoadSuggested = viewModel::loadSuggested,
        onAddCategory = viewModel::addCategory,
        onUpdateCategory = viewModel::updateCategory,
        onDeleteCategory = viewModel::deleteCategory,
        onMoveCategory = viewModel::moveCategory,
        onAddItem = viewModel::addItem,
        onUpdateItem = viewModel::updateItem,
        onDeleteItem = viewModel::deleteItem,
        onMoveItem = viewModel::moveItem,
    )
}

private sealed interface EditorDialog {
    data class Category(val category: PackingCategory?) : EditorDialog
    data class Item(val categoryId: String, val item: PackingItem?) : EditorDialog
    data class DeleteCategory(val category: PackingCategory) : EditorDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackingTemplateEditorScreen(
    state: PackingChecklistUiState,
    onBack: () -> Unit,
    onLoadSuggested: () -> Unit,
    onAddCategory: (String, String) -> Unit,
    onUpdateCategory: (String, String, String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onMoveCategory: (String, Int) -> Unit,
    onAddItem: (String, String) -> Unit,
    onUpdateItem: (String, String, String) -> Unit,
    onDeleteItem: (String, String) -> Unit,
    onMoveItem: (String, String, Int) -> Unit,
) {
    var dialog by remember { mutableStateOf<EditorDialog?>(null) }
    Scaffold(
        containerColor = MaterialTheme.czColors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.packing_edit_list)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back)) } },
                windowInsets = WindowInsets()
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            !state.canEdit -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text(stringResource(R.string.packing_leadership_only)) }
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(CzSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
            ) {
                if (state.template?.categories.isNullOrEmpty()) Surface(color = MaterialTheme.czColors.surface, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(CzSpacing.lg), verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                        Text(stringResource(R.string.packing_no_sections), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.packing_no_sections_message), color = MaterialTheme.czColors.textSecondary)
                        TextButton(onClick = onLoadSuggested) { Icon(Icons.Rounded.AutoAwesome, null); Text(stringResource(R.string.packing_load_suggested), modifier = Modifier.padding(start = CzSpacing.xs)) }
                    }
                }
                state.template?.sortedCategories.orEmpty().forEach { category ->
                    TemplateCategoryCard(
                        category = category,
                        onEditCategory = { dialog = EditorDialog.Category(category) },
                        onDeleteCategory = { dialog = EditorDialog.DeleteCategory(category) },
                        onMoveCategory = { onMoveCategory(category.id, it) },
                        onAddItem = { dialog = EditorDialog.Item(category.id, null) },
                        onEditItem = { dialog = EditorDialog.Item(category.id, it) },
                        onDeleteItem = { onDeleteItem(category.id, it) },
                        onMoveItem = { itemId, direction -> onMoveItem(category.id, itemId, direction) },
                    )
                }
                Surface(color = MaterialTheme.czColors.surface, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth()) {
                        TextButton(onClick = { dialog = EditorDialog.Category(null) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Text(stringResource(R.string.packing_add_section), modifier = Modifier.padding(start = CzSpacing.xs)) }
                        if (state.template?.categories?.isNotEmpty() == true) {
                            HorizontalDivider()
                            TextButton(onClick = onLoadSuggested, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AutoAwesome, null); Text(stringResource(R.string.packing_load_suggested), modifier = Modifier.padding(start = CzSpacing.xs)) }
                        }
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.czColors.error) }
                state.message?.let { Text(it, color = MaterialTheme.czColors.success) }
            }
        }
    }

    when (val value = dialog) {
        is EditorDialog.Category -> CategoryDialog(value.category, { dialog = null }) { title, icon ->
            if (value.category == null) onAddCategory(title, icon) else onUpdateCategory(value.category.id, title, icon)
            dialog = null
        }
        is EditorDialog.Item -> NameDialog(stringResource(if (value.item == null) R.string.packing_add_item else R.string.packing_edit_item), value.item?.title.orEmpty(), { dialog = null }) { title ->
            if (value.item == null) onAddItem(value.categoryId, title) else onUpdateItem(value.categoryId, value.item.id, title)
            dialog = null
        }
        is EditorDialog.DeleteCategory -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.packing_delete_section_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.packing_delete_section_message,
                        value.category.items.size,
                        value.category.title,
                        value.category.items.size,
                    ),
                )
            },
            confirmButton = { TextButton(onClick = { onDeleteCategory(value.category.id); dialog = null }) { Text(stringResource(R.string.packing_delete_named_section, value.category.title), color = MaterialTheme.czColors.error) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.packing_keep_section)) } },
        )
        null -> Unit
    }
}

@Composable
private fun TemplateCategoryCard(
    category: PackingCategory,
    onEditCategory: () -> Unit,
    onDeleteCategory: () -> Unit,
    onMoveCategory: (Int) -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (PackingItem) -> Unit,
    onDeleteItem: (String) -> Unit,
    onMoveItem: (String, Int) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.czColors.surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(CzSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                Text(category.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "Section options") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.packing_edit_section)) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menuOpen = false; onEditCategory() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.packing_move_up)) }, leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null) }, onClick = { menuOpen = false; onMoveCategory(-1) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.packing_move_down)) }, leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null) }, onClick = { menuOpen = false; onMoveCategory(1) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.packing_delete_section), color = MaterialTheme.czColors.error) }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menuOpen = false; onDeleteCategory() })
                    }
                }
            }
            HorizontalDivider()
            category.sortedItems.forEach { item ->
                var itemMenu by remember(item.id) { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().clickable { onEditItem(item) }.padding(start = CzSpacing.md, top = CzSpacing.sm, bottom = CzSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, modifier = Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { itemMenu = true }) { Icon(Icons.Rounded.MoreVert, "Item options") }
                        DropdownMenu(expanded = itemMenu, onDismissRequest = { itemMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.common_edit)) }, onClick = { itemMenu = false; onEditItem(item) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.packing_move_up)) }, onClick = { itemMenu = false; onMoveItem(item.id, -1) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.packing_move_down)) }, onClick = { itemMenu = false; onMoveItem(item.id, 1) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.common_delete)) }, onClick = { itemMenu = false; onDeleteItem(item.id) })
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = CzSpacing.md))
            }
            TextButton(onClick = onAddItem, modifier = Modifier.padding(horizontal = CzSpacing.sm)) { Icon(Icons.Rounded.Add, null); Text(stringResource(R.string.packing_add_item), modifier = Modifier.padding(start = CzSpacing.xs)) }
        }
    }
}

@Composable
private fun CategoryDialog(category: PackingCategory?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf(category?.title.orEmpty()) }
    var icon by remember { mutableStateOf(category?.iconName ?: "checklist") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (category == null) R.string.packing_add_section else R.string.packing_edit_section)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.packing_section_name)) })
            OutlinedTextField(icon, { icon = it }, label = { Text(stringResource(R.string.packing_icon)) })
        } },
        confirmButton = { TextButton(onClick = { onSave(title, icon) }, enabled = title.isNotBlank()) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, label = { Text(stringResource(R.string.packing_name)) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(value) }, enabled = value.isNotBlank()) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
