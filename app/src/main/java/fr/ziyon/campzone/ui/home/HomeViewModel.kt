package fr.ziyon.campzone.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface HomePhase {
    data object Loading : HomePhase
    data class Loaded(val featuredCamping: Camping?) : HomePhase
    data class Error(val message: String?) : HomePhase
}

data class HomeUiState(
    val phase: HomePhase = HomePhase.Loading,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val campingService: CampingService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        observeDashboard()
    }

    fun retry() {
        _uiState.update { it.copy(phase = HomePhase.Loading) }
        observeDashboard()
    }

    private fun observeDashboard() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            campingService.observeCampings()
                .catch { error ->
                    _uiState.update { it.copy(phase = HomePhase.Error(error.message)) }
                }
                .collect { campings ->
                    _uiState.update {
                        it.copy(phase = HomePhase.Loaded(featuredCamping = campings.featuredCamping()))
                    }
                }
        }
    }

    private fun List<Camping>.featuredCamping(now: Date = Date()): Camping? =
        filter { camping ->
            camping.endDate >= now && camping.registrationStatus != CampingRegistrationStatus.Cancelled
        }
            .sortedBy { it.startDate }
            .firstOrNull()
}
