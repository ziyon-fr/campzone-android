package fr.ziyon.campzone.data.profile

import fr.ziyon.campzone.data.auth.CampingAgeGroup

internal object UserProfilePayload {
    fun cleaned(user: UserProfile): UserProfile {
        val cleanedLanguages = cleanedList(user.languages)
        val preferred = user.preferredLanguage.trim()
            .takeUnless { it.isBlank() }
            ?: cleanedLanguages.firstOrNull()
            ?: ""
        val resolvedLanguages = if (cleanedLanguages.isEmpty() && preferred.isNotBlank()) {
            listOf(preferred)
        } else {
            cleanedLanguages
        }

        return user.copy(
            displayName = user.displayName.trim(),
            church = user.church.trim(),
            skills = cleanedList(user.skills),
            profession = user.profession.trim(),
            education = user.education.trim(),
            pathfinderRank = user.pathfinderRank.trim(),
            phone = user.phone.trim(),
            email = user.email.trim(),
            preferredLanguage = preferred,
            languages = resolvedLanguages,
            photoPublicId = user.photoPublicId?.trim()?.takeUnless { it.isBlank() },
            onboardingCompleted = true,
        )
    }

    fun userMergePayload(
        user: UserProfile,
        serverTimestamp: Any,
        deleteField: Any,
    ): Map<String, Any?> {
        val savedUser = cleaned(user)
        val payload = linkedMapOf<String, Any?>(
            "uid" to savedUser.uid,
            "displayName" to savedUser.displayName,
            "church" to savedUser.church,
            "skills" to savedUser.skills,
            "profession" to savedUser.profession,
            "education" to savedUser.education,
            "pathfinderRank" to savedUser.pathfinderRank,
            "phone" to savedUser.phone,
            "email" to savedUser.email,
            "preferredLanguage" to savedUser.preferredLanguage,
            "languages" to savedUser.languages,
            "role" to savedUser.role.rawValue,
            "onboardingCompleted" to true,
            "updatedAt" to serverTimestamp,
        )

        val age = savedUser.age
        if (age == null) {
            payload["age"] = deleteField
            payload["ageGroup"] = deleteField
        } else {
            payload["age"] = age
            payload["ageGroup"] = CampingAgeGroup.fromAge(age).wireValue
        }

        payload["gender"] = savedUser.gender?.wireValue ?: deleteField
        payload["photoURL"] = savedUser.photoUrl?.trim()?.takeUnless { it.isBlank() } ?: deleteField
        payload["photoPublicID"] = savedUser.photoPublicId ?: deleteField

        return payload
    }

    fun accountDeletionPayload(
        uid: String,
        pendingDeletionAt: Any,
        serverTimestamp: Any,
    ): Map<String, Any?> =
        linkedMapOf(
            "pendingDeletionAt" to pendingDeletionAt,
            "deletionRequestedBy" to uid,
            "updatedAt" to serverTimestamp,
        )

    fun cancelAccountDeletionPayload(
        deleteField: Any,
        serverTimestamp: Any,
    ): Map<String, Any?> =
        linkedMapOf(
            "pendingDeletionAt" to deleteField,
            "deletionRequestedBy" to deleteField,
            "updatedAt" to serverTimestamp,
        )

    fun attendeeProfilePayload(
        user: UserProfile,
        deleteField: Any,
    ): Map<String, Any?> {
        val savedUser = cleaned(user)
        val payload = linkedMapOf<String, Any?>()
        payload.putAll(participantProfilePayload(savedUser, deleteField))
        payload["languages"] = savedUser.languages
        payload["preferredLanguage"] = savedUser.preferredLanguage

        val age = savedUser.age
        if (age == null) {
            payload["age"] = deleteField
            payload["ageGroup"] = deleteField
        } else {
            payload["age"] = age
            payload["ageGroup"] = CampingAgeGroup.fromAge(age).wireValue
        }

        payload["gender"] = savedUser.gender?.wireValue ?: deleteField
        return payload
    }

    fun participantProfilePayload(
        user: UserProfile,
        deleteField: Any,
    ): Map<String, Any?> {
        val savedUser = cleaned(user)
        return linkedMapOf(
            "displayName" to savedUser.displayName,
            "church" to savedUser.church,
            "preferredLanguage" to savedUser.preferredLanguage,
            "photoURL" to (savedUser.photoUrl?.trim()?.takeUnless { it.isBlank() } ?: deleteField),
        )
    }

    fun chatProfilePayload(
        user: UserProfile,
        deleteField: Any,
    ): Map<String, Any?> {
        val savedUser = cleaned(user)
        return linkedMapOf(
            "senderName" to savedUser.displayName,
            "senderChurch" to savedUser.church,
            "senderPreferredLanguage" to savedUser.preferredLanguage,
            "senderGender" to (savedUser.gender?.wireValue ?: deleteField),
            "senderPhotoURL" to (savedUser.photoUrl?.trim()?.takeUnless { it.isBlank() } ?: deleteField),
        )
    }

    fun announcementProfilePayload(
        user: UserProfile,
        deleteField: Any,
    ): Map<String, Any?> {
        val savedUser = cleaned(user)
        return linkedMapOf(
            "authorName" to savedUser.displayName,
            "authorPhotoURL" to (savedUser.photoUrl?.trim()?.takeUnless { it.isBlank() } ?: deleteField),
        )
    }

    fun pollProfilePayload(user: UserProfile): Map<String, Any?> =
        linkedMapOf("createdByName" to cleaned(user).displayName)

    fun withUpdatedAt(
        payload: Map<String, Any?>,
        serverTimestamp: Any,
    ): Map<String, Any?> =
        linkedMapOf<String, Any?>()
            .apply {
                putAll(payload)
                put("updatedAt", serverTimestamp)
            }

    fun rewriteTeamMembers(
        members: List<Map<String, Any?>>,
        user: UserProfile,
    ): TeamMembersRewrite {
        val savedUser = cleaned(user)
        var didChange = false
        val rewritten = members.map { member ->
            if (member["userID"] != savedUser.uid) return@map member
            didChange = true
            linkedMapOf<String, Any?>()
                .apply {
                    putAll(member)
                    put("displayName", savedUser.displayName)
                    put("church", savedUser.church)
                    put("preferredLanguage", savedUser.preferredLanguage)
                    put("languages", savedUser.languages)
                    if (savedUser.age == null) {
                        remove("age")
                        remove("ageGroup")
                    } else {
                        put("age", savedUser.age)
                        put("ageGroup", CampingAgeGroup.fromAge(savedUser.age).wireValue)
                    }
                    if (savedUser.gender == null) {
                        remove("gender")
                    } else {
                        put("gender", savedUser.gender.wireValue)
                    }
                    if (savedUser.photoUrl.isNullOrBlank()) {
                        remove("photoURL")
                    } else {
                        put("photoURL", savedUser.photoUrl.trim())
                    }
                }
        }
        return TeamMembersRewrite(members = rewritten, didChange = didChange)
    }

    private fun cleanedList(values: List<String>): List<String> =
        values
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}

internal data class TeamMembersRewrite(
    val members: List<Map<String, Any?>>,
    val didChange: Boolean,
)
