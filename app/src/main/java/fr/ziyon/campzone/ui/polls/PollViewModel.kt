package fr.ziyon.campzone.ui.polls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.Poll
import fr.ziyon.campzone.data.model.PollForm
import fr.ziyon.campzone.data.model.PollFormError
import fr.ziyon.campzone.data.model.PollOption
import fr.ziyon.campzone.data.model.PollVote
import fr.ziyon.campzone.data.polls.PollDispatchEvent
import fr.ziyon.campzone.data.polls.PollNotificationDispatcher
import fr.ziyon.campzone.data.polls.PollNotificationRequest
import fr.ziyon.campzone.data.polls.PollService
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PollListUiState {
    data object Loading : PollListUiState
    data object Empty : PollListUiState
    data class Loaded(val openPolls: List<Poll>, val closedPolls: List<Poll>) : PollListUiState
    data class Error(val message: String) : PollListUiState
}

@HiltViewModel
class PollViewModel @Inject constructor(
    private val service: PollService,
    private val dispatcher: PollNotificationDispatcher,
) : ViewModel() {

    // List
    private val _listState = MutableStateFlow<PollListUiState>(PollListUiState.Loading)
    val listState: StateFlow<PollListUiState> = _listState.asStateFlow()
    private var polls: List<Poll> = emptyList()
    private var loadedCamping: String? = null

    // Detail
    private val _activePoll = MutableStateFlow<Poll?>(null)
    val activePoll: StateFlow<Poll?> = _activePoll.asStateFlow()
    private val _activeVote = MutableStateFlow<PollVote?>(null)
    val activeVote: StateFlow<PollVote?> = _activeVote.asStateFlow()
    private val _selectedOptionIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedOptionIds: StateFlow<Set<String>> = _selectedOptionIds.asStateFlow()
    private var observeJob: Job? = null

    // Editor
    private val _form = MutableStateFlow(PollForm())
    val form: StateFlow<PollForm> = _form.asStateFlow()
    private val _formError = MutableStateFlow<PollFormError?>(null)
    val formError: StateFlow<PollFormError?> = _formError.asStateFlow()
    private var editingPoll: Poll? = null

    // Shared
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    // MARK: - List

    fun loadPolls(campingId: String) {
        if (loadedCamping == campingId && _listState.value !is PollListUiState.Error) return
        loadedCamping = campingId
        _listState.value = PollListUiState.Loading
        viewModelScope.launch {
            try {
                polls = service.loadPolls(campingId)
                publishList()
            } catch (e: Exception) {
                _listState.value = PollListUiState.Error(e.message ?: "Failed to load polls.")
            }
        }
    }

    fun retry(campingId: String) {
        loadedCamping = null
        loadPolls(campingId)
    }

    // MARK: - Detail

    fun startObservingPoll(pollId: String, campingId: String, voterId: String) {
        observeJob?.cancel()
        // Load the existing vote + observe in one job so cancellation covers both
        // and a stale vote load can't overwrite newer state.
        observeJob = viewModelScope.launch {
            val vote = runCatching { service.loadVote(pollId, campingId, voterId) }.getOrNull()
            _activeVote.value = vote
            _selectedOptionIds.value = vote?.selectedOptionIds?.toSet().orEmpty()
            service.observePoll(pollId, campingId).collect { _activePoll.value = it }
        }
    }

    fun stopObservingPoll() {
        observeJob?.cancel()
        _activePoll.value = null
        _activeVote.value = null
        _selectedOptionIds.value = emptySet()
    }

    fun toggleSelection(optionId: String, allowsMultiple: Boolean) {
        _selectedOptionIds.update { current ->
            if (allowsMultiple) {
                if (optionId in current) current - optionId else current + optionId
            } else {
                setOf(optionId)
            }
        }
    }

    /** Clears the local vote so the user can re-vote while the poll is live. */
    fun changeVote() {
        _selectedOptionIds.value = emptySet()
        _activeVote.value = null
    }

    fun submitVote(poll: Poll, voterId: String) {
        val selected = _selectedOptionIds.value
        if (selected.isEmpty()) {
            _operationError.value = "Pick at least one option before voting."
            return
        }
        if (!poll.resolvedIsOpen) {
            _operationError.value = "This poll is closed."
            return
        }
        if (_isSaving.value) return
        val previousVote = _activeVote.value
        viewModelScope.launch {
            _isSaving.value = true
            _operationError.value = null
            try {
                service.castVote(poll.campingId, poll.id, voterId, selected.toList())
                _activeVote.value = PollVote(voterId, selected.toList(), Date())
                // Mirror the transaction's count change locally so the list + detail stay in sync.
                val updatedOptions = poll.options.map { option ->
                    var count = option.voteCount
                    if (previousVote?.selectedOptionIds?.contains(option.id) == true) count = maxOf(0, count - 1)
                    if (selected.contains(option.id)) count += 1
                    option.copy(voteCount = count)
                }
                val updatedPoll = poll.copy(options = updatedOptions)
                _activePoll.value = updatedPoll
                upsertLocal(updatedPoll)
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Could not submit your vote."
            } finally {
                _isSaving.value = false
            }
        }
    }

    // MARK: - Editor

    fun startEditor(pollId: String?, campingId: String) {
        _formError.value = null
        if (pollId == null) {
            editingPoll = null
            _form.value = PollForm()
            return
        }
        viewModelScope.launch {
            val poll = runCatching { service.loadPoll(pollId, campingId) }.getOrNull()
            editingPoll = poll
            _form.value = poll?.let { PollForm.from(it) } ?: PollForm()
        }
    }

    fun updateForm(transform: (PollForm) -> PollForm) {
        _form.update(transform)
    }

    fun savePoll(campingId: String, author: AuthenticatedUser, onSuccess: () -> Unit) {
        val form = _form.value
        val error = form.validationError
        if (error != null) {
            _formError.value = error
            return
        }
        if (_isSaving.value) return
        _formError.value = null
        viewModelScope.launch {
            _isSaving.value = true
            _operationError.value = null
            try {
                val editing = editingPoll
                val isCreate = editing == null
                val id = editing?.id ?: UUID.randomUUID().toString()
                val options = buildOptions(form.validOptionLabels, editing?.options.orEmpty())
                val poll = Poll(
                    id = id,
                    campingId = campingId,
                    question = form.question.trim(),
                    description = form.description.trim(),
                    options = options,
                    allowsMultiple = form.allowsMultiple,
                    showsResultsBeforeClose = form.showsResultsBeforeClose,
                    isOpen = form.isOpen,
                    createdById = editing?.createdById ?: author.uid,
                    createdByName = editing?.createdByName ?: author.preferredDisplayName,
                    createdAt = editing?.createdAt ?: Date(),
                    closesAt = if (form.hasCloseDate) form.closesAt else null,
                )
                service.savePoll(poll, includeCreatedAt = isCreate)
                upsertLocal(poll)
                // Push only on creation; label tweaks shouldn't spam participants.
                if (isCreate) dispatchBestEffort(poll, PollDispatchEvent.Created)
                onSuccess()
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Could not save the poll."
            } finally {
                _isSaving.value = false
            }
        }
    }

    /**
     * Preserves vote counts on edit: match by label, else positionally (rename),
     * else by the next unused option, else fresh. Tracks consumed options so an
     * option can never be reused — which would otherwise emit duplicate IDs.
     */
    private fun buildOptions(labels: List<String>, existing: List<PollOption>): List<PollOption> {
        val unused = existing.toMutableList()
        return labels.mapIndexed { index, label ->
            val byLabel = unused.firstOrNull { it.label == label }
            if (byLabel != null) {
                unused.remove(byLabel)
                byLabel
            } else {
                val positional = existing.getOrNull(index)?.takeIf { it in unused }
                val reuse = positional ?: unused.firstOrNull()
                if (reuse != null) {
                    unused.remove(reuse)
                    reuse.copy(label = label)
                } else {
                    PollOption(id = UUID.randomUUID().toString(), label = label)
                }
            }
        }
    }

    // MARK: - Open / close / delete

    fun setOpen(poll: Poll, isOpen: Boolean) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            _operationError.value = null
            try {
                service.setOpen(poll.id, poll.campingId, isOpen)
                val updated = poll.copy(isOpen = isOpen)
                upsertLocal(updated)
                if (_activePoll.value?.id == poll.id) _activePoll.value = updated
                dispatchBestEffort(updated, if (isOpen) PollDispatchEvent.Reopened else PollDispatchEvent.Closed)
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Could not update the poll."
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deletePoll(poll: Poll, onSuccess: () -> Unit) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            _operationError.value = null
            try {
                service.deletePoll(poll.id, poll.campingId)
                polls = polls.filterNot { it.id == poll.id }
                publishList()
                if (_activePoll.value?.id == poll.id) _activePoll.value = null
                onSuccess()
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Could not delete the poll."
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearOperationError() { _operationError.value = null }

    override fun onCleared() {
        observeJob?.cancel()
        super.onCleared()
    }

    // MARK: - Internals

    private fun dispatchBestEffort(poll: Poll, event: PollDispatchEvent) {
        viewModelScope.launch {
            runCatching {
                dispatcher.dispatchPoll(
                    PollNotificationRequest(
                        campingId = poll.campingId,
                        pollId = poll.id,
                        question = poll.question,
                        event = event,
                    ),
                )
            }
        }
    }

    private fun upsertLocal(poll: Poll) {
        polls = if (polls.any { it.id == poll.id }) {
            polls.map { if (it.id == poll.id) poll else it }
        } else {
            listOf(poll) + polls
        }
        publishList()
    }

    private fun publishList() {
        _listState.value = if (polls.isEmpty()) {
            PollListUiState.Empty
        } else {
            PollListUiState.Loaded(
                openPolls = polls.filter { it.resolvedIsOpen }.sortedByDescending { it.createdAt },
                closedPolls = polls.filterNot { it.resolvedIsOpen }.sortedByDescending { it.createdAt },
            )
        }
    }
}
