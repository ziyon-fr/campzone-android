package fr.ziyon.campzone.ui.notifications

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.core.notifications.NotificationDeviceRegistrar
import fr.ziyon.campzone.core.permissions.UserRole
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Registers this device's FCM token once per signed-in user (Firestore copy +
 * backend `POST /notifications/devices`). Mirrors iOS `prepareAfterLogin` →
 * `storeDeviceToken`. Failures are logged and non-fatal - the next sign-in
 * (or a token rotation) retries.
 */
@HiltViewModel
class NotificationBootstrapViewModel @Inject constructor(
    private val registrar: NotificationDeviceRegistrar,
) : ViewModel() {

    private var registeredUid: String? = null

    fun registerDevice(uid: String, role: UserRole) {
        if (uid.isBlank() || uid == registeredUid) return
        registeredUid = uid
        viewModelScope.launch {
            runCatching { registrar.register(uid, role) }
                .onFailure { Log.w(TAG, "FCM device registration failed", it) }
        }
    }

    private companion object {
        const val TAG = "NotificationBootstrap"
    }
}
