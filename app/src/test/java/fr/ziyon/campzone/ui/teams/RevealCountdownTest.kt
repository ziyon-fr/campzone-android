package fr.ziyon.campzone.ui.teams

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test

class RevealCountdownTest {

    @Test
    fun nullAnchorMeansNoCountdown() {
        assertEquals(0, revealCountdownSeconds(revealedAt = null, now = Date(0), windowSeconds = 10))
    }

    @Test
    fun anchorJustNowGivesFullWindow() {
        val now = Date(1_000_000L)
        // revealedAt == now → full 10s window remains.
        assertEquals(10, revealCountdownSeconds(revealedAt = now, now = now, windowSeconds = 10))
    }

    @Test
    fun midWindowGivesRemainingSeconds() {
        val revealedAt = Date(1_000_000L)
        val now = Date(1_000_000L + 4_000L) // 4s into a 10s window
        assertEquals(6, revealCountdownSeconds(revealedAt, now, windowSeconds = 10))
    }

    @Test
    fun pastWindowGivesZeroSoTrophyShowsImmediately() {
        val revealedAt = Date(1_000_000L)
        val now = Date(1_000_000L + 30_000L) // long past the 10s window
        assertEquals(0, revealCountdownSeconds(revealedAt, now, windowSeconds = 10))
    }

    @Test
    fun neverExceedsTheWindow() {
        // A clock-skewed device reading a future anchor still clamps to the window.
        val now = Date(1_000_000L)
        val futureAnchor = Date(1_000_000L + 60_000L)
        assertEquals(10, revealCountdownSeconds(futureAnchor, now, windowSeconds = 10))
    }
}
