package fr.ziyon.campzone.data.model

import fr.ziyon.campzone.data.auth.UserGender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LodgingUnitTest {

    @Test
    fun decodeReadsIosWireKeysAndEnums() {
        val data: Map<String, Any?> = mapOf(
            "campingID" to "camp-1",
            "name" to "Lakeview Cabin",
            "kind" to "cabin",
            "capacity" to 6L, // Firestore returns integers as Long
            "genderPolicy" to "female",
            "notes" to "Step-free access",
            "occupantIDs" to listOf("a1", "a2"),
        )

        val unit = data.toLodgingUnitOrNull("unit-1")

        assertEquals("unit-1", unit.id)
        assertEquals("camp-1", unit.campingId)
        assertEquals("Lakeview Cabin", unit.name)
        assertEquals(LodgingKind.Cabin, unit.kind)
        assertEquals(6, unit.capacity)
        assertEquals(LodgingGenderPolicy.Female, unit.genderPolicy)
        assertEquals("Step-free access", unit.notes)
        assertEquals(listOf("a1", "a2"), unit.occupantIds)
        assertEquals(2, unit.occupancy)
        assertEquals(4, unit.availableSpots)
        assertEquals("2/6", unit.occupancyText)
        assertFalse(unit.isFull)
        assertTrue(unit.contains("a1"))
    }

    @Test
    fun decodeFallsBackToDefaults() {
        val unit = emptyMap<String, Any?>().toLodgingUnitOrNull("u")

        assertEquals("", unit.campingId)
        assertEquals(LodgingKind.Tent, unit.kind)
        assertEquals(4, unit.capacity)
        assertEquals(LodgingGenderPolicy.Any, unit.genderPolicy)
        assertEquals("", unit.notes)
        assertTrue(unit.occupantIds.isEmpty())
    }

    @Test
    fun capacityIsClampedToAtLeastOne() {
        val unit = mapOf<String, Any?>("capacity" to 0L).toLodgingUnitOrNull("u")
        assertEquals(1, unit.capacity)
    }

    @Test
    fun fullUnitHasNoAvailableSpots() {
        val unit = LodgingUnit(
            id = "u",
            campingId = "c",
            name = "Tent",
            capacity = 2,
            occupantIds = listOf("a", "b"),
        )
        assertTrue(unit.isFull)
        assertEquals(0, unit.availableSpots)
    }

    @Test
    fun genderPolicyEligibility() {
        assertTrue(LodgingGenderPolicy.Male.accepts(UserGender.Male))
        assertFalse(LodgingGenderPolicy.Male.accepts(UserGender.Female))
        assertTrue(LodgingGenderPolicy.Female.accepts(UserGender.Female))
        assertFalse(LodgingGenderPolicy.Female.accepts(UserGender.Male))
        // Family + mixed accept anyone (family grouping happens elsewhere).
        assertTrue(LodgingGenderPolicy.Family.accepts(UserGender.Female))
        assertTrue(LodgingGenderPolicy.Any.accepts(null))
    }

    @Test
    fun unknownEnumStringsFallBackToDefaults() {
        val unit = mapOf<String, Any?>("kind" to "yurt", "genderPolicy" to "robot").toLodgingUnitOrNull("u")
        assertEquals(LodgingKind.Tent, unit.kind)
        assertEquals(LodgingGenderPolicy.Any, unit.genderPolicy)
    }
}
