package fr.ziyon.campzone.core.notifications

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import fr.ziyon.campzone.MainActivity
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.permissions.UserRole
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Receives FCM token rotations and data/notification messages. Token rotation
 * is mirrored into both stores via [NotificationDeviceRegistrar]; incoming
 * messages are surfaced as a channel notification whose tap re-opens
 * [MainActivity] carrying the FCM `data` map as intent extras (parsed by
 * `IntentDeepLinks.toCampzoneDeepLink` → `CampzoneDeepLink.fromPayload`).
 */
@AndroidEntryPoint
class CampzoneMessagingService : FirebaseMessagingService() {

    @Inject lateinit var registrar: NotificationDeviceRegistrar
    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var db: FirebaseFirestore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = auth.currentUser?.uid ?: return
        scope.launch {
            runCatching {
                val role = currentRole(uid)
                registrar.storeToken(token, uid, role)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val type = data["type"] ?: data["kind"]

        val title = message.notification?.title
            ?: data["title"]
            ?: getString(R.string.notif_default_title)
        val body = message.notification?.body
            ?: data["body"]
            ?: ""

        val channelId = NotificationChannels.channelIdFor(type)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Forward the FCM data map so the activity can resolve the deep link.
            data.forEach { (key, value) -> putExtra(key, value) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            (data["messageID"] ?: data["messageId"] ?: System.currentTimeMillis().toString()).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            runCatching {
                NotificationManagerCompat.from(this).notify(
                    System.currentTimeMillis().toInt(),
                    notification,
                )
            }
        }
    }

    private suspend fun currentRole(uid: String): UserRole = runCatching {
        val snapshot = db.collection("users").document(uid).get().await()
        UserRole.fromWire(snapshot.getString("role"))
    }.getOrDefault(UserRole.Guest)
}
