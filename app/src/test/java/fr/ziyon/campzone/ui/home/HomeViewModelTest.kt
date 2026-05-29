package fr.ziyon.campzone.ui.home

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.announcements.FakeAnnouncementService
import fr.ziyon.campzone.data.camping.FakeCampingService
import fr.ziyon.campzone.data.model.Announcement
import fr.ziyon.campzone.data.model.AnnouncementAudienceScope
import fr.ziyon.campzone.data.schedule.FakeScheduleService
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun dashboardStillShowsAppWideAnnouncementsWithoutFeaturedCamping() = runTest {
        val viewModel = HomeViewModel(
            campingService = FakeCampingService(initial = emptyList()),
            scheduleService = FakeScheduleService(),
            announcementService = FakeAnnouncementService(
                mutableListOf(
                    announcement("global-new", createdAt = Date(3_000)),
                    announcement("camping", audienceScope = AnnouncementAudienceScope.Camping, createdAt = Date(2_000)),
                    announcement("role", role = UserRole.Leader, createdAt = Date(1_000)),
                    announcement("global-old", createdAt = Date(500)),
                ),
            ),
        )

        advanceUntilIdle()

        val loaded = viewModel.uiState.value.phase as HomePhase.Loaded
        assertNull(loaded.featuredCamping)
        assertEquals(listOf("global-new", "global-old"), loaded.announcements.map { it.id })
    }

    private fun announcement(
        id: String,
        audienceScope: AnnouncementAudienceScope = AnnouncementAudienceScope.App,
        role: UserRole? = null,
        createdAt: Date,
    ) = Announcement(
        id = id,
        title = id,
        body = "Body for $id",
        audienceScopeRawValue = audienceScope.rawValue,
        notificationTargetRole = role,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}
