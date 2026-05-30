package fr.ziyon.campzone.ui.lodging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.lodging.LodgingService
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.LodgingGenderPolicy
import fr.ziyon.campzone.data.model.LodgingKind
import fr.ziyon.campzone.data.model.LodgingUnit
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.ui.checkin.permissionContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Editor form for create/edit, kept separate from the persisted model. */
data class LodgingForm(
    val id: String? = null,
    val name: String = "",
    val kind: LodgingKind = LodgingKind.Tent,
    val capacityText: String = "4",
    val genderPolicy: LodgingGenderPolicy = LodgingGenderPolicy.Any,
    val notes: String = "",
) {
    val capacity: Int get() = (capacityText.trim().toIntOrNull() ?: 1).coerceAtLeast(1)
    val isValid: Boolean get() = name.isNotBlank()

    companion object {
        fun of(unit: LodgingUnit) = LodgingForm(
            id = unit.id,
            name = unit.name,
            kind = unit.kind,
            capacityText = unit.capacity.toString(),
            genderPolicy = unit.genderPolicy,
            notes = unit.notes,
        )
    }
}

sealed interface LodgingUiState {
    data object Loading : LodgingUiState

    /** Caller lacks `canManageTeams`; the admin surface is management-only. */
    data object Restricted : LodgingUiState

    data class Error(val message: String) : LodgingUiState

    data class Ready(
        val campingId: String,
        val units: List<LodgingUnit>,
        /** Approved attendees the manager can place (self + everyone). */
        val attendees: List<CampingAttendee>,
        val filter: LodgingGenderPolicy? = null,
        val isSaving: Boolean = false,
        val operationError: String? = null,
    ) : LodgingUiState {
        val filteredUnits: List<LodgingUnit>
            get() = filter?.let { policy -> units.filter { it.genderPolicy == policy } } ?: units

        val attendeesById: Map<String, CampingAttendee>
            get() = attendees.associateBy { it.id }

        /** Attendee ids already placed in some unit (for the assignment sheet). */
        val assignedIds: Set<String>
            get() = units.flatMap { it.occupantIds }.toSet()
    }
}

/**
 * Backs the manager "Lodging" screen: tent/cabin/room/dorm units with capacity,
 * gender policy and denormalized [LodgingUnit.occupantIds]. Read is open to any
 * signed-in user, but this management surface is gated `canManageTeams`
 * (matching the deployed rules); participants see their placement via
 * `MyLodgingCard`. Mirrors the iOS `LodgingAdminView` + observer.
 */
@HiltViewModel
class LodgingViewModel @Inject constructor(
    private val lodgingService: LodgingService,
    private val campingService: CampingService,
) : ViewModel() {

    private val permissions = AppPermissionEvaluator()

    private val _uiState = MutableStateFlow<LodgingUiState>(LodgingUiState.Loading)
    val uiState: StateFlow<LodgingUiState> = _uiState.asStateFlow()

    private var campingId: String = ""
    private var attendees: List<CampingAttendee> = emptyList()
    private var filter: LodgingGenderPolicy? = null
    private var observeJob: Job? = null
    private var loadedKey: Pair<String, String>? = null

    fun load(campingId: String, user: AuthenticatedUser) {
        val key = campingId to user.uid
        if (loadedKey == key && _uiState.value !is LodgingUiState.Error) return
        loadedKey = key
        this.campingId = campingId
        _uiState.value = LodgingUiState.Loading

        viewModelScope.launch {
            runCatching {
                val camping = campingService.fetchCamping(campingId)
                val roster = campingService.loadAttendees(campingId)
                camping to roster
            }.onSuccess { (camping, roster) ->
                val permissionUser = PermissionUser(user.role, user.uid, user.church)
                val canManage = permissions.canManageTeams(permissionUser, camping.permissionContext())
                if (!canManage) {
                    _uiState.value = LodgingUiState.Restricted
                    return@onSuccess
                }
                attendees = roster
                    .filter { it.registrationStatus == RegistrationApprovalStatus.Approved }
                    .sortedBy { it.displayName.lowercase() }
                observeUnits()
            }.onFailure { error ->
                loadedKey = null
                _uiState.value = LodgingUiState.Error(error.message ?: DEFAULT_ERROR)
            }
        }
    }

    fun retry(campingId: String, user: AuthenticatedUser) {
        loadedKey = null
        load(campingId, user)
    }

    private fun observeUnits() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            lodgingService.observeUnits(campingId).collect { units ->
                _uiState.update { current ->
                    val ready = current as? LodgingUiState.Ready
                    LodgingUiState.Ready(
                        campingId = campingId,
                        units = units,
                        attendees = attendees,
                        filter = filter,
                        isSaving = ready?.isSaving ?: false,
                        operationError = ready?.operationError,
                    )
                }
            }
        }
    }

    fun setFilter(policy: LodgingGenderPolicy?) {
        filter = policy
        _uiState.update { (it as? LodgingUiState.Ready)?.copy(filter = policy) ?: it }
    }

    fun saveUnit(form: LodgingForm) {
        if (!form.isValid) return
        val current = _uiState.value as? LodgingUiState.Ready ?: return
        val existing = form.id?.let { id -> current.units.firstOrNull { it.id == id } }
        val unit = LodgingUnit(
            id = form.id ?: UUID.randomUUID().toString(),
            campingId = campingId,
            name = form.name.trim(),
            kind = form.kind,
            capacity = form.capacity,
            genderPolicy = form.genderPolicy,
            notes = form.notes.trim(),
            occupantIds = existing?.occupantIds ?: emptyList(),
            createdAt = existing?.createdAt,
        )
        runOperation { lodgingService.saveUnit(unit) }
    }

    fun deleteUnit(unit: LodgingUnit) {
        runOperation { lodgingService.deleteUnit(unit.id, campingId) }
    }

    fun setOccupants(unitId: String, occupantIds: List<String>) {
        runOperation { lodgingService.setOccupants(unitId, campingId, occupantIds) }
    }

    fun clearOperationError() {
        _uiState.update { (it as? LodgingUiState.Ready)?.copy(operationError = null) ?: it }
    }

    private fun runOperation(block: suspend () -> Unit) {
        _uiState.update { (it as? LodgingUiState.Ready)?.copy(isSaving = true, operationError = null) ?: it }
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess {
                    _uiState.update { (it as? LodgingUiState.Ready)?.copy(isSaving = false) ?: it }
                }
                .onFailure { error ->
                    _uiState.update {
                        (it as? LodgingUiState.Ready)?.copy(
                            isSaving = false,
                            operationError = error.message ?: DEFAULT_OP_ERROR,
                        ) ?: it
                    }
                }
        }
    }

    private companion object {
        const val DEFAULT_ERROR = "Lodging could not be loaded."
        const val DEFAULT_OP_ERROR = "The change could not be saved."
    }
}
