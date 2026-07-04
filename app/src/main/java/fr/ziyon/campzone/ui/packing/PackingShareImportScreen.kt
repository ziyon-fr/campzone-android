package fr.ziyon.campzone.ui.packing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors

@Composable
fun PackingShareImportRoute(
    campingId: String,
    shareId: String,
    userId: String,
    onBack: () -> Unit,
    onOpenChecklist: () -> Unit,
    viewModel: PackingShareImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(campingId, shareId, userId) { viewModel.load(campingId, shareId, userId) }
    PackingShareImportScreen(
        state,
        onBack,
        onOpenChecklist,
        viewModel::toggle,
        viewModel::importSelected,
        onRetry = { viewModel.load(campingId, shareId, userId) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackingShareImportScreen(
    state: PackingShareImportUiState,
    onBack: () -> Unit,
    onOpenChecklist: () -> Unit,
    onToggle: (String) -> Unit,
    onImport: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.czColors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.packing_add_shared_items)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back)) } },
            )
        },
        bottomBar = {
            if (!state.loading && state.importedCount == null && state.rows.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().background(MaterialTheme.czColors.surface).padding(CzSpacing.lg)) {
                    CzButton(
                        text = stringResource(R.string.packing_add_selected, state.selectedIds.size),
                        onClick = onImport,
                        enabled = state.selectedIds.isNotEmpty(),
                        loading = state.importing,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null && state.share == null -> CzErrorState(
                title = stringResource(R.string.packing_shared_load_error),
                message = state.error,
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            state.importedCount != null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(CzSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.czColors.success, modifier = Modifier.size(56.dp))
                Text(stringResource(R.string.packing_added_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = CzSpacing.lg))
                Text(pluralStringResource(R.plurals.packing_items_added, state.importedCount, state.importedCount), color = MaterialTheme.czColors.textSecondary, modifier = Modifier.padding(vertical = CzSpacing.md))
                CzButton(stringResource(R.string.packing_view_my_checklist), onOpenChecklist)
            }
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(CzSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
            ) {
                val newCount = state.rows.count { !it.alreadyHave }
                Surface(color = MaterialTheme.czColors.surface, shape = RoundedCornerShape(CzRadius.xl)) {
                    Column(Modifier.fillMaxWidth().padding(CzSpacing.lg), verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                        Text(stringResource(R.string.packing_shared_by, state.share?.ownerName.orEmpty()), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        state.share?.campName?.let { Text(it, color = MaterialTheme.czColors.textSecondary) }
                        Text(if (newCount == 0) stringResource(R.string.packing_shared_all_present) else pluralStringResource(R.plurals.packing_shared_new_count, newCount, newCount), style = MaterialTheme.typography.labelMedium, color = if (newCount == 0) MaterialTheme.czColors.success else MaterialTheme.czColors.textSecondary)
                    }
                }
                Surface(color = MaterialTheme.czColors.surface, shape = RoundedCornerShape(CzRadius.xl)) {
                    Column {
                        state.rows.forEachIndexed { index, row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable(enabled = !row.alreadyHave) { onToggle(row.item.id) }.padding(CzSpacing.lg),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                            ) {
                                Box(
                                    modifier = Modifier.size(24.dp).clip(CircleShape).background(if (row.item.id in state.selectedIds) MaterialTheme.czColors.accent else Color.Transparent),
                                    contentAlignment = Alignment.Center,
                                ) { if (row.item.id in state.selectedIds || row.alreadyHave) Icon(Icons.Rounded.Check, null, tint = if (row.alreadyHave) MaterialTheme.czColors.textSecondary else Color.White, modifier = Modifier.size(14.dp)) }
                                Column(Modifier.weight(1f)) {
                                    Text(row.item.title, color = if (row.alreadyHave) MaterialTheme.czColors.textSecondary else MaterialTheme.czColors.textPrimary)
                                    row.item.categoryTitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.czColors.textSecondary) }
                                }
                                if (row.alreadyHave) Text(stringResource(R.string.packing_on_your_list), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.czColors.textSecondary)
                            }
                            if (index < state.rows.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                        }
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.czColors.error) }
            }
        }
    }
}
