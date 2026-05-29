package fr.ziyon.campzone.ui.admin.moderation

import fr.ziyon.campzone.data.model.ContentReportStatus
import fr.ziyon.campzone.data.moderation.FakeContentReportService
import fr.ziyon.campzone.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModerationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadReportsPublishesPendingCount() = runTest {
        val vm = ModerationViewModel(FakeContentReportService())

        vm.load()
        advanceUntilIdle()

        val state = vm.uiState.value as ModerationUiState.Loaded
        assertEquals(3, state.reports.size)
        assertEquals(2, vm.pendingCount())
    }

    @Test
    fun resolveReportUpdatesLocalStateWithReviewer() = runTest {
        val vm = ModerationViewModel(FakeContentReportService())

        vm.load()
        advanceUntilIdle()
        vm.updateStatus("report-1", ContentReportStatus.Resolved, "admin-1")
        advanceUntilIdle()

        val state = vm.uiState.value as ModerationUiState.Loaded
        val report = state.reports.first { it.id == "report-1" }
        assertEquals(ContentReportStatus.Resolved, report.status)
        assertEquals("admin-1", report.reviewedById)
        assertTrue(report.reviewedAt != null)
    }

    @Test
    fun serviceFailurePublishesErrorState() = runTest {
        val vm = ModerationViewModel(FakeContentReportService(shouldFail = true))

        vm.load()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is ModerationUiState.Error)
    }
}
