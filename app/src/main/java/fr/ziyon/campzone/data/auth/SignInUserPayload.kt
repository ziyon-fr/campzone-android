package fr.ziyon.campzone.data.auth

internal data class SignInIdentity(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val providerIds: List<String>,
    val lastAuthProvider: String,
)

internal object SignInUserPayload {
    fun mergePayload(
        identity: SignInIdentity,
        existing: Map<String, Any?>?,
        serverTimestamp: Any,
    ): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "id" to identity.uid,
            "uid" to identity.uid,
            "providerIDs" to identity.providerIds.distinct(),
            "lastAuthProvider" to identity.lastAuthProvider,
            "updatedAt" to serverTimestamp,
        )

        if (existing == null) {
            payload["role"] = "guest"
            payload["createdAt"] = serverTimestamp
            payload["onboardingCompleted"] = false
        } else {
            payload.putIfMissing(existing, "role", "guest")
            payload.putIfMissing(existing, "createdAt", serverTimestamp)
            payload.putIfMissing(existing, "onboardingCompleted", false)
        }

        payload.putIfBlank(existing, "email", identity.email)
        payload.putIfBlank(existing, "displayName", identity.displayName)
        payload.putIfBlank(existing, "photoURL", identity.photoUrl)

        return payload
    }

    private fun MutableMap<String, Any?>.putIfMissing(
        existing: Map<String, Any?>,
        key: String,
        value: Any,
    ) {
        if (!existing.containsKey(key) || existing[key] == null) {
            this[key] = value
        }
    }

    private fun MutableMap<String, Any?>.putIfBlank(
        existing: Map<String, Any?>?,
        key: String,
        value: String?,
    ) {
        val cleanValue = value?.trim()?.takeUnless { it.isBlank() } ?: return
        val existingValue = existing?.get(key) as? String
        if (existing == null || existingValue.isNullOrBlank()) {
            this[key] = cleanValue
        }
    }
}
