package fr.ziyon.campzone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import fr.ziyon.campzone.core.designsystem.CampzoneTheme as CoreCampzoneTheme

@Suppress("UNUSED_PARAMETER")
@Composable
fun CampzoneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    CoreCampzoneTheme(
        darkTheme = darkTheme,
        content = content,
    )
}
