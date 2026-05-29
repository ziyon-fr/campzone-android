package fr.ziyon.campzone.ui.camping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.data.analytics.AnalyticsService
import fr.ziyon.campzone.data.analytics.NoOpAnalyticsService
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Camping
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A month grouping of campings (mirrors iOS `CampingMonthSection`). */
data class CampingMonthSection(
    val id: String,
    val title: String,
    val campings: List<Camping>,
)

/** Sealed list phase - Loading / Loaded / Empty / Error (`08` architecture rule). */
sealed interface CampingsPhase {
    data object Loading : CampingsPhase
    data class Loaded(val sections: List<CampingMonthSection>) : CampingsPhase
    data class Empty(val isSearchResult: Boolean, val query: String) : CampingsPhase
    data class Error(val message: String?) : CampingsPhase
}

data class CampingsUiState(
    val phase: CampingsPhase = CampingsPhase.Loading,
    val searchText: String = "",
)

@HiltViewModel
class CampingsViewModel @Inject constructor(
    private val service: CampingService,
    private val analyticsService: AnalyticsService = NoOpAnalyticsService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CampingsUiState())
    val uiState: StateFlow<CampingsUiState> = _uiState.asStateFlow()

    private var allCampings: List<Camping> = emptyList()
    private var observeJob: Job? = null

    init {
        observeCampings()
    }

    fun updateSearch(text: String) {
        _uiState.update { it.copy(searchText = text) }
        analyticsService.searchCampings(text)
        recompute()
    }

    fun retry() {
        _uiState.update { it.copy(phase = CampingsPhase.Loading) }
        observeCampings()
    }

    private fun observeCampings() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            service.observeCampings()
                .catch { error ->
                    _uiState.update { it.copy(phase = CampingsPhase.Error(error.message)) }
                }
                .collect { campings ->
                    allCampings = campings
                    recompute()
                }
        }
    }

    private fun recompute() {
        val query = _uiState.value.searchText.trim()
        val filtered = if (query.isBlank()) {
            allCampings
        } else {
            allCampings.filter { camping ->
                camping.title.contains(query, ignoreCase = true) ||
                    camping.location.contains(query, ignoreCase = true) ||
                    camping.description.contains(query, ignoreCase = true)
            }
        }
        val sections = groupedSections(filtered)
        val phase = when {
            sections.isNotEmpty() -> CampingsPhase.Loaded(sections)
            else -> CampingsPhase.Empty(isSearchResult = query.isNotBlank(), query = query)
        }
        _uiState.update { it.copy(phase = phase) }
    }

    companion object {
        fun groupedSections(campings: List<Camping>): List<CampingMonthSection> {
            val keyFormat = SimpleDateFormat("yyyy-MM", Locale.US)
            val titleFormat = SimpleDateFormat("LLLL yyyy", Locale.getDefault())
            return campings
                .sortedBy { it.startDate }
                .groupBy { keyFormat.format(it.startDate) }
                .map { (key, group) ->
                    CampingMonthSection(
                        id = key,
                        title = titleFormat.format(group.first().startDate)
                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                        campings = group.sortedBy { it.startDate },
                    )
                }
                .sortedBy { it.campings.first().startDate }
        }
    }
}
