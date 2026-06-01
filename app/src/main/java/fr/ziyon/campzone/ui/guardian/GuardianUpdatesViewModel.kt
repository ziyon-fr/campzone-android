package fr.ziyon.campzone.ui.guardian

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.family.FamilyRepository
import fr.ziyon.campzone.data.guardian.GuardianChildUpdate
import fr.ziyon.campzone.data.guardian.GuardianUpdatesData
import fr.ziyon.campzone.data.guardian.GuardianUpdatesService
import fr.ziyon.campzone.data.guardian.toCampAttendee
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.Program
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed interface GuardianUpdatesUiState {
    data object Loading : GuardianUpdatesUiState

    data class Error(val message: String) : GuardianUpdatesUiState

    /** The guardian has no children registered/active at this camp. */
    data object Empty : GuardianUpdatesUiState

    data class Loaded(
        val children: List<GuardianChildUpdate>,
        val currentPrograms: List<Program>,
        val upcomingProgram: Program?,
    ) : GuardianUpdatesUiState
}

/**
 * Backs the read-only guardian "Family at Camp" card + screen. Resolves the
 * guardian's candidate children (camp roster ∪ their family list), then streams
 * a composed [GuardianUpdatesData] (per-child check-in + registration, live team
 * scores, schedule) and derives per-child snapshots. Never mutates camp data.
 * Mirrors the iOS `GuardianUpdatesObserver`.
 */
@HiltViewModel
class GuardianUpdatesViewModel @Inject constructor(
    private val campingService: CampingService,
    private val familyRepository: FamilyRepository,
    private val guardianService: GuardianUpdatesService,
) : ViewModel() {

    private val _uiState = MutableStateFlow<GuardianUpdatesUiState>(GuardianUpdatesUiState.Loading)
    val uiState: StateFlow<GuardianUpdatesUiState> = _uiState.asStateFlow()

    private var campingId: String = ""
    private var user: AuthenticatedUser? = null
    private var observeJob: Job? = null
    private var loadedKey: Pair<String, String>? = null

    fun load(campingId: String, user: AuthenticatedUser) {
        val key = campingId to user.uid
        if (loadedKey == key && _uiState.value !is GuardianUpdatesUiState.Error) return
        loadedKey = key
        this.campingId = campingId
        this.user = user
        _uiState.value = GuardianUpdatesUiState.Loading

        viewModelScope.launch {
            runCatching {
                val camping = campingService.fetchCamping(campingId)
                val familyChildren = runCatching { familyRepository.loadChildren(user.uid) }
                    .getOrDefault(emptyList())
                    .map { it.toCampAttendee() }
                camping to familyChildren
            }.onSuccess { (camping, familyChildren) ->
                val rosterChildIds = camping.attendees
                    .filter { it.participantKind == RegistrationParticipantKind.Child && it.guardianId == user.uid }
                    .map { it.id }
                val candidateIds = (rosterChildIds + familyChildren.map { it.id }).distinct()
                if (candidateIds.isEmpty()) {
                    _uiState.value = GuardianUpdatesUiState.Empty
                    return@onSuccess
                }
                observe(camping, user.uid, familyChildren, candidateIds)
            }.onFailure { error ->
                loadedKey = null
                _uiState.value = GuardianUpdatesUiState.Error(error.message ?: DEFAULT_ERROR)
            }
        }
    }

    fun retry() {
        val current = user ?: return
        loadedKey = null
        load(campingId, current)
    }

    private fun observe(
        camping: Camping,
        guardianId: String,
        familyChildren: List<CampingAttendee>,
        candidateIds: List<String>,
    ) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            guardianService.observeUpdates(campingId, candidateIds)
                .catch { error ->
                    _uiState.value = GuardianUpdatesUiState.Error(error.message ?: DEFAULT_ERROR)
                }
                .collect { data ->
                    _uiState.value = reduce(camping, guardianId, familyChildren, data)
                }
        }
    }

    private fun reduce(
        camping: Camping,
        guardianId: String,
        familyChildren: List<CampingAttendee>,
        data: GuardianUpdatesData,
    ): GuardianUpdatesUiState {
        val now = Date()
        // Only family children with real camp activity become fallbacks, so a
        // child who isn't at this camp never appears.
        val fallback = familyChildren.filter {
            data.checkIn(it.id) != null || data.team(it.userId) != null
        }
        val children = GuardianChildUpdate.snapshots(
            camping = camping,
            guardianId = guardianId,
            data = data,
            fallbackChildren = fallback,
            now = now,
        )
        return if (children.isEmpty()) {
            GuardianUpdatesUiState.Empty
        } else {
            GuardianUpdatesUiState.Loaded(
                children = children,
                currentPrograms = data.currentPrograms(now),
                upcomingProgram = data.upcomingProgram(now),
            )
        }
    }

    private companion object {
        const val DEFAULT_ERROR = "Couldn't load your family's camp updates."
    }
}
