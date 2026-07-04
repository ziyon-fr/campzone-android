package fr.ziyon.campzone.data.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AllergyTest {
    @Test
    fun presetWireValuesMatchIos() {
        assertEquals(
            listOf(
                "peanuts", "tree_nuts", "milk", "eggs", "gluten", "soy", "fish",
                "shellfish", "sesame", "pollen", "dust_mites", "pet_dander",
                "insect_stings", "latex", "medication", "mold",
            ),
            CommonAllergy.entries.map(CommonAllergy::wireValue),
        )
    }

    @Test
    fun customInputNormalizesPresetsAndTrimsFreeText() {
        val labels = mapOf(CommonAllergy.TreeNuts to "Tree nuts")
        val displayName: (CommonAllergy) -> String = { labels[it] ?: it.wireValue }

        assertEquals("tree_nuts", AllergyFormatter.normalizedToken("TREE_NUTS", displayName))
        assertEquals("tree_nuts", AllergyFormatter.normalizedToken("Tree nuts", displayName))
        assertEquals("Kiwi", AllergyFormatter.normalizedToken("  Kiwi  ", displayName))
        assertNull(AllergyFormatter.normalizedToken("   ", displayName))
    }

    @Test
    fun cleaningRemovesEmptyAndCaseInsensitiveDuplicatesInSelectionOrder() {
        assertEquals(
            listOf("peanuts", "Kiwi", "milk"),
            AllergyFormatter.cleaned(listOf(" peanuts ", "Kiwi", "kiwi", "", " milk ")),
        )
    }

    @Test
    fun presetToggleKeepsMultipleSelectionsAndRemovesSelectedValue() {
        val first = AllergyFormatter.toggledPreset(emptyList(), CommonAllergy.Peanuts, "Peanuts")
        val multiple = AllergyFormatter.toggledPreset(first, CommonAllergy.Shellfish, "Shellfish")
        val removed = AllergyFormatter.toggledPreset(multiple, CommonAllergy.Peanuts, "Peanuts")

        assertEquals(listOf("peanuts", "shellfish"), multiple)
        assertEquals(listOf("shellfish"), removed)
    }

    @Test
    fun presetToggleRemovesLegacyLocalizedLabel() {
        assertEquals(
            emptyList<String>(),
            AllergyFormatter.toggledPreset(listOf("Marisco"), CommonAllergy.Shellfish, "Marisco"),
        )
    }
}
