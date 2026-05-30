package fr.ziyon.campzone.ui.transportation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingTransportationOption
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.TransportationBooking
import fr.ziyon.campzone.data.model.TransportationCheckpoint
import fr.ziyon.campzone.data.model.TransportationLeg
import fr.ziyon.campzone.data.model.TransportationPaymentStatus
import fr.ziyon.campzone.data.model.TransportationScanResult
import fr.ziyon.campzone.data.model.TransportationTicketPayload
import fr.ziyon.campzone.data.transportation.TransportationService
import fr.ziyon.campzone.ui.checkin.permissionContext
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TransportationUiState {
    data object Loading : TransportationUiState
    data object Restricted : TransportationUiState
    data object Ready : TransportationUiState
    data class Error(val message: String) : TransportationUiState
}

/**
 * Backs every transportation route (passenger My Passes, marshal Dashboard,
 * Scanner, Scan History). Each route gets its own VM instance, so the single
 * `bookings` flow means "passenger bookings" after [loadTickets] and "all
 * bookings" (manager-gated) after [loadManaged]. Mirrors the iOS
 * `TransportationObserver`.
 */
@HiltViewModel
class TransportationViewModel @Inject constructor(
    private val transportationService: TransportationService,
    private val campingService: CampingService,
) : ViewModel() {
    private val permissions = AppPermissionEvaluator()

    private val _uiState = MutableStateFlow<TransportationUiState>(TransportationUiState.Loading)
    val uiState: StateFlow<TransportationUiState> = _uiState.asStateFlow()

    private val _camping = MutableStateFlow<Camping?>(null)
    val camping: StateFlow<Camping?> = _camping.asStateFlow()

    private val _bookings = MutableStateFlow<List<TransportationBooking>>(emptyList())
    val bookings: StateFlow<List<TransportationBooking>> = _bookings.asStateFlow()

    private val _lastScanResult = MutableStateFlow<TransportationScanResult?>(null)
    val lastScanResult: StateFlow<TransportationScanResult?> = _lastScanResult.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    private var reviewerId: String = ""
    private var reviewerName: String? = null
    private var canManage = false
    private var loadedTicketsKey: Pair<String, String>? = null
    private var loadedManagedKey: Pair<String, String>? = null
    private var inFlight = false
    private var lastHandledValue: String? = null

    // MARK: - Loading

    fun loadTickets(campingId: String, user: AuthenticatedUser) {
        val key = campingId to user.uid
        if (loadedTicketsKey == key && _uiState.value !is TransportationUiState.Error) return
        loadedTicketsKey = key
        reviewerId = user.uid
        reviewerName = user.preferredDisplayName
        _uiState.value = TransportationUiState.Loading
        viewModelScope.launch {
            runCatching {
                val camping = campingService.fetchCamping(campingId)
                val bookings = transportationService.loadUserBookings(campingId, user.uid)
                camping to bookings
            }.onSuccess { (camping, bookings) ->
                _camping.value = camping
                _bookings.value = bookings
                _uiState.value = TransportationUiState.Ready
            }.onFailure { error ->
                loadedTicketsKey = null
                _uiState.value = TransportationUiState.Error(error.message ?: DEFAULT_LOAD_ERROR)
            }
        }
    }

    /** Manager-gated load of every booking - backs Dashboard, Scanner, History. */
    fun loadManaged(campingId: String, user: AuthenticatedUser) {
        val key = campingId to user.uid
        if (loadedManagedKey == key && _uiState.value !is TransportationUiState.Error) return
        loadedManagedKey = key
        reviewerId = user.uid
        reviewerName = user.preferredDisplayName
        _uiState.value = TransportationUiState.Loading
        viewModelScope.launch {
            runCatching { campingService.fetchCamping(campingId) }
                .onSuccess { camping ->
                    _camping.value = camping
                    val permissionUser = PermissionUser(user.role, user.uid, user.church)
                    canManage = permissions.canManageTransportation(permissionUser, camping.permissionContext())
                    if (!canManage) {
                        _uiState.value = TransportationUiState.Restricted
                        return@onSuccess
                    }
                    loadAllBookings(campingId)
                }
                .onFailure { error ->
                    loadedManagedKey = null
                    _uiState.value = TransportationUiState.Error(error.message ?: DEFAULT_LOAD_ERROR)
                }
        }
    }

    fun retryTickets(campingId: String, user: AuthenticatedUser) {
        loadedTicketsKey = null
        loadTickets(campingId, user)
    }

    /**
     * Silently re-pulls the signed-in passenger's bookings (and camping) using
     * the last [loadTickets] key. Used after a fare payment settles so the
     * ticket's `paymentStatus` flips without a full loading flash.
     */
    fun reloadTickets() {
        val key = loadedTicketsKey ?: return
        val (campingId, uid) = key
        viewModelScope.launch {
            runCatching {
                val camping = campingService.fetchCamping(campingId)
                val bookings = transportationService.loadUserBookings(campingId, uid)
                camping to bookings
            }.onSuccess { (camping, bookings) ->
                _camping.value = camping
                _bookings.value = bookings
            }
        }
    }

    fun retryManaged(campingId: String, user: AuthenticatedUser) {
        loadedManagedKey = null
        loadManaged(campingId, user)
    }

    private suspend fun loadAllBookings(campingId: String) {
        runCatching { transportationService.loadBookings(campingId) }
            .onSuccess {
                _bookings.value = it
                _uiState.value = TransportationUiState.Ready
            }
            .onFailure { error ->
                loadedManagedKey = null
                _uiState.value = TransportationUiState.Error(error.message ?: DEFAULT_LOAD_ERROR)
            }
    }

    // MARK: - Scanning

    fun dismissScanResult() {
        _lastScanResult.value = null
        lastHandledValue = null
    }

    fun handleScan(
        value: String,
        leg: TransportationLeg = TransportationLeg.Outbound,
        checkpoint: TransportationCheckpoint = TransportationCheckpoint.Departure,
        now: Date = Date(),
    ) {
        if (!canManage || inFlight) return
        if (value == lastHandledValue) return
        lastHandledValue = value
        val camping = _camping.value ?: return
        val payload = TransportationTicketPayload.decode(value)
        if (payload == null) {
            _lastScanResult.value = TransportationScanResult.Malformed
            return
        }
        if (payload.campingId != camping.id) {
            _lastScanResult.value = TransportationScanResult.WrongCamping
            return
        }

        inFlight = true
        _isScanning.value = true
        viewModelScope.launch {
            try {
                val booking = transportationService.booking(payload.campingId, payload.bookingId)
                val rejection = validate(booking, payload, camping, leg, checkpoint, now)
                if (rejection != null) {
                    _lastScanResult.value = rejection
                    return@launch
                }
                val updated = if (checkpoint == TransportationCheckpoint.Arrival) {
                    transportationService.markArrived(
                        campingId = booking.campingId,
                        bookingId = booking.id,
                        reviewerId = reviewerId,
                        leg = leg,
                        reviewerName = reviewerName,
                        location = null,
                    )
                } else {
                    transportationService.markBoarded(
                        campingId = booking.campingId,
                        bookingId = booking.id,
                        reviewerId = reviewerId,
                        leg = leg,
                        reviewerName = reviewerName,
                        location = null,
                    )
                }
                replace(updated)
                _lastScanResult.value = if (checkpoint == TransportationCheckpoint.Arrival) {
                    TransportationScanResult.ArrivalSuccess(updated)
                } else {
                    TransportationScanResult.Success(updated)
                }
            } catch (_: Exception) {
                _lastScanResult.value = TransportationScanResult.UnknownBooking
                lastHandledValue = null
            } finally {
                _isScanning.value = false
                inFlight = false
            }
        }
    }

    /**
     * Returns a rejection result, or `null` when the scan should be recorded.
     * Validation order mirrors iOS `TransportationObserver.handleScan`.
     */
    private fun validate(
        booking: TransportationBooking,
        payload: TransportationTicketPayload,
        camping: Camping,
        leg: TransportationLeg,
        checkpoint: TransportationCheckpoint,
        now: Date,
    ): TransportationScanResult? {
        if (
            booking.ticketToken != payload.token ||
            booking.registrationId != payload.registrationId ||
            booking.participantId != payload.participantId
        ) {
            return TransportationScanResult.TokenMismatch
        }
        if (!booking.isActive) return TransportationScanResult.Inactive(booking)
        val approved = camping.attendees.any {
            it.id == booking.registrationId && it.registrationStatus == RegistrationApprovalStatus.Approved
        }
        if (!approved) return TransportationScanResult.RegistrationNotApproved
        if (now.before(booking.validFrom) || now.after(booking.validUntil)) {
            return TransportationScanResult.Expired
        }
        if (!booking.paymentStatus.allowsBoarding) return TransportationScanResult.Unpaid(booking)
        // Round-trip safety: a return scan can't land on a one-way ticket.
        if (leg != TransportationLeg.Outbound && !booking.coversReturn) {
            return TransportationScanResult.Inactive(booking)
        }
        return if (checkpoint == TransportationCheckpoint.Arrival) {
            when {
                !booking.didScan(leg, TransportationCheckpoint.Departure) ->
                    TransportationScanResult.NotBoardedForArrival(booking)
                booking.didScan(leg, TransportationCheckpoint.Arrival) ->
                    TransportationScanResult.AlreadyArrived(booking)
                else -> null
            }
        } else {
            if (booking.didScan(leg, TransportationCheckpoint.Departure)) {
                TransportationScanResult.AlreadyBoarded(booking)
            } else {
                null
            }
        }
    }

    // MARK: - Operations

    fun updatePaymentStatus(booking: TransportationBooking, status: TransportationPaymentStatus) {
        if (status == booking.paymentStatus) return
        runOperation {
            val updated = transportationService.updatePaymentStatus(
                campingId = booking.campingId,
                bookingId = booking.id,
                status = status,
                reviewerId = reviewerId,
            )
            replace(updated)
        }
    }

    fun cancelBooking(booking: TransportationBooking, reason: String? = DEFAULT_CANCEL_REASON) {
        runOperation {
            val updated = transportationService.cancelBooking(
                campingId = booking.campingId,
                bookingId = booking.id,
                reviewerId = reviewerId,
                reason = reason,
            )
            replace(updated)
        }
    }

    fun addVoyager(
        camping: Camping,
        attendee: CampingAttendee,
        option: CampingTransportationOption?,
    ) {
        runOperation {
            val created = transportationService.createBooking(
                campingId = camping.id,
                attendee = attendee,
                option = option,
                validFrom = camping.startDate,
                validUntil = camping.endDate,
            )
            // Mirror iOS: a free option settles as `waived`; reach that end-state
            // via the manager update path so the create literal stays `unpaid`.
            val settled = if (option?.hasFee != true) {
                transportationService.updatePaymentStatus(
                    campingId = camping.id,
                    bookingId = created.id,
                    status = TransportationPaymentStatus.Waived,
                    reviewerId = reviewerId,
                )
            } else {
                created
            }
            replace(settled)
        }
    }

    fun clearOperationError() {
        _operationError.value = null
    }

    private fun runOperation(block: suspend () -> Unit) {
        if (_isUpdating.value) return
        _isUpdating.value = true
        _operationError.value = null
        viewModelScope.launch {
            runCatching { block() }
                .onFailure { _operationError.value = it.message ?: DEFAULT_OP_ERROR }
            _isUpdating.value = false
        }
    }

    private fun replace(booking: TransportationBooking) {
        _bookings.update { current ->
            (current.filterNot { it.id == booking.id } + booking)
                .sortedBy { it.participantName.lowercase() }
        }
    }

    private companion object {
        const val DEFAULT_LOAD_ERROR = "Transportation bookings could not be loaded."
        const val DEFAULT_OP_ERROR = "The transportation update could not be saved."
        const val DEFAULT_CANCEL_REASON = "Cancelled from dashboard"
    }
}
