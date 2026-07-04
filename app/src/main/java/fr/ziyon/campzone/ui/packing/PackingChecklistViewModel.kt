package fr.ziyon.campzone.ui.packing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.i18n.StringProvider
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.navigation.CampzoneDeepLink
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.packing.PackingCategory
import fr.ziyon.campzone.data.packing.PackingChecklistCatalog
import fr.ziyon.campzone.data.packing.PackingChecklistService
import fr.ziyon.campzone.data.packing.PackingChecklistSnapshot
import fr.ziyon.campzone.data.packing.PackingChecklistTemplate
import fr.ziyon.campzone.data.packing.PackingCustomItem
import fr.ziyon.campzone.data.packing.PackingItem
import fr.ziyon.campzone.data.packing.PackingShare
import fr.ziyon.campzone.data.packing.PackingShareItem
import fr.ziyon.campzone.data.packing.UserPackingProgress
import fr.ziyon.campzone.data.packing.packingSnapshot
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PackingChecklistUiState(
    val loading: Boolean = true,
    val camping: Camping? = null,
    val template: PackingChecklistTemplate? = null,
    val progress: UserPackingProgress? = null,
    val snapshot: PackingChecklistSnapshot? = null,
    val canEdit: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val preparedShareUrl: String? = null,
    val ownedShareCount: Int = 0,
)

