package fr.ziyon.campzone.data.packing

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

interface PackingChecklistService {
    suspend fun loadTemplate(campingId: String): PackingChecklistTemplate
    suspend fun saveTemplate(template: PackingChecklistTemplate): PackingChecklistTemplate
    suspend fun loadProgress(campingId: String, userId: String): UserPackingProgress
    suspend fun saveProgress(progress: UserPackingProgress): UserPackingProgress
    suspend fun createShare(share: PackingShare): PackingShare
    suspend fun updateShare(share: PackingShare): PackingShare
    suspend fun loadShare(campingId: String, shareId: String): PackingShare?
    suspend fun loadOwnedShares(campingId: String, ownerUid: String): List<PackingShare>
    suspend fun deleteShare(campingId: String, shareId: String)
    suspend fun mergeSharedItems(campingId: String, userId: String, items: List<PackingShareItem>): PackingShareImportResult
}

@Singleton
class FirestorePackingChecklistService @Inject constructor(
    private val firestore: FirebaseFirestore,
) : PackingChecklistService {
    override suspend fun loadTemplate(campingId: String): PackingChecklistTemplate {
        val data = templateDocument(campingId).get().await().data ?: return PackingChecklistTemplate(campingId)
        return PackingChecklistTemplate(
            campingId = data["campingID"] as? String ?: campingId,
            categories = data.mapList("categories").mapIndexedNotNull(::decodeCategory),
            updatedAt = data.date("updatedAt") ?: Date(),
            updatedByUid = data["updatedByUID"] as? String,
            updatedByName = data["updatedByName"] as? String,
        )
    }

    override suspend fun saveTemplate(template: PackingChecklistTemplate): PackingChecklistTemplate {
        val payload = mutableMapOf<String, Any>(
            "campingID" to template.campingId,
            "categories" to template.categories.map(::categoryPayload),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        payload["updatedByUID"] = template.updatedByUid ?: FieldValue.delete()
        payload["updatedByName"] = template.updatedByName ?: FieldValue.delete()
        templateDocument(template.campingId).set(payload, SetOptions.merge()).await()
        return template.copy(updatedAt = Date())
    }

    override suspend fun loadProgress(campingId: String, userId: String): UserPackingProgress {
        val data = progressDocument(campingId, userId).get().await().data
            ?: return UserPackingProgress(userId, campingId)
        return UserPackingProgress(
            userId = data["userID"] as? String ?: userId,
            campingId = data["campingID"] as? String ?: campingId,
            checkedItemIds = (data["checkedItemIDs"] as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty(),
            customItems = data.mapList("customItems").mapNotNull(::decodeCustomItem),
            personalNotes = data["personalNotes"] as? String ?: "",
            updatedAt = data.date("updatedAt") ?: Date(),
        )
    }

    override suspend fun saveProgress(progress: UserPackingProgress): UserPackingProgress {
        progressDocument(progress.campingId, progress.userId).set(
            mapOf(
                "userID" to progress.userId,
                "campingID" to progress.campingId,
                "checkedItemIDs" to progress.checkedItemIds.toList(),
                "customItems" to progress.customItems.map(::customItemPayload),
                "personalNotes" to progress.personalNotes,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
        return progress.copy(updatedAt = Date())
    }

    override suspend fun createShare(share: PackingShare): PackingShare {
        val payload = mutableMapOf<String, Any>(
            "campingID" to share.campingId,
            "ownerUID" to share.ownerUid,
            "ownerName" to share.ownerName,
            "items" to share.items.map { item ->
                buildMap<String, Any> {
                    put("id", item.id)
                    put("title", item.title)
                    item.categoryTitle?.takeIf { it.isNotBlank() }?.let { put("categoryTitle", it) }
                }
            },
            "createdAt" to Timestamp(share.createdAt),
        )
        share.campName?.takeIf { it.isNotBlank() }?.let { payload["campName"] = it }
        share.expiresAt?.let { payload["expiresAt"] = Timestamp(it) }
        shareDocument(share.campingId, share.id).set(payload).await()
        return share
    }

    override suspend fun updateShare(share: PackingShare): PackingShare = createShare(share)

    override suspend fun loadShare(campingId: String, shareId: String): PackingShare? {
        val data = shareDocument(campingId, shareId).get().await().data ?: return null
        return decodeShare(shareId, campingId, data)
    }

    override suspend fun loadOwnedShares(campingId: String, ownerUid: String): List<PackingShare> =
        firestore.collection("campings").document(campingId).collection("packingShares")
            .whereEqualTo("ownerUID", ownerUid)
            .get().await().documents
            .mapNotNull { document -> document.data?.let { decodeShare(document.id, campingId, it) } }
            .sortedByDescending { it.createdAt }

    override suspend fun deleteShare(campingId: String, shareId: String) {
        shareDocument(campingId, shareId).delete().await()
    }

    override suspend fun mergeSharedItems(
        campingId: String,
        userId: String,
        items: List<PackingShareItem>,
    ): PackingShareImportResult {
        val templateReference = templateDocument(campingId)
        val progressReference = progressDocument(campingId, userId)
        val addedCount = firestore.runTransaction { transaction ->
            val templateData = transaction.get(templateReference).data.orEmpty()
            val progressData = transaction.get(progressReference).data.orEmpty()

            val categories = templateData.mapList("categories")
            val existing = categories
                .flatMap { it.mapList("items") }
                .mapNotNull { it["title"] as? String }
                .mapTo(hashSetOf(), ::packingTitleKey)
            val categoryIdsByTitle = categories.mapNotNull { category ->
                val title = category["title"] as? String ?: return@mapNotNull null
                val id = category["id"] as? String ?: return@mapNotNull null
                packingTitleKey(title) to id
            }.toMap()
            val customItems = progressData.mapList("customItems").toMutableList()
            customItems.mapNotNull { it["title"] as? String }.mapTo(existing, ::packingTitleKey)

            var added = 0
            items.forEach { item ->
                if (!existing.add(packingTitleKey(item.title))) return@forEach
                customItems += buildMap<String, Any?> {
                    put("id", UUID.randomUUID().toString())
                    put("title", item.title)
                    put("createdAt", Timestamp.now())
                    item.categoryTitle
                        ?.let(::packingTitleKey)
                        ?.let(categoryIdsByTitle::get)
                        ?.let { put("categoryID", it) }
                }
                added += 1
            }

            if (added > 0) transaction.set(
                progressReference,
                mapOf(
                    "userID" to userId,
                    "campingID" to campingId,
                    "customItems" to customItems,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            added
        }.await()
        return PackingShareImportResult(loadProgress(campingId, userId), addedCount)
    }

    private fun decodeShare(shareId: String, campingId: String, data: Map<String, Any?>): PackingShare {
        return PackingShare(
            id = shareId,
            campingId = data["campingID"] as? String ?: campingId,
            campName = data["campName"] as? String,
            ownerUid = data["ownerUID"] as? String ?: "",
            ownerName = data["ownerName"] as? String ?: "",
            items = data.mapList("items").mapNotNull { raw ->
                val title = raw["title"] as? String ?: return@mapNotNull null
                PackingShareItem(raw["id"] as? String ?: UUID.randomUUID().toString(), title, raw["categoryTitle"] as? String)
            },
            createdAt = data.date("createdAt") ?: Date(),
            expiresAt = data.date("expiresAt"),
        )
    }

    private fun categoryPayload(category: PackingCategory): Map<String, Any> = mapOf(
        "id" to category.id,
        "title" to category.title,
        "iconName" to category.iconName,
        "sortIndex" to category.sortIndex,
        "items" to category.items.map { item -> mapOf("id" to item.id, "title" to item.title, "sortIndex" to item.sortIndex) },
    )

    private fun decodeCategory(index: Int, raw: Map<String, Any?>): PackingCategory? {
        val title = raw["title"] as? String ?: return null
        return PackingCategory(
            id = raw["id"] as? String ?: UUID.randomUUID().toString(),
            title = title,
            iconName = raw["iconName"] as? String ?: "checklist",
            items = raw.mapList("items").mapIndexedNotNull { itemIndex, item ->
                val itemTitle = item["title"] as? String ?: return@mapIndexedNotNull null
                PackingItem(item["id"] as? String ?: UUID.randomUUID().toString(), itemTitle, item.int("sortIndex") ?: itemIndex)
            },
            sortIndex = raw.int("sortIndex") ?: index,
        )
    }

    private fun customItemPayload(item: PackingCustomItem): Map<String, Any> = buildMap {
        put("id", item.id)
        put("title", item.title)
        put("createdAt", Timestamp(item.createdAt))
        item.categoryId?.takeIf { it.isNotBlank() }?.let { put("categoryID", it) }
    }

    private fun decodeCustomItem(raw: Map<String, Any?>): PackingCustomItem? {
        val title = raw["title"] as? String ?: return null
        return PackingCustomItem(
            id = raw["id"] as? String ?: UUID.randomUUID().toString(),
            categoryId = raw["categoryID"] as? String,
            title = title,
            createdAt = raw.date("createdAt") ?: Date(),
        )
    }

    private fun templateDocument(campingId: String) = firestore.collection("campings").document(campingId)
        .collection("packingChecklistTemplate").document("config")
    private fun progressDocument(campingId: String, userId: String) = firestore.collection("campings").document(campingId)
        .collection("packingChecklists").document(userId)
    private fun shareDocument(campingId: String, shareId: String) = firestore.collection("campings").document(campingId)
        .collection("packingShares").document(shareId)
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.mapList(key: String): List<Map<String, Any?>> =
    (this[key] as? List<*>)?.mapNotNull { it as? Map<String, Any?> }.orEmpty()

private fun Map<String, Any?>.date(key: String): Date? = when (val value = this[key]) {
    is Timestamp -> value.toDate()
    is Date -> value
    else -> null
}

private fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Number)?.toInt()

@Module
@InstallIn(SingletonComponent::class)
abstract class PackingChecklistBindings {
    @Binds abstract fun bindPackingChecklistService(implementation: FirestorePackingChecklistService): PackingChecklistService
}
