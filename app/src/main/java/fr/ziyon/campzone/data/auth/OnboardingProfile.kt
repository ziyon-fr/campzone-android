package fr.ziyon.campzone.data.auth

import java.util.Locale

data class OnboardingProfile(
    val age: Int?,
    val church: String,
    val preferredLanguage: String,
    val gender: UserGender?,
) {
    val languageCodes: List<String>
        get() = preferredLanguage.trim().takeUnless { it.isBlank() }?.let(::listOf).orEmpty()

    val ageGroup: CampingAgeGroup?
        get() = age?.let(CampingAgeGroup::fromAge)
}

enum class CampingAgeGroup(
    val wireValue: String,
    val displayName: String,
) {
    Kids("kids", "Kids"),
    Youth("youth", "Youth"),
    Adult("adult", "Young Adult");

    companion object {
        fun fromAge(age: Int): CampingAgeGroup = when {
            age < 13 -> Kids
            age < 36 -> Youth
            else -> Adult
        }

        fun fromWire(value: String?): CampingAgeGroup? =
            entries.firstOrNull { it.wireValue == value }
    }
}

enum class UserGender(
    val wireValue: String,
    val displayName: String,
) {
    Female("female", "Female"),
    Male("male", "Male"),
    PreferNotToSay("prefer_not_to_say", "Prefer not to say");

    companion object {
        fun fromWire(value: String?): UserGender? =
            entries.firstOrNull { it.wireValue == value }
    }
}

enum class PreferredLanguage(
    val wireValue: String,
    val displayName: String,
) {
    English("en", "English"),
    Mandarin("zh", "Mandarin Chinese"),
    Hindi("hi", "Hindi"),
    Spanish("es", "Spanish"),
    French("fr", "French"),
    Arabic("ar", "Arabic"),
    Bengali("bn", "Bengali"),
    Portuguese("pt", "Portuguese"),
    Russian("ru", "Russian"),
    Urdu("ur", "Urdu"),
    Indonesian("id", "Indonesian"),
    German("de", "German"),
    Japanese("ja", "Japanese"),
    Swahili("sw", "Swahili"),
    Marathi("mr", "Marathi"),
    Telugu("te", "Telugu"),
    Turkish("tr", "Turkish"),
    Tamil("ta", "Tamil"),
    Vietnamese("vi", "Vietnamese"),
    Korean("ko", "Korean"),
    Italian("it", "Italian"),
    Thai("th", "Thai"),
    Gujarati("gu", "Gujarati"),
    Persian("fa", "Persian"),
    Polish("pl", "Polish"),
    Ukrainian("uk", "Ukrainian"),
    Malay("ms", "Malay"),
    Kannada("kn", "Kannada"),
    Oromo("om", "Oromo"),
    Romanian("ro", "Romanian");

    companion object {
        fun fromWire(value: String?): PreferredLanguage? =
            entries.firstOrNull { it.wireValue == value }

        fun defaultForLocale(locale: Locale = Locale.getDefault()): PreferredLanguage =
            fromWire(locale.language) ?: French
    }
}

internal object OnboardingProfilePayload {
    fun userMergePayload(
        profile: OnboardingProfile,
        serverTimestamp: Any,
        deleteField: Any,
    ): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "church" to profile.church.trim(),
            "languages" to profile.languageCodes,
            "preferredLanguage" to profile.preferredLanguage.trim(),
            "role" to "adult",
            "onboardingCompleted" to true,
            "updatedAt" to serverTimestamp,
        )

        val age = profile.age
        if (age == null) {
            payload["age"] = deleteField
            payload["ageGroup"] = deleteField
        } else {
            payload["age"] = age
            payload["ageGroup"] = CampingAgeGroup.fromAge(age).wireValue
        }

        val gender = profile.gender
        payload["gender"] = gender?.wireValue ?: deleteField

        return payload
    }

    fun registrationSnapshotPayload(
        user: AuthenticatedUser,
        profile: OnboardingProfile,
        serverTimestamp: Any,
    ): Map<String, Any?> {
        val age = requireNotNull(profile.age)
        val ageGroup = requireNotNull(profile.ageGroup)
        return linkedMapOf(
            "displayName" to user.preferredDisplayName,
            "church" to profile.church.trim(),
            "age" to age,
            "ageGroup" to ageGroup.wireValue,
            "gender" to requireNotNull(profile.gender).wireValue,
            "preferredLanguage" to profile.preferredLanguage.trim(),
            "languages" to profile.languageCodes,
            "updatedAt" to serverTimestamp,
        )
    }

    fun checkInSnapshotPayload(
        user: AuthenticatedUser,
        profile: OnboardingProfile,
        serverTimestamp: Any,
    ): Map<String, Any?> {
        val ageGroup = requireNotNull(profile.ageGroup)
        return linkedMapOf(
            "displayName" to user.preferredDisplayName,
            "church" to profile.church.trim(),
            "ageGroup" to ageGroup.wireValue,
            "gender" to requireNotNull(profile.gender).wireValue,
            "preferredLanguage" to profile.preferredLanguage.trim(),
            "updatedAt" to serverTimestamp,
        )
    }
}
