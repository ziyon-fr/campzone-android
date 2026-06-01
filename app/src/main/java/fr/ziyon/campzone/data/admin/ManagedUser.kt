package fr.ziyon.campzone.data.admin

import com.google.firebase.Timestamp
import fr.ziyon.campzone.core.permissions.UserRole
import java.util.Date

/**
 * Compact directory entry used by the admin role-management screen. Mirrors the
 * iOS `ManagedUser`: only the fields needed to render a row + role picker, so we
 * never load every profile field for users we just list.
 *
 * Decoded **manually** from the raw Firestore map (no POJO auto-mapping per the
 * `07` data contract). `id` falls back to `uid`, then the document id, so docs
 * written before the `id` field landed still decode.
 */
data class ManagedUser(
    val id: String,
    val displayName: String,
    val email: String,
    val church: String,
    val role: UserRole,
    val photoUrl: String?,
    val updatedAt: Date?,
)

internal fun Map<String, Any?>.toManagedUser(documentId: String): ManagedUser {
    val id = stringField("id")
        ?: stringField("uid")
        ?: documentId
    return ManagedUser(
        id = id,
        displayName = stringField("displayName").orEmpty(),
        email = stringField("email").orEmpty(),
        church = stringField("church").orEmpty(),
        role = UserRole.fromWire(this["role"] as? String),
        photoUrl = stringField("photoURL"),
        updatedAt = dateField("updatedAt"),
    )
}

private fun Map<String, Any?>.stringField(key: String): String? =
    (this[key] as? String)?.trim()?.takeUnless { it.isBlank() }

private fun Map<String, Any?>.dateField(key: String): Date? =
    when (val value = this[key]) {
        is Timestamp -> value.toDate()
        is Date -> value
        else -> null
    }
