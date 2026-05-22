package fr.ziyon.campzone.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkInboxTest {
    @Test
    fun keepsDeepLinkParkedUntilConsumed() {
        val inbox = DeepLinkInbox()
        val deepLink = CampzoneDeepLink.Camping("camp-1")

        inbox.offer(deepLink)

        assertEquals(deepLink, inbox.pendingDeepLink.value)

        inbox.consume(deepLink)

        assertNull(inbox.pendingDeepLink.value)
    }
}
