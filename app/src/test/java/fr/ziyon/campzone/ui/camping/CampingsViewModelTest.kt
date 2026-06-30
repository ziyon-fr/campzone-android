package fr.ziyon.campzone.ui.camping

import fr.ziyon.campzone.data.camping.FakeCampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingPublicationStatus
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Calendar
import java.util.TimeZone
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

    @Test
    fun historyGroupsByOrganizerThenDescendingYear() {
        val south2024 = camping("south-2024", "South 2024", 2024, Calendar.JULY)
        val south2025 = camping("south-2025", "South 2025", 2025, Calendar.JULY)
        val north = camping("north", "North", 2023, Calendar.JULY).copy(
            organizerLevel = OrganizerLevel(OrganizerType.Regional, "North"),
        )
        val groups = CampingsViewModel.groupedHistory(listOf(south2024, north, south2025))
        assertEquals(listOf("North", "South"), groups.map { it.organizerLevel.value })
        assertEquals(listOf(2025, 2024), groups.last().yearGroups.map { it.year })
    }

    @Test
    fun unpublishedCampingsStayHiddenUntilAllowedThenGroupByPublicationStatus() = runTest {
        val published = camping("published", "Published Camp", 2026, Calendar.JULY)
        val draft = camping("draft", "Draft Camp", 2026, Calendar.AUGUST)
            .copy(publicationStatus = CampingPublicationStatus.Draft)
        val viewModel = CampingsViewModel(FakeCampingService(listOf(published, draft)))

        val initialPhase = viewModel.uiState.value.phase as CampingsPhase.Loaded
        assertEquals(listOf("published"), initialPhase.sections.flatMap { it.campings }.map { it.id })

        viewModel.configureUnpublishedVisibility { it.id == "draft" }

        val allowedPhase = viewModel.uiState.value.phase as CampingsPhase.Loaded
        assertEquals(listOf("draft", "published"), allowedPhase.publicationSections.flatMap { it.campings }.map { it.id })
        assertEquals(
            listOf(CampingPublicationStatus.Draft, CampingPublicationStatus.Published),
            allowedPhase.publicationSections.map { it.status },
        )
    }

    @Test
    fun draftHistoryIsPrivateUntilAllowed() = runTest {
        val publishedPast = camping("published-past", "Published Past", 2025, Calendar.JULY)
        val draftPast = camping("draft-past", "Draft Past", 2025, Calendar.AUGUST)
            .copy(publicationStatus = CampingPublicationStatus.Draft)
        val viewModel = CampingsViewModel(FakeCampingService(listOf(publishedPast, draftPast)))

        assertEquals(
            listOf("published-past"),
            viewModel.uiState.value.historyGroups.flatMap { group ->
                group.yearGroups.flatMap { it.campings }
            }.map { it.id },
        )

        viewModel.configureUnpublishedVisibility { it.id == "draft-past" }

        assertEquals(
            setOf("published-past", "draft-past"),
            viewModel.uiState.value.historyGroups.flatMap { group ->
                group.yearGroups.flatMap { it.campings }
            }.map { it.id }.toSet(),
        )
    }

    @Test
    fun campingDurationCountsBothCalendarDays() {
        val zone = TimeZone.getTimeZone("Europe/Paris")
        val calendar = Calendar.getInstance(zone).apply {
            clear()
            set(2026, Calendar.JULY, 18, 18, 0)
        }
        val start = calendar.time
        calendar.set(2026, Calendar.JULY, 19, 9, 0)

        assertEquals(2, inclusiveCampingDayCount(start, calendar.time, zone))
        assertEquals(1, inclusiveCampingDayCount(start, start, zone))
    }

    @Test
    fun campingDurationUsesCalendarDaysAcrossDaylightSavingChange() {
        val zone = TimeZone.getTimeZone("Europe/Paris")
        val calendar = Calendar.getInstance(zone).apply {
            clear()
            set(2026, Calendar.MARCH, 28, 12, 0)
        }
        val start = calendar.time
        calendar.set(2026, Calendar.MARCH, 30, 12, 0)

        assertEquals(3, inclusiveCampingDayCount(start, calendar.time, zone))
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
