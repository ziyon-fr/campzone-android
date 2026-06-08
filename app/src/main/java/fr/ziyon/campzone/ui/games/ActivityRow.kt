package fr.ziyon.campzone.ui.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.model.Activity

@Composable
fun ActivityRow(
    activity: Activity,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val isAward = activity.points >= 0
    val accent = if (isAward) colors.success else colors.error

    Row(
        modifier = modifier.padding(CzSpacing.lg),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Surface(
            color = accent.copy(alpha = 0.16f),
            shape = CircleShape,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = if (isAward) Icons.Outlined.Add else Icons.Outlined.Remove,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.padding(8.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = activity.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        R.string.teams_points_format,
                        if (activity.points > 0) "+${activity.points}" else "${activity.points}",
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = accent,
                )
            }

            val targetName = activity.targetTeamName ?: activity.targetUserName
            if (targetName != null) {
                Text(
                    text = targetName,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }

            if (activity.reason.isNotBlank()) {
                Text(
                    text = activity.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                )
            }

            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.games_activity_by, activity.createdByName),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
    }
}
