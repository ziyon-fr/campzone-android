package fr.ziyon.campzone.data.dashboard

import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainDashboardRepositoryTest {

    @Test
    fun activeRegisteredCampingWinsOverPinnedCamping() {
        val now = Date(10_000)
        val activeRegistered = camping("active", start = 8_000, end = 12_000)
        val pinned = camping("pinned", start = 20_000, end = 30_000, isFeatured = true)

        val selected = selectFeaturedCamping(
            campingsInEndDateOrder = listOf(activeRegistered, pinned),
            registeredCampingIds = setOf(activeRegistered.id),
            now = now,
        )

        assertEquals("active", selected?.id)
    }

    @Test
    fun firstAdminPinnedCampingWinsWhenNoActiveRegistration() {
        val now = Date(10_000)
        val firstPinned = camping("first-pinned", start = 20_000, end = 30_000, isFeatured = true)
        val secondPinned = camping("second-pinned", start = 11_000, end = 40_000, isFeatured = true)

        val selected = selectFeaturedCamping(
            campingsInEndDateOrder = listOf(firstPinned, secondPinned),
            registeredCampingIds = emptySet(),
            now = now,
        )

        assertEquals("first-pinned", selected?.id)
    }

    @Test
    fun openRegistrationSoonestStartWinsBeforeDateFallback() {
        val now = Date(10_000)
        val fallback = camping(
            id = "fallback",
            start = 50_000,
            end = 20_000,
            status = CampingRegistrationStatus.Closed,
        )
        val laterOpen = camping("later-open", start = 40_000, end = 30_000)
        val soonestOpen = camping("soonest-open", start = 15_000, end = 40_000)

        val selected = selectFeaturedCamping(
            campingsInEndDateOrder = listOf(fallback, laterOpen, soonestOpen),
            registeredCampingIds = emptySet(),
            now = now,
        )

        assertEquals("soonest-open", selected?.id)
    }

    @Test
    fun dateFallbackUsesFirstEligibleEndDateOrderedCamping() {
        val now = Date(10_000)
        val cancelled = camping(
            id = "cancelled",
            start = 8_000,
            end = 11_000,
            status = CampingRegistrationStatus.Cancelled,
        )
        val firstEligible = camping(
            id = "first-eligible",
            start = 5_000,
            end = 12_000,
            status = CampingRegistrationStatus.Closed,
        )
        val secondEligible = camping(
            id = "second-eligible",
            start = 6_000,
            end = 13_000,
            status = CampingRegistrationStatus.Closed,
        )

        val selected = selectFeaturedCamping(
            campingsInEndDateOrder = listOf(cancelled, firstEligible, secondEligible),
            registeredCampingIds = emptySet(),
            now = now,
        )

        assertEquals("first-eligible", selected?.id)
    }

    @Test
    fun returnsNullWhenNoCandidateIsEligible() {
        val now = Date(10_000)
        val selected = selectFeaturedCamping(
            campingsInEndDateOrder = listOf(
                camping("ended", start = 1_000, end = 9_000),
                camping("cancelled", start = 8_000, end = 12_000, status = CampingRegistrationStatus.Cancelled),
            ),
            registeredCampingIds = setOf("ended"),
            now = now,
        )

        assertNull(selected)
    }

    private fun camping(
        id: String,
        start: Long,
        end: Long,
        status: CampingRegistrationStatus = CampingRegistrationStatus.Open,
        isFeatured: Boolean = false,
    ) = Camping(
        id = id,
        title = id,
        description = "Description",
        startDate = Date(start),
        endDate = Date(end),
        organizerLevel = OrganizerLevel(OrganizerType.Church, "Paris Central SDA"),
        location = "Pine Valley",
        registrationStatus = status,
        isFeatured = isFeatured,
    )
}
