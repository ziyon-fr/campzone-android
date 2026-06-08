package fr.ziyon.campzone.data.model

import fr.ziyon.campzone.data.auth.CampingAgeGroup
import fr.ziyon.campzone.data.auth.UserGender
import java.util.Date

/**
 * `campings/{id}/registrations/{attendeeId}` (`02-firestore-schema.md` §3.6).
 * Doc ID == [id] == participant id (the user uid for self, the child id for a
 * child). Both `userID` and a duplicate `uid` are written.
 */
data class CampingAttendee(
    val id: String,
    val userId: String,
    val displayName: String,
    val church: String,
    val age: Int,
    val languages: List<String>,
    val registrationStatus: RegistrationApprovalStatus,
    val gender: UserGender? = null,
    val preferredLanguage: String = "",
    val participantKind: RegistrationParticipantKind = RegistrationParticipantKind.SelfParticipant,
    val guardianId: String? = null,
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = "",
    val medicalNotes: String = "",
    val guardianConsentAt: Date? = null,
    val transportationChoice: TransportationChoice = TransportationChoice.OwnCar,
    val transportationBookingId: String? = null,
    val transportationOptionId: String? = null,
    val transportationOptionName: String? = null,
    val transportationMode: TransportationMode? = null,
    val vehicleId: String? = null,
    val isDriver: Boolean = false,
    val needsTransportHelp: Boolean = false,
    val transportationNotes: String? = null,
    val paymentStatus: TransportationPaymentStatus = TransportationPaymentStatus.Unpaid,
    val paymentReference: String? = null,
    val paymentUpdatedAt: Date? = null,
    val approvedVia: String? = null,
    val approvedAt: Date? = null,
    val photoUrl: String? = null,
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
) {
    val ageGroup: CampingAgeGroup
        get() = CampingAgeGroup.fromAge(age)

    val isChild: Boolean
        get() = participantKind == RegistrationParticipantKind.Child
}

internal fun Map<String, Any?>.toCampingAttendeeOrNull(documentId: String): CampingAttendee? {
    val displayName = stringValue("displayName") ?: return null
    val church = stringValue("church") ?: return null
    val age = intValue("age") ?: return null
    val registrationStatus = stringValue("registrationStatus") ?: return null
    val userId = stringValue("userID") ?: stringValue("uid") ?: documentId

    return CampingAttendee(
        id = stringValue("id") ?: documentId,
        userId = userId,
        displayName = displayName,
        church = church,
        age = age,
        languages = stringListValue("languages"),
        registrationStatus = RegistrationApprovalStatus.fromWire(registrationStatus),
        gender = UserGender.fromWire(stringValue("gender")),
        preferredLanguage = rawStringValue("preferredLanguage").orEmpty(),
        participantKind = RegistrationParticipantKind.fromWire(stringValue("participantKind")),
        guardianId = stringValue("guardianID"),
        emergencyContactName = rawStringValue("emergencyContactName").orEmpty(),
        emergencyContactPhone = rawStringValue("emergencyContactPhone").orEmpty(),
        medicalNotes = rawStringValue("medicalNotes").orEmpty(),
        guardianConsentAt = dateValue("guardianConsentAt"),
        transportationChoice = TransportationChoice.fromWire(stringValue("transportationChoice")),
        transportationBookingId = stringValue("transportationBookingID"),
        transportationOptionId = stringValue("transportationOptionID"),
        transportationOptionName = stringValue("transportationOptionName"),
        transportationMode = stringValue("transportationMode")?.let(TransportationMode::fromWire),
        vehicleId = stringValue("vehicleID"),
        isDriver = boolValue("isDriver") ?: false,
        needsTransportHelp = boolValue("needsTransportHelp") ?: false,
        transportationNotes = stringValue("transportationNotes"),
        paymentStatus = TransportationPaymentStatus.fromWire(stringValue("paymentStatus")),
        paymentReference = stringValue("paymentReference"),
        paymentUpdatedAt = dateValue("paymentUpdatedAt"),
        approvedVia = stringValue("approvedVia"),
        approvedAt = dateValue("approvedAt"),
        photoUrl = stringValue("photoURL"),
        createdAt = dateValue("createdAt"),
        updatedAt = dateValue("updatedAt"),
    )
}

/**
 * Registration write payloads. The create payload **never** writes
 * `paymentStatus` (the backend settles it); the status-update payload writes
 * only `{ registrationStatus, updatedAt }` (RBAC-enforced).
 */
internal object CampingAttendeePayload {

    fun registrationPayload(
        attendee: CampingAttendee,
        serverTimestamp: Any,
        includeCreatedAt: Boolean,
    ): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "id" to attendee.id,
            "userID" to attendee.userId,
            "uid" to attendee.userId,
            "displayName" to attendee.displayName.trim(),
            "church" to attendee.church.trim(),
            "age" to attendee.age,
            "ageGroup" to attendee.ageGroup.wireValue,
            "preferredLanguage" to attendee.preferredLanguage.trim(),
            "languages" to attendee.languages,
            "participantKind" to attendee.participantKind.wireValue,
            "emergencyContactName" to attendee.emergencyContactName.trim(),
            "emergencyContactPhone" to attendee.emergencyContactPhone.trim(),
            "medicalNotes" to attendee.medicalNotes.trim(),
            "transportationChoice" to attendee.transportationChoice.wireValue,
            "registrationStatus" to attendee.registrationStatus.wireValue,
            "updatedAt" to serverTimestamp,
        )

        attendee.gender?.let { payload["gender"] = it.wireValue }
        attendee.guardianId?.trim()?.takeUnless { it.isBlank() }?.let { payload["guardianID"] = it }
        attendee.guardianConsentAt?.let { payload["guardianConsentAt"] = it }
        attendee.transportationBookingId?.trim()?.takeUnless { it.isBlank() }
            ?.let { payload["transportationBookingID"] = it }
        attendee.transportationOptionId?.trim()?.takeUnless { it.isBlank() }
            ?.let { payload["transportationOptionID"] = it }
        attendee.transportationOptionName?.trim()?.takeUnless { it.isBlank() }
            ?.let { payload["transportationOptionName"] = it }
        attendee.transportationMode?.let { payload["transportationMode"] = it.wireValue }
        attendee.vehicleId?.trim()?.takeUnless { it.isBlank() }?.let { payload["vehicleID"] = it }
        payload["isDriver"] = attendee.isDriver
        payload["needsTransportHelp"] = attendee.needsTransportHelp
        attendee.transportationNotes?.trim()?.takeUnless { it.isBlank() }
            ?.let { payload["transportationNotes"] = it }
        attendee.photoUrl?.trim()?.takeUnless { it.isBlank() }?.let { payload["photoURL"] = it }

        if (includeCreatedAt) {
            payload["createdAt"] = serverTimestamp
        }
        return payload
    }

    fun statusUpdatePayload(
        status: RegistrationApprovalStatus,
        serverTimestamp: Any,
    ): Map<String, Any?> =
        linkedMapOf(
            "registrationStatus" to status.wireValue,
            "updatedAt" to serverTimestamp,
        )
}
