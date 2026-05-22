package fr.ziyon.campzone.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val CzRoundedFontFamily = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-rounded"), FontWeight.Normal),
    Font(DeviceFontFamilyName("sans-serif-rounded"), FontWeight.Medium),
    Font(DeviceFontFamilyName("sans-serif-rounded"), FontWeight.SemiBold),
    Font(DeviceFontFamilyName("sans-serif-rounded"), FontWeight.Bold),
)

object CzTypeScale {
    val largeTitle = czTextStyle(size = 34, lineHeight = 41, weight = FontWeight.Bold)
    val title = czTextStyle(size = 28, lineHeight = 34, weight = FontWeight.Bold)
    val title2 = czTextStyle(size = 22, lineHeight = 28, weight = FontWeight.SemiBold)
    val title3 = czTextStyle(size = 20, lineHeight = 25, weight = FontWeight.SemiBold)
    val headline = czTextStyle(size = 17, lineHeight = 22, weight = FontWeight.SemiBold)
    val body = czTextStyle(size = 17, lineHeight = 22, weight = FontWeight.Normal)
    val callout = czTextStyle(size = 16, lineHeight = 21, weight = FontWeight.Normal)
    val subhead = czTextStyle(size = 15, lineHeight = 20, weight = FontWeight.Medium)
    val caption = czTextStyle(size = 12, lineHeight = 16, weight = FontWeight.Normal)
    val caption2 = czTextStyle(size = 11, lineHeight = 14, weight = FontWeight.Medium)
}

val CzTypography = Typography(
    displayLarge = CzTypeScale.largeTitle,
    headlineLarge = CzTypeScale.title,
    headlineMedium = CzTypeScale.title2,
    headlineSmall = CzTypeScale.title3,
    titleLarge = CzTypeScale.title2,
    titleMedium = CzTypeScale.title3,
    titleSmall = CzTypeScale.headline,
    bodyLarge = CzTypeScale.body,
    bodyMedium = CzTypeScale.callout,
    bodySmall = CzTypeScale.subhead,
    labelLarge = CzTypeScale.subhead,
    labelMedium = CzTypeScale.caption,
    labelSmall = CzTypeScale.caption2,
)

private fun czTextStyle(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
) = TextStyle(
    fontFamily = CzRoundedFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp,
)
