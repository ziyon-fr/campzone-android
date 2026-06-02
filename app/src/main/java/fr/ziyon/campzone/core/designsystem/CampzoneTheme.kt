package fr.ziyon.campzone.core.designsystem

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val CampzoneLightColorScheme = lightColorScheme(
    primary = CzColors.PrimaryLight,
    onPrimary = CzColors.TextPrimaryLight,
    primaryContainer = CzColors.EmberLight,
    onPrimaryContainer = CzColors.TextPrimaryLight,
    secondary = CzColors.SecondaryLight,
    onSecondary = CzColors.TextPrimaryDark,
    secondaryContainer = CzColors.PineLight,
    onSecondaryContainer = CzColors.TextPrimaryDark,
    tertiary = CzColors.AccentLight,
    onTertiary = CzColors.TextPrimaryLight,
    background = CzColors.BackgroundLight,
    onBackground = CzColors.TextPrimaryLight,
    surface = CzColors.BackgroundLight,
    onSurface = CzColors.TextPrimaryLight,
    surfaceVariant = CzColors.SurfaceLight,
    onSurfaceVariant = CzColors.TextSecondaryLight,
    outline = CzColors.DividerLight,
    outlineVariant = CzColors.DividerLight,
    error = CzColors.ErrorLight,
    onError = CzColors.TextPrimaryDark,
    errorContainer = CzColors.ErrorLight,
    onErrorContainer = CzColors.TextPrimaryDark,
    inverseSurface = CzColors.TextPrimaryLight,
    inverseOnSurface = CzColors.BackgroundLight,
    inversePrimary = CzColors.PrimaryDark,
    scrim = Color.Black,
)

private val CampzoneDarkColorScheme = darkColorScheme(
    primary = CzColors.PrimaryDark,
    onPrimary = CzColors.BackgroundDark,
    primaryContainer = CzColors.EmberDark,
    onPrimaryContainer = CzColors.BackgroundDark,
    secondary = CzColors.SecondaryDark,
    onSecondary = CzColors.TextPrimaryDark,
    secondaryContainer = CzColors.PineDark,
    onSecondaryContainer = CzColors.TextPrimaryDark,
    tertiary = CzColors.AccentDark,
    onTertiary = CzColors.BackgroundDark,
    background = CzColors.BackgroundDark,
    onBackground = CzColors.TextPrimaryDark,
    surface = CzColors.BackgroundDark,
    onSurface = CzColors.TextPrimaryDark,
    surfaceVariant = CzColors.SurfaceDark,
    onSurfaceVariant = CzColors.TextSecondaryDark,
    outline = CzColors.DividerDark,
    outlineVariant = CzColors.DividerDark,
    error = CzColors.ErrorDark,
    onError = CzColors.BackgroundDark,
    errorContainer = CzColors.ErrorDark,
    onErrorContainer = CzColors.BackgroundDark,
    inverseSurface = CzColors.TextPrimaryDark,
    inverseOnSurface = CzColors.BackgroundDark,
    inversePrimary = CzColors.PrimaryLight,
    scrim = Color.Black,
)

private val CzShapes = Shapes(
    extraSmall = RoundedCornerShape(CzRadius.xs),
    small = RoundedCornerShape(CzRadius.sm),
    medium = RoundedCornerShape(CzRadius.md),
    large = RoundedCornerShape(CzRadius.lg),
    extraLarge = RoundedCornerShape(CzRadius.xl),
)

@Composable
fun CampzoneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.applicationContext.getSharedPreferences(THEME_PREFERENCES, Context.MODE_PRIVATE)
    }
    var appTheme by remember(preferences) {
        mutableStateOf(AppTheme.fromStorageKey(preferences.getString(THEME_STORAGE_KEY, null)))
    }
    val selectTheme: (AppTheme) -> Unit = { theme ->
        if (theme != appTheme) {
            appTheme = theme
            preferences.edit().putString(THEME_STORAGE_KEY, theme.storageKey).apply()
        }
    }
    val colorPalette = CzColors.palette(darkTheme, appTheme)
    val accent = appTheme.color(darkTheme)
    val baseColorScheme = if (darkTheme) CampzoneDarkColorScheme else CampzoneLightColorScheme
    val colorScheme = baseColorScheme.copy(
        primary = accent,
        primaryContainer = accent,
        tertiary = accent,
        onPrimary = colorPalette.onAccent,
        onPrimaryContainer = colorPalette.onAccent,
        onTertiary = colorPalette.onAccent,
    )

    CompositionLocalProvider(
        LocalCzColors provides colorPalette,
        LocalAppTheme provides appTheme,
        LocalSelectAppTheme provides selectTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CzTypography,
            shapes = CzShapes,
            content = content,
        )
    }
}

private const val THEME_PREFERENCES = "campzone_theme"
private const val THEME_STORAGE_KEY = "cz.appTheme"
