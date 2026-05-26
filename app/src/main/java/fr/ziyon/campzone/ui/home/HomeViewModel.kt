package fr.ziyon.campzone.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.Program
import fr.ziyon.campzone.data.schedule.ScheduleService
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
    data class Loaded(
        val featuredCamping: Camping?,
        val upcomingPrograms: List<Program> = emptyList(),
    ) : HomePhase

    data class Error(val message: String?) : HomePhase
}

data class HomeUiState(
    val phase: HomePhase = HomePhase.Loading,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val campingService: CampingService,
    private val scheduleService: ScheduleService,
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
                    val featuredCamping = campings.featuredCamping()
                    val upcomingPrograms = featuredCamping
                        ?.let { camping -> loadUpcomingPrograms(camping.id) }
                        .orEmpty()
                    _uiState.update {
                        it.copy(
                            phase = HomePhase.Loaded(
                                featuredCamping = featuredCamping,
                                upcomingPrograms = upcomingPrograms,
                            ),
                        )
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

    private suspend fun loadUpcomingPrograms(
        campingId: String,
        now: Date = Date(),
    ): List<Program> = runCatching {
        scheduleService.loadSchedule(campingId)
            .allPrograms
            .filter { program -> program.endDate >= now }
            .sortedBy { it.startDate }
            .take(3)
    }.getOrDefault(emptyList())
}
