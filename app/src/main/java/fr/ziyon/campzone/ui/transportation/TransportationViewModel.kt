package fr.ziyon.campzone.ui.transportation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.TransportationBoardingStatus
import fr.ziyon.campzone.data.model.TransportationBooking
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

    private var reviewerId: String = ""
    private var canManage = false
    private var loadedTicketsKey: Pair<String, String>? = null
    private var loadedScannerKey: Pair<String, String>? = null
    private var inFlight = false
    private var lastHandledValue: String? = null

    fun loadTickets(campingId: String, user: AuthenticatedUser) {
        val key = campingId to user.uid
        if (loadedTicketsKey == key && _uiState.value !is TransportationUiState.Error) return
        loadedTicketsKey = key
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

    fun loadScanner(campingId: String, user: AuthenticatedUser) {
        val key = campingId to user.uid
        if (loadedScannerKey == key && _uiState.value !is TransportationUiState.Error) return
        loadedScannerKey = key
        reviewerId = user.uid
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
                    loadedScannerKey = null
                    _uiState.value = TransportationUiState.Error(error.message ?: DEFAULT_LOAD_ERROR)
                }
        }
    }

    fun retryTickets(campingId: String, user: AuthenticatedUser) {
        loadedTicketsKey = null
        loadTickets(campingId, user)
    }

    fun retryScanner(campingId: String, user: AuthenticatedUser) {
        loadedScannerKey = null
        loadScanner(campingId, user)
    }

    fun dismissScanResult() {
        _lastScanResult.value = null
        lastHandledValue = null
    }

    fun handleScan(value: String, now: Date = Date()) {
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
                val result = validate(booking, payload, camping, now)
                if (result != null) {
                    _lastScanResult.value = result
                    return@launch
                }
                val updated = transportationService.markBoarded(
                    campingId = booking.campingId,
                    bookingId = booking.id,
                    boardedBy = reviewerId,
                )
                replace(updated)
                _lastScanResult.value = TransportationScanResult.Success(updated)
            } catch (_: Exception) {
                _lastScanResult.value = TransportationScanResult.UnknownBooking
                lastHandledValue = null
            } finally {
                _isScanning.value = false
                inFlight = false
            }
        }
    }

    private suspend fun loadAllBookings(campingId: String) {
        runCatching { transportationService.loadBookings(campingId) }
            .onSuccess {
                _bookings.value = it
                _uiState.value = TransportationUiState.Ready
            }
            .onFailure { error ->
                loadedScannerKey = null
                _uiState.value = TransportationUiState.Error(error.message ?: DEFAULT_LOAD_ERROR)
            }
    }

    private fun validate(
        booking: TransportationBooking,
        payload: TransportationTicketPayload,
        camping: Camping,
        now: Date,
    ): TransportationScanResult? {
        if (
            booking.ticketToken != payload.token ||
            booking.registrationId != payload.registrationId ||
            booking.participantId != payload.participantId
        ) {
            return TransportationScanResult.TokenMismatch
        }
        val attendee = camping.attendees.firstOrNull {
            it.id == booking.registrationId && it.registrationStatus == RegistrationApprovalStatus.Approved
        } ?: return TransportationScanResult.RegistrationNotApproved
        if (attendee.id != payload.participantId) return TransportationScanResult.TokenMismatch
        if (now.before(booking.validFrom) || now.after(booking.validUntil)) return TransportationScanResult.Expired
        if (!booking.canBoard) {
            return if (booking.boardingStatus == TransportationBoardingStatus.Boarded) {
                TransportationScanResult.AlreadyBoarded(booking)
            } else {
                TransportationScanResult.Unpaid(booking)
            }
        }
        return null
    }

    private fun replace(booking: TransportationBooking) {
        _bookings.update { current ->
            (current.filterNot { it.id == booking.id } + booking)
                .sortedBy { it.participantName.lowercase() }
        }
    }

    private companion object {
        const val DEFAULT_LOAD_ERROR = "Transportation bookings could not be loaded."
    }
}
