package fr.ziyon.campzone.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.feedback.FeedbackService
import fr.ziyon.campzone.data.model.CampFeedback
import fr.ziyon.campzone.data.model.ProgramFeedback
import fr.ziyon.campzone.data.model.ProgramType
import fr.ziyon.campzone.data.schedule.ScheduleService
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Days the survey stays open after a camp ends; matches the iOS 60-day window. */
private const val FEEDBACK_WINDOW_DAYS = 60

internal fun feedbackWindowEnd(endDate: Date): Date =
    Calendar.getInstance().apply {
        time = endDate
        add(Calendar.DAY_OF_YEAR, FEEDBACK_WINDOW_DAYS)
    }.time

// region Survey

/**
 * Drives [CampFeedbackSurveyScreen]. Resolves the survey availability window
 * (opens on the camp end date, closes 60 days later), seeds the per-session
 * program rows from the schedule (meals excluded, mirroring iOS), holds the
 * editable form, and submits one [CampFeedback] per participant.
 */
sealed interface FeedbackSurveyUiState {
    data object Loading : FeedbackSurveyUiState

    data class Error(val message: String) : FeedbackSurveyUiState

    /** The camp has not ended yet; the survey opens on [opensOn]. */
    data class NotAvailable(val campTitle: String, val opensOn: Date) : FeedbackSurveyUiState

    /** The 60-day feedback window has closed. */
    data class Expired(val campTitle: String) : FeedbackSurveyUiState

    /** The participant already submitted; show the thank-you confirmation. */
    data class Submitted(val campTitle: String, val overallRating: Int) : FeedbackSurveyUiState

    data class Editing(
        val campTitle: String,
        val overallRating: Int = 0,
        val programFeedback: List<ProgramFeedback> = emptyList(),
        val highlights: String = "",
        val improvements: String = "",
        val wouldReturn: Boolean = true,
        val isAnonymous: Boolean = false,
        val isSaving: Boolean = false,
        val operationError: String? = null,
    ) : FeedbackSurveyUiState {
        val canSubmit: Boolean get() = overallRating in 1..5 && !isSaving
    }
}

