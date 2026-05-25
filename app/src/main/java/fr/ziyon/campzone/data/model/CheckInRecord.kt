package fr.ziyon.campzone.data.model

import fr.ziyon.campzone.data.auth.CampingAgeGroup
import fr.ziyon.campzone.data.auth.UserGender
import java.util.Date

/**
 * `campings/{id}/checkIns/{attendeeId}` (`02-firestore-schema.md` §7.1). Doc ID
 * == [attendeeId] (one per attendee; re-check-in overwrites, full `set`).
 * Decode drops the doc if `campingID`/`attendeeID`/`userID`/`displayName`/
 * `method` is missing. `ageGroup`/`gender`/`photoURL` are omit-when-nil.
 */
data class CheckInRecord(
    val campingId: String,
    val attendeeId: String,
    val userId: String,
    val displayName: String,
    val method: CheckInMethod,
    val checkedInBy: String,
    val church: String = "",
    val preferredLanguage: String = "",
    val ageGroup: CampingAgeGroup? = null,
    val gender: UserGender? = null,
    val photoUrl: String? = null,
    val checkedInAt: Date? = null,
    val updatedAt: Date? = null,
)

internal fun Map<String, Any?>.toCheckInRecordOrNull(documentId: String): CheckInRecord? {
    val campingId = stringValue("campingID") ?: return null
    val userId = stringValue("userID") ?: return null
    val displayName = stringValue("displayName") ?: return null
    val method = CheckInMethod.fromWire(stringValue("method")) ?: return null
    val checkedInBy = stringValue("checkedInBy") ?: return null
    return CheckInRecord(
        campingId = campingId,
        attendeeId = stringValue("attendeeID") ?: documentId,
        userId = userId,
        displayName = displayName,
        method = method,
        checkedInBy = checkedInBy,
        church = rawStringValue("church").orEmpty(),
        preferredLanguage = rawStringValue("preferredLanguage").orEmpty(),
        ageGroup = CampingAgeGroup.fromWire(stringValue("ageGroup")),
        gender = UserGender.fromWire(stringValue("gender")),
        photoUrl = stringValue("photoURL"),
        checkedInAt = dateValue("checkedInAt"),
        updatedAt = dateValue("updatedAt"),
    )
}

internal object CheckInRecordPayload {
    fun checkInPayload(
        record: CheckInRecord,
        serverTimestamp: Any,
    ): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "campingID" to record.campingId,
            "attendeeID" to record.attendeeId,
            "userID" to record.userId,
            "displayName" to record.displayName,
            "church" to record.church,
            "preferredLanguage" to record.preferredLanguage,
            "method" to record.method.wireValue,
            "checkedInBy" to record.checkedInBy,
            "checkedInAt" to serverTimestamp,
        )
        record.ageGroup?.let { payload["ageGroup"] = it.wireValue }
        record.gender?.let { payload["gender"] = it.wireValue }
        record.photoUrl?.trim()?.takeUnless { it.isBlank() }?.let { payload["photoURL"] = it }
        return payload
    }
}
