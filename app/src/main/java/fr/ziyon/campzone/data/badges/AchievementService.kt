package fr.ziyon.campzone.data.badges

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.model.AchievementCatalog
import fr.ziyon.campzone.data.model.EarnedBadge
import fr.ziyon.campzone.data.model.EarnedBadgePayload
import fr.ziyon.campzone.data.model.toEarnedBadgeOrNull
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

interface AchievementService {
    suspend fun loadEarned(userId: String): List<EarnedBadge>
    suspend fun award(achievementId: String, userId: String, campingId: String?, note: String?): EarnedBadge
    suspend fun award(achievementId: String, userIds: List<String>, campingId: String?, note: String?): List<EarnedBadge>
}

@Singleton
class FirestoreAchievementService @Inject constructor(
    private val db: FirebaseFirestore,
) : AchievementService {
    override suspend fun loadEarned(userId: String): List<EarnedBadge> {
        val snapshot = badgesCollection(userId)
            .orderBy(Field.EarnedAt, Query.Direction.DESCENDING)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            @Suppress("UNCHECKED_CAST")
            (doc.data as? Map<String, Any?>)
                ?.toEarnedBadgeOrNull(doc.id)
                ?.takeIf { AchievementCatalog.achievement(it.id) != null }
        }
    }

    override suspend fun award(
        achievementId: String,
        userId: String,
        campingId: String?,
        note: String?,
    ): EarnedBadge {
        require(AchievementCatalog.achievement(achievementId)?.canBeAwardedManually == true) {
            "This badge is awarded automatically."
        }
        val badge = EarnedBadge(
            id = achievementId,
            userId = userId.trim(),
            earnedAt = Date(),
            campingId = campingId?.trim()?.takeUnless { it.isBlank() },
            note = note?.trim()?.takeUnless { it.isBlank() },
        )
        badgesCollection(badge.userId)
            .document(achievementId)
            .set(EarnedBadgePayload.awardPayload(badge, FieldValue.serverTimestamp()), SetOptions.merge())
            .await()
        return badge
    }

    override suspend fun award(
        achievementId: String,
        userIds: List<String>,
        campingId: String?,
        note: String?,
    ): List<EarnedBadge> {
        require(AchievementCatalog.achievement(achievementId)?.canBeAwardedManually == true) {
            "This badge is awarded automatically."
        }
        val ids = userIds.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
        require(ids.isNotEmpty()) { "Select at least one participant." }
        val badges = ids.map { EarnedBadge(achievementId, it, Date(), campingId, note?.trim()?.takeUnless { n -> n.isBlank() }) }
        val batch = db.batch()
        badges.forEach { badge ->
            batch.set(
                badgesCollection(badge.userId).document(achievementId),
                EarnedBadgePayload.awardPayload(badge, FieldValue.serverTimestamp()),
                SetOptions.merge(),
            )
        }
        batch.commit().await()
        return badges
    }

    private fun badgesCollection(userId: String) =
        db.collection(Collection.Users).document(userId).collection(Collection.Badges)

    private object Collection {
        const val Users = "users"
        const val Badges = "badges"
    }

    private object Field {
        const val EarnedAt = "earnedAt"
    }
}

class FakeAchievementService(
    initialBadges: List<EarnedBadge> = emptyList(),
    var shouldFail: Boolean = false,
) : AchievementService {
    private val badgesByUser = initialBadges.groupBy { it.userId }
        .mapValues { it.value.toMutableList() }
        .toMutableMap()

    private fun check() {
        if (shouldFail) throw IllegalStateException("FakeAchievementService configured to fail.")
    }

    override suspend fun loadEarned(userId: String): List<EarnedBadge> {
        check()
        return badgesByUser[userId].orEmpty()
            .filter { AchievementCatalog.achievement(it.id) != null }
            .sortedByDescending { it.earnedAt ?: Date(0) }
    }

    override suspend fun award(achievementId: String, userId: String, campingId: String?, note: String?): EarnedBadge {
        check()
        require(AchievementCatalog.achievement(achievementId)?.canBeAwardedManually == true) {
            "This badge is awarded automatically."
        }
        val badge = EarnedBadge(achievementId, userId.trim(), Date(), campingId, note?.trim()?.takeUnless { it.isBlank() })
        val list = badgesByUser.getOrPut(badge.userId) { mutableListOf() }
        list.removeAll { it.id == achievementId }
        list.add(badge)
        return badge
    }

    override suspend fun award(achievementId: String, userIds: List<String>, campingId: String?, note: String?): List<EarnedBadge> =
        userIds.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted().map {
            award(achievementId, it, campingId, note)
        }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AchievementBindings {
    @Binds
    @Singleton
    abstract fun bindAchievementService(impl: FirestoreAchievementService): AchievementService
}
