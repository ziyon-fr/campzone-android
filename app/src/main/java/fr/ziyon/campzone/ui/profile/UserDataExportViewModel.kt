package fr.ziyon.campzone.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.profile.UserDataExportRepository
import fr.ziyon.campzone.data.profile.UserDataExportResult
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UserDataExportUiState(
    val isExporting: Boolean = false,
    val exportResult: UserDataExportResult? = null,
    val exportError: String? = null,
)

@HiltViewModel
class UserDataExportViewModel @Inject constructor(
    private val repository: UserDataExportRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UserDataExportUiState())
    val uiState: StateFlow<UserDataExportUiState> = _uiState.asStateFlow()

    fun exportData(user: AuthenticatedUser) {
        if (_uiState.value.isExporting) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isExporting = true,
                exportError = null,
            )
            runCatching { repository.exportData(user) }
                .onSuccess { result ->
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        exportResult = result,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        exportError = error.message
                            ?.takeUnless { it.isBlank() }
                            ?: "Your data export could not be created. Please try again.",
                    )
                }
        }
    }
}
