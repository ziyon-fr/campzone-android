package fr.ziyon.campzone.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickActionPinStoreTest {
    @Test
    fun normalizedPinsAreOrderedUniqueAndLimitedToTwo() {
        assertEquals(
            listOf(QuickActionKind.Songbook, QuickActionKind.Schedule),
            QuickActionPinPolicy.normalized(
                listOf(
                    QuickActionKind.Songbook,
                    QuickActionKind.Songbook,
                    QuickActionKind.Schedule,
                    QuickActionKind.VenueMap,
                ),
            ),
        )
    }
}
