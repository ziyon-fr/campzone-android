package fr.ziyon.campzone.ui.games

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.camping.PreviewCampingService
import fr.ziyon.campzone.data.games.FakeGameService
import fr.ziyon.campzone.data.media.PreviewMediaUploader
import fr.ziyon.campzone.data.games.previewGame
import fr.ziyon.campzone.data.model.Activity
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.Game
import fr.ziyon.campzone.data.model.GameInstructionAttachment
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.data.model.PointRule
import fr.ziyon.campzone.data.model.PointRuleTarget
import fr.ziyon.campzone.data.model.PointRuleVisibility
import fr.ziyon.campzone.data.model.Team
import fr.ziyon.campzone.data.model.TeamMember
import fr.ziyon.campzone.data.teams.FakeTeamNotificationDispatcher
import fr.ziyon.campzone.data.teams.FakeTeamService
import fr.ziyon.campzone.data.venuemap.FakeVenueMapService
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        imageUploader = PreviewMediaUploader,
        assetDeleter = PreviewMediaUploader,
        venueMapService = FakeVenueMapService(),
    )

    private fun viewModel(service: FakeGameService, teamService: FakeTeamService) = GameViewModel(
        gameService = service,
        teamService = teamService,
        campingService = PreviewCampingService(),
        teamNotificationDispatcher = FakeTeamNotificationDispatcher(),
        imageUploader = PreviewMediaUploader,
        assetDeleter = PreviewMediaUploader,
        venueMapService = FakeVenueMapService(),
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
    fun savesAndDeletesLeadershipInstructionsAgainstFakeService() = runTest {
        val service = FakeGameService()
        val vm = viewModel(service)

        vm.updateInstructionsForm {
            GameInstructionsForm(
                title = "  Setup note  ",
                description = "Leaders meet behind the stage.",
                images = listOf(
                    GameInstructionAttachment(
                        url = "https://example.com/setup.jpg",
                        publicId = "campzone/setup",
                    ),
                ),
            )
        }

        assertTrue(vm.saveInstructions(gameId = "game-1", campingId = "camp-1"))
        val saved = service.loadInstructions(gameId = "game-1", campingId = "camp-1")
        assertEquals("Setup note", saved?.title)
        assertEquals("Leaders meet behind the stage.", saved?.description)
        assertEquals("campzone/setup", saved?.images?.single()?.publicId)

        vm.updateInstructionsForm { GameInstructionsForm() }

        assertTrue(vm.saveInstructions(gameId = "game-1", campingId = "camp-1"))
        assertNull(service.loadInstructions(gameId = "game-1", campingId = "camp-1"))
    }

    @Test
    fun streamFailurePublishesErrorState() = runTest {
        val vm = viewModel(FakeGameService(shouldFail = true))

        vm.load("camp-1")
        advanceUntilIdle()

        assertTrue(vm.uiState.value is GamesUiState.Error)
    }

    @Test
    fun teamOnlyPointRuleRejectsParticipantTarget() = runTest {
        val game = scopeGame(PointRuleTarget.Team)
        val gameService = FakeGameService(games = listOf(game), activities = emptyList())
        val teamService = FakeTeamService(scopeTeams())
        val vm = viewModel(gameService, teamService)
        val team = teamService.loadTeams("camp-1").single()
        val member = team.members.single()

        val activity = vm.awardPoints(
            request = ActivityRequest(
                gameId = game.id,
                pointRuleId = "scope-rule",
                name = "Scoped rule",
                points = 10,
                reason = "",
                targetTeamId = null,
                targetTeamName = null,
                targetUserId = member.userId,
                targetUserName = member.displayName,
                visibility = PointRuleVisibility.Immediate,
            ),
            camping = scopeCamping(),
            teams = listOf(team),
            actor = scopeActor(),
            suppliedGame = game,
        )

        assertNull(activity)
        assertEquals("This point rule can only be awarded to teams.", vm.operationError)
        assertTrue(gameService.loadActivities("camp-1").isEmpty())
    }

    @Test
    fun participantOnlyPointRuleRejectsTeamTarget() = runTest {
        val game = scopeGame(PointRuleTarget.User)
        val gameService = FakeGameService(games = listOf(game), activities = emptyList())
        val teamService = FakeTeamService(scopeTeams())
        val vm = viewModel(gameService, teamService)
        val team = teamService.loadTeams("camp-1").single()

        val activity = vm.awardPoints(
            request = ActivityRequest(
                gameId = game.id,
                pointRuleId = "scope-rule",
                name = "Scoped rule",
                points = 10,
                reason = "",
                targetTeamId = team.id,
                targetTeamName = team.name,
                targetUserId = null,
                targetUserName = null,
                visibility = PointRuleVisibility.Immediate,
            ),
            camping = scopeCamping(),
            teams = listOf(team),
            actor = scopeActor(),
            suppliedGame = game,
        )

        assertNull(activity)
        assertEquals("This point rule can only be awarded to participants.", vm.operationError)
        assertTrue(gameService.loadActivities("camp-1").isEmpty())
    }

    @Test
    fun mixedPointRuleAllowsTeamAndParticipantTargets() = runTest {
        val game = scopeGame(PointRuleTarget.Any)
        val gameService = FakeGameService(games = listOf(game), activities = emptyList())
        val teamService = FakeTeamService(scopeTeams())
        val vm = viewModel(gameService, teamService)
        val team = teamService.loadTeams("camp-1").single()
        val member = team.members.single()

        val teamActivity = vm.awardPoints(
            request = ActivityRequest(
                gameId = game.id,
                pointRuleId = "scope-rule",
                name = "Scoped rule",
                points = 10,
                reason = "",
                targetTeamId = team.id,
                targetTeamName = team.name,
                targetUserId = null,
                targetUserName = null,
                visibility = PointRuleVisibility.Immediate,
            ),
            camping = scopeCamping(),
            teams = listOf(team),
            actor = scopeActor(),
            suppliedGame = game,
        )
        val teamsAfterTeamAward = teamService.loadTeams("camp-1")
        val participantActivity = vm.awardPoints(
            request = ActivityRequest(
                gameId = game.id,
                pointRuleId = "scope-rule",
                name = "Scoped rule",
                points = 10,
                reason = "",
                targetTeamId = null,
                targetTeamName = null,
                targetUserId = member.userId,
                targetUserName = member.displayName,
                visibility = PointRuleVisibility.Immediate,
            ),
            camping = scopeCamping(),
            teams = teamsAfterTeamAward,
            actor = scopeActor(),
            suppliedGame = game,
        )

        assertNotNull(teamActivity)
        assertNotNull(participantActivity)
        assertEquals(2, gameService.loadActivities("camp-1").size)
    }

    @Test
    fun teamAwardRecordsActivityBeforeIncrementingTeamScore() = runTest {
        val game = scopeGame(PointRuleTarget.Team)
        val gameService = FakeGameService(games = listOf(game), activities = emptyList())
        val teamService = FakeTeamService(scopeTeams())
        val vm = viewModel(gameService, teamService)
        val team = teamService.loadTeams("camp-1").single()

        val activity = vm.awardPoints(
            request = ActivityRequest(
                gameId = game.id,
                pointRuleId = "scope-rule",
                name = "Bible quiz win",
                points = 15,
                reason = "Fastest correct answer",
                targetTeamId = team.id,
                targetTeamName = team.name,
                targetUserId = null,
                targetUserName = null,
                visibility = PointRuleVisibility.Immediate,
            ),
            camping = scopeCamping(),
            teams = listOf(team),
            actor = scopeActor(),
            suppliedGame = game,
        )

        assertNotNull(activity)
        val recorded = gameService.loadActivities("camp-1").single()
        assertEquals(activity!!.id, recorded.id)
        assertEquals(game.id, recorded.gameId)
        assertEquals(team.id, recorded.targetTeamId)
        assertNull(recorded.targetUserId)
        assertEquals(25, recorded.previousScore)
        assertEquals(40, recorded.newScore)

        val updatedTeam = teamService.loadTeams("camp-1").single()
        assertEquals(35, updatedTeam.points)
        assertEquals(40, updatedTeam.totalScore)
        assertTrue(updatedTeam.penalties.isEmpty())
    }

    private fun scopeGame(appliesTo: PointRuleTarget) = Game(
        id = "scope-game",
        campingId = "camp-1",
        name = "Scope Game",
        pointRules = listOf(
            PointRule(
                id = "scope-rule",
                name = "Scoped rule",
                points = 10,
                appliesTo = appliesTo,
            ),
        ),
        createdBy = "admin-1",
        createdAt = Date(),
        updatedAt = Date(),
    )

    private fun scopeTeams() = mutableListOf(
        Team(
            id = "team-1",
            campingId = "camp-1",
            name = "Lions",
            slogan = "Courage",
            symbolName = "flame.fill",
            colorHex = "#D9432F",
            points = 20,
            members = listOf(
                TeamMember(
                    id = "member-1",
                    userId = "user-1",
                    displayName = "Ana Silva",
                    church = "Central SDA",
                    personalScore = 5,
                ),
            ),
            createdAt = Date(),
            updatedAt = Date(),
        ),
    )

    private fun scopeCamping() = Camping(
        id = "camp-1",
        title = "Scope Camp",
        description = "",
        startDate = Date(),
        endDate = Date(Date().time + 86_400_000),
        organizerLevel = OrganizerLevel(OrganizerType.Church, "Central SDA"),
        location = "Camp",
        registrationStatus = CampingRegistrationStatus.Open,
    )

    private fun scopeActor() = AuthenticatedUser(
        uid = "admin-1",
        email = "admin@example.com",
        displayName = "Admin",
        photoUrl = null,
        role = UserRole.Admin,
        church = "Central SDA",
        age = 40,
        preferredLanguage = "en",
        gender = UserGender.Male,
        onboardingCompleted = true,
    )
}
