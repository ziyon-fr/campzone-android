package fr.ziyon.campzone.ui.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.model.Activity
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ActivityRow(
    activity: Activity,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val isAward = activity.points >= 0
    val accent = if (isAward) colors.success else colors.error
    val title = activity.reason.trim().ifEmpty { activity.name }
    val targetName = activity.targetTeamName ?: activity.targetUserName
    val dateFormatter = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()) }

    Row(
        modifier = modifier.padding(CzSpacing.md),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Icon(
            imageVector = if (isAward) Icons.Filled.AddCircle else Icons.Filled.RemoveCircle,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(32.dp),
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        R.string.teams_points_format,
                        if (activity.points > 0) "+${activity.points}" else "${activity.points}",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = accent,
                )
            }

            if (targetName != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = activity.createdByName,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                    Text(
                        text = targetName,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                }
            }

            Spacer(Modifier.height(2.dp))
            Text(
                text = dateFormatter.format(activity.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
    }
}
