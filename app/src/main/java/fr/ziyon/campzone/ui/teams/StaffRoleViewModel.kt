package fr.ziyon.campzone.ui.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingStaffRole
import fr.ziyon.campzone.data.model.StaffCapability
import fr.ziyon.campzone.data.model.StaffRoleKind
import fr.ziyon.campzone.data.model.StaffRoleMember
import fr.ziyon.campzone.data.teams.StaffRoleDraft
import fr.ziyon.campzone.data.teams.TeamService
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface StaffRolesUiState {
    data object Loading : StaffRolesUiState
    data class Loaded(val roles: List<CampingStaffRole>) : StaffRolesUiState
    data object Empty : StaffRolesUiState
    data class Error(val message: String) : StaffRolesUiState
}

enum class StaffRoleOperationMessage {
    Saved,
    Deleted,
}

data class StaffRoleForm(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val kind: StaffRoleKind = StaffRoleKind.Custom,
    val description: String = "",
    val symbolName: String = CampingStaffRole.DEFAULT_SYMBOL,
    val colorHex: String = CampingStaffRole.DEFAULT_COLOR,
    val members: List<StaffRoleMember> = emptyList(),
    val capabilities: List<StaffCapability> = emptyList(),
    val chatEnabled: Boolean = true,
) {
    val isValid: Boolean get() = name.isNotBlank()
}

@HiltViewModel
class StaffRoleViewModel @Inject constructor(
    private val service: TeamService,
) : ViewModel() {
    private val _uiState = MutableStateFlow<StaffRolesUiState>(StaffRolesUiState.Loading)
    val uiState: StateFlow<StaffRolesUiState> = _uiState.asStateFlow()

    private val _form = MutableStateFlow(StaffRoleForm())
    val form: StateFlow<StaffRoleForm> = _form.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    private val _operationMessage = MutableStateFlow<StaffRoleOperationMessage?>(null)
    val operationMessage: StateFlow<StaffRoleOperationMessage?> = _operationMessage.asStateFlow()

    private var roles: List<CampingStaffRole> = emptyList()
    private var observeJob: Job? = null
    private var activeScope: Scope? = null

    fun start(campingId: String, currentUserId: String, canManageAll: Boolean) {
        val scope = Scope(campingId, currentUserId.takeUnless { canManageAll })
        if (scope == activeScope && observeJob?.isActive == true) return
        activeScope = scope
        observeJob?.cancel()
        _uiState.value = StaffRolesUiState.Loading
        observeJob = viewModelScope.launch {
            try {
                service.observeStaffRoles(campingId, scope.memberUserId).collect { latest ->
                    roles = latest
                    publish()
                }
            } catch (_: CancellationException) {
                // Expected when navigation changes the observed scope.
            } catch (error: Exception) {
                _uiState.value = StaffRolesUiState.Error(
                    error.message ?: "Could not load operations teams.",
                )
            }
        }
    }

    fun refresh(campingId: String, currentUserId: String, canManageAll: Boolean) {
        viewModelScope.launch {
            try {
                roles = service.loadStaffRoles(
                    campingId,
                    currentUserId.takeUnless { canManageAll },
                )
                publish()
            } catch (error: Exception) {
                _operationError.value = error.message ?: "Could not refresh operations teams."
            }
        }
    }

    fun role(id: String): CampingStaffRole? = roles.firstOrNull { it.id == id }

    fun prepareNew() {
        _form.value = StaffRoleForm()
        _operationError.value = null
    }

    fun prepareEdit(role: CampingStaffRole) {
        _form.value = StaffRoleForm(
            id = role.id,
            name = role.name,
            kind = role.kind,
            description = role.description,
            symbolName = role.symbolName,
            colorHex = role.colorHex,
            members = role.members,
            capabilities = role.capabilities,
            chatEnabled = role.chatEnabled,
        )
        _operationError.value = null
    }

    fun updateForm(transform: (StaffRoleForm) -> StaffRoleForm) {
        _form.value = transform(_form.value)
    }

    fun setMember(attendee: CampingAttendee, selected: Boolean) {
        updateForm { current ->
            val members = if (selected) {
                if (current.members.any { it.userId == attendee.userId }) current.members
                else current.members + attendee.toStaffRoleMember()
            } else {
                current.members.filterNot { it.userId == attendee.userId }
            }
            current.copy(members = members.sortedBy { it.displayName.lowercase() })
        }
    }

    fun updateMemberTitle(userId: String, title: String) {
        updateForm { current ->
            current.copy(
                members = current.members.map { member ->
                    if (member.userId == userId) member.copy(title = title) else member
                },
            )
        }
    }

    fun save(campingId: String, createdByUid: String, onSuccess: (CampingStaffRole) -> Unit) {
        val current = _form.value
        if (!current.isValid || _isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            _operationError.value = null
            try {
                val saved = service.saveStaffRole(
                    StaffRoleDraft(
                        id = current.id,
                        campingId = campingId,
                        name = current.name.trim(),
                        kind = current.kind,
                        description = current.description.trim(),
                        symbolName = current.symbolName,
                        colorHex = normalizeColor(current.colorHex),
                        members = current.members.distinctBy { it.userId },
                        capabilities = current.capabilities.distinct(),
                        chatEnabled = current.chatEnabled,
                        createdByUid = createdByUid,
                    ),
                )
                roles = (roles.filterNot { it.id == saved.id } + saved)
                    .sortedBy { it.name.lowercase() }
                _form.value = StaffRoleForm(
                    id = saved.id,
                    name = saved.name,
                    kind = saved.kind,
                    description = saved.description,
                    symbolName = saved.symbolName,
                    colorHex = saved.colorHex,
                    members = saved.members,
                    capabilities = saved.capabilities,
                    chatEnabled = saved.chatEnabled,
                )
                _operationMessage.value = StaffRoleOperationMessage.Saved
                publish()
                onSuccess(saved)
            } catch (error: Exception) {
                _operationError.value = error.message ?: "Could not save the operations team."
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun delete(id: String, campingId: String, onSuccess: () -> Unit) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            _operationError.value = null
            try {
                service.deleteStaffRole(id, campingId)
                roles = roles.filterNot { it.id == id }
                _operationMessage.value = StaffRoleOperationMessage.Deleted
                publish()
                onSuccess()
            } catch (error: Exception) {
                _operationError.value = error.message ?: "Could not delete the operations team."
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearOperationError() { _operationError.value = null }
    fun clearOperationMessage() { _operationMessage.value = null }

    override fun onCleared() {
        observeJob?.cancel()
        super.onCleared()
    }

    private fun publish() {
        _uiState.value = if (roles.isEmpty()) StaffRolesUiState.Empty else StaffRolesUiState.Loaded(roles)
    }

    private fun normalizeColor(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return CampingStaffRole.DEFAULT_COLOR
        return if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    }

    private fun CampingAttendee.toStaffRoleMember(): StaffRoleMember = StaffRoleMember(
        id = userId,
        userId = userId,
        displayName = displayName,
        church = church,
        notificationUserId = guardianId?.trim()?.takeUnless { it.isBlank() } ?: userId,
        preferredLanguage = preferredLanguage,
        photoUrl = photoUrl,
    )

    private data class Scope(val campingId: String, val memberUserId: String?)
}
