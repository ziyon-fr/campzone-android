package fr.ziyon.campzone.data.model

import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.CampingAgeGroup
import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.family.ChildParticipant
import java.util.Date

/**
 * A participant that can be registered for a camping. Either the signed-in user
 * (selfParticipant) or one of their registered children.
 * Mirrors iOS `CampingRegistrationParticipant`.
 */
data class RegistrationParticipant(
    val id: String,
    val userId: String,
    val displayName: String,
    val photoUrl: String?,
    val age: Int,
    val church: String,
    val gender: UserGender?,
    val preferredLanguage: String,
    val languages: List<String>,
    val kind: RegistrationParticipantKind,
    val guardianId: String?,
    val guardianConsentAt: Date?,
    val emergencyContactName: String,
    val emergencyContactPhone: String,
    val medicalNotes: String,
) {
    val ageGroup: CampingAgeGroup
        get() = CampingAgeGroup.fromAge(age)

    companion object {
        fun from(user: AuthenticatedUser): RegistrationParticipant = RegistrationParticipant(
            id = user.uid,
            userId = user.uid,
            displayName = user.preferredDisplayName,
            photoUrl = user.photoUrl,
            age = user.age ?: 0,
            church = user.church,
            gender = user.gender,
            preferredLanguage = user.preferredLanguage,
            languages = user.preferredLanguage.takeUnless { it.isBlank() }?.let(::listOf).orEmpty(),
            kind = RegistrationParticipantKind.SelfParticipant,
            guardianId = null,
            guardianConsentAt = null,
            emergencyContactName = "",
            emergencyContactPhone = "",
            medicalNotes = "",
        )

        fun from(child: ChildParticipant): RegistrationParticipant = RegistrationParticipant(
            id = child.id,
            userId = child.id,
            displayName = child.displayName,
            photoUrl = child.photoUrl,
            age = child.age,
            church = child.church,
            gender = child.gender,
            preferredLanguage = child.preferredLanguage,
            languages = child.languages,
            kind = RegistrationParticipantKind.Child,
            guardianId = child.guardianId,
            guardianConsentAt = child.guardianConsentAt,
            emergencyContactName = child.emergencyContactName,
            emergencyContactPhone = child.emergencyContactPhone,
            medicalNotes = child.medicalNotes,
        )
    }
}

/**
 * A completed registration intent for one participant.
 * Mirrors iOS `CampingRegistrationSubmission`.
 */
data class RegistrationSubmission(
    val participant: RegistrationParticipant,
    val transportationChoice: TransportationChoice,
    val transportationOptionId: String? = null,
    val transportationOptionName: String? = null,
)
