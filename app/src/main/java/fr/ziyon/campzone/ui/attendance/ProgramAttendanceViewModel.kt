package fr.ziyon.campzone.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.i18n.StringProvider
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.attendance.ProgramAttendanceService
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CheckInMethod
import fr.ziyon.campzone.data.model.CheckInQrPayload
import fr.ziyon.campzone.data.model.Program
import fr.ziyon.campzone.data.model.ProgramAttendanceRecord
import fr.ziyon.campzone.data.model.ProgramAttendanceScanResult
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.schedule.ScheduleService
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ProgramAttendanceUiState {
    data object Loading : ProgramAttendanceUiState
    data object Restricted : ProgramAttendanceUiState
    data object Ready : ProgramAttendanceUiState
    data class Error(val message: String) : ProgramAttendanceUiState
}

@HiltViewModel
class ProgramAttendanceViewModel @Inject constructor(
    private val attendanceService: ProgramAttendanceService,
    private val campingService: CampingService,
    private val scheduleService: ScheduleService,
    private val stringProvider: StringProvider,
) : ViewModel() {
    private val permissions = AppPermissionEvaluator()

    private val _uiState = MutableStateFlow<ProgramAttendanceUiState>(ProgramAttendanceUiState.Loading)
    val uiState: StateFlow<ProgramAttendanceUiState> = _uiState.asStateFlow()

    private val _camping = MutableStateFlow<Camping?>(null)
    val camping: StateFlow<Camping?> = _camping.asStateFlow()

    private val _program = MutableStateFlow<Program?>(null)
    val program: StateFlow<Program?> = _program.asStateFlow()

    private val _records = MutableStateFlow<List<ProgramAttendanceRecord>>(emptyList())
    val records: StateFlow<List<ProgramAttendanceRecord>> = _records.asStateFlow()

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _lastScanResult = MutableStateFlow<ProgramAttendanceScanResult?>(null)
    val lastScanResult: StateFlow<ProgramAttendanceScanResult?> = _lastScanResult.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    val filteredRecords: StateFlow<List<ProgramAttendanceRecord>> =
        combine(_records, _searchText) { records, query ->
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                records
            } else {
                records.filter {
                    it.displayName.contains(trimmed, ignoreCase = true) ||
                        it.church.contains(trimmed, ignoreCase = true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val missingAttendees: StateFlow<List<CampingAttendee>> =
        combine(_camping, _records) { camping, records ->
            val presentIds = records.mapTo(mutableSetOf()) { it.attendeeId }
            camping?.approvedAttendees?.filterNot { it.id in presentIds }.orEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private var reviewerId: String = ""
    private var canManage = false
    private var loadedKey: Triple<String, String, String>? = null
    private var inFlight = false
    private var lastHandledValue: String? = null

    fun start(campingId: String, programId: String, user: AuthenticatedUser) {
        val key = Triple(campingId, programId, user.uid)
        if (loadedKey == key && _uiState.value !is ProgramAttendanceUiState.Error) return
        loadedKey = key
        reviewerId = user.uid
        _uiState.value = ProgramAttendanceUiState.Loading
        _operationError.value = null

        viewModelScope.launch {
            runCatching {
                val camping = campingService.fetchCamping(campingId)
                val schedule = scheduleService.loadSchedule(campingId)
                val program = schedule.allPrograms.firstOrNull { it.id == programId }
                    ?: error(stringProvider.get(R.string.program_not_found_message))
                camping to program
            }.onSuccess { (camping, program) ->
                _camping.value = camping
                _program.value = program
                val permissionUser = PermissionUser(
                    role = user.role,
                    userId = user.uid,
                    church = user.church,
                )
                canManage = permissions.canManageCheckIns(permissionUser, camping.permissionContext())
                if (!canManage) {
                    _uiState.value = ProgramAttendanceUiState.Restricted
                    return@onSuccess
                }
                loadRecords(camping.id, program.id)
            }.onFailure { error ->
                loadedKey = null
                _uiState.value = ProgramAttendanceUiState.Error(
                    error.message ?: stringProvider.get(R.string.program_attendance_load_failed),
                )
            }
        }
    }

    fun retry(campingId: String, programId: String, user: AuthenticatedUser) {
        loadedKey = null
        start(campingId, programId, user)
    }

    private suspend fun loadRecords(campingId: String, programId: String) {
        runCatching { attendanceService.loadRecords(campingId, programId) }
            .onSuccess {
                _records.value = it
                _uiState.value = ProgramAttendanceUiState.Ready
            }
            .onFailure { error ->
                loadedKey = null
                _uiState.value = ProgramAttendanceUiState.Error(
                    error.message ?: stringProvider.get(R.string.program_attendance_load_failed),
                )
            }
    }

    fun updateSearch(text: String) {
        _searchText.value = text
    }

    fun dismissScanResult() {
        _lastScanResult.value = null
        lastHandledValue = null
    }

    fun consumeOperationError() {
        _operationError.value = null
    }

    fun consumeOperationMessage() {
        _operationMessage.value = null
    }

    fun recordFor(attendeeId: String): ProgramAttendanceRecord? =
        _records.value.firstOrNull { it.attendeeId == attendeeId }

    fun handleScan(value: String) {
        if (!canManage || inFlight) return
        if (value == lastHandledValue) return
        lastHandledValue = value
        val camping = _camping.value ?: return
        val program = _program.value ?: return

        val payload = CheckInQrPayload.decode(value)
        if (payload == null) {
            _lastScanResult.value = ProgramAttendanceScanResult.Malformed
            return
        }
        if (payload.campingId != camping.id) {
            _lastScanResult.value = ProgramAttendanceScanResult.WrongCamping
            return
        }
        val attendee = camping.attendees.firstOrNull {
            it.id == payload.attendeeId && it.userId == payload.userId
        }
        if (attendee == null) {
            _lastScanResult.value = ProgramAttendanceScanResult.UnknownAttendee
            return
        }
        if (attendee.registrationStatus != RegistrationApprovalStatus.Approved) {
            _lastScanResult.value = ProgramAttendanceScanResult.NotApproved
            return
        }
        val existing = recordFor(attendee.id)
        if (existing != null) {
            _lastScanResult.value = ProgramAttendanceScanResult.AlreadyRecorded(existing)
            return
        }
        saveRecord(
            record = makeRecord(attendee, camping, program, CheckInMethod.Qr),
            mode = SaveMode.ScanCreate,
        )
    }

    fun manualRecord(attendee: CampingAttendee) {
        if (!canManage || inFlight) return
        val camping = _camping.value ?: return
        val program = _program.value ?: return
        if (recordFor(attendee.id) != null) return
        saveRecord(
            record = makeRecord(attendee, camping, program, CheckInMethod.Manual),
            mode = SaveMode.ManualCreate,
        )
    }

    fun refreshTimestamp(record: ProgramAttendanceRecord) {
        if (!canManage || inFlight) return
        saveRecord(
            record = record.copy(
                method = CheckInMethod.Manual,
                checkedInBy = reviewerId,
                checkedInAt = Date(),
            ),
            mode = SaveMode.Update,
        )
    }

    fun deleteRecord(record: ProgramAttendanceRecord) {
        if (!canManage || inFlight) return
        inFlight = true
        _isSaving.value = true
        _operationError.value = null
        viewModelScope.launch {
            runCatching {
                attendanceService.deleteAttendance(
                    campingId = record.campingId,
                    programId = record.programId,
                    attendeeId = record.attendeeId,
                )
            }.onSuccess {
                _records.update { current -> current.filterNot { it.attendeeId == record.attendeeId } }
                _operationMessage.value = stringProvider.get(R.string.program_attendance_removed)
            }.onFailure { error ->
                _operationError.value = error.message ?: stringProvider.get(R.string.program_attendance_remove_failed)
            }
            _isSaving.value = false
            inFlight = false
        }
    }

    private fun saveRecord(record: ProgramAttendanceRecord, mode: SaveMode) {
        inFlight = true
        _isSaving.value = true
        _operationError.value = null
        viewModelScope.launch {
            try {
                when (mode) {
                    SaveMode.ScanCreate,
                    SaveMode.ManualCreate,
                    -> attendanceService.recordAttendance(record)
                    SaveMode.Update -> attendanceService.updateAttendance(record)
                }
                _records.update { current ->
                    (listOf(record) + current.filterNot { it.attendeeId == record.attendeeId })
                        .sortedByDescending { it.checkedInAt }
                }
                when (mode) {
                    SaveMode.ScanCreate -> _lastScanResult.value = ProgramAttendanceScanResult.Success(record)
                    SaveMode.ManualCreate -> _operationMessage.value =
                        stringProvider.get(R.string.program_attendance_recorded)
                    SaveMode.Update -> _operationMessage.value =
                        stringProvider.get(R.string.program_attendance_corrected)
                }
            } catch (error: Exception) {
                if (mode == SaveMode.ScanCreate) {
                    _lastScanResult.value = ProgramAttendanceScanResult.SaveFailed
                    lastHandledValue = null
                }
                _operationError.value = error.message ?: stringProvider.get(R.string.program_attendance_save_failed)
            } finally {
                _isSaving.value = false
                inFlight = false
            }
        }
    }

    private fun makeRecord(
        attendee: CampingAttendee,
        camping: Camping,
        program: Program,
        method: CheckInMethod,
    ): ProgramAttendanceRecord =
        ProgramAttendanceRecord(
            id = attendee.id,
            campingId = camping.id,
            programId = program.id,
            programTitle = program.title,
            attendeeId = attendee.id,
            userId = attendee.userId,
            displayName = attendee.displayName,
            church = attendee.church,
            ageGroup = attendee.ageGroup,
            gender = attendee.gender,
            preferredLanguage = attendee.preferredLanguage,
            photoUrl = attendee.photoUrl,
            method = method,
            checkedInBy = reviewerId,
            checkedInAt = Date(),
        )

    private enum class SaveMode {
        ScanCreate,
        ManualCreate,
        Update,
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun Camping.permissionContext(): CampingPermissionContext =
    CampingPermissionContext(
        organizerLevelType = organizerLevel.type.wireValue,
        organizerLevelValue = organizerLevel.value,
        createdByUid = createdByUid,
    )
