package fr.ziyon.campzone.data.profile

/** Stable allergy tokens shared with the shipped iOS client. */
enum class CommonAllergy(
    val wireValue: String,
    val isFood: Boolean,
) {
    Peanuts("peanuts", true),
    TreeNuts("tree_nuts", true),
    Milk("milk", true),
    Eggs("eggs", true),
    Gluten("gluten", true),
    Soy("soy", true),
    Fish("fish", true),
    Shellfish("shellfish", true),
    Sesame("sesame", true),
    Pollen("pollen", false),
    DustMites("dust_mites", false),
    PetDander("pet_dander", false),
    InsectStings("insect_stings", false),
    Latex("latex", false),
    Medication("medication", false),
    Mold("mold", false);

    companion object {
        val foodAllergies: List<CommonAllergy> = entries.filter(CommonAllergy::isFood)
        val otherAllergies: List<CommonAllergy> = entries.filterNot(CommonAllergy::isFood)

        fun fromWire(value: String): CommonAllergy? =
            entries.firstOrNull { it.wireValue == value }
    }
}

object AllergyFormatter {
    fun toggledPreset(
        tokens: List<String>,
        allergy: CommonAllergy,
        displayName: String,
    ): List<String> {
        fun matches(token: String): Boolean =
            token.equals(allergy.wireValue, ignoreCase = true) ||
                token.equals(displayName, ignoreCase = true)
        return if (tokens.any(::matches)) {
            cleaned(tokens.filterNot(::matches))
        } else {
            cleaned(tokens + allergy.wireValue)
        }
    }

    fun normalizedToken(raw: String, displayName: (CommonAllergy) -> String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return CommonAllergy.entries.firstOrNull { allergy ->
            allergy.wireValue.equals(trimmed, ignoreCase = true) ||
                displayName(allergy).equals(trimmed, ignoreCase = true)
        }?.wireValue ?: trimmed
    }

    fun cleaned(tokens: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        return tokens.mapNotNull { token ->
            token.trim().takeUnless(String::isEmpty)
        }.filter { token -> seen.add(token.lowercase()) }
    }
}
