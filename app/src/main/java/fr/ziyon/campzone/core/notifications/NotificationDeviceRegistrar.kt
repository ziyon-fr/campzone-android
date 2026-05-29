package fr.ziyon.campzone.core.notifications

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import fr.ziyon.campzone.BuildConfig
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.model.NotificationPrefsPayload
import fr.ziyon.campzone.data.model.NotificationToken
import fr.ziyon.campzone.data.notifications.NotificationApi
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Registers (or refreshes) the device's FCM token in **both** stores
 * (`04-backend-api.md` §3.1): the client-direct Firestore copy at
 * `users/{uid}/notificationTokens/{sha256hex}` and the backend API
 * (`POST /notifications/devices`) that drives the actual FCM topic
 * subscription. Mirrors iOS `CampzoneNotificationService.storeDeviceToken`.
 */
@Singleton
class NotificationDeviceRegistrar @Inject constructor(
    private val db: FirebaseFirestore,
    private val api: NotificationApi,
    private val messaging: FirebaseMessaging,
) {
    /** Fetches the current FCM token, then persists it for [uid]. */
    suspend fun register(uid: String, role: UserRole) {
        if (uid.isBlank()) return
        val token = messaging.token.await()?.takeUnless { it.isBlank() } ?: return
        storeToken(token, uid, role)
    }

    /**
     * Persists a known [token] (e.g. from `onNewToken` token rotation) for
     * [uid] into both stores.
     */
    suspend fun storeToken(token: String, uid: String, role: UserRole) {
        if (uid.isBlank() || token.isBlank()) return

        val localeIdentifier = Locale.getDefault().toString().ifBlank { "en_US" }
        val appVersion = BuildConfig.VERSION_NAME.ifBlank { "unknown" }

        val document = db.collection("users")
            .document(uid)
            .collection("notificationTokens")
            .document(documentId(token))

        val exists = document.get().await().exists()
        val payload = NotificationPrefsPayload.tokenPayload(
            token = NotificationToken(
                token = token,
                role = role,
                localeIdentifier = localeIdentifier,
                appVersion = appVersion,
            ),
            serverTimestamp = FieldValue.serverTimestamp(),
            includeCreatedAt = !exists,
        )
        document.set(payload, com.google.firebase.firestore.SetOptions.merge()).await()

        api.registerDevice(
            token = token,
            roleRawValue = role.rawValue,
            localeIdentifier = localeIdentifier,
            appVersion = appVersion,
        )
    }

    /** Lowercase hex SHA-256 of the raw token (64 chars), matching iOS. */
    private fun documentId(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
