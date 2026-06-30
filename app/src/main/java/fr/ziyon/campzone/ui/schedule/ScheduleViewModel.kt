package fr.ziyon.campzone.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.i18n.StringProvider
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.games.FakeGameService
import fr.ziyon.campzone.data.games.GameService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampDay
import fr.ziyon.campzone.data.model.CampingSchedule
import fr.ziyon.campzone.data.model.CustomProgramType
import fr.ziyon.campzone.data.model.DateKeys
import fr.ziyon.campzone.data.model.FoodMenuProgramSync
import fr.ziyon.campzone.data.model.Game
import fr.ziyon.campzone.data.model.Program
import fr.ziyon.campzone.data.model.ProgramType
import fr.ziyon.campzone.data.model.ScheduleReminderTiming
import fr.ziyon.campzone.data.model.VenuePoint
import fr.ziyon.campzone.data.model.normalizedForCamping
import fr.ziyon.campzone.data.notifications.NoOpNotificationApi
import fr.ziyon.campzone.data.notifications.NotificationApi
import fr.ziyon.campzone.data.notifications.ProgramReminderPlanner
import fr.ziyon.campzone.data.schedule.FoodMenuService
import fr.ziyon.campzone.data.schedule.ScheduleService
import fr.ziyon.campzone.data.venuemap.FakeVenueMapService
import fr.ziyon.campzone.data.venuemap.VenueMapService
import java.util.Calendar
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val linkedGameId: String? = null,
    val endsNextDay: Boolean = false,
    val customType: CustomProgramType? = null,
)

