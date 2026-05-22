package fr.ziyon.campzone.core.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeepLinkInbox {
    private val _pendingDeepLink = MutableStateFlow<CampzoneDeepLink?>(null)
    val pendingDeepLink: StateFlow<CampzoneDeepLink?> = _pendingDeepLink.asStateFlow()

    fun offer(deepLink: CampzoneDeepLink?) {
        if (deepLink != null) {
            _pendingDeepLink.value = deepLink
        }
    }

    fun consume(deepLink: CampzoneDeepLink) {
        if (_pendingDeepLink.value == deepLink) {
            _pendingDeepLink.value = null
        }
    }
}
