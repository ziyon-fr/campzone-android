package fr.ziyon.campzone.ui.profile.badges

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.badges.FakeAchievementService
import fr.ziyon.campzone.data.camping.FakeCampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.AchievementCatalog
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.Team
import fr.ziyon.campzone.data.model.TeamMember
import fr.ziyon.campzone.data.teams.FakeTeamService
import fr.ziyon.campzone.testing.FakeStringProvider
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
class AchievementViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun profileLoadFiltersUnknownBadges() = runTest {
        val viewModel = AchievementViewModel(
            achievementService = FakeAchievementService(
                initialBadges = listOf(
                    fr.ziyon.campzone.data.model.EarnedBadge("tent-ready", "user-1", Date()),
                    fr.ziyon.campzone.data.model.EarnedBadge("ghost-badge", "user-1", Date()),
                ),
            ),
            campingService = campingService(emptyList()),
            teamService = FakeTeamService(mutableListOf()),
            strings = FakeStringProvider(),
        )

        viewModel.loadProfileBadges("user-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value as AchievementUiState.Loaded
        assertEquals(listOf("tent-ready"), state.earned.map { it.id })
    }

    @Test
    fun profileLoadUsesServiceCatalogInsteadOfStaticCatalogOnly() = runTest {
        val remoteAchievement = AchievementCatalog.all.first().copy(
            id = "remote-firestore-badge",
            title = "Remote Firestore Badge",
        )
        val viewModel = AchievementViewModel(
            achievementService = FakeAchievementService(
                initialBadges = listOf(
                    fr.ziyon.campzone.data.model.EarnedBadge("remote-firestore-badge", "user-1", Date()),
                    fr.ziyon.campzone.data.model.EarnedBadge("tent-ready", "user-1", Date()),
                ),
                initialCatalog = listOf(remoteAchievement),
            ),
            campingService = campingService(emptyList()),
            teamService = FakeTeamService(mutableListOf()),
            strings = FakeStringProvider(),
        )

        viewModel.loadProfileBadges("user-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value as AchievementUiState.Loaded
        assertEquals(listOf("remote-firestore-badge"), state.catalog.map { it.id })
        assertEquals(listOf("remote-firestore-badge"), state.earned.map { it.id })
    }

    @Test
    fun manualAwardWritesSelectedTeamRecipientsAndRejectsSelfAward() = runTest {
        val badges = FakeAchievementService()
        val viewModel = AchievementViewModel(
            achievementService = badges,
            campingService = campingService(listOf(approved("a1", "Maria"), approved("a2", "Joao"))),
            teamService = FakeTeamService(
                mutableListOf(
                    Team(
                        id = "lions",
                        campingId = "camp-1",
                        name = "Lions",
                        members = listOf(
                            TeamMember(id = "m1", userId = "a1", displayName = "Maria", church = "Paris"),
                            TeamMember(id = "m2", userId = "a2", displayName = "Joao", church = "Paris"),
                        ),
                    ),
                ),
            ),
            strings = FakeStringProvider(),
        )

        viewModel.loadAwardSurface("camp-1", admin(uid = "admin-1"))
        advanceUntilIdle()
        viewModel.selectAchievement("tent-ready")
        viewModel.awardSelected(currentUserId = "admin-1")
        advanceUntilIdle()

        assertEquals(listOf("a1"), badges.loadEarned("a1").map { it.userId })
        assertEquals(listOf("a2"), badges.loadEarned("a2").map { it.userId })

        viewModel.awardSelected(currentUserId = "a1")
        advanceUntilIdle()
        assertEquals("Ask another leader to award badges that include you.", viewModel.operationMessage.value)
    }

    @Test
    fun nonScopedLeaderIsRestrictedFromAwardSurface() = runTest {
        val viewModel = AchievementViewModel(
            achievementService = FakeAchievementService(),
            campingService = campingService(emptyList()),
            teamService = FakeTeamService(mutableListOf()),
            strings = FakeStringProvider(),
        )

        viewModel.loadAwardSurface("camp-1", admin(role = UserRole.Leader, church = "Other Church"))
        advanceUntilIdle()

        assertTrue(viewModel.awardState.value is BadgeAwardUiState.Restricted)
    }

    private fun campingService(attendees: List<CampingAttendee>) = FakeCampingService(
        initial = listOf(camping()),
        attendeesByCamping = mapOf("camp-1" to attendees),
    )

    private fun camping() = Camping(
        id = "camp-1",
        title = "Summer Camp",
        description = "A week of fun",
        startDate = Date(1_000_000),
        endDate = Date(2_000_000),
        organizerLevel = OrganizerLevel(OrganizerType.Church, "Paris Central SDA"),
        location = "Lake Annecy",
        registrationStatus = CampingRegistrationStatus.Open,
    )

    private fun approved(id: String, name: String) = CampingAttendee(
        id = id,
        userId = id,
        displayName = name,
        church = "Paris Central SDA",
        age = 20,
        languages = listOf("fr"),
        registrationStatus = RegistrationApprovalStatus.Approved,
    )

    private fun admin(
        uid: String = "admin-1",
        role: UserRole = UserRole.Admin,
        church: String = "Paris Central SDA",
    ) = AuthenticatedUser(
        uid = uid,
        email = "$uid@example.com",
        displayName = "User $uid",
        photoUrl = null,
        role = role,
        church = church,
        age = 30,
        preferredLanguage = "fr",
        gender = UserGender.PreferNotToSay,
        onboardingCompleted = true,
    )
}
