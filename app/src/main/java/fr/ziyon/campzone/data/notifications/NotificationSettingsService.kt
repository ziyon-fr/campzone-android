package fr.ziyon.campzone.data.notifications

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.model.NotificationPrefsPayload
import fr.ziyon.campzone.data.model.NotificationSettings
import fr.ziyon.campzone.data.model.toNotificationSettings
import fr.ziyon.campzone.data.notifications.NotificationSettingsRules.normalizedFor
import fr.ziyon.campzone.data.notifications.NotificationSettingsRules.sanitized
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Reads/writes `users/{uid}/notificationSettings/default` (`02` §2.3) and
 * keeps the backend in sync (`POST /notifications/settings`). Both stores must
 * be written - the API call is what re-subscribes the device's FCM topics.
 */
interface NotificationSettingsService {
    suspend fun load(uid: String, role: UserRole): NotificationSettings
    suspend fun save(settings: NotificationSettings, uid: String, role: UserRole): NotificationSettings
}

@Singleton
class FirestoreNotificationSettingsService @Inject constructor(
    private val db: FirebaseFirestore,
    private val api: NotificationApi,
) : NotificationSettingsService {
    private val backendSyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun load(uid: String, role: UserRole): NotificationSettings {
        val snapshot = settingsDoc(uid).get().await()
        @Suppress("UNCHECKED_CAST")
        val data = snapshot.data as? Map<String, Any?>
            ?: return NotificationSettingsRules.defaultSettings(role)

        val decoded = data.toNotificationSettings()
        // Empty role set defaults to the user's own role (matches iOS).
        val withRoleDefault = if (decoded.subscribedRoles.isEmpty()) {
            decoded.copy(subscribedRoles = listOf(role))
        } else {
            decoded
        }
        return withRoleDefault.normalizedFor(role)
    }

    override suspend fun save(
        settings: NotificationSettings,
        uid: String,
        role: UserRole,
    ): NotificationSettings {
        val sanitized = settings.normalizedFor(role).sanitized()

        settingsDoc(uid)
            .set(
                NotificationPrefsPayload.settingsPayload(sanitized, FieldValue.serverTimestamp()),
                SetOptions.merge(),
            )
            .await()

        backendSyncScope.launch {
            runCatching { api.syncSettings(sanitized, uid) }
        }
        return sanitized
    }

    private fun settingsDoc(uid: String) =
        db.collection("users")
            .document(uid)
            .collection("notificationSettings")
            .document("default")
}

/** In-memory fake for previews/tests. */
class FakeNotificationSettingsService(
    initial: NotificationSettings? = null,
    var shouldFail: Boolean = false,
) : NotificationSettingsService {
    var stored: NotificationSettings? = initial

    override suspend fun load(uid: String, role: UserRole): NotificationSettings {
        if (shouldFail) throw IllegalStateException("Fake settings load failed.")
        val loaded = (stored ?: NotificationSettingsRules.defaultSettings(role)).normalizedFor(role)
        stored = loaded
        return loaded
    }

    override suspend fun save(
        settings: NotificationSettings,
        uid: String,
        role: UserRole,
    ): NotificationSettings {
        if (shouldFail) throw IllegalStateException("Fake settings save failed.")
        val sanitized = settings.normalizedFor(role).sanitized()
        stored = sanitized
        return sanitized
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationSettingsBindings {
    @Binds
    abstract fun bindNotificationSettingsService(
        impl: FirestoreNotificationSettingsService,
    ): NotificationSettingsService
}
