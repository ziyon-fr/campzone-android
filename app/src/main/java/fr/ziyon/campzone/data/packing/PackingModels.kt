package fr.ziyon.campzone.data.packing

import androidx.annotation.StringRes
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.i18n.StringProvider
import java.util.Date
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

data class PackingItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val sortIndex: Int = 0,
)

data class PackingCategory(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val iconName: String = "checklist",
    val items: List<PackingItem> = emptyList(),
    val sortIndex: Int = 0,
) {
    val sortedItems: List<PackingItem>
        get() = items.sortedWith(compareBy<PackingItem> { it.sortIndex }.thenBy { it.title.lowercase() })
}

data class PackingChecklistTemplate(
    val campingId: String,
    val categories: List<PackingCategory> = emptyList(),
    val updatedAt: Date = Date(0),
    val updatedByUid: String? = null,
    val updatedByName: String? = null,
) {
    val isPublished: Boolean get() = categories.any { it.items.isNotEmpty() }
    val sortedCategories: List<PackingCategory>
        get() = categories
            .sortedWith(compareBy<PackingCategory> { it.sortIndex }.thenBy { it.title.lowercase() })
            .map { it.copy(items = it.sortedItems) }
}

data class PackingCustomItem(
    val id: String = UUID.randomUUID().toString(),
    val categoryId: String? = null,
    val title: String,
    val createdAt: Date = Date(),
)

data class UserPackingProgress(
    val userId: String,
    val campingId: String,
    val checkedItemIds: Set<String> = emptySet(),
    val customItems: List<PackingCustomItem> = emptyList(),
    val personalNotes: String = "",
    val updatedAt: Date = Date(),
)

data class PackingItemRowState(
    val id: String,
    val title: String,
    val isChecked: Boolean,
    val isCustom: Boolean,
)

data class PackingCategorySnapshot(
    val id: String,
    val title: String,
    val iconName: String,
    val rows: List<PackingItemRowState>,
) {
    val totalCount: Int get() = rows.size
    val checkedCount: Int get() = rows.count { it.isChecked }
    val isComplete: Boolean get() = rows.isNotEmpty() && checkedCount == totalCount
}

data class PackingChecklistSnapshot(
    val campingId: String,
    val campName: String?,
    val categories: List<PackingCategorySnapshot>,
    val notes: String,
) {
    val totalItems: Int get() = categories.sumOf { it.totalCount }
    val checkedItems: Int get() = categories.sumOf { it.checkedCount }
    val progress: Float get() = if (totalItems == 0) 0f else checkedItems.toFloat() / totalItems
    val isComplete: Boolean get() = totalItems > 0 && checkedItems == totalItems
    val hasItems: Boolean get() = totalItems > 0
    val allItemIds: Set<String> get() = categories.flatMap { it.rows }.mapTo(linkedSetOf()) { it.id }

    fun shareText(strings: StringProvider): String = buildList {
        add(
            campName?.takeIf { it.isNotBlank() }
                ?.let { strings.get(R.string.packing_share_title_camp, it) }
                ?: strings.get(R.string.packing_title),
        )
        add(strings.getQuantity(R.plurals.packing_items_ready, totalItems, checkedItems, totalItems))
        add("")
        categories.filter { it.rows.isNotEmpty() }.forEach { category ->
            add(category.title.uppercase())
            category.rows.forEach { add("${if (it.isChecked) "☑" else "☐"} ${it.title}") }
            add("")
        }
        if (notes.isNotBlank()) {
            add(strings.get(R.string.packing_personal_notes).uppercase())
            add(notes)
        }
    }.joinToString("\n").trim()

    companion object { const val GeneralCategoryId = "__general__" }
}

data class PackingShareItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val categoryTitle: String? = null,
)

data class PackingShare(
    val id: String = UUID.randomUUID().toString(),
    val campingId: String,
    val campName: String? = null,
    val ownerUid: String,
    val ownerName: String,
    val items: List<PackingShareItem>,
    val createdAt: Date = Date(),
    val expiresAt: Date? = Date(System.currentTimeMillis() + DefaultLifetimeMillis),
) {
    val isExpired: Boolean get() = expiresAt?.before(Date()) == true

    companion object { const val DefaultLifetimeMillis = 90L * 24 * 60 * 60 * 1000 }
}

