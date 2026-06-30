package fr.ziyon.campzone.ui.packing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.i18n.StringProvider
import fr.ziyon.campzone.data.packing.PackingChecklistService
import fr.ziyon.campzone.data.packing.PackingChecklistTemplate
import fr.ziyon.campzone.data.packing.PackingShare
import fr.ziyon.campzone.data.packing.PackingShareItem
import fr.ziyon.campzone.data.packing.UserPackingProgress
import fr.ziyon.campzone.data.packing.packingTitleKey
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PackingImportRow(val item: PackingShareItem, val alreadyHave: Boolean)
data class PackingShareImportUiState(
    val loading: Boolean = true,
    val share: PackingShare? = null,
    val rows: List<PackingImportRow> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val importing: Boolean = false,
    val importedCount: Int? = null,
    val error: String? = null,
)

@HiltViewModel
class PackingShareImportViewModel @Inject constructor(
    private val service: PackingChecklistService,
    private val strings: StringProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PackingShareImportUiState())
    val uiState: StateFlow<PackingShareImportUiState> = _uiState.asStateFlow()
    private var template: PackingChecklistTemplate? = null
    private var progress: UserPackingProgress? = null
    private var userId: String? = null

    fun load(campingId: String, shareId: String, userId: String) {
        this.userId = userId
        viewModelScope.launch {
            _uiState.value = PackingShareImportUiState(loading = true)
            runCatching {
                val share = service.loadShare(campingId, shareId) ?: error(strings.get(R.string.packing_import_unavailable))
                if (share.isExpired) error(strings.get(R.string.packing_import_expired))
                template = service.loadTemplate(campingId)
                progress = service.loadProgress(campingId, userId)
                val existing = existingTitles()
                val rows = share.items.map { PackingImportRow(it, packingTitleKey(it.title) in existing) }
                PackingShareImportUiState(
                    loading = false,
                    share = share,
                    rows = rows,
                    selectedIds = rows.filterNot { it.alreadyHave }.mapTo(linkedSetOf()) { it.item.id },
                )
            }.onSuccess { _uiState.value = it }
                .onFailure { _uiState.value = PackingShareImportUiState(loading = false, error = it.message) }
        }
    }

    fun toggle(id: String) = _uiState.update { state ->
        val row = state.rows.firstOrNull { it.item.id == id } ?: return@update state
        if (row.alreadyHave) state else state.copy(selectedIds = state.selectedIds.toMutableSet().apply { if (!add(id)) remove(id) })
    }

    fun importSelected() {
        val state = _uiState.value
        val share = state.share ?: return
        val userId = userId ?: return
        val selected = share.items.filter { it.id in state.selectedIds }
        if (selected.isEmpty()) return _uiState.update { it.copy(error = strings.get(R.string.packing_import_select_item)) }
        viewModelScope.launch {
            _uiState.update { it.copy(importing = true, error = null) }
            runCatching { service.mergeSharedItems(share.campingId, userId, selected) }
                .onSuccess { result ->
                    if (result.addedCount == 0) {
                        _uiState.update { it.copy(importing = false, error = strings.get(R.string.packing_import_already_present)) }
                    } else {
                        progress = result.progress
                        _uiState.update { it.copy(importing = false, importedCount = result.addedCount) }
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(importing = false, error = error.message ?: strings.get(R.string.packing_import_error)) } }
        }
    }

    private fun existingTitles(): Set<String> = buildSet {
        template?.categories.orEmpty().flatMap { it.items }.forEach { add(packingTitleKey(it.title)) }
        progress?.customItems.orEmpty().forEach { add(packingTitleKey(it.title)) }
    }
}
