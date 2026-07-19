package fr.ziyon.campzone.data.model

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registration deadline gating (`Camping.effectiveRegistrationStatus` /
 * `acceptsRegistrations`). The deadline is a client-side gate: an `Open` camp
 * auto-closes once the deadline passes, and the only way back open is to
 * extend (edit) the deadline. `acceptsRegistrations` is also the service-level
 * register guard, so this drives the real close - not just the hidden CTA.
 */
class CampingRegistrationDeadlineTest {

    @Test
    fun openCampWithoutDeadlineAcceptsRegistrations() {
        val camp = baseCamp(deadline = null)
        assertFalse(camp.hasRegistrationDeadline)
        assertFalse(camp.isRegistrationDeadlinePassed)
        assertEquals(CampingRegistrationStatus.Open, camp.effectiveRegistrationStatus)
        assertTrue(camp.acceptsRegistrations)
    }

    @Test
    fun openCampWithFutureDeadlineStaysOpen() {
        val camp = baseCamp(deadline = Date(System.currentTimeMillis() + DAY_MS))
        assertTrue(camp.hasRegistrationDeadline)
        assertFalse(camp.isRegistrationDeadlinePassed)
        assertEquals(CampingRegistrationStatus.Open, camp.effectiveRegistrationStatus)
        assertTrue(camp.acceptsRegistrations)
    }

    @Test
    fun openCampWithPastDeadlineAutoCloses() {
        val camp = baseCamp(deadline = Date(System.currentTimeMillis() - DAY_MS))
        assertTrue(camp.isRegistrationDeadlinePassed)
        assertEquals(CampingRegistrationStatus.Closed, camp.effectiveRegistrationStatus)
        assertFalse(camp.acceptsRegistrations)
    }

    @Test
    fun cancelledCampPassesThroughRegardlessOfDeadline() {
        val camp = baseCamp(
            status = CampingRegistrationStatus.Cancelled,
            deadline = Date(System.currentTimeMillis() - DAY_MS),
        )
        // Auto-close only promotes Open -> Closed; Cancelled is never rewritten.
        assertEquals(CampingRegistrationStatus.Cancelled, camp.effectiveRegistrationStatus)
        assertFalse(camp.acceptsRegistrations)
    }

    @Test
    fun endedCampIsFinishedAndDoesNotAcceptRegistrations() {
        val camp = baseCamp(deadline = null).copy(
            startDate = Date(System.currentTimeMillis() - 3 * DAY_MS),
            endDate = Date(System.currentTimeMillis() - DAY_MS),
        )

        assertEquals(CampingPhase.Finished, camp.currentPhase)
        assertEquals(CampingRegistrationStatus.Closed, camp.effectiveRegistrationStatus)
        assertFalse(camp.acceptsRegistrations)
    }

    @Test
    fun liveCampKeepsOpenRegistrationWhileEndDateHasNotPassed() {
        val camp = baseCamp(deadline = null).copy(
            startDate = Date(System.currentTimeMillis() - DAY_MS),
            endDate = Date(System.currentTimeMillis() + DAY_MS),
        )

        assertEquals(CampingPhase.Live, camp.currentPhase)
        assertEquals(CampingRegistrationStatus.Open, camp.effectiveRegistrationStatus)
        assertTrue(camp.acceptsRegistrations)
    }

    private fun baseCamp(
        status: CampingRegistrationStatus = CampingRegistrationStatus.Open,
        deadline: Date?,
    ) = Camping(
        id = "c1",
        title = "Camp",
        description = "d",
        startDate = Date(System.currentTimeMillis() + DAY_MS),
        endDate = Date(System.currentTimeMillis() + 2 * DAY_MS),
        organizerLevel = OrganizerLevel(OrganizerType.Regional, "South"),
        location = "Lake",
        registrationStatus = status,
        registrationDeadline = deadline,
    )

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