@HiltViewModel
class FeedbackSurveyViewModel @Inject constructor(
    private val campingService: CampingService,
    private val scheduleService: ScheduleService,
    private val feedbackService: FeedbackService,
) : ViewModel() {

    private val _uiState = MutableStateFlow<FeedbackSurveyUiState>(FeedbackSurveyUiState.Loading)
    val uiState: StateFlow<FeedbackSurveyUiState> = _uiState.asStateFlow()

    private var campingId: String = ""
    private var user: AuthenticatedUser? = null
    private var loadedKey: Pair<String, String>? = null

    fun load(campingId: String, user: AuthenticatedUser) {
        val key = campingId to user.uid
        if (loadedKey == key && _uiState.value !is FeedbackSurveyUiState.Error) return
        loadedKey = key
        this.campingId = campingId
        this.user = user
        _uiState.value = FeedbackSurveyUiState.Loading

        viewModelScope.launch {
            runCatching {
                val camping = campingService.fetchCamping(campingId)
                val myFeedback = feedbackService.loadMyFeedback(user.uid, campingId)
                val programs = runCatching { scheduleService.loadSchedule(campingId) }
                    .getOrNull()
                    ?.sortedDays
                    ?.flatMap { it.programs }
                    ?.filter { it.type != ProgramType.Breakfast && it.type != ProgramType.Lunch && it.type != ProgramType.Dinner }
                    .orEmpty()
                Triple(camping.title, camping.endDate, Pair(myFeedback, programs))
            }.onSuccess { (title, endDate, payload) ->
                val (myFeedback, programs) = payload
                val now = Date()
                _uiState.value = when {
                    now.before(endDate) -> FeedbackSurveyUiState.NotAvailable(title, endDate)
                    now.after(feedbackWindowEnd(endDate)) -> FeedbackSurveyUiState.Expired(title)
                    myFeedback != null -> FeedbackSurveyUiState.Submitted(title, myFeedback.overallRating)
                    else -> FeedbackSurveyUiState.Editing(
                        campTitle = title,
                        programFeedback = programs.map {
                            ProgramFeedback(id = it.id, programTitle = it.title, rating = 0, comment = "")
                        },
                    )
                }
            }.onFailure { error ->
                loadedKey = null
                _uiState.value = FeedbackSurveyUiState.Error(error.message ?: DEFAULT_ERROR)
            }
        }
    }

    fun retry() {
        val current = user ?: return
        loadedKey = null
        load(campingId, current)
    }

    fun setOverallRating(rating: Int) = updateForm { it.copy(overallRating = rating) }

    fun setHighlights(value: String) = updateForm { it.copy(highlights = value) }

    fun setImprovements(value: String) = updateForm { it.copy(improvements = value) }

    fun setWouldReturn(value: Boolean) = updateForm { it.copy(wouldReturn = value) }

    fun setAnonymous(value: Boolean) = updateForm { it.copy(isAnonymous = value) }

    fun setProgramRating(programId: String, rating: Int) = updateForm { editing ->
        editing.copy(
            programFeedback = editing.programFeedback.map {
                if (it.id == programId) it.copy(rating = rating) else it
            },
        )
    }

    fun setProgramComment(programId: String, comment: String) = updateForm { editing ->
        editing.copy(
            programFeedback = editing.programFeedback.map {
                if (it.id == programId) it.copy(comment = comment) else it
            },
        )
    }

    fun clearOperationError() = updateForm { it.copy(operationError = null) }

    fun submit() {
        val editing = _uiState.value as? FeedbackSurveyUiState.Editing ?: return
        val account = user ?: return
        if (!editing.canSubmit) return

        val feedback = CampFeedback(
            id = account.uid,
            campingId = campingId,
            userId = account.uid,
            displayName = account.preferredDisplayName,
            overallRating = editing.overallRating,
            wouldReturn = editing.wouldReturn,
            isAnonymous = editing.isAnonymous,
            programFeedback = editing.programFeedback
                .filter { it.rating > 0 }
                .map { it.copy(comment = it.comment.trim()) },
            highlights = editing.highlights.trim(),
            improvements = editing.improvements.trim(),
        )

        _uiState.update { (it as? FeedbackSurveyUiState.Editing)?.copy(isSaving = true, operationError = null) ?: it }
        viewModelScope.launch {
            runCatching { feedbackService.submitFeedback(feedback) }
                .onSuccess {
                    _uiState.value = FeedbackSurveyUiState.Submitted(editing.campTitle, feedback.overallRating)
                }
                .onFailure { error ->
                    _uiState.update {
                        (it as? FeedbackSurveyUiState.Editing)?.copy(
                            isSaving = false,
                            operationError = error.message ?: DEFAULT_SUBMIT_ERROR,
                        ) ?: it
                    }
                }
        }
    }

    private fun updateForm(transform: (FeedbackSurveyUiState.Editing) -> FeedbackSurveyUiState.Editing) {
        _uiState.update { (it as? FeedbackSurveyUiState.Editing)?.let(transform) ?: it }
    }

    private companion object {
        const val DEFAULT_ERROR = "The survey could not be loaded."
        const val DEFAULT_SUBMIT_ERROR = "Your feedback could not be sent. Please try again."
    }
}

// endregion

// region Results

/** Per-program average used by the admin results view, sorted best-first. */
data class ProgramAverage(val title: String, val average: Double, val count: Int)

/**
 * Drives [CampFeedbackResultsScreen]. Gated `canManageAnyCamping` at load; once
 * authorized it aggregates every response into the average overall rating,
 * would-return %, per-program averages and the written-comment stream
 * (replicating the iOS `FeedbackObserver` aggregates).
 */
sealed interface FeedbackResultsUiState {
    data object Loading : FeedbackResultsUiState

