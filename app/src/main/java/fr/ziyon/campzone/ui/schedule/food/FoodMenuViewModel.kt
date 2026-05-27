package fr.ziyon.campzone.ui.schedule.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.DateKeys
import fr.ziyon.campzone.data.model.FoodMealKind
import fr.ziyon.campzone.data.model.FoodMenuEntry
import fr.ziyon.campzone.data.model.FoodMenuProgramSync
import fr.ziyon.campzone.data.model.Program
import fr.ziyon.campzone.data.schedule.FoodMenuService
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface FoodMenuUiState {
    data object Loading : FoodMenuUiState
    data class Loaded(val entries: List<FoodMenuEntry>) : FoodMenuUiState
    data object Empty : FoodMenuUiState
    data class Error(val message: String) : FoodMenuUiState
}

/** Mirror of iOS `FoodMenuEntryForm`. `dishesText` holds all dishes joined by `\n`. */
data class FoodMenuEntryForm(
    val id: String = "",
    val date: Date = Date(),
    val meal: FoodMealKind = FoodMealKind.Breakfast,
    val dishesText: String = "",
    val notes: String = "",
) {
    val parsedDishes: List<String>
        get() = dishesText
            .split("\n", ",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    val isValid: Boolean get() = parsedDishes.isNotEmpty()

    val validationError: String?
        get() = if (!isValid) "Add at least one dish." else null
}

/** Grouped by calendar day - mirrors iOS `FoodMenuDaySection`. */
data class FoodMenuDaySection(
    val id: String,
    val date: Date,
    val entries: List<FoodMenuEntry>,
) {
    val dayTitle: String
        get() {
            val fmt = java.text.SimpleDateFormat("EEEE, MMMM d", java.util.Locale.getDefault())
            return fmt.format(date)
        }
}

@HiltViewModel
class FoodMenuViewModel @Inject constructor(
    private val foodMenuService: FoodMenuService,
    private val campingService: CampingService,
) : ViewModel() {

    private val _uiState = MutableStateFlow<FoodMenuUiState>(FoodMenuUiState.Loading)
    val uiState: StateFlow<FoodMenuUiState> = _uiState.asStateFlow()

    private val _canManageFoodMenu = MutableStateFlow(false)
    val canManageFoodMenu: StateFlow<Boolean> = _canManageFoodMenu.asStateFlow()

    private val _editorForm = MutableStateFlow(FoodMenuEntryForm())
    val editorForm: StateFlow<FoodMenuEntryForm> = _editorForm.asStateFlow()

    private val _campingDateRange = MutableStateFlow<Pair<Date, Date>?>(null)
    val campingDateRange: StateFlow<Pair<Date, Date>?> = _campingDateRange.asStateFlow()

    private val _editingEntryId = MutableStateFlow<String?>(null)
    val editingEntryId: StateFlow<String?> = _editingEntryId.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    private val permissions = AppPermissionEvaluator()
    private var lastUser: AuthenticatedUser? = null
    private var cachedCamping: Camping? = null
    private var loadedCampingIds = mutableSetOf<String>()

    fun load(campingId: String, user: AuthenticatedUser? = null) {
        if (user != null) lastUser = user
        viewModelScope.launch {
            _uiState.value = FoodMenuUiState.Loading
            _operationError.value = null
            runCatching {
                val camping = runCatching { campingService.fetchCamping(campingId) }.getOrNull()
                cachedCamping = camping
                _campingDateRange.value = camping?.let {
                    DateKeys.startOfDay(it.startDate) to it.endDate.endOfDay()
                }
                updateCanManage(lastUser, camping)
                val entries = foodMenuService.loadMenu(campingId)
                loadedCampingIds.add(campingId)
                publishEntries(entries)
            }.onFailure { e ->
                _uiState.value = FoodMenuUiState.Error(e.message ?: "Failed to load food menu.")
            }
        }
    }

    fun loadIfNeeded(campingId: String, user: AuthenticatedUser? = null) {
        if (user != null) lastUser = user
        if (loadedCampingIds.contains(campingId) && _uiState.value !is FoodMenuUiState.Loading) {
            return
        }
        load(campingId, user)
    }

    fun prepareNew(campingId: String) {
        val camping = cachedCamping
        val date = DateKeys.startOfDay(camping?.startDate ?: Date())
            .withDefaultMealTime(FoodMealKind.Breakfast)
        _editingEntryId.value = null
        _editorForm.value = FoodMenuEntryForm(
            date = date,
            meal = FoodMealKind.Breakfast,
            dishesText = "",
            notes = "",
        )
        _operationError.value = null
    }

    fun prepareEdit(entry: FoodMenuEntry) {
        _editingEntryId.value = entry.id
        _editorForm.value = FoodMenuEntryForm(
            id = entry.id,
            date = entry.date,
            meal = entry.meal,
            dishesText = entry.dishes.joinToString("\n"),
            notes = entry.notes,
        )
        _operationError.value = null
    }

    fun updateForm(update: (FoodMenuEntryForm) -> FoodMenuEntryForm) {
        _editorForm.value = update(_editorForm.value)
    }

    fun selectMeal(meal: FoodMealKind) {
        val form = _editorForm.value
        val shouldMoveToDefaultTime = form.date.isMidnight() || form.date.isDefaultMealTime(form.meal)
        _editorForm.value = form.copy(
            meal = meal,
            date = if (shouldMoveToDefaultTime) form.date.withDefaultMealTime(meal) else form.date,
        )
    }

    fun saveEntry(campingId: String, onSuccess: () -> Unit) {
        val form = _editorForm.value
        if (!form.isValid) {
            _operationError.value = form.validationError
            return
        }

        val entry = FoodMenuEntry(
            id = DateKeys.foodMenuId(form.date, form.meal),
            campingId = campingId,
            date = form.date,
            meal = form.meal,
            dishes = form.parsedDishes,
            notes = form.notes.trim(),
        )

        viewModelScope.launch {
            _isSaving.value = true
            _operationError.value = null
            runCatching {
                val entries = foodMenuService.saveEntry(entry, replacingEntryId = _editingEntryId.value)
                _editingEntryId.value = entry.id
                _operationMessage.value = "Menu saved."
                publishEntries(entries)
                onSuccess()
            }.onFailure { e ->
                _operationError.value = e.message ?: "Could not save menu entry."
            }
            _isSaving.value = false
        }
    }

    fun deleteEntry(entryId: String, campingId: String) {
        viewModelScope.launch {
            _operationError.value = null
            runCatching {
                val entries = foodMenuService.deleteEntry(entryId, campingId)
                _operationMessage.value = "Menu entry deleted."
                publishEntries(entries)
            }.onFailure { e ->
                _operationError.value = e.message ?: "Could not delete menu entry."
            }
        }
    }

    /** Groups the loaded entries by calendar day, sorted by meal order within each day. */
    fun daySections(): List<FoodMenuDaySection> {
        val entries = (_uiState.value as? FoodMenuUiState.Loaded)?.entries ?: return emptyList()
        val dayFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val grouped = entries.groupBy { DateKeys.startOfDay(it.date) }
        return grouped
            .map { (date, dayEntries) ->
                FoodMenuDaySection(
                    id = dayFmt.format(date),
                    date = date,
                    entries = dayEntries.sortedBy { mealOrder(it.meal) },
                )
            }
            .sortedBy { it.date }
    }

    fun clearOperationMessage() { _operationMessage.value = null }
    fun clearOperationError() { _operationError.value = null }

    fun entryFor(program: Program): FoodMenuEntry? {
        val entries = (_uiState.value as? FoodMenuUiState.Loaded)?.entries ?: return null
        return entries.firstOrNull { FoodMenuProgramSync.matches(program, it) }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun mealOrder(meal: FoodMealKind): Int = when (meal) {
        FoodMealKind.Breakfast -> 0
        FoodMealKind.Snack -> 1
        FoodMealKind.Lunch -> 2
        FoodMealKind.Dinner -> 3
    }

    private fun publishEntries(entries: List<FoodMenuEntry>) {
        _uiState.value = if (entries.isEmpty()) FoodMenuUiState.Empty
        else FoodMenuUiState.Loaded(entries)
    }

    private fun updateCanManage(user: AuthenticatedUser?, camping: Camping?) {
        if (user == null || camping == null) return
        val permUser = PermissionUser(role = user.role, userId = user.uid, church = user.church)
        val ctx = CampingPermissionContext(
            organizerLevelType = camping.organizerLevel.type.wireValue,
            organizerLevelValue = camping.organizerLevel.value,
            createdByUid = camping.createdByUid,
        )
        _canManageFoodMenu.value = permissions.canManageFoodMenu(permUser, ctx)
    }

    private fun Date.withDefaultMealTime(meal: FoodMealKind): Date {
        val (hour, minute) = when (meal) {
            FoodMealKind.Breakfast -> 8 to 0
            FoodMealKind.Snack -> 10 to 30
            FoodMealKind.Lunch -> 12 to 30
            FoodMealKind.Dinner -> 18 to 30
        }
        return Calendar.getInstance().apply {
            time = this@withDefaultMealTime
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    private fun Date.isDefaultMealTime(meal: FoodMealKind): Boolean {
        val cal = Calendar.getInstance().apply { time = this@isDefaultMealTime }
        val (hour, minute) = when (meal) {
            FoodMealKind.Breakfast -> 8 to 0
            FoodMealKind.Snack -> 10 to 30
            FoodMealKind.Lunch -> 12 to 30
            FoodMealKind.Dinner -> 18 to 30
        }
        return cal.get(Calendar.HOUR_OF_DAY) == hour && cal.get(Calendar.MINUTE) == minute
    }

    private fun Date.isMidnight(): Boolean {
        val cal = Calendar.getInstance().apply { time = this@isMidnight }
        return cal.get(Calendar.HOUR_OF_DAY) == 0 && cal.get(Calendar.MINUTE) == 0
    }

    private fun Date.endOfDay(): Date =
        Calendar.getInstance().apply {
            time = this@endOfDay
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.time
}
