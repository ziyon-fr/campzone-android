package fr.ziyon.campzone.ui.feedback

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.camping.FakeCampingService
import fr.ziyon.campzone.data.feedback.FakeFeedbackService
import fr.ziyon.campzone.data.model.CampDay
import fr.ziyon.campzone.data.model.CampFeedback
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.CampingSchedule
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.data.model.Program
import fr.ziyon.campzone.data.model.ProgramFeedback
import fr.ziyon.campzone.data.model.ProgramType
import fr.ziyon.campzone.data.schedule.FakeScheduleService
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

class FeedbackViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val participant = user(uid = "u1", role = UserRole.User)
    private val admin = user(uid = "admin-1", role = UserRole.Admin)

    // --- Survey ---

    @Test
    fun surveyNotYetAvailableBeforeCampEnds() {
        val viewModel = surveyViewModel(endDate = Date(System.currentTimeMillis() + 5 * DAY_MILLIS))

        viewModel.load("camp-1", participant)

        assertTrue(viewModel.uiState.value is FeedbackSurveyUiState.NotAvailable)
    }

    @Test
    fun surveyExpiredAfterSixtyDayWindow() {
        val viewModel = surveyViewModel(endDate = Date(System.currentTimeMillis() - 61 * DAY_MILLIS))

        viewModel.load("camp-1", participant)

        assertTrue(viewModel.uiState.value is FeedbackSurveyUiState.Expired)
    }

    @Test
    fun surveyEditingSeedsProgramsExcludingMeals() {
        val viewModel = surveyViewModel(
            endDate = Date(System.currentTimeMillis() - DAY_MILLIS),
            schedule = scheduleWith(
                program("p1", "Morning Worship", ProgramType.Preaching),
                program("m1", "Breakfast", ProgramType.Breakfast),
                program("p2", "Team Games", ProgramType.Games),
                program("m2", "Lunch", ProgramType.Lunch),
            ),
        )

        viewModel.load("camp-1", participant)

        val state = viewModel.uiState.value as FeedbackSurveyUiState.Editing
        assertEquals(listOf("p1", "p2"), state.programFeedback.map { it.id })
        assertEquals(0, state.overallRating)
        assertTrue(state.wouldReturn)
    }

    @Test
    fun surveyShowsThanksWhenAlreadySubmitted() {
        val viewModel = surveyViewModel(
            endDate = Date(System.currentTimeMillis() - DAY_MILLIS),
            existing = listOf(
                feedback(userId = "u1", overall = 4),
            ),
        )

        viewModel.load("camp-1", participant)

        val state = viewModel.uiState.value as FeedbackSurveyUiState.Submitted
        assertEquals(4, state.overallRating)
    }

    @Test
    fun submitPersistsAndTransitionsToThanks() {
        val service = FakeFeedbackService()
        val viewModel = surveyViewModel(
            endDate = Date(System.currentTimeMillis() - DAY_MILLIS),
            schedule = scheduleWith(program("p1", "Worship", ProgramType.Preaching)),
            feedbackService = service,
        )
        viewModel.load("camp-1", participant)

        viewModel.setOverallRating(5)
        viewModel.setProgramRating("p1", 4)
        viewModel.setHighlights("  The worship nights  ")
        viewModel.setWouldReturn(false)
        viewModel.submit()

        val state = viewModel.uiState.value as FeedbackSurveyUiState.Submitted
        assertEquals(5, state.overallRating)

        // The stored doc carries the participant uid, trimmed text and kept rating.
        val stored = service.peek("u1")!!
        assertEquals("u1", stored.id)
        assertEquals("The worship nights", stored.highlights)
        assertEquals(1, stored.programFeedback.size)
        assertEquals(4, stored.programFeedback.first().rating)
        assertEquals(false, stored.wouldReturn)
    }

    @Test
    fun submitIgnoredWithoutOverallRating() {
        val service = FakeFeedbackService()
        val viewModel = surveyViewModel(
            endDate = Date(System.currentTimeMillis() - DAY_MILLIS),
            feedbackService = service,
        )
        viewModel.load("camp-1", participant)

        viewModel.submit()

        assertTrue(viewModel.uiState.value is FeedbackSurveyUiState.Editing)
        assertEquals(null, service.peek("u1"))
    }

    // --- Results ---

    @Test
    fun resultsRestrictedForNonManager() {
        val viewModel = resultsViewModel(responses = emptyList())

        viewModel.load("camp-1", participant)

        assertTrue(viewModel.uiState.value is FeedbackResultsUiState.Restricted)
    }

    @Test
    fun resultsEmptyWhenNoResponses() {
        val viewModel = resultsViewModel(responses = emptyList())

        viewModel.load("camp-1", admin)

        assertTrue(viewModel.uiState.value is FeedbackResultsUiState.Empty)
    }

    @Test
    fun resultsAggregateAverageReturnRateAndPrograms() {
        val viewModel = resultsViewModel(
            responses = listOf(
                feedback(
                    userId = "a",
                    overall = 5,
                    wouldReturn = true,
                    highlights = "Loved it",
                    programs = listOf(ProgramFeedback("p1", "Worship", 5)),
                ),
                feedback(
                    userId = "b",
                    overall = 3,
                    wouldReturn = false,
                    improvements = "More rest",
                    programs = listOf(ProgramFeedback("p1", "Worship", 3), ProgramFeedback("p2", "Games", 4)),
                ),
            ),
        )

        viewModel.load("camp-1", admin)

        val state = viewModel.uiState.value as FeedbackResultsUiState.Loaded
        assertEquals(2, state.responseCount)
        assertEquals(4.0, state.averageOverall, 0.001)
        assertEquals(50, state.wouldReturnPercent)
        // Games (4.0) ranks ahead of Worship (4.0 avg of 5+3) — tie, both present.
        assertEquals(setOf("Worship", "Games"), state.programAverages.map { it.title }.toSet())
        assertEquals(2, state.comments.size)
    }

    // --- builders ---

    private fun surveyViewModel(
        endDate: Date,
        schedule: CampingSchedule? = null,
        existing: List<CampFeedback> = emptyList(),
        feedbackService: FakeFeedbackService = FakeFeedbackService(existing),
    ) = FeedbackSurveyViewModel(
        campingService = FakeCampingService(initial = listOf(camping(endDate))),
        scheduleService = FakeScheduleService(
            schedules = schedule?.let { mutableMapOf("camp-1" to it) } ?: mutableMapOf(),
        ),
        feedbackService = feedbackService,
    )

    private fun resultsViewModel(responses: List<CampFeedback>) = FeedbackResultsViewModel(
        campingService = FakeCampingService(initial = listOf(camping(Date(2_000_000)))),
        feedbackService = FakeFeedbackService(responses),
    )

    private fun camping(endDate: Date) = Camping(
        id = "camp-1",
        title = "Summer Camp",
        description = "A week of fun",
        startDate = Date(endDate.time - 5 * DAY_MILLIS),
        endDate = endDate,
        organizerLevel = OrganizerLevel(OrganizerType.Church, "Paris Central SDA"),
        location = "Annecy",
        registrationStatus = CampingRegistrationStatus.Open,
        createdByUid = "admin-1",
    )

    private fun scheduleWith(vararg programs: Program) = CampingSchedule(
        campingId = "camp-1",
        days = listOf(
            CampDay(id = "d1", campingId = "camp-1", date = Date(1_000_000), programs = programs.toList()),
        ),
    )

    private fun program(id: String, title: String, type: ProgramType) = Program(
        id = id,
        campingId = "camp-1",
        campDayId = "d1",
        title = title,
        type = type,
        startDate = Date(1_000_000),
        endDate = Date(1_100_000),
    )

    private fun feedback(
        userId: String,
        overall: Int,
        wouldReturn: Boolean = true,
        highlights: String = "",
        improvements: String = "",
        programs: List<ProgramFeedback> = emptyList(),
    ) = CampFeedback(
        id = userId,
        campingId = "camp-1",
        userId = userId,
        displayName = userId,
        overallRating = overall,
        wouldReturn = wouldReturn,
        isAnonymous = false,
        programFeedback = programs,
        highlights = highlights,
        improvements = improvements,
        submittedAt = Date(),
    )

    private fun user(uid: String, role: UserRole) = AuthenticatedUser(
        uid = uid,
        email = "$uid@example.com",
        displayName = uid,
        photoUrl = null,
        role = role,
        church = "Paris Central SDA",
        age = 30,
        preferredLanguage = "en",
        gender = UserGender.Male,
        onboardingCompleted = true,
    )
}
