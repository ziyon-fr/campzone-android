package fr.ziyon.campzone.data.packing

import fr.ziyon.campzone.testing.FakeStringProvider
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class PackingModelsTest {
    private val strings = FakeStringProvider()

    @Test fun suggestedCatalogHasStableStructure() {
        val categories = PackingChecklistCatalog.suggestedCategories(strings)
        assertEquals(listOf("spiritual", "shelter", "food", "clothing", "hygiene"), categories.map { it.id })
        assertEquals(27, categories.sumOf { it.items.size })
        assertEquals("spiritual.bible", categories.first().items.first().id)
    }

    @Test fun snapshotMergesTemplateProgressAndCustomItems() {
        val template = PackingChecklistTemplate("c1", PackingChecklistCatalog.suggestedCategories(strings))
        val progress = UserPackingProgress(
            userId = "u1",
            campingId = "c1",
            checkedItemIds = setOf("spiritual.bible", "custom-general"),
            customItems = listOf(
                PackingCustomItem("custom-food", "food", "Coffee"),
                PackingCustomItem("custom-general", null, "Power bank"),
            ),
        )
        val snapshot = packingSnapshot(template, progress, "Camp", strings.get(fr.ziyon.campzone.R.string.packing_my_items))
        assertEquals(29, snapshot.totalItems)
        assertEquals(2, snapshot.checkedItems)
        assertEquals(PackingChecklistSnapshot.GeneralCategoryId, snapshot.categories.last().id)
        assertEquals(8, snapshot.categories.first { it.id == "food" }.totalCount)
    }

    @Test fun shareTextContainsProgressAndItems() {
        val template = PackingChecklistTemplate("c1", PackingChecklistCatalog.suggestedCategories(strings))
        val snapshot = packingSnapshot(template, UserPackingProgress("u1", "c1", setOf("spiritual.bible")), "Camp", strings.get(fr.ziyon.campzone.R.string.packing_my_items))
        assertTrue(snapshot.shareText(strings).contains("1 of 27"))
        assertTrue(snapshot.shareText(strings).contains("Bible"))
    }

    @Test fun orphanedCustomItemsRemainVisibleInMyItems() {
        val template = PackingChecklistTemplate(
            campingId = "c1",
            categories = listOf(PackingCategory(id = "still-here", title = "Current")),
        )
        val progress = UserPackingProgress(
            userId = "u1",
            campingId = "c1",
            customItems = listOf(PackingCustomItem(id = "orphan", categoryId = "deleted", title = "Power bank")),
        )

        val snapshot = packingSnapshot(template, progress, "Camp", "My items")

        assertEquals(listOf("Power bank"), snapshot.categories.last().rows.map { it.title })
        assertEquals(PackingChecklistSnapshot.GeneralCategoryId, snapshot.categories.last().id)
    }

    @Test fun titleComparisonIsUnicodeAndCaseStable() {
        assertEquals(packingTitleKey("  CAFÉ "), packingTitleKey("Cafe\u0301"))
    }
}
