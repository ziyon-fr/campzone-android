package fr.ziyon.campzone.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import fr.ziyon.campzone.R

enum class AppTheme(
    val storageKey: String,
    @param:StringRes val labelRes: Int,
) {
    Ember("ember", R.string.theme_ember),
    Sienna("sienna", R.string.theme_sienna),
    Leaf("leaf", R.string.theme_leaf),
    Pine("pine", R.string.theme_pine),
    Champagne("champagne", R.string.theme_champagne),
    Expresso("expresso", R.string.theme_expresso);

    fun color(darkTheme: Boolean): Color = when (this) {
        Ember -> if (darkTheme) CzColors.EmberDark else CzColors.EmberLight
        Sienna -> if (darkTheme) CzColors.SiennaDark else CzColors.SiennaLight
        Leaf -> if (darkTheme) CzColors.LeafDark else CzColors.LeafLight
        Pine -> if (darkTheme) CzColors.PineDark else CzColors.PineLight
        Champagne -> if (darkTheme) CzColors.ChampagneDark else CzColors.ChampagneLight
        Expresso -> if (darkTheme) CzColors.EspressoDark else CzColors.EspressoLight
    }

    companion object {
        val Default = Ember

        fun fromStorageKey(raw: String?): AppTheme =
            entries.firstOrNull { it.storageKey == raw } ?: Default
    }
}

internal val LocalAppTheme = staticCompositionLocalOf { AppTheme.Default }
internal val LocalSelectAppTheme = staticCompositionLocalOf<(AppTheme) -> Unit> { {} }
