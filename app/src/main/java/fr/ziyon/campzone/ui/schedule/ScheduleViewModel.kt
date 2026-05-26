package fr.ziyon.campzone.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampDay
import fr.ziyon.campzone.data.model.CampingSchedule
import fr.ziyon.campzone.data.model.DateKeys
import fr.ziyon.campzone.data.model.Program
import fr.ziyon.campzone.data.model.ProgramType
import fr.ziyon.campzone.data.model.ScheduleReminderTiming
import fr.ziyon.campzone.data.model.normalizedForCamping
import fr.ziyon.campzone.data.schedule.ScheduleService
import java.util.Calendar
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ScheduleUiState {
    data object Loading : ScheduleUiState
    data class Loaded(val schedule: CampingSchedule) : ScheduleUiState
    data object Empty : ScheduleUiState
    data class Error(val message: String) : ScheduleUiState
}

data class ProgramForm(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val location: String = "",
    val description: String = "",
    val type: ProgramType = ProgramType.Other,
    val dayId: String = "",
    val startDate: Date = Date(),
    val endDate: Date = Date(System.currentTimeMillis() + 3_600_000L),
    val venuePointId: String? = null,
    val endsNextDay: Boolean = false,
)

enum class ProgramValidationError(val message: String) {
    TitleRequired("Program title is required."),
    LocationRequired("Program location is required."),
    EndBeforeStart("End time must be after the start time."),
}

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleService: ScheduleService,
    private val campingService: CampingService,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScheduleUiState>(ScheduleUiState.Loading)
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private val _selectedDayId = MutableStateFlow<String?>(null)
    val selectedDayId: StateFlow<String?> = _selectedDayId.asStateFlow()

    private val _reminderTiming = MutableStateFlow(ScheduleReminderTiming.None)
    val reminderTiming: StateFlow<ScheduleReminderTiming> = _reminderTiming.asStateFlow()

    private val _editorForm = MutableStateFlow(ProgramForm())
    val editorForm: StateFlow<ProgramForm> = _editorForm.asStateFlow()

    private val _editingProgramId = MutableStateFlow<String?>(null)
    val editingProgramId: StateFlow<String?> = _editingProgramId.asStateFlow()

    private val _validationErrors = MutableStateFlow<List<ProgramValidationError>>(emptyList())
    val validationErrors: StateFlow<List<ProgramValidationError>> = _validationErrors.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    private val permissions = AppPermissionEvaluator()

    private val _canManageSchedule = MutableStateFlow(false)
    val canManageSchedule: StateFlow<Boolean> = _canManageSchedule.asStateFlow()

    /** In-memory cache keyed by campingId. */
    private val schedules = mutableMapOf<String, CampingSchedule>()
    private val normalizedIds = mutableSetOf<String>()
    private var lastUser: AuthenticatedUser? = null

    fun load(campingId: String, user: AuthenticatedUser? = null) {
        if (user != null) lastUser = user
        viewModelScope.launch {
            _uiState.value = ScheduleUiState.Loading
            _operationError.value = null
            runCatching {
                val schedule = scheduleService.loadSchedule(campingId)
                val camping = runCatching { campingService.fetchCamping(campingId) }.getOrNull()
                val normalized = if (camping != null) schedule.normalizedForCamping(camping) else schedule
                schedules[campingId] = normalized
                updateCanManage(lastUser, camping)
                publishSchedule(campingId)
            }.onFailure { e ->
                _uiState.value = ScheduleUiState.Error(
                    e.message ?: "Failed to load schedule."
                )
            }
        }
    }

    fun loadIfNeeded(campingId: String, user: AuthenticatedUser? = null) {
        if (user != null) lastUser = user
        if (schedules.containsKey(campingId)) {
            publishSchedule(campingId)
        } else {
            load(campingId, user)
        }
    }

    /** Runs normalization repair (idempotent); only called from the editor. */
    fun normalizeSchedule(campingId: String, user: AuthenticatedUser? = null) {
        if (user != null) lastUser = user
        if (normalizedIds.contains(campingId)) {
            loadIfNeeded(campingId, user)
            return
        }
        viewModelScope.launch {
            runCatching {
                val camping = runCatching { campingService.fetchCamping(campingId) }.getOrNull()
                val schedule = scheduleService.normalizeDays(campingId)
                val normalized = if (camping != null) schedule.normalizedForCamping(camping) else schedule
                normalizedIds.add(campingId)
                schedules[campingId] = normalized
                updateCanManage(lastUser, camping)
                publishSchedule(campingId)
            }.onFailure {
                loadIfNeeded(campingId, user)
            }
        }
    }

    fun setSelectedDayId(dayId: String?) {
        _selectedDayId.value = dayId
    }

    fun setReminderTiming(timing: ScheduleReminderTiming) {
        _reminderTiming.value = timing
    }

    fun saveReminderTiming(campingId: String) {
        viewModelScope.launch {
            _isSaving.value = true
            runCatching {
                val schedule = scheduleService.saveReminderTiming(_reminderTiming.value, campingId)
                schedules[campingId] = schedule
                _operationMessage.value = "Reminder timing saved."
                publishSchedule(campingId)
            }.onFailure { e ->
                _operationError.value = e.message ?: "Could not save reminder timing."
            }
            _isSaving.value = false
        }
    }

    fun prepareNewProgram(campingId: String, dayId: String? = null) {
        val schedule = schedules[campingId] ?: return
        val day = (dayId?.let { id -> schedule.days.firstOrNull { it.id == id } }
            ?: schedule.sortedDays.firstOrNull()) ?: return
        _selectedDayId.value = day.id
        _editingProgramId.value = null
        _editorForm.value = ProgramForm(
            id = UUID.randomUUID().toString(),
            dayId = day.id,
            startDate = day.date.withTime(9, 0),
            endDate = day.date.withTime(10, 0),
        )
        _validationErrors.value = emptyList()
        _operationError.value = null
    }

    fun prepareEditingProgram(program: Program) {
        _selectedDayId.value = program.campDayId
        _editingProgramId.value = program.id
        _editorForm.value = ProgramForm(
            id = program.id,
            title = program.title,
            location = program.location,
            description = program.description,
            type = program.type,
            dayId = program.campDayId,
            startDate = program.startDate,
            endDate = program.endDate,
            venuePointId = program.venuePointId,
        )
        _validationErrors.value = emptyList()
        _operationError.value = null
    }

    fun updateEditorForm(update: (ProgramForm) -> ProgramForm) {
        _editorForm.value = update(_editorForm.value)
    }

    fun clearOperationMessage() {
        _operationMessage.value = null
    }

    fun clearOperationError() {
        _operationError.value = null
    }

    /** @return the saved [Program] on success, null on validation failure or error. */
    fun saveProgram(campingId: String, onSuccess: (Program) -> Unit) {
        val errors = validateForm(campingId)
        _validationErrors.value = errors
        if (errors.isNotEmpty()) return

        val program = buildProgram(campingId)
        viewModelScope.launch {
            _isSaving.value = true
            _operationError.value = null
            runCatching {
                val schedule = scheduleService.saveProgram(program)
                schedules[campingId] = schedule
                _selectedDayId.value = program.campDayId
                _editingProgramId.value = program.id
                _operationMessage.value = "Program saved."
                publishSchedule(campingId)
                onSuccess(program)
            }.onFailure { e ->
                _operationError.value = e.message ?: "Could not save program."
            }
            _isSaving.value = false
        }
    }

    fun deleteProgram(programId: String, campingId: String) {
        viewModelScope.launch {
            runCatching {
                val schedule = scheduleService.deleteProgram(programId, campingId)
                schedules[campingId] = schedule
                _operationMessage.value = "Program deleted."
                publishSchedule(campingId)
            }.onFailure { e ->
                _operationError.value = e.message ?: "Could not delete program."
            }
        }
    }

    fun program(id: String): Program? =
        schedules.values.flatMap { it.allPrograms }.firstOrNull { it.id == id }

    fun selectedDay(campingId: String): CampDay? {
        val schedule = schedules[campingId] ?: return null
        val dayId = _selectedDayId.value
        return (dayId?.let { id -> schedule.days.firstOrNull { it.id == id } }
            ?: schedule.sortedDays.firstOrNull())
    }

    fun schedule(campingId: String): CampingSchedule? = schedules[campingId]

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun updateCanManage(user: AuthenticatedUser?, camping: Camping?) {
        if (user == null || camping == null) return
        val permUser = PermissionUser(role = user.role, userId = user.uid, church = user.church)
        val ctx = CampingPermissionContext(
            organizerLevelType = camping.organizerLevel.type.wireValue,
            organizerLevelValue = camping.organizerLevel.value,
            createdByUid = camping.createdByUid,
        )
        _canManageSchedule.value = permissions.canManageSchedule(permUser, ctx)
    }

    private fun publishSchedule(campingId: String) {
        val schedule = schedules[campingId]
        _uiState.value = when {
            schedule == null || schedule.sortedDays.isEmpty() -> ScheduleUiState.Empty
            else -> ScheduleUiState.Loaded(schedule)
        }
        val current = schedule?.sortedDays ?: return
        if (_selectedDayId.value == null || current.none { it.id == _selectedDayId.value }) {
            _selectedDayId.value = current.firstOrNull()?.id
        }
        _reminderTiming.value = schedule.reminderTiming
    }

    private fun validateForm(campingId: String): List<ProgramValidationError> {
        val form = _editorForm.value
        val errors = mutableListOf<ProgramValidationError>()
        if (form.title.isBlank()) errors.add(ProgramValidationError.TitleRequired)
        if (form.location.isBlank()) errors.add(ProgramValidationError.LocationRequired)
        if (form.endDate <= form.startDate) errors.add(ProgramValidationError.EndBeforeStart)
        return errors
    }

    private fun buildProgram(campingId: String): Program {
        val form = _editorForm.value
        val schedule = schedules[campingId]
        val day = schedule?.days?.firstOrNull { it.id == form.dayId }

        val startDate = if (day != null) {
            day.date.withTime(
                hour = form.startDate.hours(),
                minute = form.startDate.minutes(),
            )
        } else {
            form.startDate
        }

        val endDate = if (day != null) {
            val startCal = Calendar.getInstance().apply { time = form.startDate }
            val endCal = Calendar.getInstance().apply { time = form.endDate }
            val dayOffset = ((endCal.timeInMillis - startCal.apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis) / 86_400_000L).toInt()

            val rebasedEndDay = Calendar.getInstance().apply {
                time = DateKeys.startOfDay(day.date)
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }.time

            rebasedEndDay.withTime(
                hour = form.endDate.hours(),
                minute = form.endDate.minutes(),
            )
        } else {
            form.endDate
        }

        return Program(
            id = _editingProgramId.value ?: form.id,
            campingId = campingId,
            campDayId = DateKeys.campDayId(campingId, startDate),
            title = form.title.trim(),
            type = form.type,
            startDate = startDate,
            endDate = endDate,
            location = form.location.trim(),
            description = form.description.trim(),
            venuePointId = form.venuePointId?.takeUnless { it.isBlank() },
        )
    }
}

// ── Extension helpers ─────────────────────────────────────────────────────────

private fun Date.withTime(hour: Int, minute: Int): Date =
    Calendar.getInstance().apply {
        time = this@withTime
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

@Suppress("DEPRECATION")
private fun Date.hours(): Int = Calendar.getInstance().apply { time = this@hours }.get(Calendar.HOUR_OF_DAY)

@Suppress("DEPRECATION")
private fun Date.minutes(): Int = Calendar.getInstance().apply { time = this@minutes }.get(Calendar.MINUTE)
