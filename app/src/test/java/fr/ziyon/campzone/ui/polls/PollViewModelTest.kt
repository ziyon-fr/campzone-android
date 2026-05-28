package fr.ziyon.campzone.ui.polls

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.model.Poll
import fr.ziyon.campzone.data.model.PollOption
import fr.ziyon.campzone.data.polls.FakePollNotificationDispatcher
import fr.ziyon.campzone.data.polls.FakePollService
import fr.ziyon.campzone.data.polls.PollDispatchEvent
import fr.ziyon.campzone.testing.MainDispatcherRule
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PollViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(service: FakePollService, dispatcher: FakePollNotificationDispatcher) =
        PollViewModel(service, dispatcher)

    @Test
    fun savePollCreatesAndDispatchesCreated() = runTest {
        val service = FakePollService()
        val dispatcher = FakePollNotificationDispatcher()
        val viewModel = vm(service, dispatcher)

        viewModel.startEditor(pollId = null, campingId = "camp-1")
        advanceUntilIdle()
        viewModel.updateForm { it.copy(question = "Best snack?", optionLabels = listOf("Apple", "Banana")) }

        var saved = false
        viewModel.savePoll("camp-1", user()) { saved = true }
        advanceUntilIdle()

        assertTrue(saved)
        val dispatched = dispatcher.dispatched.single()
        assertEquals(PollDispatchEvent.Created, dispatched.event)
        assertEquals(1, service.loadPolls("camp-1").size)
    }

    @Test
    fun submitVoteCastsAndRecordsActiveVote() = runTest {
        val poll = Poll(
            id = "p1",
            campingId = "camp-1",
            question = "Q",
            options = listOf(PollOption("a", "A"), PollOption("b", "B")),
            isOpen = true,
            createdAt = Date(),
        )
        val service = FakePollService(polls = listOf(poll))
        val viewModel = vm(service, FakePollNotificationDispatcher())

        viewModel.startObservingPoll("p1", "camp-1", "voter-1")
        advanceUntilIdle()
        viewModel.toggleSelection("a", allowsMultiple = false)
        viewModel.submitVote(poll, "voter-1")
        advanceUntilIdle()

        assertNotNull(viewModel.activeVote.value)
        assertEquals(listOf("a"), viewModel.activeVote.value?.selectedOptionIds)
        assertEquals(1, service.loadVote("p1", "camp-1", "voter-1")?.selectedOptionIds?.size)
    }

    @Test
    fun closingPollDispatchesClosed() = runTest {
        val poll = Poll(id = "p1", campingId = "camp-1", question = "Q", isOpen = true, createdAt = Date())
        val service = FakePollService(polls = listOf(poll))
        val dispatcher = FakePollNotificationDispatcher()
        val viewModel = vm(service, dispatcher)

        viewModel.setOpen(poll, isOpen = false)
        advanceUntilIdle()

        assertEquals(PollDispatchEvent.Closed, dispatcher.dispatched.single().event)
    }

    private fun user() = AuthenticatedUser(
        uid = "me",
        email = "me@campzone.local",
        displayName = "Me Camper",
        photoUrl = null,
        role = UserRole.Admin,
        church = "Central SDA",
        age = 30,
        preferredLanguage = "en",
        gender = UserGender.Female,
        onboardingCompleted = true,
    )
}
