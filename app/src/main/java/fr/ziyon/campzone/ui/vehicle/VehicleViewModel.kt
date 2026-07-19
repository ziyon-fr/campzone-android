package fr.ziyon.campzone.ui.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.i18n.StringProvider
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingVehicle
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import fr.ziyon.campzone.data.model.TransportationMode
import fr.ziyon.campzone.data.model.UserVehicle
import fr.ziyon.campzone.data.model.VehicleCheckIn
import fr.ziyon.campzone.data.model.VehicleCheckInPayload
import fr.ziyon.campzone.data.model.VehicleMutation
import fr.ziyon.campzone.data.model.VehicleScanResult
import fr.ziyon.campzone.data.model.VehicleStatus
import fr.ziyon.campzone.data.model.VehicleTokenFactory
import fr.ziyon.campzone.data.notifications.NotificationApi
import fr.ziyon.campzone.data.notifications.TransportationNotification
import fr.ziyon.campzone.data.vehicle.UserVehicleService
import fr.ziyon.campzone.data.vehicle.VehicleAssignmentConflictMessage
import fr.ziyon.campzone.data.vehicle.VehicleSeatUnavailableMessage
import fr.ziyon.campzone.data.vehicle.VehicleService
import fr.ziyon.campzone.ui.checkin.permissionContext
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface VehicleLoadState {
    data object Loading : VehicleLoadState
    data object Ready : VehicleLoadState
    data object Restricted : VehicleLoadState
    data class Error(val message: String) : VehicleLoadState
}

data class VehicleDashboardStats(
    val totalVehicles: Int = 0,
    val arrivedVehicles: Int = 0,
    val peopleExpected: Int = 0,
    val peopleArrived: Int = 0,
    val vehiclesWithSeats: Int = 0,
    val seatsAvailable: Int = 0,
    val peopleNeedingTransport: Int = 0,
)

data class VehicleUiState(
    val loadState: VehicleLoadState = VehicleLoadState.Loading,
    val camping: Camping? = null,
    val vehicles: List<CampingVehicle> = emptyList(),
    val availableSeatVehicles: List<CampingVehicle> = emptyList(),
    val peopleNeedingTransport: List<CampingAttendee> = emptyList(),
    val canManageTransportation: Boolean = false,
    val operationMessage: String? = null,
    val operationError: String? = null,
    val isUpdating: Boolean = false,
    val isScanning: Boolean = false,
    val savingVehicle: Boolean = false,
    val lastScanResult: VehicleScanResult? = null,
) {
    val activeVehicles: List<CampingVehicle>
        get() = vehicles.filter { it.status != VehicleStatus.Cancelled }

    val arrivedVehicles: List<CampingVehicle>
        get() = activeVehicles.filter { it.hasArrived }

    val dashboardStats: VehicleDashboardStats
        get() = VehicleDashboardStats(
            totalVehicles = activeVehicles.size,
            arrivedVehicles = arrivedVehicles.size,
            peopleExpected = activeVehicles.sumOf { it.expectedRegisteredCount },
            peopleArrived = arrivedVehicles.sumOf { it.expectedRegisteredCount },
            vehiclesWithSeats = activeVehicles.count { it.offeredSeatCount > 0 },
            seatsAvailable = activeVehicles.sumOf { it.offeredSeatCount },
            peopleNeedingTransport = peopleNeedingTransport.size,
        )

    fun vehicle(id: String): CampingVehicle? = vehicles.firstOrNull { it.id == id }

    fun selfAttendee(user: AuthenticatedUser): CampingAttendee? =
        camping?.attendees?.firstOrNull {
            it.registrationStatus == RegistrationApprovalStatus.Approved &&
                it.participantKind == RegistrationParticipantKind.SelfParticipant &&
                (it.id == user.uid || it.userId == user.uid)
        }

    fun primarySubjectAttendee(user: AuthenticatedUser): CampingAttendee? =
        selfAttendee(user) ?: camping?.attendees
            ?.filter {
                it.registrationStatus == RegistrationApprovalStatus.Approved &&
                    it.participantKind == RegistrationParticipantKind.Child &&
                    it.guardianId == user.uid
            }
            ?.minByOrNull { it.displayName.lowercase(Locale.ROOT) }

    fun actionSubjectAttendee(
        user: AuthenticatedUser,
        initialDecisionKind: String?,
        initialRegistrationId: String?,
    ): CampingAttendee? {
        val isInvitation = initialDecisionKind?.trim()?.lowercase(Locale.ROOT) == "invitation"
        if (isInvitation && !initialRegistrationId.isNullOrBlank()) {
            camping?.attendees?.firstOrNull {
                it.canActAsInvitationSubject(
                    registrationId = initialRegistrationId,
                    userId = user.uid,
                )
            }?.let { return it }
        }

        return primarySubjectAttendee(user)
    }

    fun vehicleDriven(registrationId: String): CampingVehicle? =
        vehicles.firstOrNull {
            it.driverRegistrationId == registrationId && it.status != VehicleStatus.Cancelled
        }

    fun vehicleRidden(registrationId: String): CampingVehicle? =
        vehicles.firstOrNull {
            it.includesPassenger(registrationId) && it.status != VehicleStatus.Cancelled
        }

    fun pendingVehicle(registrationId: String): CampingVehicle? =
        vehicles.firstOrNull {
            it.status != VehicleStatus.Cancelled && registrationId in it.pendingPassengerRegistrationIds
        }

    fun isRegistrationClaimed(registrationId: String): Boolean =
        activeVehicles.any { it.claimsRegistration(registrationId) }

    private fun CampingAttendee.canActAsInvitationSubject(
        registrationId: String,
        userId: String,
    ): Boolean {
        if (id != registrationId || registrationStatus != RegistrationApprovalStatus.Approved) return false
        if (participantKind == RegistrationParticipantKind.Child && guardianId == userId) return true
        return participantKind == RegistrationParticipantKind.SelfParticipant &&
            (this.userId == userId || id == userId)
    }
}