enum class ProgramValidationError(val messageRes: Int) {
    TitleRequired(R.string.schedule_validation_title_required),
    LocationRequired(R.string.schedule_validation_location_required),
    EndBeforeStart(R.string.schedule_validation_end_after_start),
    CustomTypeRequired(R.string.schedule_validation_custom_type_required),
}

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleService: ScheduleService,
    private val campingService: CampingService,
    private val foodMenuService: FoodMenuService,
    private val stringProvider: StringProvider,
    private val notificationApi: NotificationApi = NoOpNotificationApi,
    private val venueMapService: VenueMapService = FakeVenueMapService(),
    private val gameService: GameService = FakeGameService(),
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

    /** Camp venue-map pins, offered as quick picks for a program's location. */
    private val _venuePoints = MutableStateFlow<List<VenuePoint>>(emptyList())
    val venuePoints: StateFlow<List<VenuePoint>> = _venuePoints.asStateFlow()

    private val _games = MutableStateFlow<List<Game>>(emptyList())
    val games: StateFlow<List<Game>> = _games.asStateFlow()

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

    private val _camping = MutableStateFlow<Camping?>(null)
    val camping: StateFlow<Camping?> = _camping.asStateFlow()

    /** In-memory cache keyed by campingId. */
    private val schedules = mutableMapOf<String, CampingSchedule>()
    private val normalizedIds = mutableSetOf<String>()
    private var lastUser: AuthenticatedUser? = null
    private var observeJob: Job? = null
    private var observedCampingId: String? = null

    fun load(campingId: String, user: AuthenticatedUser? = null) {
        if (user != null) lastUser = user
        observeSchedule(campingId, showLoading = true)
    }

    fun loadIfNeeded(campingId: String, user: AuthenticatedUser? = null) {
        if (user != null) lastUser = user
        if (observedCampingId == campingId && observeJob?.isActive == true) {
            publishSchedule(campingId)
            return
        }
        observeSchedule(campingId, showLoading = !schedules.containsKey(campingId))
    }

    private fun observeSchedule(campingId: String, showLoading: Boolean) {
        observedCampingId = campingId
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            if (showLoading) _uiState.value = ScheduleUiState.Loading
            _operationError.value = null
            runCatching {
                _venuePoints.value = runCatching { venueMapService.loadMap(campingId).points }
                    .getOrDefault(emptyList())
                _games.value = runCatching { gameService.loadGames(campingId) }
                    .getOrDefault(emptyList())
            }.onFailure { e ->
                _operationError.value = e.message
            }
            try {
                combine(
                    scheduleService.observeSchedule(campingId),
                    campingService.observeCamping(campingId),
                ) { schedule, camping ->
                    val normalized = schedule.normalizedForCamping(camping, ::defaultDayTitle)
                    camping to normalized
                }.collect { (camping, schedule) ->
                    _camping.value = camping
                    schedules[campingId] = schedule
                    updateCanManage(lastUser, camping)
                    publishSchedule(campingId)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                _uiState.value = ScheduleUiState.Error(
                    e.message ?: stringProvider.get(R.string.schedule_load_error),
                )
            }
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
                val normalized = if (camping != null) schedule.normalizedForCamping(camping, ::defaultDayTitle) else schedule
                normalizedIds.add(campingId)
                schedules[campingId] = normalized
                updateCanManage(lastUser, camping)
                publishSchedule(campingId)
            }.onFailure {
                loadIfNeeded(campingId, user)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        observeJob?.cancel()
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
                syncCampingReminders(schedule)
                _operationMessage.value = stringProvider.get(R.string.schedule_reminder_saved)
                publishSchedule(campingId)
            }.onFailure { e ->
                _operationError.value = e.message ?: stringProvider.get(R.string.schedule_reminder_save_error)
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
            linkedGameId = program.linkedGameId,
            customType = program.customType,
        )
        _validationErrors.value = emptyList()
        _operationError.value = null
    }

    fun updateEditorForm(update: (ProgramForm) -> ProgramForm) {
        _editorForm.value = update(_editorForm.value)
    }

    /** Links the program to a venue pin: keeps the pin name in `location` and
     *  records `venuePointID` (mirrors iOS program↔venue linkage). */
    fun selectVenuePoint(point: VenuePoint) {
        _editorForm.value = _editorForm.value.copy(location = point.name, venuePointId = point.id)
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

        val previousProgram = _editingProgramId.value?.let(::program)
        val program = buildProgram(campingId)
        viewModelScope.launch {
            _isSaving.value = true
            _operationError.value = null
            runCatching {
                val schedule = scheduleService.saveProgram(program)
                syncMenuAfterSaving(program, previousProgram)
                schedules[campingId] = schedule
                syncProgramReminder(program, schedule.reminderTiming)
                _selectedDayId.value = program.campDayId
                _editingProgramId.value = program.id
                _operationMessage.value = stringProvider.get(R.string.schedule_program_saved)
                publishSchedule(campingId)
                onSuccess(program)
            }.onFailure { e ->
                _operationError.value = e.message ?: stringProvider.get(R.string.schedule_program_save_error)
            }
            _isSaving.value = false
        }
    }

    fun deleteProgram(programId: String, campingId: String) {
        val removedProgram = program(programId)
        viewModelScope.launch {
            runCatching {
                val schedule = scheduleService.deleteProgram(programId, campingId)
                syncMenuAfterDeleting(removedProgram)
                schedules[campingId] = schedule
                cancelProgramReminder(campingId, programId)
                _operationMessage.value = stringProvider.get(R.string.schedule_program_deleted)
                publishSchedule(campingId)
            }.onFailure { e ->
                _operationError.value = e.message ?: stringProvider.get(R.string.schedule_program_delete_error)
            }
        }
    }

    fun saveDayTitle(title: String, dayId: String, campingId: String) {
        viewModelScope.launch {
            _isSaving.value = true
            _operationError.value = null
            runCatching {
                val schedule = scheduleService.saveDayTitle(title, dayId, campingId)
                val camping = runCatching { campingService.fetchCamping(campingId) }.getOrNull()
                schedules[campingId] = if (camping != null) {
                    schedule.normalizedForCamping(camping, ::defaultDayTitle)
                } else {
                    schedule
                }
                _operationMessage.value = stringProvider.get(R.string.schedule_day_title_saved)
                publishSchedule(campingId)
            }.onFailure { e ->
                _operationError.value = e.message ?: stringProvider.get(R.string.schedule_day_title_save_error)
            }
            _isSaving.value = false
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

    fun customProgramTypes(campingId: String): List<CustomProgramType> =
        schedules[campingId]?.customProgramTypes.orEmpty()

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
            val todayKey = DateKeys.dayKey(Date())
            _selectedDayId.value = current.firstOrNull { DateKeys.dayKey(it.date) == todayKey }?.id
                ?: current.firstOrNull()?.id
        }
        _reminderTiming.value = schedule.reminderTiming
    }

    private suspend fun syncMenuAfterSaving(program: Program, previousProgram: Program?) {
        runCatching {
            val previousEntryId = previousProgram?.let(FoodMenuProgramSync::menuEntryId)
            val entryId = FoodMenuProgramSync.menuEntryId(program)
            val existingEntry = entryId
                ?.let { foodMenuService.loadMenu(program.campingId).firstOrNull { entry -> entry.id == it } }
            val entry = FoodMenuProgramSync.menuEntryFor(program, existingEntry)

            if (previousProgram != null && previousEntryId != null && previousEntryId != entry?.id) {
                foodMenuService.deleteEntry(
                    entryId = previousEntryId,
                    campingId = previousProgram.campingId,
                    syncProgram = false,
                )
            }

            if (entry != null) {
                foodMenuService.saveEntry(entry, syncProgram = false)
            }
        }.onFailure {
            _operationError.value = stringProvider.get(R.string.schedule_program_saved_menu_sync_error)
        }
    }

    private suspend fun syncMenuAfterDeleting(program: Program?) {
        if (program == null) return
        runCatching {
            val entryId = FoodMenuProgramSync.menuEntryId(program) ?: return
            foodMenuService.deleteEntry(
                entryId = entryId,
                campingId = program.campingId,
                syncProgram = false,
            )
        }.onFailure {
            _operationError.value = stringProvider.get(R.string.schedule_program_deleted_menu_sync_error)
        }
    }

    private suspend fun syncCampingReminders(schedule: CampingSchedule) {
        runCatching {
            notificationApi.replaceCampingReminders(
                campingId = schedule.campingId,
                reminders = ProgramReminderPlanner.plans(schedule),
            )
        }.onFailure {
            _operationError.value = stringProvider.get(R.string.schedule_reminder_dispatch_sync_error)
        }
    }

    private suspend fun syncProgramReminder(program: Program, timing: ScheduleReminderTiming) {
        runCatching {
            val reminders = ProgramReminderPlanner.plan(program, timing)?.let(::listOf).orEmpty()
            notificationApi.replaceProgramReminders(
                campingId = program.campingId,
                programIds = listOf(program.id),
                reminders = reminders,
            )
        }.onFailure {
            _operationError.value = stringProvider.get(R.string.schedule_reminder_dispatch_sync_error)
        }
    }

    private suspend fun cancelProgramReminder(campingId: String, programId: String) {
        runCatching {
            notificationApi.replaceProgramReminders(
                campingId = campingId,
                programIds = listOf(programId),
                reminders = emptyList(),
            )
        }.onFailure {
            _operationError.value = stringProvider.get(R.string.schedule_program_deleted_reminder_sync_error)
        }
    }

    private fun validateForm(campingId: String): List<ProgramValidationError> {
        val form = _editorForm.value
        val errors = mutableListOf<ProgramValidationError>()
        if (form.title.isBlank()) errors.add(ProgramValidationError.TitleRequired)
        if (form.location.isBlank()) errors.add(ProgramValidationError.LocationRequired)
        if (form.endDate <= form.startDate) errors.add(ProgramValidationError.EndBeforeStart)
        if (form.type == ProgramType.Custom && form.customType?.isValid != true) {
            errors.add(ProgramValidationError.CustomTypeRequired)
        }
        return errors
    }

    private fun defaultDayTitle(dayNumber: Int): String =
        stringProvider.get(R.string.schedule_day_title, dayNumber)

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

        val customType = if (form.type == ProgramType.Custom) form.customType else null

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
            linkedGameId = form.linkedGameId
                ?.takeIf { form.type == ProgramType.Games }
                ?.takeUnless { it.isBlank() },
            customTypeName = customType?.trimmedName,
            customTypeSymbol = customType?.symbol,
            customTypeColorHex = customType?.colorHex,
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
