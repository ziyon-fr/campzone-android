package fr.ziyon.campzone.ui.polls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.model.Poll
import fr.ziyon.campzone.data.model.PollOption
import kotlin.math.roundToInt

@Composable
fun PollCard(poll: Poll, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.czColors
    val live = poll.resolvedIsOpen
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(colors.surface, RoundedCornerShape(CzRadius.xl))
            .padding(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(if (live) colors.success else colors.textSecondary, CircleShape),
            )
            Text(
                stringResource(if (live) R.string.poll_live else R.string.poll_closed),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (live) colors.success else colors.textSecondary,
            )
        }
        Text(
            poll.question,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            maxLines = 3,
        )
        if (poll.description.isNotBlank()) {
            Text(
                poll.description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 2,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
            Text(
                stringResource(R.string.poll_votes, poll.totalVotes),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
            Text(
                stringResource(R.string.poll_options_count, poll.options.size),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textSecondary,
            )
        }
    }
}

@Composable
fun PollResultsBar(
    option: PollOption,
    percentage: Double,
    isUserChoice: Boolean,
    isWinning: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val barColor = when {
        isUserChoice -> colors.ember
        isWinning -> colors.amber
        else -> colors.pine
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
            Text(
                option.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isUserChoice) FontWeight.SemiBold else FontWeight.Normal,
                color = colors.textPrimary,
                maxLines = 2,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isUserChoice) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = colors.ember,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${(percentage * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = barColor,
            )
            Text(
                "${option.voteCount}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(colors.textSecondary.copy(alpha = 0.10f), RoundedCornerShape(6.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(percentage.coerceIn(0.0, 1.0).toFloat())
                    .height(8.dp)
                    .background(barColor.copy(alpha = if (isUserChoice) 0.85f else 0.55f), RoundedCornerShape(6.dp)),
            )
        }
    }
}
