package fr.ziyon.campzone.ui.guardian

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzAvatar
import fr.ziyon.campzone.core.designsystem.CzAvatarSize
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.guardian.GuardianChildUpdate
import fr.ziyon.campzone.ui.teams.symbolIcon

/** Cream used for text/icons over the pine gradient (mirrors iOS `czCream`). */
private val Cream = Color(0xFFFFF4E0)

/**
 * Participant-facing summary card embedded in the camp detail: how the children
 * a guardian registered are doing at this camp. **Self-silences** for anyone who
 * isn't a guardian here (renders nothing unless the VM resolves child
 * snapshots). Tapping opens the full read-only [GuardianUpdatesRoute]. Mirrors
 * the iOS `GuardianUpdatesCard`.
 */
@Composable
fun GuardianUpdatesCard(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GuardianUpdatesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.load(campingId, authenticatedUser)
    }

    val loaded = state as? GuardianUpdatesUiState.Loaded ?: return
    if (loaded.children.isEmpty()) return

    val openLabel = stringResource(R.string.guardian_card_open_cd)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.xl))
            .clickable { onOpen(campingId) }
            .semantics { contentDescription = openLabel },
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.czColors.pine,
                            MaterialTheme.czColors.pine.copy(alpha = 0.82f),
                        ),
                    ),
                )
                .padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Groups,
                    contentDescription = null,
                    tint = Cream,
                )
                Text(
                    text = stringResource(R.string.guardian_card_title),
                    color = Cream,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = CzSpacing.sm),
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = Cream.copy(alpha = 0.75f),
                )
            }

            loaded.children.forEach { GuardianChildSummaryRow(it) }
        }
    }
}

@Composable
private fun GuardianChildSummaryRow(update: GuardianChildUpdate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.lg))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(CzSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CzAvatar(
            imageUrl = update.attendee.photoUrl,
            contentDescription = update.attendee.displayName,
            initials = initialsOf(update.attendee.displayName),
            size = CzAvatarSize.Small,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = update.attendee.displayName,
                color = Cream,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (update.isCheckedIn) {
                    stringResource(R.string.guardian_summary_checked_in)
                } else {
                    stringResource(R.string.guardian_not_checked_in)
                },
                color = Cream.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        val team = update.team
        if (team != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = symbolIcon(team.symbolName),
                    contentDescription = null,
                    tint = Cream.copy(alpha = 0.9f),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = update.personalScore?.let { "${team.name} · $it" } ?: team.name,
                    color = Cream.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
