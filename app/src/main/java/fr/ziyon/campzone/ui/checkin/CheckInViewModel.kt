package fr.ziyon.campzone.ui.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.checkin.CheckInService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CheckInMethod
import fr.ziyon.campzone.data.model.CheckInQrPayload
import fr.ziyon.campzone.data.model.CheckInRecord
import fr.ziyon.campzone.data.model.CheckInScanResult
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
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

sealed interface CheckInUiState {
    data object Loading : CheckInUiState
    data object Restricted : CheckInUiState
    data object Ready : CheckInUiState
    data class Error(val message: String) : CheckInUiState
}

/**
 * Drives the QR scanner + records screens (`02-firestore-schema.md` §7.1).
 * Mirrors the iOS `CheckInObserver`: decode a scanned QR, validate it against
 * the camping's approved attendees, then record the check-in. [start] loads the
 * camping (with attendees) and gates on `canManageCheckIns` so only camp
 * leaders can scan/record.
 */
@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val checkInService: CheckInService,
    private val campingService: CampingService,
) : ViewModel() {

    private val permissions = AppPermissionEvaluator()

    private val _uiState = MutableStateFlow<CheckInUiState>(CheckInUiState.Loading)
    val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()

    private val _camping = MutableStateFlow<Camping?>(null)
    val camping: StateFlow<Camping?> = _camping.asStateFlow()

    private val _records = MutableStateFlow<List<CheckInRecord>>(emptyList())
    val records: StateFlow<List<CheckInRecord>> = _records.asStateFlow()

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _lastScanResult = MutableStateFlow<CheckInScanResult?>(null)
    val lastScanResult: StateFlow<CheckInScanResult?> = _lastScanResult.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    /** Checked-in records filtered by the live search query (name or church). */
    val filteredRecords: StateFlow<List<CheckInRecord>> =
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

    /** Approved attendees still awaiting a check-in (manual fallback list). */
    val pendingAttendees: StateFlow<List<CampingAttendee>> =
        combine(_camping, _records) { camping, records ->
            val checkedIn = records.mapTo(mutableSetOf()) { it.attendeeId }
            camping?.approvedAttendees?.filterNot { it.id in checkedIn }.orEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private var reviewerId: String = ""
    private var canManage: Boolean = false
    private var loadedKey: Pair<String, String>? = null

    // Synchronous guards (VM methods run on the main thread): block a second
    // write while one is in flight and skip re-processing the same QR frame.
    private var inFlight = false
    private var lastHandledValue: String? = null

    fun start(campingId: String, user: AuthenticatedUser) {
        val key = campingId to user.uid
        if (loadedKey == key && _uiState.value !is CheckInUiState.Error) return
        loadedKey = key
        reviewerId = user.uid
        _uiState.value = CheckInUiState.Loading
        viewModelScope.launch {
            runCatching { campingService.fetchCamping(campingId) }
                .onSuccess { camping ->
                    _camping.value = camping
                    val permissionUser = PermissionUser(
                        role = user.role,
                        userId = user.uid,
                        church = user.church,
                    )
                    canManage = permissions.canManageCheckIns(permissionUser, camping.permissionContext())
                    if (!canManage) {
                        _uiState.value = CheckInUiState.Restricted
                        return@onSuccess
                    }
                    loadRecords(campingId)
                }
                .onFailure { error ->
                    loadedKey = null
                    _uiState.value = CheckInUiState.Error(error.message ?: DEFAULT_LOAD_ERROR)
                }
        }
    }

    fun retry(campingId: String, user: AuthenticatedUser) {
        loadedKey = null
        start(campingId, user)
    }

    private suspend fun loadRecords(campingId: String) {
        runCatching { checkInService.loadRecords(campingId) }
            .onSuccess {
                _records.value = it
                _uiState.value = CheckInUiState.Ready
            }
            .onFailure { error ->
                loadedKey = null
                _uiState.value = CheckInUiState.Error(error.message ?: DEFAULT_LOAD_ERROR)
            }
    }

    fun updateSearch(text: String) {
        _searchText.value = text
    }

    fun dismissScanResult() {
        _lastScanResult.value = null
        lastHandledValue = null
    }

    fun recordFor(attendeeId: String): CheckInRecord? =
        _records.value.firstOrNull { it.attendeeId == attendeeId }

    /** Decodes + validates a scanned QR string, then records the check-in. */
    fun handleScan(value: String) {
        if (!canManage || inFlight) return
        if (value == lastHandledValue) return
        lastHandledValue = value
        val camping = _camping.value ?: return

        val payload = CheckInQrPayload.decode(value)
        if (payload == null) {
            _lastScanResult.value = CheckInScanResult.Malformed
            return
        }
        if (payload.campingId != camping.id) {
            _lastScanResult.value = CheckInScanResult.WrongCamping
            return
        }
        val attendee = camping.attendees.firstOrNull {
            it.id == payload.attendeeId && it.userId == payload.userId
        }
        if (attendee == null) {
            _lastScanResult.value = CheckInScanResult.UnknownAttendee
            return
        }
        if (attendee.registrationStatus != RegistrationApprovalStatus.Approved) {
            _lastScanResult.value = CheckInScanResult.NotApproved
            return
        }
        val existing = recordFor(attendee.id)
        if (existing != null) {
            _lastScanResult.value = CheckInScanResult.AlreadyCheckedIn(existing)
            return
        }
        recordCheckIn(attendee, camping, CheckInMethod.Qr)
    }

    /** Manual override when a QR is unavailable. */
    fun manualCheckIn(attendee: CampingAttendee) {
        if (!canManage || inFlight) return
        val camping = _camping.value ?: return
        if (recordFor(attendee.id) != null) return
        recordCheckIn(attendee, camping, CheckInMethod.Manual)
    }

    private fun recordCheckIn(attendee: CampingAttendee, camping: Camping, method: CheckInMethod) {
        inFlight = true
        _isRecording.value = true
        viewModelScope.launch {
            val record = CheckInRecord(
                campingId = camping.id,
                attendeeId = attendee.id,
                userId = attendee.userId,
                displayName = attendee.displayName,
                method = method,
                checkedInBy = reviewerId,
                church = attendee.church,
                preferredLanguage = attendee.preferredLanguage,
                ageGroup = attendee.ageGroup,
                gender = attendee.gender,
                photoUrl = attendee.photoUrl,
                checkedInAt = Date(),
            )
            try {
                checkInService.recordCheckIn(record)
                _records.update { current ->
                    listOf(record) + current.filterNot { it.attendeeId == record.attendeeId }
                }
                _lastScanResult.value = CheckInScanResult.Success(record)
            } catch (e: Exception) {
                _uiState.value = CheckInUiState.Error(e.message ?: "Could not record the check-in.")
                // Allow the same code to be retried after a failure.
                lastHandledValue = null
            } finally {
                _isRecording.value = false
                inFlight = false
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val DEFAULT_LOAD_ERROR = "Check-in records could not be loaded."
    }
}

internal fun Camping.permissionContext(): CampingPermissionContext =
    CampingPermissionContext(
        organizerLevelType = organizerLevel.type.wireValue,
        organizerLevelValue = organizerLevel.value,
        createdByUid = createdByUid,
    )