    /** Caller lacks `canManageAnyCamping`. */
    data object Restricted : FeedbackResultsUiState

    data class Error(val message: String) : FeedbackResultsUiState

    data class Empty(val campTitle: String) : FeedbackResultsUiState

    data class Loaded(
        val campTitle: String,
        val responseCount: Int,
        val averageOverall: Double,
        val wouldReturnPercent: Int,
        val programAverages: List<ProgramAverage>,
        val comments: List<CampFeedback>,
    ) : FeedbackResultsUiState
}

@HiltViewModel
class FeedbackResultsViewModel @Inject constructor(
    private val campingService: CampingService,
    private val feedbackService: FeedbackService,
) : ViewModel() {

    private val permissions = AppPermissionEvaluator()

    private val _uiState = MutableStateFlow<FeedbackResultsUiState>(FeedbackResultsUiState.Loading)
    val uiState: StateFlow<FeedbackResultsUiState> = _uiState.asStateFlow()

    private var campingId: String = ""
    private var user: AuthenticatedUser? = null
    private var loadedKey: Pair<String, String>? = null

    fun load(campingId: String, user: AuthenticatedUser) {
        val key = campingId to user.uid
        if (loadedKey == key && _uiState.value !is FeedbackResultsUiState.Error) return
        loadedKey = key
        this.campingId = campingId
        this.user = user
        _uiState.value = FeedbackResultsUiState.Loading

        val permissionUser = PermissionUser(user.role, user.uid, user.church)
        if (!permissions.canManageAnyCamping(permissionUser)) {
            _uiState.value = FeedbackResultsUiState.Restricted
            return
        }

        viewModelScope.launch {
            runCatching {
                val title = campingService.fetchCamping(campingId).title
                val responses = feedbackService.loadAllFeedback(campingId)
                title to responses
            }.onSuccess { (title, responses) ->
                _uiState.value = if (responses.isEmpty()) {
                    FeedbackResultsUiState.Empty(title)
                } else {
                    FeedbackResultsUiState.Loaded(
                        campTitle = title,
                        responseCount = responses.size,
                        averageOverall = averageOverall(responses),
                        wouldReturnPercent = wouldReturnPercent(responses),
                        programAverages = programAverages(responses),
                        comments = responses.filter {
                            it.highlights.isNotBlank() || it.improvements.isNotBlank()
                        },
                    )
                }
            }.onFailure { error ->
                loadedKey = null
                _uiState.value = FeedbackResultsUiState.Error(error.message ?: DEFAULT_ERROR)
            }
        }
    }

    fun retry() {
        val current = user ?: return
        loadedKey = null
        load(campingId, current)
    }

    private fun averageOverall(responses: List<CampFeedback>): Double {
        val rated = responses.filter { it.overallRating > 0 }
        if (rated.isEmpty()) return 0.0
        return rated.sumOf { it.overallRating }.toDouble() / rated.size
    }

    private fun wouldReturnPercent(responses: List<CampFeedback>): Int {
        if (responses.isEmpty()) return 0
        val yes = responses.count { it.wouldReturn }
        return ((yes.toDouble() / responses.size) * 100).toInt()
    }

    private fun programAverages(responses: List<CampFeedback>): List<ProgramAverage> {
        data class Totals(val title: String, val sum: Int, val count: Int)
        val totals = LinkedHashMap<String, Totals>()
        for (response in responses) {
            for (pf in response.programFeedback) {
                if (pf.rating <= 0) continue
                val current = totals[pf.id] ?: Totals(pf.programTitle, 0, 0)
                totals[pf.id] = current.copy(sum = current.sum + pf.rating, count = current.count + 1)
            }
        }
        return totals.values
            .map { ProgramAverage(it.title, it.sum.toDouble() / it.count, it.count) }
            .sortedByDescending { it.average }
    }

    private companion object {
        const val DEFAULT_ERROR = "Feedback results could not be loaded."
    }
}

// endregion
