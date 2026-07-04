package fr.ziyon.campzone.data.family

import org.junit.Assert.assertEquals
import org.junit.Test

class FamilyRepositoryTest {
    @Test
    fun backendErrorExtractsNestedMessageInsteadOfShowingRawJson() {
        assertEquals(
            "An internal server error occurred",
            familyBackendErrorMessage(
                """{"success":false,"error":{"code":"INTERNAL_SERVER_ERROR","message":"An internal server error occurred"}}""",
                "Fallback",
            ),
        )
    }

    @Test
    fun backendErrorUsesFallbackForEmptyResponse() {
        assertEquals("Fallback", familyBackendErrorMessage("", "Fallback"))
    }

    @Test
    fun backendErrorNeverShowsUnrecognizedRawJson() {
        assertEquals("Fallback", familyBackendErrorMessage("{\"error\":true}", "Fallback"))
    }
}
