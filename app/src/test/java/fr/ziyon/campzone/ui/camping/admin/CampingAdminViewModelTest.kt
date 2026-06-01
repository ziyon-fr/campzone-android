package fr.ziyon.campzone.ui.camping.admin

import fr.ziyon.campzone.data.camping.FakeCampingService
import fr.ziyon.campzone.data.media.FakeImageUploader
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CampingAdminViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun deleteCampingInvokesCallbackAfterSuccessfulDelete() = runTest {
        val service = FakeCampingService(listOf(camping()))
        val viewModel = CampingAdminViewModel(service, FakeImageUploader())
        var deletedCallbackCalled = false

        viewModel.deleteCamping("camp-1") { deletedCallbackCalled = true }

        assertEquals(listOf("camp-1"), service.deleted)
        assertTrue(deletedCallbackCalled)
        assertFalse(viewModel.uiState.value.isDeleting)
    }

    @Test
    fun deleteCampingKeepsScreenOpenWhenDeleteFails() = runTest {
        val service = FakeCampingService(listOf(camping()), shouldFail = true)
        val viewModel = CampingAdminViewModel(service, FakeImageUploader())
        var deletedCallbackCalled = false

        viewModel.deleteCamping("camp-1") { deletedCallbackCalled = true }

        assertTrue(service.deleted.isEmpty())
        assertFalse(deletedCallbackCalled)
        assertFalse(viewModel.uiState.value.isDeleting)
        assertEquals("Could not delete camping.", viewModel.uiState.value.errorMessage)
    }

    private fun camping() = Camping(
        id = "camp-1",
        title = "Summer Camp",
        description = "A camp",
        startDate = Date(1_800_000_000_000),
        endDate = Date(1_800_086_400_000),
        organizerLevel = OrganizerLevel(OrganizerType.Church, "Paris Central SDA"),
        location = "Lake Annecy",
        registrationStatus = CampingRegistrationStatus.Open,
    )
}
