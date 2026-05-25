package fr.ziyon.campzone.ui.camping

import fr.ziyon.campzone.data.camping.FakeCampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CampingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadedGroupsCampingsByMonth() = runTest {
        val service = FakeCampingService(
            listOf(
                camping("a", "Summer Camp", 2026, Calendar.JULY),
                camping("b", "Fall Retreat", 2026, Calendar.OCTOBER),
            ),
        )
        val viewModel = CampingsViewModel(service)

        val phase = viewModel.uiState.value.phase
        assertTrue(phase is CampingsPhase.Loaded)
        assertEquals(2, (phase as CampingsPhase.Loaded).sections.size)
    }

    @Test
    fun emptyWhenNoCampings() = runTest {
        val viewModel = CampingsViewModel(FakeCampingService(emptyList()))
        val phase = viewModel.uiState.value.phase
        assertTrue(phase is CampingsPhase.Empty)
        assertEquals(false, (phase as CampingsPhase.Empty).isSearchResult)
    }

    @Test
    fun searchFiltersByTitle() = runTest {
        val service = FakeCampingService(
            listOf(
                camping("a", "Summer Camp", 2026, Calendar.JULY),
                camping("b", "Fall Retreat", 2026, Calendar.OCTOBER),
            ),
        )
        val viewModel = CampingsViewModel(service)
        viewModel.updateSearch("summer")

        val phase = viewModel.uiState.value.phase as CampingsPhase.Loaded
        assertEquals(1, phase.sections.sumOf { it.campings.size })
        assertEquals("a", phase.sections.first().campings.first().id)
    }

    @Test
    fun searchWithNoMatchesShowsSearchEmpty() = runTest {
        val service = FakeCampingService(listOf(camping("a", "Summer Camp", 2026, Calendar.JULY)))
        val viewModel = CampingsViewModel(service)
        viewModel.updateSearch("zzz")

        val phase = viewModel.uiState.value.phase
        assertTrue(phase is CampingsPhase.Empty)
        assertTrue((phase as CampingsPhase.Empty).isSearchResult)
        assertEquals("zzz", phase.query)
    }

    @Test
    fun streamFailureShowsError() = runTest {
        val viewModel = CampingsViewModel(FakeCampingService(shouldFail = true))
        assertTrue(viewModel.uiState.value.phase is CampingsPhase.Error)
    }

    private fun camping(id: String, title: String, year: Int, month: Int): Camping {
        val cal = Calendar.getInstance().apply { clear(); set(year, month, 18) }
        val start = cal.time
        cal.add(Calendar.DAY_OF_MONTH, 6)
        return Camping(
            id = id,
            title = title,
            description = "Description for $title",
            startDate = start,
            endDate = cal.time,
            organizerLevel = OrganizerLevel(OrganizerType.Regional, "South"),
            location = "Lake Annecy",
            registrationStatus = CampingRegistrationStatus.Open,
        )
    }
}
