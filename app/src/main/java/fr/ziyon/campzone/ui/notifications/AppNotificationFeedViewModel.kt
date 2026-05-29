package fr.ziyon.campzone.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.model.AppNotification
import fr.ziyon.campzone.data.notifications.AppNotificationFeedService
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed interface AppNotificationFeedUiState {
    data object Loading : AppNotificationFeedUiState
    data class Loaded(val notifications: List<AppNotification>) : AppNotificationFeedUiState
    data object Empty : AppNotificationFeedUiState
    data object Error : AppNotificationFeedUiState
}

@HiltViewModel
class AppNotificationFeedViewModel @Inject constructor(
    private val service: AppNotificationFeedService,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AppNotificationFeedUiState>(AppNotificationFeedUiState.Loading)
    val uiState: StateFlow<AppNotificationFeedUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null
    private var activeUid: String? = null

    fun load(uid: String, role: UserRole) {
        if (activeUid == uid && streamJob?.isActive == true) return
        activeUid = uid
        streamJob?.cancel()
        _uiState.value = AppNotificationFeedUiState.Loading
        streamJob = viewModelScope.launch {
            service.observeNotifications(uid, role)
                .catch { _uiState.value = AppNotificationFeedUiState.Error }
                .collect { notifications ->
                    _uiState.value = if (notifications.isEmpty()) {
                        AppNotificationFeedUiState.Empty
                    } else {
                        AppNotificationFeedUiState.Loaded(notifications)
                    }
                }
        }
    }

    fun retry(uid: String, role: UserRole) {
        activeUid = null
        load(uid, role)
    }

    override fun onCleared() {
        streamJob?.cancel()
        super.onCleared()
    }
}
