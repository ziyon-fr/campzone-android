package fr.ziyon.campzone.data.support

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull

class SupportHubTest {
    @Test fun validatorNormalizesWebUrlsAndRejectsUnsafeSchemes() {
        assertEquals("https://example.com/support", SupportUrlValidator.url("example.com/support"))
        assertNotNull(SupportUrlValidator.url("mailto:hello@example.com"))
        assertNull(SupportUrlValidator.url("javascript:alert(1)"))
    }

    @Test fun linksAndSponsorsAreFilteredAndOrdered() {
        val hub = SupportHub(
            intro = "Support",
            impactNote = "External only",
            links = listOf(
                SupportExternalLink(title = "Second", urlString = "https://example.com/2"),
                SupportExternalLink(title = "Primary", urlString = "https://example.com/1", isPrimary = true),
                SupportExternalLink(title = "Broken", urlString = "bad://url"),
            ),
            sponsors = listOf(SponsorAcknowledgement(name = "Community"), SponsorAcknowledgement(name = "Gold", tier = SponsorTier.Gold)),
        )
        assertEquals(listOf("Primary", "Second"), hub.externalLinks.map { it.title })
        assertEquals(listOf("Gold", "Community"), hub.visibleSponsors.map { it.name })
    }
}
