package fr.ziyon.campzone.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.ziyon.campzone.R
import fr.ziyon.campzone.data.auth.PreferredLanguage

@Composable
fun preferredLanguageDisplayName(rawValue: String): String {
    val language = PreferredLanguage.fromWire(rawValue) ?: return rawValue
    return stringResource(
        when (language) {
            PreferredLanguage.English -> R.string.language_english
            PreferredLanguage.Mandarin -> R.string.language_mandarin
            PreferredLanguage.Hindi -> R.string.language_hindi
            PreferredLanguage.Spanish -> R.string.language_spanish
            PreferredLanguage.French -> R.string.language_french
            PreferredLanguage.Arabic -> R.string.language_arabic
            PreferredLanguage.Bengali -> R.string.language_bengali
            PreferredLanguage.Portuguese -> R.string.language_portuguese
            PreferredLanguage.Russian -> R.string.language_russian
            PreferredLanguage.Urdu -> R.string.language_urdu
            PreferredLanguage.Indonesian -> R.string.language_indonesian
            PreferredLanguage.German -> R.string.language_german
            PreferredLanguage.Japanese -> R.string.language_japanese
            PreferredLanguage.Swahili -> R.string.language_swahili
            PreferredLanguage.Marathi -> R.string.language_marathi
            PreferredLanguage.Telugu -> R.string.language_telugu
            PreferredLanguage.Turkish -> R.string.language_turkish
            PreferredLanguage.Tamil -> R.string.language_tamil
            PreferredLanguage.Vietnamese -> R.string.language_vietnamese
            PreferredLanguage.Korean -> R.string.language_korean
            PreferredLanguage.Italian -> R.string.language_italian
            PreferredLanguage.Thai -> R.string.language_thai
            PreferredLanguage.Gujarati -> R.string.language_gujarati
            PreferredLanguage.Persian -> R.string.language_persian
            PreferredLanguage.Polish -> R.string.language_polish
            PreferredLanguage.Ukrainian -> R.string.language_ukrainian
            PreferredLanguage.Malay -> R.string.language_malay
            PreferredLanguage.Kannada -> R.string.language_kannada
            PreferredLanguage.Oromo -> R.string.language_oromo
            PreferredLanguage.Romanian -> R.string.language_romanian
        },
    )
}
