package fr.ziyon.campzone.data.model

import com.google.firebase.Timestamp
import java.util.Date

/**
 * `users/{uid}/blockedUsers/{blockedUid}` (`02-firestore-schema.md` §2.4). Doc
 * ID == blocked uid. Block = `merge:true`; unblock = hard delete. `blockedAt` is
 * read **strictly as Timestamp** (no Date fallback); `displayName` falls back to
 * the doc ID.
 */
data class BlockedUser(
    val blockedUserId: String,
    val displayName: String,
    val blockedAt: Date? = null,
)

internal fun Map<String, Any?>.toBlockedUser(documentId: String): BlockedUser =
    BlockedUser(
        blockedUserId = stringValue("blockedUserID") ?: documentId,
        displayName = stringValue("displayName") ?: documentId,
        blockedAt = (this["blockedAt"] as? Timestamp)?.toDate(), // Timestamp-only, no Date fallback
    )

internal object BlockedUserPayload {
    fun blockPayload(
        blockedUser: BlockedUser,
        serverTimestamp: Any,
    ): Map<String, Any?> =
        linkedMapOf(
            "blockedUserID" to blockedUser.blockedUserId,
            "displayName" to blockedUser.displayName,
            "blockedAt" to serverTimestamp,
        )
}
