package fr.ziyon.campzone.ui.camping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.data.analytics.AnalyticsService
import fr.ziyon.campzone.data.analytics.NoOpAnalyticsService
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingPublicationStatus
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.OrganizerLevel
import java.util.Calendar
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

/** Leadership grouping that mirrors iOS `CampingPublicationSection`. */
data class CampingPublicationSection(
    val status: CampingPublicationStatus,
    val campings: List<Camping>,
) {
    val id: String get() = status.wireValue
}

/** History hierarchy used by the shipped iOS sheet: organizer, then descending year. */
data class CampingHistoryOrganizerGroup(
    val organizerLevel: OrganizerLevel,
    val yearGroups: List<CampingHistoryYearGroup>,
) {
    val id: String get() = "${organizerLevel.type.wireValue}:${organizerLevel.value}"
    val count: Int get() = yearGroups.sumOf { it.campings.size }
}

data class CampingHistoryYearGroup(
    val year: Int,
    val campings: List<Camping>,
)

/** Sealed list phase - Loading / Loaded / Empty / Error (`08` architecture rule). */
sealed interface CampingsPhase {
    data object Loading : CampingsPhase
    data class Loaded(
        val sections: List<CampingMonthSection>,
        val publicationSections: List<CampingPublicationSection> = emptyList(),
    ) : CampingsPhase
    data class Empty(val isSearchResult: Boolean, val query: String) : CampingsPhase
    data class Error(val message: String?) : CampingsPhase
}

data class CampingsUiState(
    val phase: CampingsPhase = CampingsPhase.Loading,
    val searchText: String = "",
    val historyGroups: List<CampingHistoryOrganizerGroup> = emptyList(),
)

@HiltViewModel
class CampingsViewModel @Inject constructor(
    private val service: CampingService,
    private val analyticsService: AnalyticsService = NoOpAnalyticsService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CampingsUiState())
    val uiState: StateFlow<CampingsUiState> = _uiState.asStateFlow()

    private var allCampings: List<Camping> = emptyList()
    private var hasLoadedCampings = false
    private var canViewUnpublished: (Camping) -> Boolean = { false }
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
        hasLoadedCampings = false
        _uiState.update { it.copy(phase = CampingsPhase.Loading) }
        observeCampings()
    }

    fun configureUnpublishedVisibility(predicate: (Camping) -> Boolean) {
        canViewUnpublished = predicate
        recompute()
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
                    hasLoadedCampings = true
                    recompute()
                }
        }
    }

    private fun recompute() {
        if (!hasLoadedCampings) return
        val query = _uiState.value.searchText.trim()
        val now = java.util.Date()
        val activeCampings = allCampings.filter {
            it.registrationStatus != CampingRegistrationStatus.Cancelled &&
                !it.endDate.before(now) &&
                (it.isPublished || canViewUnpublished(it))
        }
        val historyGroups = groupedHistory(
            allCampings
                .filter {
                    val isPast = it.endDate.before(now) || it.registrationStatus == CampingRegistrationStatus.Cancelled
                    isPast && (!it.isDraft || canViewUnpublished(it))
                }
                .sortedByDescending { it.endDate },
        )
        val filtered = if (query.isBlank()) {
            activeCampings
        } else {
            activeCampings.filter { camping ->
                camping.title.contains(query, ignoreCase = true) ||
                    camping.location.contains(query, ignoreCase = true) ||
                    camping.description.contains(query, ignoreCase = true)
            }
        }
        val sections = groupedSections(filtered)
        val publicationSections = groupedPublicationSections(filtered)
        val phase = when {
            sections.isNotEmpty() -> CampingsPhase.Loaded(
                sections = sections,
                publicationSections = publicationSections,
            )
            else -> CampingsPhase.Empty(isSearchResult = query.isNotBlank(), query = query)
        }
        _uiState.update { it.copy(phase = phase, historyGroups = historyGroups) }
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

        fun groupedPublicationSections(campings: List<Camping>): List<CampingPublicationSection> {
            val order = listOf(
                CampingPublicationStatus.Draft,
                CampingPublicationStatus.Published,
                CampingPublicationStatus.Archived,
            )
            return order.mapNotNull { status ->
                val group = campings
                    .filter { it.publicationStatus == status }
                    .sortedBy { it.startDate }
                group.takeIf { it.isNotEmpty() }?.let {
                    CampingPublicationSection(status = status, campings = it)
                }
            }
        }

        fun groupedHistory(campings: List<Camping>): List<CampingHistoryOrganizerGroup> =
            campings
                .groupBy { it.organizerLevel.type.wireValue to it.organizerLevel.value }
                .map { (_, organizerCampings) ->
                    CampingHistoryOrganizerGroup(
                        organizerLevel = organizerCampings.first().organizerLevel,
                        yearGroups = organizerCampings
                            .groupBy { camping ->
                                Calendar.getInstance().apply { time = camping.endDate }.get(Calendar.YEAR)
                            }
                            .map { (year, yearCampings) ->
                                CampingHistoryYearGroup(
                                    year = year,
                                    campings = yearCampings.sortedByDescending { it.endDate },
                                )
                            }
                            .sortedByDescending { it.year },
                    )
                }
                .sortedBy { it.organizerLevel.value.lowercase(Locale.getDefault()) }
    }
}
