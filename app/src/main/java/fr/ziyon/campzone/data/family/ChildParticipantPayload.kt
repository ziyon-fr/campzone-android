package fr.ziyon.campzone.data.family

/**
 * Hand-built Firestore payload for `users/{uid}/children/{childId}`, mirroring
 * the iOS `FirestoreFamilyService.payload(for:)`. `serverTimestamp` and
 * `deleteField` are passed in as opaque tokens so the shape is unit-testable
 * without Firebase.
 */
internal object ChildParticipantPayload {
    fun childPayload(
        child: ChildParticipant,
        serverTimestamp: Any,
        deleteField: Any,
        includeCreatedAt: Boolean,
    ): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "id" to child.id,
            "guardianID" to child.guardianId,
            "displayName" to child.displayName,
            "normalizedDisplayName" to normalizeFamilyParticipantName(child.displayName),
            "age" to child.age,
            "ageGroup" to child.ageGroup.wireValue,
            "gender" to child.gender.wireValue,
            "church" to child.church,
            "preferredLanguage" to child.preferredLanguage,
            "languages" to child.languages,
            "emergencyContactName" to child.emergencyContactName,
            "emergencyContactPhone" to child.emergencyContactPhone,
            "medicalNotes" to child.medicalNotes,
            "allergies" to fr.ziyon.campzone.data.profile.AllergyFormatter.cleaned(child.allergies),
            "relationship" to child.relationship.wireValue,
            "customRelationshipLabel" to child.customRelationshipLabel,
            "updatedAt" to serverTimestamp,
        )

        payload["guardianConsentAt"] = child.guardianConsentAt ?: deleteField
        payload["photoURL"] = child.photoUrl?.trim()?.takeUnless { it.isBlank() } ?: deleteField
        payload["photoPublicID"] = child.photoPublicId?.trim()?.takeUnless { it.isBlank() } ?: deleteField

        if (includeCreatedAt) {
            payload["createdAt"] = serverTimestamp
        }

        return payload
    }
}
