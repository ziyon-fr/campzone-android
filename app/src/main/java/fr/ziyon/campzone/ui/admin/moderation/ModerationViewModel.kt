package fr.ziyon.campzone.ui.admin.moderation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.data.model.ContentReport
import fr.ziyon.campzone.data.model.ContentReportStatus
import fr.ziyon.campzone.data.moderation.ContentReportService
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ModerationUiState {
    data object Loading : ModerationUiState
    data class Loaded(val reports: List<ContentReport>) : ModerationUiState
    data class Error(val message: String) : ModerationUiState
}

@HiltViewModel
class ModerationViewModel @Inject constructor(
    private val service: ContentReportService,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ModerationUiState>(ModerationUiState.Loading)
    val uiState: StateFlow<ModerationUiState> = _uiState.asStateFlow()

    private var hasLoaded = false

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    fun loadIfNeeded() {
        if (hasLoaded) return
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = ModerationUiState.Loading
            _operationMessage.value = null
            runCatching { service.loadReports() }
                .onSuccess { reports ->
                    hasLoaded = true
                    _uiState.value = ModerationUiState.Loaded(reports)
                }
                .onFailure { error ->
                    _uiState.value = ModerationUiState.Error(
                        error.message ?: "Could not load content reports.",
                    )
                }
        }
    }

    fun updateStatus(
        reportId: String,
        status: ContentReportStatus,
        reviewerId: String,
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            _operationMessage.value = null
            runCatching {
                service.updateStatus(reportId, status, reviewerId)
            }.onSuccess {
                mutateReport(reportId) { report ->
                    report.copy(
                        status = status,
                        reviewedById = reviewerId,
                        reviewedAt = java.util.Date(),
                    )
                }
                _operationMessage.value = when (status) {
                    ContentReportStatus.Dismissed -> "Report dismissed."
                    ContentReportStatus.Resolved -> "Report resolved."
                    ContentReportStatus.Pending -> null
                }
            }.onFailure { error ->
                _operationMessage.value = error.message ?: "Could not update the report."
            }
            _isSaving.value = false
        }
    }

    fun pendingCount(): Int =
        (_uiState.value as? ModerationUiState.Loaded)
            ?.reports
            ?.count { it.status == ContentReportStatus.Pending }
            ?: 0

    private fun mutateReport(
        reportId: String,
        update: (ContentReport) -> ContentReport,
    ) {
        val loaded = _uiState.value as? ModerationUiState.Loaded ?: return
        _uiState.value = loaded.copy(
            reports = loaded.reports.map { report ->
                if (report.id == reportId) update(report) else report
            },
        )
    }
}
