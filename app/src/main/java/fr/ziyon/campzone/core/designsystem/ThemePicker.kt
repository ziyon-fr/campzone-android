package fr.ziyon.campzone.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable

@Composable
fun ThemePicker(
    modifier: Modifier = Modifier,
) {
    val selectedTheme = LocalAppTheme.current
    val selectTheme = LocalSelectAppTheme.current
    val darkTheme = isSystemInDarkTheme()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = CzSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        AppTheme.entries.forEach { theme ->
            ThemeSwatch(
                theme = theme,
                isSelected = selectedTheme == theme,
                swatchColor = theme.color(darkTheme),
                onClick = { selectTheme(theme) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ThemeSwatch(
    theme: AppTheme,
    isSelected: Boolean,
    swatchColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val label = stringResource(theme.labelRes)

    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 70.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { selected = isSelected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .border(
                        BorderStroke(2.dp, if (isSelected) colors.textPrimary else Color.Transparent),
                        CircleShape,
                    ),
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .scale(if (isSelected) 1f else 0.94f)
                    .background(swatchColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }

        Text(
            text = label,
            color = if (isSelected) colors.textPrimary else colors.textSecondary,
            style = CzTypeScale.caption.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
