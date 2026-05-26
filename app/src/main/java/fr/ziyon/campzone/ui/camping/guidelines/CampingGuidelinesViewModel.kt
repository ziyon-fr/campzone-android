package fr.ziyon.campzone.ui.camping.guidelines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Camping
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CampingGuidelinesUiState(
    val isLoading: Boolean = true,
    val camping: Camping? = null,
    val canEditGuidelines: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class CampingGuidelinesViewModel @Inject constructor(
    private val campingService: CampingService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CampingGuidelinesUiState())
    val uiState: StateFlow<CampingGuidelinesUiState> = _uiState.asStateFlow()

    private val evaluator = AppPermissionEvaluator()

    fun load(campingId: String, authenticatedUser: AuthenticatedUser) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { campingService.fetchCamping(campingId) }
                .onSuccess { camping ->
                    val user = PermissionUser(
                        role = authenticatedUser.role,
                        userId = authenticatedUser.uid,
                        church = authenticatedUser.church,
                    )
                    val campingCtx = CampingPermissionContext(
                        organizerLevelType = camping.organizerLevel.type.wireValue,
                        organizerLevelValue = camping.organizerLevel.value,
                        createdByUid = camping.createdByUid,
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            camping = camping,
                            canEditGuidelines = evaluator.canEditGuidelines(user, campingCtx),
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }

    fun saveGuidelines(campingId: String, body: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching { campingService.updateGuidelines(campingId, body) }
                .onSuccess { updated ->
                    _uiState.update { it.copy(isSaving = false, camping = updated) }
                    onSuccess()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = error.message) }
                }
        }
    }

    fun deleteGuidelines(campingId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching { campingService.updateGuidelines(campingId, "") }
                .onSuccess { updated ->
                    _uiState.update { it.copy(isSaving = false, camping = updated) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = error.message) }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
