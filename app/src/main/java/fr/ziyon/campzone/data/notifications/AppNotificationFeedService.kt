package fr.ziyon.campzone.data.notifications

import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.model.AppNotification
import fr.ziyon.campzone.data.model.NotificationTopics
import fr.ziyon.campzone.data.model.isPreferredFeedRepresentativeOver
import fr.ziyon.campzone.data.model.toAppNotificationOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Streams the in-app notification feed (`ziyon_notifications`, `02` §6.5).
 * Reads one bounded snapshot listener per visible topic (derived from the
 * user's saved settings), merges by document id, de-dupes announcement update
 * rows by `announcementID`, filters `concerns`, and emits newest-first.
 * Clients are readers only.
 */
interface AppNotificationFeedService {
    fun observeNotifications(uid: String, role: UserRole): Flow<List<AppNotification>>
}

@Singleton
class FirestoreAppNotificationFeedService @Inject constructor(
    private val db: FirebaseFirestore,
    private val settingsService: NotificationSettingsService,
) : AppNotificationFeedService {

    override fun observeNotifications(uid: String, role: UserRole): Flow<List<AppNotification>> =
        callbackFlow {
            val settings = runCatching { settingsService.load(uid, role) }
                .getOrElse { NotificationSettingsRules.defaultSettings(role) }
            val topics = NotificationTopics.visibleTopics(role, settings, userId = uid)

            if (topics.isEmpty()) {
                trySend(emptyList())
                awaitClose { }
                return@callbackFlow
            }

            val store = NotificationFeedSnapshotStore(
                userId = uid,
                role = role,
                visibleTopics = topics,
            )
            val lock = Any()

            val registrations = topics.map { topic ->
                db.collection(Notifications)
                    .whereEqualTo(TopicField, topic)
                    .limit(PerTopicLimit)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null || snapshot == null) return@addSnapshotListener
                        val merged = synchronized(lock) {
                            val notifications = snapshot.documents.mapNotNull { doc ->
                                @Suppress("UNCHECKED_CAST")
                                val data = doc.data as? Map<String, Any?> ?: return@mapNotNull null
                                data.toAppNotificationOrNull(doc.id)
                            }
                            store.update(topic, notifications)
                        }
                        if (merged != null) trySend(merged)
                    }
            }

            awaitClose { registrations.forEach { it.remove() } }
        }

    private companion object {
        const val Notifications = "ziyon_notifications"
        const val TopicField = "topic"
        const val PerTopicLimit = 200L
    }
}

internal class NotificationFeedSnapshotStore(
    private val userId: String,
    private val role: UserRole,
    private val visibleTopics: Set<String>,
) {
    private val byId = HashMap<String, AppNotification>()
    private val idsByTopic = HashMap<String, Set<String>>()
    private val initializedTopics = mutableSetOf<String>()
    private var lastEmittedNotifications: List<AppNotification> = emptyList()

    fun update(topic: String, notifications: List<AppNotification>): List<AppNotification>? {
        val filtered = notifications
            .filter { it.concerns(userId, role, visibleTopics) }

        val nextIds = filtered.mapTo(mutableSetOf()) { it.id }
        idsByTopic[topic]
            .orEmpty()
            .subtract(nextIds)
            .forEach { byId.remove(it) }

        idsByTopic[topic] = nextIds
        initializedTopics += topic

        filtered.forEach { notification ->
            byId[notification.id] = notification
        }

        if (!initializedTopics.containsAll(visibleTopics)) return null

        val sorted = byId.values.sortedForFeed()
        if (sorted == lastEmittedNotifications) return null

        lastEmittedNotifications = sorted
        return sorted
    }
}

/** In-memory fake for previews/tests. Emits the configured list once. */
class FakeAppNotificationFeedService(
    var notifications: List<AppNotification> = emptyList(),
    var shouldFail: Boolean = false,
) : AppNotificationFeedService {
    override fun observeNotifications(uid: String, role: UserRole): Flow<List<AppNotification>> =
        callbackFlow {
            if (shouldFail) {
                close(IllegalStateException("Fake feed failed."))
                return@callbackFlow
            }
            trySend(notifications.sortedForFeed())
            awaitClose { }
        }
}

private fun Iterable<AppNotification>.sortedForFeed(): List<AppNotification> {
    val byFeedKey = linkedMapOf<String, AppNotification>()
    for (notification in this) {
        val existing = byFeedKey[notification.feedDeduplicationKey]
        if (existing == null || notification.isPreferredFeedRepresentativeOver(existing)) {
            byFeedKey[notification.feedDeduplicationKey] = notification
        }
    }

    return byFeedKey.values.sortedWith(
        compareByDescending<AppNotification> { it.sentAt }
            .thenBy { it.id },
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppNotificationFeedBindings {
    @Binds
    abstract fun bindAppNotificationFeedService(
        impl: FirestoreAppNotificationFeedService,
    ): AppNotificationFeedService
}