@HiltViewModel
class PackingChecklistViewModel @Inject constructor(
    private val campingService: CampingService,
    private val packingService: PackingChecklistService,
    private val strings: StringProvider,
) : ViewModel() {
    private val permissions = AppPermissionEvaluator()
    private val _uiState = MutableStateFlow(PackingChecklistUiState())
    val uiState: StateFlow<PackingChecklistUiState> = _uiState.asStateFlow()
    private var currentUser: AuthenticatedUser? = null

    fun load(campingId: String, user: AuthenticatedUser) {
        currentUser = user
        viewModelScope.launch {
            _uiState.value = PackingChecklistUiState(loading = true)
            runCatching {
                val camping = campingService.fetchCamping(campingId)
                val template = packingService.loadTemplate(campingId)
                val progress = packingService.loadProgress(campingId, user.uid)
                val ownedShareCount = runCatching {
                    packingService.loadOwnedShares(campingId, user.uid).size
                }.getOrDefault(0)
                val canEdit = permissions.canEditGuidelines(
                    PermissionUser(user.role, user.uid, user.church),
                    camping.permissionContext(),
                )
                PackingChecklistUiState(
                    loading = false,
                    camping = camping,
                    template = template,
                    progress = progress,
                    snapshot = if (template.isPublished || progress.customItems.isNotEmpty()) {
                        snapshot(template, progress, camping.title)
                    } else null,
                    canEdit = canEdit,
                    ownedShareCount = ownedShareCount,
                )
            }.onSuccess { _uiState.value = it }
                .onFailure { _uiState.value = PackingChecklistUiState(loading = false, error = it.message ?: strings.get(R.string.packing_load_error)) }
        }
    }

    fun toggle(itemId: String) = mutateProgress { progress ->
        progress.copy(checkedItemIds = progress.checkedItemIds.toMutableSet().apply {
            if (!add(itemId)) remove(itemId)
        })
    }

    fun markAllReady() {
        val ids = _uiState.value.snapshot?.allItemIds ?: return
        mutateProgress(message = strings.get(R.string.packing_feedback_complete)) { it.copy(checkedItemIds = ids) }
    }

    fun clearChecklist() = mutateProgress(message = strings.get(R.string.packing_feedback_cleared)) { it.copy(checkedItemIds = emptySet()) }

    fun saveNotes(notes: String) = mutateProgress { it.copy(personalNotes = notes) }

    fun addCustomItem(title: String, categoryId: String?) {
        val value = title.trim()
        if (value.isEmpty()) return setError(strings.get(R.string.packing_error_item_name))
        mutateProgress(message = strings.get(R.string.packing_feedback_item_added)) {
            it.copy(customItems = it.customItems + PackingCustomItem(categoryId = normalizeCategory(categoryId), title = value))
        }
    }

    fun editCustomItem(id: String, title: String, categoryId: String?) {
        val value = title.trim()
        if (value.isEmpty()) return setError(strings.get(R.string.packing_error_item_name))
        mutateProgress(message = strings.get(R.string.packing_feedback_item_updated)) { progress ->
            progress.copy(customItems = progress.customItems.map {
                if (it.id == id) it.copy(title = value, categoryId = normalizeCategory(categoryId)) else it
            })
        }
    }

    fun deleteCustomItem(id: String) = mutateProgress(message = strings.get(R.string.packing_feedback_item_removed)) { progress ->
        progress.copy(
            customItems = progress.customItems.filterNot { it.id == id },
            checkedItemIds = progress.checkedItemIds - id,
        )
    }

    fun loadSuggested() {
        val state = _uiState.value
        val template = state.template ?: return
        val user = currentUser ?: return
        val categories = template.categories.toMutableList()
        PackingChecklistCatalog.suggestedCategories(strings).forEach { suggested ->
            val index = categories.indexOfFirst { it.id == suggested.id }
            if (index >= 0) {
                val existing = categories[index]
                val itemIds = existing.items.mapTo(mutableSetOf()) { it.id }
                categories[index] = existing.copy(items = existing.items + suggested.items.filter { it.id !in itemIds })
            } else {
                categories += suggested.copy(sortIndex = (categories.maxOfOrNull { it.sortIndex } ?: -1) + 1)
            }
        }
        saveTemplate(template.copy(categories = categories, updatedByUid = user.uid, updatedByName = user.preferredDisplayName), strings.get(R.string.packing_feedback_suggested_loaded))
    }

    fun addCategory(title: String, iconName: String) {
        val template = _uiState.value.template ?: return
        val user = currentUser ?: return
        val value = title.trim()
        if (value.isEmpty()) return setError(strings.get(R.string.packing_error_section_name))
        val category = PackingCategory(
            title = value,
            iconName = iconName.ifBlank { "checklist" },
            sortIndex = (template.categories.maxOfOrNull { it.sortIndex } ?: -1) + 1,
        )
        saveTemplate(template.copy(categories = template.categories + category, updatedByUid = user.uid, updatedByName = user.preferredDisplayName), strings.get(R.string.packing_feedback_section_added))
    }

    fun updateCategory(id: String, title: String, iconName: String) {
        val template = _uiState.value.template ?: return
        val value = title.trim()
        if (value.isEmpty()) return setError(strings.get(R.string.packing_error_section_name))
        saveTemplate(template.copy(categories = template.categories.map {
            if (it.id == id) it.copy(title = value, iconName = iconName.ifBlank { "checklist" }) else it
        }), strings.get(R.string.packing_feedback_section_updated))
    }

    fun deleteCategory(id: String) {
        val template = _uiState.value.template ?: return
        saveTemplate(template.copy(categories = template.categories.filterNot { it.id == id }).normalizedSort(), strings.get(R.string.packing_feedback_section_deleted))
    }

    fun moveCategory(id: String, direction: Int) {
        val template = _uiState.value.template ?: return
        val list = template.sortedCategories.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        val target = (index + direction).coerceIn(0, list.lastIndex)
        if (index < 0 || index == target) return
        val category = list.removeAt(index)
        list.add(target, category)
        saveTemplate(template.copy(categories = list.mapIndexed { i, item -> item.copy(sortIndex = i) }))
    }

    fun addItem(categoryId: String, title: String) {
        val template = _uiState.value.template ?: return
        val value = title.trim()
        if (value.isEmpty()) return setError(strings.get(R.string.packing_error_item_name))
        saveTemplate(template.copy(categories = template.categories.map { category ->
            if (category.id == categoryId) category.copy(
                items = category.items + PackingItem(title = value, sortIndex = (category.items.maxOfOrNull { it.sortIndex } ?: -1) + 1),
            ) else category
        }), strings.get(R.string.packing_feedback_item_added))
    }

    fun updateItem(categoryId: String, itemId: String, title: String) {
        val template = _uiState.value.template ?: return
        val value = title.trim()
        if (value.isEmpty()) return setError(strings.get(R.string.packing_error_item_name))
        saveTemplate(template.copy(categories = template.categories.map { category ->
            if (category.id == categoryId) category.copy(items = category.items.map { if (it.id == itemId) it.copy(title = value) else it }) else category
        }), strings.get(R.string.packing_feedback_item_updated))
    }

    fun deleteItem(categoryId: String, itemId: String) {
        val template = _uiState.value.template ?: return
        saveTemplate(template.copy(categories = template.categories.map { category ->
            if (category.id == categoryId) category.copy(items = category.items.filterNot { it.id == itemId }.mapIndexed { i, it -> it.copy(sortIndex = i) }) else category
        }), strings.get(R.string.packing_feedback_item_removed))
    }

    fun moveItem(categoryId: String, itemId: String, direction: Int) {
        val template = _uiState.value.template ?: return
        saveTemplate(template.copy(categories = template.categories.map { category ->
            if (category.id != categoryId) return@map category
            val list = category.sortedItems.toMutableList()
            val index = list.indexOfFirst { it.id == itemId }
            val target = (index + direction).coerceIn(0, list.lastIndex)
            if (index < 0 || index == target) category else {
                val item = list.removeAt(index)
                list.add(target, item)
                category.copy(items = list.mapIndexed { i, value -> value.copy(sortIndex = i) })
            }
        }))
    }

    fun prepareShare() {
        val state = _uiState.value
        val progress = state.progress ?: return
        val user = currentUser ?: return
        val template = state.template ?: return
        if (progress.customItems.isEmpty()) return setError(strings.get(R.string.packing_error_share_empty))
        val categoryTitles = template.categories.associate { it.id to it.title }
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null, preparedShareUrl = null) }
            runCatching {
                val ownedShares = packingService.loadOwnedShares(progress.campingId, user.uid)
                val activeShare = ownedShares.firstOrNull { !it.isExpired }
                val share = PackingShare(
                    id = activeShare?.id ?: UUID.randomUUID().toString(),
                    campingId = progress.campingId,
                    campName = state.camping?.title,
                    ownerUid = user.uid,
                    ownerName = user.preferredDisplayName,
                    items = progress.customItems.map { PackingShareItem(title = it.title, categoryTitle = it.categoryId?.let(categoryTitles::get)) },
                    createdAt = activeShare?.createdAt ?: Date(),
                    expiresAt = Date(System.currentTimeMillis() + PackingShare.DefaultLifetimeMillis),
                )
                val saved = if (activeShare == null) packingService.createShare(share) else packingService.updateShare(share)
                val url = CampzoneDeepLink.PackingShare(saved.campingId, saved.id)
                    .canonicalShareUrlOrNull()
                    ?: error(strings.get(R.string.packing_error_share))
                Triple(saved, if (activeShare == null) ownedShares.size + 1 else ownedShares.size, url)
            }
                .onSuccess {
                    val (_, ownedShareCount, url) = it
                    _uiState.update { state -> state.copy(saving = false, preparedShareUrl = url, ownedShareCount = ownedShareCount) }
                }
                .onFailure { error -> _uiState.update { it.copy(saving = false, error = error.message ?: strings.get(R.string.packing_error_share)) } }
        }
    }

    fun revokeAllShares() {
        val state = _uiState.value
        val campingId = state.progress?.campingId ?: return
        val user = currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null, preparedShareUrl = null) }
            runCatching {
                packingService.loadOwnedShares(campingId, user.uid).forEach { share ->
                    packingService.deleteShare(campingId, share.id)
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        saving = false,
                        ownedShareCount = 0,
                        message = strings.get(R.string.packing_feedback_links_revoked),
                    )
                }
            }.onFailure { error ->
                val remaining = runCatching { packingService.loadOwnedShares(campingId, user.uid).size }
                    .getOrDefault(state.ownedShareCount)
                _uiState.update {
                    it.copy(
                        saving = false,
                        ownedShareCount = remaining,
                        error = error.message ?: strings.get(R.string.packing_error_revoke),
                    )
                }
            }
        }
    }

    fun clearFeedback() = _uiState.update { it.copy(message = null, error = null, preparedShareUrl = null) }

    private fun mutateProgress(message: String? = null, mutation: (UserPackingProgress) -> UserPackingProgress) {
        val state = _uiState.value
        val template = state.template ?: return
        val progress = mutation(state.progress ?: return).copy(updatedAt = Date())
        val snapshot = snapshot(template, progress, state.camping?.title)
        _uiState.update { it.copy(progress = progress, snapshot = snapshot, message = message, error = null) }
        viewModelScope.launch {
            runCatching { packingService.saveProgress(progress) }
                .onFailure { error -> _uiState.update { it.copy(error = error.message ?: strings.get(R.string.packing_error_save_progress)) } }
        }
    }

    private fun saveTemplate(template: PackingChecklistTemplate, message: String? = null) {
        val state = _uiState.value
        val progress = state.progress
        _uiState.update {
            it.copy(
                template = template,
                snapshot = progress?.let { value ->
                    if (template.isPublished || value.customItems.isNotEmpty()) snapshot(template, value, state.camping?.title) else null
                },
                saving = true,
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching { packingService.saveTemplate(template) }
                .onSuccess { saved -> _uiState.update { it.copy(template = saved, saving = false, message = message) } }
                .onFailure { error -> _uiState.update { it.copy(saving = false, error = error.message ?: strings.get(R.string.packing_error_save_template)) } }
        }
    }

    private fun setError(message: String) { _uiState.update { it.copy(error = message) } }
    private fun snapshot(template: PackingChecklistTemplate, progress: UserPackingProgress, campName: String?) =
        packingSnapshot(template, progress, campName, strings.get(R.string.packing_my_items))
    private fun normalizeCategory(categoryId: String?) = categoryId?.takeUnless { it == PackingChecklistSnapshot.GeneralCategoryId || it.isBlank() }
}

private fun Camping.permissionContext() = CampingPermissionContext(
    organizerLevelType = organizerLevel.type.wireValue,
    organizerLevelValue = organizerLevel.value,
    createdByUid = createdByUid,
)

private fun PackingChecklistTemplate.normalizedSort() = copy(categories = sortedCategories.mapIndexed { i, it -> it.copy(sortIndex = i) })
