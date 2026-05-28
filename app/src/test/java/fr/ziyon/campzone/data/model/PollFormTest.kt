package fr.ziyon.campzone.data.model

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PollFormTest {

    @Test
    fun blankQuestionIsInvalid() {
        val form = PollForm(question = "  ", optionLabels = listOf("A", "B"))
        assertEquals(PollFormError.QuestionRequired, form.validationError)
    }

    @Test
    fun needsAtLeastTwoOptions() {
        val form = PollForm(question = "Q", optionLabels = listOf("A", " "))
        assertEquals(PollFormError.NotEnoughOptions, form.validationError)
    }

    @Test
    fun duplicateOptionsRejectedCaseInsensitively() {
        val form = PollForm(question = "Q", optionLabels = listOf("Pizza", "pizza"))
        assertEquals(PollFormError.DuplicateOptions, form.validationError)
    }

    @Test
    fun pastCloseDateRejected() {
        val form = PollForm(
            question = "Q",
            optionLabels = listOf("A", "B"),
            hasCloseDate = true,
            closesAt = Date(0),
        )
        assertEquals(PollFormError.InvalidCloseDate, form.validationError)
    }

    @Test
    fun validFormHasNoError() {
        val form = PollForm(question = "Q", optionLabels = listOf("A", "B"))
        assertNull(form.validationError)
        assertTrue(form.isValid)
    }
}