data class VehicleFormInput(
    val driverName: String,
    val plateNumber: String,
    val brand: String?,
    val model: String?,
    val color: String?,
    val totalSeats: Int,
    val peopleInCar: Int,
    val hasAvailableSeats: Boolean,
    val offeredSeats: Int? = null,
    val notes: String?,
    val userVehicleId: String? = null,
    val passengers: List<CampingAttendee> = emptyList(),
)

data class SavedVehicleUiState(
    val loadState: VehicleLoadState = VehicleLoadState.Loading,
    val vehicles: List<UserVehicle> = emptyList(),
    val operationMessage: String? = null,
    val operationError: String? = null,
    val isSaving: Boolean = false,
) {
    val defaultVehicle: UserVehicle?
        get() = vehicles.firstOrNull { it.isDefault } ?: vehicles.firstOrNull()
}

@HiltViewModel
class VehicleViewModel @Inject constructor(
    private val vehicleService: VehicleService,
    private val userVehicleService: UserVehicleService,
    private val campingService: CampingService,
    private val stringProvider: StringProvider,
    private val notificationApi: NotificationApi,
) : ViewModel() {
    private val permissions = AppPermissionEvaluator()
    private val _uiState = MutableStateFlow(VehicleUiState())
    val uiState: StateFlow<VehicleUiState> = _uiState.asStateFlow()

    val savedVehicleState: StateFlow<SavedVehicleUiState>
        get() = _savedVehicleState.asStateFlow()
    private val _savedVehicleState = MutableStateFlow(SavedVehicleUiState())

    val vehicleSearchText = MutableStateFlow("")
    val filteredVehicles: StateFlow<List<CampingVehicle>> =
        combine(_uiState, vehicleSearchText) { state, query ->
            val trimmed = query.trim()
            if (trimmed.isBlank()) {
                state.vehicles
            } else {
                state.vehicles.filter {
                    it.driverName.contains(trimmed, ignoreCase = true) ||
                        it.plateNumber.contains(trimmed, ignoreCase = true) ||
                        it.brand.orEmpty().contains(trimmed, ignoreCase = true) ||
                        it.model.orEmpty().contains(trimmed, ignoreCase = true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private var vehicleJob: Job? = null
    private var savedVehicleJob: Job? = null
    private var loadedCampingKey: Pair<String, String>? = null
    private var loadedSavedUserId: String? = null
    private var inFlightScan = false
    private var lastHandledScan: String? = null

    fun load(campingId: String, user: AuthenticatedUser, requireManager: Boolean = false) {
        val key = campingId to user.uid
        if (loadedCampingKey == key && _uiState.value.loadState !is VehicleLoadState.Error) return
        loadedCampingKey = key
        vehicleJob?.cancel()
        _uiState.value = VehicleUiState(loadState = VehicleLoadState.Loading)
        viewModelScope.launch {
            runCatching { campingService.fetchCamping(campingId) }
                .onSuccess { camping ->
                    val permissionUser = PermissionUser(user.role, user.uid, user.church)
                    val canManage = permissions.canManageTransportation(permissionUser, camping.permissionContext())
                    if (requireManager && !canManage) {
                        _uiState.update {
                            it.copy(
                                loadState = VehicleLoadState.Restricted,
                                camping = camping,
                                canManageTransportation = false,
                            )
                        }
                        return@onSuccess
                    }
                    _uiState.update {
                        it.copy(
                            loadState = VehicleLoadState.Ready,
                            camping = camping,
                            canManageTransportation = canManage,
                        )
                    }
                    collectVehicles(camping.id)
                    loadDashboardExtras(camping.id)
                }
                .onFailure { error ->
                    loadedCampingKey = null
                    _uiState.value = VehicleUiState(
                        loadState = VehicleLoadState.Error(error.message ?: stringProvider.get(R.string.vehicle_load_error)),
                    )
                }
        }
    }

    fun retry(campingId: String, user: AuthenticatedUser, requireManager: Boolean = false) {
        loadedCampingKey = null
        load(campingId, user, requireManager)
    }

    fun loadSavedVehicles(userId: String) {
        if (loadedSavedUserId == userId && _savedVehicleState.value.loadState !is VehicleLoadState.Error) return
        loadedSavedUserId = userId
        savedVehicleJob?.cancel()
        _savedVehicleState.value = SavedVehicleUiState(loadState = VehicleLoadState.Loading)
        savedVehicleJob = viewModelScope.launch {
            userVehicleService.vehicles(userId).collect { vehicles ->
                _savedVehicleState.update {
                    it.copy(loadState = VehicleLoadState.Ready, vehicles = vehicles)
                }
            }
        }
    }

    fun retrySavedVehicles(userId: String) {
        loadedSavedUserId = null
        loadSavedVehicles(userId)
    }

    private fun collectVehicles(campingId: String) {
        vehicleJob?.cancel()
        vehicleJob = viewModelScope.launch {
            vehicleService.vehicles(campingId).collect { vehicles ->
                _uiState.update { it.copy(vehicles = vehicles, loadState = VehicleLoadState.Ready) }
            }
        }
    }

    private fun loadDashboardExtras(campingId: String) {
        viewModelScope.launch {
            runCatching {
                vehicleService.vehiclesWithAvailableSeats(campingId) to
                    vehicleService.peopleNeedingTransport(campingId)
            }.onSuccess { (available, needing) ->
                _uiState.update {
                    it.copy(
                        availableSeatVehicles = available,
                        peopleNeedingTransport = needing,
                    )
                }
            }
        }
    }

    fun refreshCamping(campingId: String) {
        viewModelScope.launch {
            runCatching { campingService.fetchCamping(campingId) }
                .onSuccess { camping -> _uiState.update { it.copy(camping = camping) } }
        }
    }

    fun createVehicle(
        campingId: String,
        user: AuthenticatedUser,
        attendee: CampingAttendee,
        input: VehicleFormInput,
        onCreated: (CampingVehicle) -> Unit,
    ) {
        val occupied = minOf(
            input.totalSeats,
            maxOf(input.peopleInCar, input.passengers.size + 1, 1),
        )
        val claimedRegistrationIds = listOf(attendee.id) + input.passengers.map { it.id }
        if (VehicleMutation.hasActiveAssignmentConflict(_uiState.value.activeVehicles, claimedRegistrationIds)) {
            _uiState.update { state ->
                state.copy(operationError = stringProvider.get(R.string.vehicle_assignment_conflict_error))
            }
            return
        }
        val vehicle = CampingVehicle(
            campingId = campingId,
            ownerUserId = user.uid,
            userVehicleId = input.userVehicleId,
            driverUserId = user.uid,
            driverRegistrationId = attendee.id,
            driverName = input.driverName.ifBlank { user.preferredDisplayName.ifBlank { attendee.displayName } },
            driverPhotoUrl = user.photoUrl ?: attendee.photoUrl,
            plateNumber = input.plateNumber,
            brand = input.brand.clean(),
            model = input.model.clean(),
            color = input.color.clean(),
            totalSeats = input.totalSeats,
            occupiedSeats = occupied,
            hasAvailableSeats = input.hasAvailableSeats,
            offeredSeats = input.offeredSeats?.takeIf { input.hasAvailableSeats },
            passengerRegistrationIds = input.passengers.map { it.id },
            passengerNames = input.passengers.map { it.displayName },
            qrToken = VehicleTokenFactory.makeToken(),
            invitationCode = VehicleTokenFactory.makeInvitationCode(),
            status = VehicleStatus.Pending,
            notes = input.notes.clean(),
        )
        vehicle.validationError?.let {
            _uiState.update { state -> state.copy(operationError = it.message) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(savingVehicle = true, operationError = null, operationMessage = null) }
            runCatching {
                val created = vehicleService.createVehicle(vehicle)
                campingService.updateRegistrationTransport(
                    campingId = campingId,
                    attendeeId = attendee.id,
                    transportationMode = TransportationMode.OwnCar,
                    vehicleId = created.id,
                    isDriver = true,
                    needsTransportHelp = false,
                    notes = input.notes.clean(),
                )
                input.passengers.forEach { passenger ->
                    campingService.updateRegistrationTransport(
                        campingId = campingId,
                        attendeeId = passenger.id,
                        transportationMode = TransportationMode.Carpool,
                        vehicleId = created.id,
                        isDriver = false,
                        needsTransportHelp = false,
                        notes = passenger.transportationNotes,
                    )
                }
                created
            }.onSuccess { created ->
                _uiState.update {
                    it.copy(
                        savingVehicle = false,
                        operationMessage = stringProvider.get(R.string.vehicle_car_ready_message),
                    )
                }
                refreshCamping(campingId)
                onCreated(created)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        savingVehicle = false,
                        operationError = error.message ?: stringProvider.get(R.string.vehicle_create_error),
                    )
                }
            }
        }
    }

    fun updateVehicle(vehicle: CampingVehicle, onSaved: () -> Unit = {}) {
        vehicle.validationError?.let {
            _uiState.update { state -> state.copy(operationError = it.message) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(savingVehicle = true, operationError = null) }
            runCatching { vehicleService.updateVehicle(vehicle) }
                .onSuccess { updated ->
                    upsert(updated)
                    _uiState.update {
                        it.copy(savingVehicle = false, operationMessage = stringProvider.get(R.string.vehicle_details_updated_message))
                    }
                    onSaved()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            savingVehicle = false,
                            operationError = error.message ?: stringProvider.get(R.string.vehicle_save_error),
                        )
                    }
                }
        }
    }

    fun cancelVehicle(vehicle: CampingVehicle, attendeeId: String? = null) {
        viewModelScope.launch {
            performVehicleAction(
                action = { vehicleService.cancelVehicle(vehicle.campingId, vehicle.id) },
                success = stringProvider.get(R.string.vehicle_cancelled_message),
            )
            if (attendeeId != null) {
                runCatching {
                    campingService.updateRegistrationTransport(
                        campingId = vehicle.campingId,
                        attendeeId = attendeeId,
                        transportationMode = null,
                        vehicleId = null,
                        isDriver = false,
                        needsTransportHelp = false,
                        notes = null,
                    )
                }
            }
            vehicle.passengerRegistrationIds.forEach { passengerId ->
                runCatching {
                    campingService.updateRegistrationTransport(
                        campingId = vehicle.campingId,
                        attendeeId = passengerId,
                        transportationMode = null,
                        vehicleId = null,
                        isDriver = false,
                        needsTransportHelp = false,
                        notes = null,
                    )
                }
            }
            refreshCamping(vehicle.campingId)
        }
    }

    fun deleteVehicle(vehicle: CampingVehicle) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true, operationError = null) }
            runCatching { vehicleService.deleteVehicle(vehicle.campingId, vehicle.id) }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            isUpdating = false,
                            vehicles = state.vehicles.filterNot { it.id == vehicle.id },
                            operationMessage = stringProvider.get(R.string.vehicle_removed_message),
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isUpdating = false, operationError = error.message ?: stringProvider.get(R.string.vehicle_remove_error))
                    }
                }
        }
    }

    fun requestJoin(vehicle: CampingVehicle, attendee: CampingAttendee) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true, operationError = null) }
            runCatching {
                val updated = vehicleService.requestJoin(vehicle.campingId, vehicle.id, attendee.id, attendee.displayName)
                upsert(updated)
                notificationApi.dispatchTransportation(
                    TransportationNotification(
                        event = "join_request",
                        campingId = vehicle.campingId,
                        vehicleId = vehicle.id,
                        registrationId = attendee.id,
                        participantName = attendee.displayName,
                        driverName = vehicle.driverName,
                    ),
                )
                campingService.updateRegistrationTransport(
                    campingId = vehicle.campingId,
                    attendeeId = attendee.id,
                    transportationMode = TransportationMode.Carpool,
                    vehicleId = null,
                    isDriver = false,
                    needsTransportHelp = false,
                    notes = null,
                )
            }.onSuccess {
                _uiState.update { it.copy(isUpdating = false, operationMessage = stringProvider.get(R.string.vehicle_request_sent_driver)) }
                refreshCamping(vehicle.campingId)
            }.onFailure { error ->
                _uiState.update { it.copy(isUpdating = false, operationError = vehicleError(error, R.string.vehicle_request_ride_error)) }
            }
        }
    }

    fun joinByInvitationCode(campingId: String, code: String, attendee: CampingAttendee) {
        val trimmed = code.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(operationError = stringProvider.get(R.string.vehicle_invitation_code_required)) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true, operationError = null) }
            runCatching {
                val vehicle = vehicleService.vehicleByInvitationCode(campingId, trimmed)
                    ?: error(stringProvider.get(R.string.vehicle_no_car_for_code))
                val updated = vehicleService.requestJoin(campingId, vehicle.id, attendee.id, attendee.displayName)
                notificationApi.dispatchTransportation(
                    TransportationNotification(
                        event = "join_request",
                        campingId = campingId,
                        vehicleId = vehicle.id,
                        registrationId = attendee.id,
                        participantName = attendee.displayName,
                        driverName = vehicle.driverName,
                    ),
                )
                updated
            }.onSuccess { updated ->
                upsert(updated)
                campingService.updateRegistrationTransport(
                    campingId = campingId,
                    attendeeId = attendee.id,
                    transportationMode = TransportationMode.Carpool,
                    vehicleId = null,
                    isDriver = false,
                    needsTransportHelp = false,
                    notes = null,
                )
                _uiState.update {
                    it.copy(isUpdating = false, operationMessage = stringProvider.get(R.string.vehicle_request_sent_driver))
                }
                refreshCamping(campingId)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isUpdating = false, operationError = vehicleError(error, R.string.vehicle_join_code_error))
                }
            }
        }
    }

    fun withdrawJoinRequest(vehicle: CampingVehicle, attendeeId: String) {
        viewModelScope.launch {
            performVehicleAction(
                action = { vehicleService.withdrawJoinRequest(vehicle.campingId, vehicle.id, attendeeId) },
                success = stringProvider.get(R.string.vehicle_request_cancelled),
            )
        }
    }

    fun approvePassenger(vehicle: CampingVehicle, registrationId: String) {
        viewModelScope.launch {
            performVehicleAction(
                action = {
                    val updated = vehicleService.approvePassenger(vehicle.campingId, vehicle.id, registrationId)
                    campingService.updateRegistrationTransport(
                        campingId = vehicle.campingId,
                        attendeeId = registrationId,
                        transportationMode = TransportationMode.Carpool,
                        vehicleId = vehicle.id,
                        isDriver = false,
                        needsTransportHelp = false,
                        notes = null,
                    )
                    updated
                },
                success = stringProvider.get(R.string.vehicle_passenger_approved_message),
            )
            refreshCamping(vehicle.campingId)
        }
    }

    fun denyPassenger(vehicle: CampingVehicle, registrationId: String) {
        viewModelScope.launch {
            performVehicleAction(
                action = { vehicleService.denyPassenger(vehicle.campingId, vehicle.id, registrationId) },
                success = stringProvider.get(R.string.vehicle_request_declined_message),
            )
        }
    }

    fun removePassenger(vehicle: CampingVehicle, registrationId: String) {
        viewModelScope.launch {
            performVehicleAction(
                action = {
                    val updated = vehicleService.removePassenger(vehicle.campingId, vehicle.id, registrationId)
                    campingService.updateRegistrationTransport(
                        campingId = vehicle.campingId,
                        attendeeId = registrationId,
                        transportationMode = null,
                        vehicleId = null,
                        isDriver = false,
                        needsTransportHelp = false,
                        notes = null,
                    )
                    updated
                },
                success = stringProvider.get(R.string.vehicle_passenger_removed_message),
            )
            refreshCamping(vehicle.campingId)
        }
    }

    fun addPassenger(vehicle: CampingVehicle, attendee: CampingAttendee) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true, operationError = null) }
            runCatching {
                val updated = vehicleService.addPassenger(
                    vehicle.campingId,
                    vehicle.id,
                    attendee.id,
                    attendee.displayName,
                )
                campingService.updateRegistrationTransport(
                    campingId = vehicle.campingId,
                    attendeeId = attendee.id,
                    transportationMode = TransportationMode.Carpool,
                    vehicleId = vehicle.id,
                    isDriver = false,
                    needsTransportHelp = false,
                    notes = attendee.transportationNotes,
                )
                updated
            }.onSuccess { updated ->
                upsert(updated)
                refreshCamping(vehicle.campingId)
                _uiState.update { it.copy(isUpdating = false, operationMessage = stringProvider.get(R.string.vehicle_passenger_added_message, attendee.displayName)) }
            }.onFailure { error ->
                _uiState.update { it.copy(isUpdating = false, operationError = vehicleError(error, R.string.vehicle_add_passenger_error)) }
            }
        }
    }

    fun invitePassenger(vehicle: CampingVehicle, attendee: CampingAttendee) {
        if (!VehicleMutation.canAcceptNewPassenger(vehicle)) {
            _uiState.update { it.copy(operationError = stringProvider.get(R.string.vehicle_seat_unavailable_error)) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true, operationError = null) }
            runCatching {
                notificationApi.dispatchTransportation(
                    TransportationNotification(
                        event = "invitation",
                        campingId = vehicle.campingId,
                        vehicleId = vehicle.id,
                        registrationId = attendee.id,
                        participantName = attendee.displayName,
                        driverName = vehicle.driverName,
                    ),
                )
            }.onSuccess {
                _uiState.update { it.copy(isUpdating = false, operationMessage = stringProvider.get(R.string.vehicle_invitation_sent_message, attendee.displayName)) }
            }.onFailure { error ->
                _uiState.update { it.copy(isUpdating = false, operationError = error.message ?: stringProvider.get(R.string.vehicle_invitation_send_error)) }
            }
        }
    }

    fun respondToInvitation(vehicle: CampingVehicle, attendee: CampingAttendee, accept: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true, operationError = null) }
            runCatching {
                if (accept) {
                    val updated = vehicleService.addPassenger(
                        vehicle.campingId,
                        vehicle.id,
                        attendee.id,
                        attendee.displayName,
                    )
                    campingService.updateRegistrationTransport(
                        campingId = vehicle.campingId,
                        attendeeId = attendee.id,
                        transportationMode = TransportationMode.Carpool,
                        vehicleId = vehicle.id,
                        isDriver = false,
                        needsTransportHelp = false,
                        notes = attendee.transportationNotes,
                    )
                    upsert(updated)
                }
                notificationApi.dispatchTransportation(
                    TransportationNotification(
                        event = if (accept) "invitation_accepted" else "invitation_declined",
                        campingId = vehicle.campingId,
                        vehicleId = vehicle.id,
                        registrationId = attendee.id,
                        participantName = attendee.displayName,
                        driverName = vehicle.driverName,
                    ),
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isUpdating = false,
                        operationMessage = stringProvider.get(
                            if (accept) R.string.vehicle_invitation_accepted_message else R.string.vehicle_invitation_declined_message,
                        ),
                    )
                }
                refreshCamping(vehicle.campingId)
            }.onFailure { error ->
                _uiState.update { it.copy(isUpdating = false, operationError = vehicleError(error, R.string.vehicle_invitation_response_error)) }
            }
        }
    }

    fun requestTransportHelp(campingId: String, attendeeId: String, notes: String? = null) {
        updateTransportIntent(
            campingId = campingId,
            attendeeId = attendeeId,
            transportationMode = null,
            vehicleId = null,
            isDriver = false,
            needsTransportHelp = true,
            notes = notes,
            success = stringProvider.get(R.string.vehicle_transport_help_requested_message),
        )
    }

    fun clearTransportIntent(
        campingId: String,
        attendeeId: String,
        success: String? = null,
    ) {
        updateTransportIntent(
            campingId = campingId,
            attendeeId = attendeeId,
            transportationMode = null,
            vehicleId = null,
            isDriver = false,
            needsTransportHelp = false,
            notes = null,
            success = success ?: stringProvider.get(R.string.vehicle_transport_choice_cleared),
        )
    }

    private fun updateTransportIntent(
        campingId: String,
        attendeeId: String,
        transportationMode: TransportationMode?,
        vehicleId: String?,
        isDriver: Boolean,
        needsTransportHelp: Boolean,
        notes: String?,
        success: String,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true, operationError = null) }
            runCatching {
                campingService.updateRegistrationTransport(
                    campingId = campingId,
                    attendeeId = attendeeId,
                    transportationMode = transportationMode,
                    vehicleId = vehicleId,
                    isDriver = isDriver,
                    needsTransportHelp = needsTransportHelp,
                    notes = notes,
                )
            }.onSuccess { camping ->
                _uiState.update {
                    it.copy(
                        isUpdating = false,
                        camping = camping,
                        operationMessage = success,
                    )
                }
                loadDashboardExtras(campingId)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isUpdating = false, operationError = error.message ?: stringProvider.get(R.string.vehicle_update_transport_error))
                }
            }
        }
    }

    fun handleScan(value: String, campingId: String) {
        if (inFlightScan || value == lastHandledScan) return
        lastHandledScan = value
        val payload = VehicleCheckInPayload.decode(value)
        if (payload == null) {
            _uiState.update { it.copy(lastScanResult = VehicleScanResult.Malformed) }
            return
        }
        inFlightScan = true
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, operationError = null) }
            runCatching { vehicleService.vehicleByToken(campingId, payload.token) }
                .onSuccess { vehicle ->
                    val result = when {
                        vehicle == null -> VehicleScanResult.UnknownVehicle
                        vehicle.campingId != campingId -> VehicleScanResult.WrongCamping
                        vehicle.status == VehicleStatus.Cancelled -> VehicleScanResult.Cancelled(vehicle)
                        vehicle.hasArrived -> VehicleScanResult.AlreadyArrived(vehicle)
                        else -> VehicleScanResult.Resolved(vehicle)
                    }
                    if (vehicle != null) upsert(vehicle)
                    _uiState.update { it.copy(isScanning = false, lastScanResult = result) }
                }
                .onFailure {
                    lastHandledScan = null
                    _uiState.update {
                        it.copy(isScanning = false, lastScanResult = VehicleScanResult.UnknownVehicle)
                    }
                }
            inFlightScan = false
        }
    }

    fun confirmArrival(
        vehicle: CampingVehicle,
        presentRegistrationIds: List<String>,
        plateNumberConfirmed: Boolean,
        notes: String?,
        reviewer: AuthenticatedUser,
        onDone: () -> Unit,
    ) {
        val expected = listOf(vehicle.driverRegistrationId) + vehicle.passengerRegistrationIds
        val present = presentRegistrationIds.distinct().filter { it in expected }
        val missing = expected.filterNot { it in present.toSet() }
        val checkIn = VehicleCheckIn(
            campingId = vehicle.campingId,
            vehicleId = vehicle.id,
            scannedToken = vehicle.qrToken,
            checkedInByUid = reviewer.uid,
            checkedInByName = reviewer.preferredDisplayName,
            expectedPassengerCount = expected.size,
            actualPassengerCount = present.size,
            presentRegistrationIds = present,
            missingRegistrationIds = missing,
            plateNumberConfirmed = plateNumberConfirmed,
            notes = notes.clean(),
        )
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true, operationError = null) }
            runCatching { vehicleService.checkInVehicle(checkIn) }
                .onSuccess { updated ->
                    upsert(updated)
                    _uiState.update {
                        it.copy(
                            isUpdating = false,
                            operationMessage = if (missing.isEmpty()) {
                                stringProvider.get(R.string.vehicle_arrival_all_here_message)
                            } else {
                                stringProvider.get(R.string.vehicle_arrival_missing_passengers_message)
                            },
                        )
                    }
                    onDone()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isUpdating = false, operationError = error.message ?: stringProvider.get(R.string.vehicle_confirm_arrival_error))
                    }
                }
        }
    }

    fun dismissScanResult() {
        lastHandledScan = null
        _uiState.update { it.copy(lastScanResult = null) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(operationMessage = null, operationError = null) }
        _savedVehicleState.update { it.copy(operationMessage = null, operationError = null) }
    }

    fun saveUserVehicle(vehicle: UserVehicle, onSaved: () -> Unit = {}) {
        vehicle.validationError?.let {
            _savedVehicleState.update { state -> state.copy(operationError = it.message) }
            return
        }
        viewModelScope.launch {
            _savedVehicleState.update { it.copy(isSaving = true, operationError = null) }
            runCatching { userVehicleService.saveVehicle(vehicle) }
                .onSuccess {
                    _savedVehicleState.update { state ->
                        state.copy(isSaving = false, operationMessage = stringProvider.get(R.string.vehicle_saved_message))
                    }
                    onSaved()
                }
                .onFailure { error ->
                    _savedVehicleState.update {
                        it.copy(isSaving = false, operationError = error.message ?: stringProvider.get(R.string.vehicle_save_error))
                    }
                }
        }
    }

    fun deleteUserVehicle(vehicle: UserVehicle) {
        viewModelScope.launch {
            _savedVehicleState.update { it.copy(isSaving = true, operationError = null) }
            runCatching { userVehicleService.deleteVehicle(vehicle.ownerUserId, vehicle.id) }
                .onSuccess {
                    _savedVehicleState.update { state ->
                        state.copy(
                            isSaving = false,
                            vehicles = state.vehicles.filterNot { it.id == vehicle.id },
                            operationMessage = stringProvider.get(R.string.vehicle_removed_message),
                        )
                    }
                }
                .onFailure { error ->
                    _savedVehicleState.update {
                        it.copy(isSaving = false, operationError = error.message ?: stringProvider.get(R.string.vehicle_remove_error))
                    }
                }
        }
    }

    fun setDefaultUserVehicle(vehicle: UserVehicle) {
        viewModelScope.launch {
            _savedVehicleState.update { it.copy(isSaving = true, operationError = null) }
            runCatching { userVehicleService.setDefault(vehicle.ownerUserId, vehicle.id) }
                .onSuccess { vehicles ->
                    _savedVehicleState.update {
                        it.copy(
                            isSaving = false,
                            vehicles = vehicles,
                            operationMessage = stringProvider.get(R.string.vehicle_default_updated_message),
                        )
                    }
                }
                .onFailure { error ->
                    _savedVehicleState.update {
                        it.copy(isSaving = false, operationError = error.message ?: stringProvider.get(R.string.vehicle_default_update_error))
                    }
                }
        }
    }

    private suspend fun performVehicleAction(
        action: suspend () -> CampingVehicle,
        success: String,
    ) {
        _uiState.update { it.copy(isUpdating = true, operationError = null, operationMessage = null) }
        runCatching { action() }
            .onSuccess { updated ->
                upsert(updated)
                _uiState.update { it.copy(isUpdating = false, operationMessage = success) }
                loadDashboardExtras(updated.campingId)
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(isUpdating = false, operationError = vehicleError(error, R.string.vehicle_update_failed))
                }
            }
    }

    private fun vehicleError(error: Throwable, fallbackResId: Int): String =
        when (error.message) {
            VehicleAssignmentConflictMessage -> stringProvider.get(R.string.vehicle_assignment_conflict_error)
            VehicleSeatUnavailableMessage -> stringProvider.get(R.string.vehicle_seat_unavailable_error)
            else -> error.message ?: stringProvider.get(fallbackResId)
        }

    private fun upsert(vehicle: CampingVehicle) {
        _uiState.update { state ->
            val updated = (state.vehicles.filterNot { it.id == vehicle.id } + vehicle)
                .sortedWith(compareBy<CampingVehicle> { it.status == VehicleStatus.Cancelled }
                    .thenBy { it.hasArrived }
                    .thenBy { it.driverName.lowercase(Locale.ROOT) })
            state.copy(vehicles = updated)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun String?.clean(): String? =
    this?.trim()?.takeUnless { it.isBlank() }
