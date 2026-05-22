package fr.ziyon.campzone.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape

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
    val colorPalette = CzColors.palette(darkTheme)
    val colorScheme = if (darkTheme) CampzoneDarkColorScheme else CampzoneLightColorScheme

    CompositionLocalProvider(LocalCzColors provides colorPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CzTypography,
            shapes = CzShapes,
            content = content,
        )
    }
}
