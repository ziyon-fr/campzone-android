package fr.ziyon.campzone.ui.admin.role

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.admin.ManagedUser
import fr.ziyon.campzone.data.admin.RoleAssignmentService
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A church section in the role-management list (mirrors iOS `groupedByChurch`). */
data class ChurchGroup(
    val church: String,
    val users: List<ManagedUser>,
)

sealed interface RoleManagementUiState {
    data object Loading : RoleManagementUiState
    data class Error(val message: String?) : RoleManagementUiState

    /**
     * @param groups users filtered by the current search and grouped by church.
     * @param hasUsers whether the directory had any users before filtering
     * (lets the screen tell "no users" apart from "no search matches").
     */
    data class Loaded(
        val groups: List<ChurchGroup>,
        val hasUsers: Boolean,
    ) : RoleManagementUiState
}

/**
 * Backs the admin/leadership role-assignment screen. Owns no permission logic of
 * its own — the screen passes the resolved `churchFilter` (null for admins, the
 * acting church for scoped leadership) and the `writeIdField` flag. Read-only
 * until a role is changed; the only write is [updateRole].
 */
@HiltViewModel
class RoleManagementViewModel @Inject constructor(
    private val service: RoleAssignmentService,
) : ViewModel() {

    private val _uiState = MutableStateFlow<RoleManagementUiState>(RoleManagementUiState.Loading)
    val uiState: StateFlow<RoleManagementUiState> = _uiState.asStateFlow()

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    private var allUsers: List<ManagedUser> = emptyList()
    private var hasLoaded = false
    private var churchFilter: String? = null

    fun loadIfNeeded(churchFilter: String?) {
        this.churchFilter = churchFilter
        if (hasLoaded) return
        load(churchFilter)
    }

    fun load(churchFilter: String? = this.churchFilter) {
        this.churchFilter = churchFilter
        viewModelScope.launch {
            _uiState.value = RoleManagementUiState.Loading
            _operationMessage.value = null
            _operationError.value = null
            runCatching { service.loadUsers(churchFilter) }
                .onSuccess { users ->
                    hasLoaded = true
                    allUsers = users
                    recomputeLoaded()
                }
                .onFailure { error ->
                    _uiState.value = RoleManagementUiState.Error(error.message)
                }
        }
    }

    fun onSearchChange(text: String) {
        _searchText.value = text
        if (hasLoaded) recomputeLoaded()
    }

    fun updateRole(user: ManagedUser, newRole: UserRole, writeIdField: Boolean) {
        if (user.role == newRole) return
        viewModelScope.launch {
            _isSaving.value = true
            _operationMessage.value = null
            _operationError.value = null
            runCatching { service.updateRole(user.id, newRole, writeIdField) }
                .onSuccess {
                    allUsers = allUsers.map { existing ->
                        if (existing.id == user.id) {
                            existing.copy(role = newRole, updatedAt = Date())
                        } else {
                            existing
                        }
                    }
                    recomputeLoaded()
                    _operationMessage.value = user.displayName
                }
                .onFailure { error ->
                    _operationError.value = error.message
                }
            _isSaving.value = false
        }
    }

    private fun recomputeLoaded() {
        _uiState.value = RoleManagementUiState.Loaded(
            groups = groupByChurch(filterUsers(allUsers, _searchText.value)),
            hasUsers = allUsers.isNotEmpty(),
        )
    }

    private fun filterUsers(users: List<ManagedUser>, query: String): List<ManagedUser> {
        val trimmed = query.trim().lowercase()
        val matched = if (trimmed.isEmpty()) {
            users
        } else {
            users.filter { user ->
                user.displayName.lowercase().contains(trimmed) ||
                    user.email.lowercase().contains(trimmed) ||
                    user.church.lowercase().contains(trimmed)
            }
        }
        return matched.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
    }

    private fun groupByChurch(users: List<ManagedUser>): List<ChurchGroup> =
        users
            .groupBy { it.church.trim() }
            .map { (church, members) -> ChurchGroup(church = church, users = members) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.church })
}
