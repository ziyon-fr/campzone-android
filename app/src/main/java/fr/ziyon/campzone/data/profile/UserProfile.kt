package fr.ziyon.campzone.data.profile

import com.google.firebase.Timestamp
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.CampingAgeGroup
import fr.ziyon.campzone.data.auth.UserGender
import java.util.Date

data class UserProfile(
    val uid: String,
    val displayName: String,
    val age: Int?,
    val gender: UserGender?,
    val church: String,
    val skills: List<String>,
    val profession: String,
    val education: String,
    val pathfinderRank: String,
    val phone: String,
    val email: String,
    val preferredLanguage: String,
    val languages: List<String>,
    val role: UserRole,
    val photoUrl: String?,
    val photoPublicId: String?,
    val onboardingCompleted: Boolean,
    val pendingDeletionAt: Date? = null,
) {
    val ageGroup: CampingAgeGroup?
        get() = age?.let(CampingAgeGroup::fromAge)

    val isPendingDeletion: Boolean
        get() = pendingDeletionAt != null

    val deletionGraceEnds: Date?
        get() = pendingDeletionAt?.let { Date(it.time + DeletionGracePeriodMillis) }

    val initials: String
        get() = displayName
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
            .joinToString("")
            .ifBlank { "CZ" }

    val preferredDisplayName: String
        get() = displayName
            .takeUnless { it.isBlank() }
            ?: email.takeUnless { it.isBlank() }
            ?: "Campzone guest"

    companion object {
        const val DeletionGracePeriodMillis: Long = 30L * 24L * 60L * 60L * 1000L

        fun empty(from: AuthenticatedUser): UserProfile =
            UserProfile(
                uid = from.uid,
                displayName = from.displayName,
                age = from.age,
                gender = from.gender,
                church = from.church,
                skills = emptyList(),
                profession = "",
                education = "",
                pathfinderRank = "",
                phone = "",
                email = from.email,
                preferredLanguage = from.preferredLanguage,
                languages = from.preferredLanguage
                    .takeUnless { it.isBlank() }
                    ?.let(::listOf)
                    .orEmpty(),
                role = from.role,
                photoUrl = from.photoUrl,
                photoPublicId = null,
                onboardingCompleted = from.onboardingCompleted,
            )
    }
}

internal fun Map<String, Any?>.toUserProfile(
    documentId: String,
    fallback: AuthenticatedUser,
): UserProfile {
    val languages = stringListValue("languages")
    return UserProfile(
        uid = stringValue("uid") ?: documentId,
        displayName = stringValue("displayName") ?: fallback.displayName,
        age = intValue("age"),
        gender = UserGender.fromWire(stringValue("gender")) ?: fallback.gender,
        church = stringValue("church") ?: fallback.church,
        skills = stringListValue("skills"),
        profession = stringValue("profession").orEmpty(),
        education = stringValue("education").orEmpty(),
        pathfinderRank = stringValue("pathfinderRank").orEmpty(),
        phone = stringValue("phone").orEmpty(),
        email = stringValue("email") ?: fallback.email,
        preferredLanguage = stringValue("preferredLanguage")
            ?: languages.firstOrNull()
            ?: fallback.preferredLanguage,
        languages = languages,
        role = stringValue("role")?.let(UserRole::fromWire) ?: fallback.role,
        photoUrl = stringValue("photoURL") ?: fallback.photoUrl,
        photoPublicId = stringValue("photoPublicID"),
        onboardingCompleted = this["onboardingCompleted"] as? Boolean ?: fallback.onboardingCompleted,
        pendingDeletionAt = dateValue("pendingDeletionAt"),
    )
}

internal fun Map<String, Any?>.toUserProfile(
    documentId: String,
    fallbackUid: String,
): UserProfile {
    val languages = stringListValue("languages")
    return UserProfile(
        uid = stringValue("uid") ?: documentId.ifBlank { fallbackUid },
        displayName = stringValue("displayName").orEmpty(),
        age = intValue("age"),
        gender = UserGender.fromWire(stringValue("gender")),
        church = stringValue("church").orEmpty(),
        skills = stringListValue("skills"),
        profession = stringValue("profession").orEmpty(),
        education = stringValue("education").orEmpty(),
        pathfinderRank = stringValue("pathfinderRank").orEmpty(),
        phone = stringValue("phone").orEmpty(),
        email = stringValue("email").orEmpty(),
        preferredLanguage = stringValue("preferredLanguage") ?: languages.firstOrNull().orEmpty(),
        languages = languages,
        role = UserRole.fromWire(stringValue("role")),
        photoUrl = stringValue("photoURL"),
        photoPublicId = stringValue("photoPublicID"),
        onboardingCompleted = this["onboardingCompleted"] as? Boolean ?: false,
        pendingDeletionAt = dateValue("pendingDeletionAt"),
    )
}

private fun Map<String, Any?>.stringValue(key: String): String? =
    (this[key] as? String)?.trim()?.takeUnless { it.isBlank() }

private fun Map<String, Any?>.intValue(key: String): Int? =
    when (val value = this[key]) {
        is Int -> value
        is Long -> value.toInt()
        is Double -> value.toInt()
        else -> null
    }

private fun Map<String, Any?>.stringListValue(key: String): List<String> =
    (this[key] as? List<*>)
        ?.mapNotNull { (it as? String)?.trim()?.takeUnless { value -> value.isBlank() } }
        .orEmpty()

private fun Map<String, Any?>.dateValue(key: String): Date? =
    when (val value = this[key]) {
        is Timestamp -> value.toDate()
        is Date -> value
        else -> null
    }
