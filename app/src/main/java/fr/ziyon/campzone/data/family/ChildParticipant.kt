package fr.ziyon.campzone.data.family

import com.google.firebase.Timestamp
import fr.ziyon.campzone.data.auth.CampingAgeGroup
import fr.ziyon.campzone.data.auth.UserGender
import java.util.Date

/**
 * A family participant a guardian can register for campings. Wire shape mirrors
 * `users/{uid}/children/{childId}` (`02-firestore-schema.md` §2.1) and the iOS
 * `ChildParticipant`. Mapped manually (no POJO auto-mapping) so the
 * delete-when-nil / derived-field rules are explicit.
 */
data class ChildParticipant(
    val id: String,
    val guardianId: String,
    val displayName: String,
    val age: Int,
    val gender: UserGender,
    val church: String,
    val preferredLanguage: String,
    val emergencyContactName: String,
    val emergencyContactPhone: String,
    val medicalNotes: String = "",
    val allergies: List<String> = emptyList(),
    val relationship: FamilyRelationship = FamilyRelationship.Parent,
    val customRelationshipLabel: String = "",
    val guardianConsentAt: Date? = null,
    val photoUrl: String? = null,
    val photoPublicId: String? = null,
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
) {
    val ageGroup: CampingAgeGroup
        get() = CampingAgeGroup.fromAge(age)

    val languages: List<String>
        get() = preferredLanguage.takeUnless { it.isBlank() }?.let(::listOf).orEmpty()

    /** Relationship label, preferring the free-text value when "Other" is set. */
    val relationshipDisplayName: String
        get() = if (relationship == FamilyRelationship.Other && customRelationshipLabel.isNotBlank()) {
            customRelationshipLabel
        } else {
            relationship.displayName
        }
}

enum class FamilyRelationship(
    val wireValue: String,
    val displayName: String,
) {
    Parent("parent", "Parent"),
    StepParent("step_parent", "Step-parent"),
    LegalGuardian("legal_guardian", "Legal guardian"),
    Grandparent("grandparent", "Grandparent"),
    Sibling("sibling", "Sibling"),
    Aunt("aunt", "Aunt"),
    Uncle("uncle", "Uncle"),
    Cousin("cousin", "Cousin"),
    Friend("friend", "Family friend"),
    Other("other", "Other");

    /** True when the editor must ask the guardian to describe the relationship. */
    val requiresCustomLabel: Boolean
        get() = this == Other

    companion object {
        fun fromWire(value: String?): FamilyRelationship =
            entries.firstOrNull { it.wireValue == value } ?: Parent
    }
}

/**
 * Decodes a Firestore document into a [ChildParticipant], returning null when a
 * required field is missing or malformed so list reads can drop the bad doc
 * (tolerant read, like the iOS `compactMap`).
 */
internal fun Map<String, Any?>.toChildParticipantOrNull(documentId: String): ChildParticipant? {
    val guardianId = stringValue("guardianID") ?: return null
    val displayName = stringValue("displayName") ?: return null
    val age = intValue("age") ?: return null
    val gender = UserGender.fromWire(stringValue("gender")) ?: return null
    val church = stringValue("church") ?: return null
    val preferredLanguage = stringValue("preferredLanguage") ?: return null
    val emergencyContactName = stringValue("emergencyContactName") ?: return null
    val emergencyContactPhone = stringValue("emergencyContactPhone") ?: return null

    return ChildParticipant(
        id = stringValue("id") ?: documentId,
        guardianId = guardianId,
        displayName = displayName,
        age = age,
        gender = gender,
        church = church,
        preferredLanguage = preferredLanguage,
        emergencyContactName = emergencyContactName,
        emergencyContactPhone = emergencyContactPhone,
        medicalNotes = rawStringValue("medicalNotes").orEmpty(),
        allergies = stringListValue("allergies"),
        relationship = FamilyRelationship.fromWire(stringValue("relationship")),
        customRelationshipLabel = rawStringValue("customRelationshipLabel").orEmpty(),
        guardianConsentAt = dateValue("guardianConsentAt"),
        photoUrl = stringValue("photoURL"),
        photoPublicId = stringValue("photoPublicID"),
        createdAt = dateValue("createdAt"),
        updatedAt = dateValue("updatedAt"),
    )
}

private fun Map<String, Any?>.stringValue(key: String): String? =
    (this[key] as? String)?.trim()?.takeUnless { it.isBlank() }

private fun Map<String, Any?>.rawStringValue(key: String): String? =
    (this[key] as? String)

private fun Map<String, Any?>.stringListValue(key: String): List<String> =
    (this[key] as? List<*>)
        ?.mapNotNull { (it as? String)?.trim()?.takeUnless(String::isEmpty) }
        .orEmpty()

private fun Map<String, Any?>.intValue(key: String): Int? =
    when (val value = this[key]) {
        is Int -> value
        is Long -> value.toInt()
        is Double -> value.toInt()
        else -> null
    }

private fun Map<String, Any?>.dateValue(key: String): Date? =
    when (val value = this[key]) {
        is Timestamp -> value.toDate()
        is Date -> value
        else -> null
    }
