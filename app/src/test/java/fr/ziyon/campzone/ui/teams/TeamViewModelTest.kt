package fr.ziyon.campzone.ui.teams

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.games.ActivityReadScope
import fr.ziyon.campzone.data.games.FakeGameService
import fr.ziyon.campzone.data.media.PreviewMediaUploader
import fr.ziyon.campzone.data.model.Team
import fr.ziyon.campzone.data.model.TeamMember
import fr.ziyon.campzone.data.teams.FakeTeamNotificationDispatcher
import fr.ziyon.campzone.data.teams.FakeTeamService
import fr.ziyon.campzone.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TeamViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun manualTeamAdjustmentRequiresReasonAndRecordsGameIndependentActivity() = runTest {
        val (viewModel, gameService) = viewModel()
        viewModel.startObserving(CampingId)
        advanceUntilIdle()

        viewModel.updateTeamScore(TeamId, CampingId, 10, "  Great teamwork  ", actor())
        advanceUntilIdle()

        val saved = viewModel.team(TeamId, CampingId)!!
        assertEquals(110, saved.points)
        val activity = gameService.loadActivities(CampingId, ActivityReadScope.All).single()
        assertNull(activity.gameId)
        assertEquals("Manual adjustment", activity.name)
        assertEquals("Great teamwork", activity.reason)
        assertEquals(120, activity.previousScore)
        assertEquals(130, activity.newScore)
        assertEquals(TeamId, activity.targetTeamId)
        assertEquals("leader-1", activity.createdBy)
    }

    @Test
    fun manualMemberAdjustmentRecordsReasonAndPersonalScoreSnapshots() = runTest {
        val (viewModel, gameService) = viewModel()
        viewModel.startObserving(CampingId)
        advanceUntilIdle()

        viewModel.updateMemberScore(MemberId, TeamId, CampingId, -5, "Late for setup", actor())
        advanceUntilIdle()

        assertEquals(15, viewModel.team(TeamId, CampingId)!!.members.single().personalScore)
        val activity = gameService.loadActivities(CampingId, ActivityReadScope.All).single()
        assertNull(activity.gameId)
        assertEquals(-5, activity.points)
        assertEquals(20, activity.previousScore)
        assertEquals(15, activity.newScore)
        assertEquals(MemberId, activity.targetUserId)
        assertNull(activity.targetTeamId)
    }

    @Test
    fun penaltyRecordsSignedLedgerEntry() = runTest {
        val (viewModel, gameService) = viewModel()
        viewModel.startObserving(CampingId)
        advanceUntilIdle()

        viewModel.applyPenalty(TeamId, CampingId, 7, "Missed cleanup", actor())
        advanceUntilIdle()

        val saved = viewModel.team(TeamId, CampingId)!!
        assertEquals(7, saved.penalties.single().points)
        val activity = gameService.loadActivities(CampingId, ActivityReadScope.All).single()
        assertEquals(-7, activity.points)
        assertEquals(120, activity.previousScore)
        assertEquals(113, activity.newScore)
        assertEquals("Missed cleanup", activity.reason)
    }

    @Test
    fun blankReasonDoesNotMutateScoreOrCreateLedgerEntry() = runTest {
        val (viewModel, gameService) = viewModel()
        viewModel.startObserving(CampingId)
        advanceUntilIdle()

        viewModel.updateTeamScore(TeamId, CampingId, 10, "   ", actor())
        viewModel.updateMemberScore(MemberId, TeamId, CampingId, 5, "", actor())
        advanceUntilIdle()

        assertEquals(100, viewModel.team(TeamId, CampingId)!!.points)
        assertEquals(20, viewModel.team(TeamId, CampingId)!!.members.single().personalScore)
        assertTrue(gameService.loadActivities(CampingId, ActivityReadScope.All).isEmpty())
    }

    @Test
    fun ledgerFailureDoesNotUndoPersistedScoreChange() = runTest {
        val (viewModel, gameService) = viewModel()
        viewModel.startObserving(CampingId)
        advanceUntilIdle()
        gameService.shouldFail = true

        viewModel.updateTeamScore(TeamId, CampingId, 10, "Great teamwork", actor())
        advanceUntilIdle()

        assertEquals(110, viewModel.team(TeamId, CampingId)!!.points)
        assertNull(viewModel.operationError.value)
    }

    @Test
    fun stoppingTeamObserverDoesNotPublishCoroutineCancellationAsError() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.startObserving(CampingId)
        advanceUntilIdle()

        viewModel.stopObserving()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is TeamsUiState.Loaded)
    }

    private fun viewModel(): Pair<TeamViewModel, FakeGameService> {
        val gameService = FakeGameService()
        val teamService = FakeTeamService(mutableListOf(team()))
        return TeamViewModel(
            teamService = teamService,
            gameService = gameService,
            imageUploader = PreviewMediaUploader,
            teamNotificationDispatcher = FakeTeamNotificationDispatcher(),
        ) to gameService
    }

    private fun team() = Team(
        id = TeamId,
        campingId = CampingId,
        name = "Lions",
        slogan = "Together",
        symbolName = "flame.fill",
        colorHex = "#D9432F",
        points = 100,
        members = listOf(
            TeamMember(
                id = MemberId,
                userId = MemberId,
                displayName = "Ana Silva",
                church = "Central SDA",
                personalScore = 20,
            ),
        ),
    )

    private fun actor() = AuthenticatedUser(
        uid = "leader-1",
        email = "leader@example.com",
        displayName = "Leader One",
        photoUrl = null,
        role = UserRole.Leader,
        church = "Central SDA",
        age = 30,
        preferredLanguage = "en",
        gender = null,
        onboardingCompleted = true,
    )

    private companion object {
        const val CampingId = "camp-1"
        const val TeamId = "team-1"
        const val MemberId = "member-1"
    }
}
