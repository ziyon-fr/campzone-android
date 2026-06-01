package fr.ziyon.campzone.ui.games

import fr.ziyon.campzone.data.camping.PreviewCampingService
import fr.ziyon.campzone.data.games.FakeGameService
import fr.ziyon.campzone.data.games.previewGame
import fr.ziyon.campzone.data.model.Activity
import fr.ziyon.campzone.data.teams.FakeTeamNotificationDispatcher
import fr.ziyon.campzone.data.teams.FakeTeamService
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(service: FakeGameService) = GameViewModel(
        gameService = service,
        teamService = FakeTeamService(mutableListOf()),
        campingService = PreviewCampingService(),
        teamNotificationDispatcher = FakeTeamNotificationDispatcher(),
    )

    private fun activity(id: String, campingId: String, gameId: String) = Activity(
        id = id,
        campingId = campingId,
        gameId = gameId,
        name = "Correct answer",
        points = 10,
        previousScore = 0,
        newScore = 10,
        createdBy = "admin-1",
        createdByName = "Admin",
        createdAt = Date(),
        targetTeamId = "lions",
        targetTeamName = "Lions",
    )

    @Test
    fun liveGamesEmissionPublishesLoadedState() = runTest {
        val service = FakeGameService(games = listOf(previewGame("camp-1")))
        val vm = viewModel(service)

        vm.loadIfNeeded("camp-1")
        advanceUntilIdle()

        assertTrue(vm.uiState.value is GamesUiState.Loaded)
        assertEquals(1, vm.gamesFor("camp-1").size)
    }

    @Test
    fun recordedActivityReflectsLiveWithoutReload() = runTest {
        val service = FakeGameService(games = listOf(previewGame("camp-1")))
        val vm = viewModel(service)
        vm.loadIfNeeded("camp-1")
        advanceUntilIdle()
        assertTrue(vm.activitiesFor("camp-1").isEmpty())

        // A point award on another device records an activity; the live listener
        // must surface it without the screen calling load() again.
        service.recordActivity(activity("act-1", "camp-1", "game-1"))
        advanceUntilIdle()

        val activities = vm.activitiesFor("camp-1")
        assertEquals(1, activities.size)
        assertEquals("act-1", activities.first().id)
    }

    @Test
    fun streamFailurePublishesErrorState() = runTest {
        val vm = viewModel(FakeGameService(shouldFail = true))

        vm.load("camp-1")
        advanceUntilIdle()

        assertTrue(vm.uiState.value is GamesUiState.Error)
    }
}