data class PackingShareImportResult(
    val progress: UserPackingProgress,
    val addedCount: Int,
)

fun packingTitleKey(value: String): String =
    Normalizer.normalize(value.trim(), Normalizer.Form.NFC).lowercase(Locale.ROOT)

fun packingSnapshot(
    template: PackingChecklistTemplate,
    progress: UserPackingProgress,
    campName: String?,
    generalCategoryTitle: String,
): PackingChecklistSnapshot {
    val categories = template.sortedCategories.map { category ->
        val defaultRows = category.sortedItems.map { item ->
            PackingItemRowState(item.id, item.title, item.id in progress.checkedItemIds, false)
        }
        val customRows = progress.customItems
            .filter { it.categoryId == category.id }
            .sortedBy { it.createdAt }
            .map { PackingItemRowState(it.id, it.title, it.id in progress.checkedItemIds, true) }
        PackingCategorySnapshot(category.id, category.title, category.iconName, defaultRows + customRows)
    }.toMutableList()
    val validCategoryIds = template.categories.mapTo(hashSetOf()) { it.id }
    val general = progress.customItems
        .filter { it.categoryId == null || it.categoryId !in validCategoryIds }
        .sortedBy { it.createdAt }
    if (general.isNotEmpty()) {
        categories += PackingCategorySnapshot(
            PackingChecklistSnapshot.GeneralCategoryId,
            generalCategoryTitle,
            "person",
            general.map { PackingItemRowState(it.id, it.title, it.id in progress.checkedItemIds, true) },
        )
    }
    return PackingChecklistSnapshot(template.campingId, campName, categories, progress.personalNotes)
}

object PackingChecklistCatalog {
    private data class Definition(
        val id: String,
        val icon: String,
        @param:StringRes val titleRes: Int,
        val items: List<Pair<String, Int>>,
    )

    fun suggestedCategories(strings: StringProvider): List<PackingCategory> =
        suggestedCategories { id -> strings.get(id) }

    internal fun suggestedCategories(resolve: (Int) -> String): List<PackingCategory> = definitions.mapIndexed { categoryIndex, category ->
        PackingCategory(
            id = category.id,
            title = resolve(category.titleRes),
            iconName = category.icon,
            sortIndex = categoryIndex,
            items = category.items.mapIndexed { itemIndex, item ->
                PackingItem("${category.id}.${item.first}", resolve(item.second), itemIndex)
            },
        )
    }

    private val definitions = listOf(
        Definition("spiritual", "book", R.string.packing_category_spiritual, listOf(
            "bible" to R.string.packing_item_bible, "energy" to R.string.packing_item_energy, "alarm" to R.string.packing_item_alarm,
        )),
        Definition("shelter", "tent", R.string.packing_category_shelter, listOf(
            "tent" to R.string.packing_item_tent, "pad" to R.string.packing_item_sleeping_pad, "blanket" to R.string.packing_item_blanket, "flashlight" to R.string.packing_item_flashlight, "chair" to R.string.packing_item_chair,
        )),
        Definition("food", "food", R.string.packing_category_food, listOf(
            "plate" to R.string.packing_item_plate, "cup" to R.string.packing_item_cup, "mug" to R.string.packing_item_mug, "bowl" to R.string.packing_item_bowl, "cutlery" to R.string.packing_item_cutlery, "thermos" to R.string.packing_item_thermos, "snacks" to R.string.packing_item_snacks,
        )),
        Definition("clothing", "clothing", R.string.packing_category_clothing, listOf(
            "sneakers" to R.string.packing_item_sneakers, "flipflops" to R.string.packing_item_flip_flops, "watershoes" to R.string.packing_item_water_shoes, "swimsuit" to R.string.packing_item_swimsuit, "shorts" to R.string.packing_item_shorts, "activitywear" to R.string.packing_item_activity_clothes, "hat" to R.string.packing_item_hat,
        )),
        Definition("hygiene", "hygiene", R.string.packing_category_hygiene, listOf(
            "towel" to R.string.packing_item_towel, "toiletpaper" to R.string.packing_item_toilet_paper, "sunscreen" to R.string.packing_item_sunscreen, "sunglasses" to R.string.packing_item_sunglasses, "medication" to R.string.packing_item_medicine,
        )),
    )
}
