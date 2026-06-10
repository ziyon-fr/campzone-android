package fr.ziyon.campzone.data.model

import fr.ziyon.campzone.data.auth.CampingAgeGroup
import fr.ziyon.campzone.data.auth.UserGender
import java.util.Date

/**
 * `campings/{campingId}/programAttendance/{programId}/records/{attendeeId}`.
 * The document id is the attendee id so a participant can be present at most
 * once per program. The QR payload is the existing arrival check-in QR; this
 * record only scopes that attendee to a schedule program.
 */
data class ProgramAttendanceRecord(
    val id: String,
    val campingId: String,
    val programId: String,
    val programTitle: String,
    val attendeeId: String,
    val userId: String,
    val displayName: String,
    val church: String = "",
    val ageGroup: CampingAgeGroup? = null,
    val gender: UserGender? = null,
    val preferredLanguage: String = "",
    val photoUrl: String? = null,
    val method: CheckInMethod,
    val checkedInBy: String,
    val checkedInAt: Date = Date(),
    val updatedAt: Date? = null,
)

internal fun Map<String, Any?>.toProgramAttendanceRecordOrNull(documentId: String): ProgramAttendanceRecord? {
    val campingId = stringValue("campingID") ?: return null
    val programId = stringValue("programID") ?: return null
    val attendeeId = stringValue("attendeeID") ?: documentId
    val userId = stringValue("userID") ?: return null
    val displayName = stringValue("displayName") ?: return null
    val method = CheckInMethod.fromWire(stringValue("method")) ?: return null
    val checkedInBy = stringValue("checkedInBy") ?: return null
    return ProgramAttendanceRecord(
        id = documentId,
        campingId = campingId,
        programId = programId,
        programTitle = rawStringValue("programTitle").orEmpty(),
        attendeeId = attendeeId,
        userId = userId,
        displayName = displayName,
        church = rawStringValue("church").orEmpty(),
        ageGroup = CampingAgeGroup.fromWire(stringValue("ageGroup")),
        gender = UserGender.fromWire(stringValue("gender")),
        preferredLanguage = rawStringValue("preferredLanguage").orEmpty(),
        photoUrl = stringValue("photoURL"),
        method = method,
        checkedInBy = checkedInBy,
        checkedInAt = dateValue("checkedInAt") ?: Date(),
        updatedAt = dateValue("updatedAt"),
    )
}

internal object ProgramAttendanceRecordPayload {
    fun attendancePayload(
        record: ProgramAttendanceRecord,
        serverTimestamp: Any,
        includeCreatedAt: Boolean,
    ): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "campingID" to record.campingId,
            "programID" to record.programId,
            "programTitle" to record.programTitle,
            "attendeeID" to record.attendeeId,
            "userID" to record.userId,
            "displayName" to record.displayName,
            "church" to record.church,
            "preferredLanguage" to record.preferredLanguage,
            "method" to record.method.wireValue,
            "checkedInBy" to record.checkedInBy,
            "checkedInAt" to record.checkedInAt,
            "updatedAt" to serverTimestamp,
        )
        if (includeCreatedAt) payload["createdAt"] = serverTimestamp
        record.ageGroup?.let { payload["ageGroup"] = it.wireValue }
        record.gender?.let { payload["gender"] = it.wireValue }
        record.photoUrl?.trim()?.takeUnless { it.isBlank() }?.let { payload["photoURL"] = it }
        return payload
    }
}

sealed interface ProgramAttendanceScanResult {
    data class Success(val record: ProgramAttendanceRecord) : ProgramAttendanceScanResult
    data class AlreadyRecorded(val record: ProgramAttendanceRecord) : ProgramAttendanceScanResult
    data object UnknownAttendee : ProgramAttendanceScanResult
    data object WrongCamping : ProgramAttendanceScanResult
    data object NotApproved : ProgramAttendanceScanResult
    data object Malformed : ProgramAttendanceScanResult
    data object SaveFailed : ProgramAttendanceScanResult

    val isSuccess: Boolean
        get() = this is Success
}
