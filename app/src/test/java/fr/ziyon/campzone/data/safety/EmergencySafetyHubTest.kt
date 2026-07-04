package fr.ziyon.campzone.data.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencySafetyHubTest {
    @Test
    fun fallbackAlwaysProvidesCallableEmergencyServices() {
        val hub = EmergencySafetyHub.fallback("Main gate")

        assertEquals("112", hub.emergencyContacts.first().dialablePhoneNumber)
        assertTrue(hub.emergencyContacts.first().isPrimary)
        assertTrue(hub.emergencyInstructions.contains("Main gate"))
    }

    @Test
    fun normalizationTrimsAndAssignsPrimaryContact() {
        val hub = EmergencySafetyHub(
            emergencyContacts = listOf(
                EmergencyContact(name = "  Camp nurse ", phoneNumber = " +33 1 23 "),
                EmergencyContact(name = "", phoneNumber = ""),
            ),
            emergencyInstructions = "  Call first  ",
            firstAidInfo = "  Tent A  ",
        ).normalized()

        assertEquals(1, hub.emergencyContacts.size)
        assertEquals("Camp nurse", hub.emergencyContacts.single().name)
        assertTrue(hub.emergencyContacts.single().isPrimary)
        assertEquals("+33123", hub.emergencyContacts.single().dialablePhoneNumber)
        assertEquals("Call first", hub.emergencyInstructions)
    }
}
