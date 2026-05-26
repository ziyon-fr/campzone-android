package fr.ziyon.campzone.ui.camping.registrations

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzAvatar
import fr.ziyon.campzone.core.designsystem.CzAvatarSize
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.CampingAgeGroup
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.ui.camping.label

@Composable
internal fun RegistrationAttendeeRow(
    attendee: CampingAttendee,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CzAvatar(
            imageUrl = attendee.photoUrl,
            contentDescription = attendee.displayName,
            initials = attendee.displayName,
            size = CzAvatarSize.Small,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = attendee.displayName,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${attendee.church} · ${attendee.ageGroup.label()}",
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (attendee.languages.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                ) {
                    attendee.languages.forEach { language ->
                        LanguageChip(language)
                    }
                }
            }
        }
        ApprovalStatusPill(attendee.registrationStatus)
    }
}

@Composable
internal fun ApprovalStatusPill(status: RegistrationApprovalStatus) {
    val color = status.statusColor()
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(CzRadius.full),
    ) {
        Text(
            text = status.label(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = CzSpacing.sm, vertical = 4.dp),
            maxLines = 1,
        )
    }
}

@Composable
internal fun RegistrationApprovalStatus.statusColor(): Color = when (this) {
    RegistrationApprovalStatus.Approved -> MaterialTheme.czColors.success
    RegistrationApprovalStatus.Pending -> MaterialTheme.czColors.warning
    RegistrationApprovalStatus.Waitlisted -> MaterialTheme.czColors.textSecondary
    RegistrationApprovalStatus.Rejected -> MaterialTheme.czColors.error
}

@Composable
private fun LanguageChip(language: String) {
    Surface(
        color = MaterialTheme.czColors.background,
        shape = RoundedCornerShape(CzRadius.full),
    ) {
        Text(
            text = language,
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = CzSpacing.xs, vertical = 3.dp),
        )
    }
}

@Composable
internal fun CampingAgeGroup.label(): String = stringResource(
    when (this) {
        CampingAgeGroup.Kids -> R.string.age_group_kids
        CampingAgeGroup.Youth -> R.string.age_group_youth
        CampingAgeGroup.Adult -> R.string.age_group_adult
    },
)
