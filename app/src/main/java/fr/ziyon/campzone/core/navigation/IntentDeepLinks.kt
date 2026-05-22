package fr.ziyon.campzone.core.navigation

import android.content.Intent

fun Intent.toCampzoneDeepLink(): CampzoneDeepLink? =
    CampzoneDeepLink.fromCampzoneUrl(dataString) ?: CampzoneDeepLink.fromPayload(extraPayload())

private fun Intent.extraPayload(): Map<String, String?> {
    val extras = extras ?: return emptyMap()

    return extras.keySet().associateWith { key ->
        extras.getString(key)
    }
}
