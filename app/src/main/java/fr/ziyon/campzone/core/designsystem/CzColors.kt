package fr.ziyon.campzone.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class CzColorPalette(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val divider: Color,
    val ember: Color,
    val flame: Color,
    val amber: Color,
    val pine: Color,
    val onPrimary: Color,
    val onSecondary: Color,
    val onAccent: Color,
    val onError: Color,
)

object CzColors {
    val BackgroundLight = Color(0xFFF8F4EE)
    val BackgroundDark = Color(0xFF070E1A)
    val SurfaceLight = Color(0x0A000000)
    val SurfaceDark = Color(0x14FFFFFF)
    val PrimaryLight = Color(0xFFFF6B35)
    val PrimaryDark = Color(0xFFFF7A47)
    val SecondaryLight = Color(0xFF3A6248)
    val SecondaryDark = Color(0xFF4A7C59)
    val AccentLight = Color(0xFFD97706)
    val AccentDark = Color(0xFFFFB347)
    val TextPrimaryLight = Color(0xFF1C1917)
    val TextPrimaryDark = Color(0xFFFFF4E0)
    val TextSecondaryLight = Color(0xFF6B6052)
    val TextSecondaryDark = Color(0xFFC4A875)
    val SuccessLight = Color(0xFF16A34A)
    val SuccessDark = Color(0xFF66BB6A)
    val WarningLight = Color(0xFFD97706)
    val WarningDark = Color(0xFFFFB347)
    val ErrorLight = Color(0xFFDC2626)
    val ErrorDark = Color(0xFFFF6B6B)
    val DividerLight = Color(0x14000000)
    val DividerDark = Color(0x1FFFFFFF)
    val EmberLight = Color(0xFFFF6B35)
    val EmberDark = Color(0xFFFF7A47)
    val FlameLight = Color(0xFFFF8C00)
    val FlameDark = Color(0xFFFFA133)
    val AmberLight = Color(0xFFFFB347)
    val AmberDark = Color(0xFFFFC266)
    val PineLight = Color(0xFF243824)
    val PineDark = Color(0xFF2F4A2F)

    val Light = CzColorPalette(
        background = BackgroundLight,
        surface = SurfaceLight,
        primary = PrimaryLight,
        secondary = SecondaryLight,
        accent = AccentLight,
        textPrimary = TextPrimaryLight,
        textSecondary = TextSecondaryLight,
        success = SuccessLight,
        warning = WarningLight,
        error = ErrorLight,
        divider = DividerLight,
        ember = EmberLight,
        flame = FlameLight,
        amber = AmberLight,
        pine = PineLight,
        onPrimary = TextPrimaryLight,
        onSecondary = TextPrimaryDark,
        onAccent = TextPrimaryLight,
        onError = TextPrimaryDark,
    )

    val Dark = CzColorPalette(
        background = BackgroundDark,
        surface = SurfaceDark,
        primary = PrimaryDark,
        secondary = SecondaryDark,
        accent = AccentDark,
        textPrimary = TextPrimaryDark,
        textSecondary = TextSecondaryDark,
        success = SuccessDark,
        warning = WarningDark,
        error = ErrorDark,
        divider = DividerDark,
        ember = EmberDark,
        flame = FlameDark,
        amber = AmberDark,
        pine = PineDark,
        onPrimary = BackgroundDark,
        onSecondary = TextPrimaryDark,
        onAccent = BackgroundDark,
        onError = BackgroundDark,
    )

    fun palette(darkTheme: Boolean): CzColorPalette = if (darkTheme) Dark else Light
}

val LocalCzColors = staticCompositionLocalOf { CzColors.Light }

val MaterialTheme.czColors: CzColorPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalCzColors.current
