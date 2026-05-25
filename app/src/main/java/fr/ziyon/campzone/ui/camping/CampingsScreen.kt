package fr.ziyon.campzone.ui.camping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzBadge
import fr.ziyon.campzone.core.designsystem.CzCard
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTextField
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Festival
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import java.util.Date
import kotlin.math.max

@Composable
fun CampingsRoute(
    onOpenCamping: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CampingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    CampingsScreen(
        state = state,
        onSearchChange = viewModel::updateSearch,
        onOpenCamping = onOpenCamping,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
fun CampingsScreen(
    state: CampingsUiState,
    onSearchChange: (String) -> Unit,
    onOpenCamping: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.nav_campings),
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .padding(
                start = CzSpacing.xl,
                end = CzSpacing.xl,
                top = CzSpacing.xl,
                bottom = CzSpacing.sm,
            )

        )
        CzTextField(
            value = state.searchText,
            onValueChange = onSearchChange,
            label = stringResource(R.string.camping_search_label),
            placeholder = stringResource(R.string.camping_search_placeholder),
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CzSpacing.xl),
        )

        when (val phase = state.phase) {
            CampingsPhase.Loading -> CzLoadingView(
                modifier = Modifier.fillMaxSize(),
                message = stringResource(R.string.camping_loading),
            )

            is CampingsPhase.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CzErrorState(
                    title = stringResource(R.string.camping_error_title),
                    message = phase.message,
                    onRetry = onRetry,
                    retryLabel = stringResource(R.string.common_retry),
                )
            }

            is CampingsPhase.Empty -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                if (phase.isSearchResult) {
                    CzEmptyState(
                        title = stringResource(R.string.camping_empty_search_title, phase.query),
                        message = stringResource(R.string.camping_empty_search_message),
                    )
                } else {
                    CzEmptyState(
                        title = stringResource(R.string.camping_empty_title),
                        message = stringResource(R.string.camping_empty_message),
                    )
                }
            }

            is CampingsPhase.Loaded -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = CzSpacing.xl,
                    end = CzSpacing.xl,
                    top = CzSpacing.base,
                    bottom = CzSpacing.xxxl,
                ),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            ) {
                phase.sections.forEach { section ->
                    item(key = "header-${section.id}") {
                        Text(
                            text = section.title,
                            color = MaterialTheme.czColors.textSecondary,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = CzSpacing.sm, bottom = CzSpacing.xs),
                        )
                    }
                    items(section.campings, key = { it.id }) { camping ->
                        CampingCard(camping = camping, onClick = { onOpenCamping(camping.id) })
                    }
                }
            }
        }
    }
}

// Camping Card
@Composable
private fun CampingCard(
    camping: Camping,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showAdminInfo: Boolean = false,
) {
    val fillRatio = remember(camping.participantCount, camping.participantCapacity) {
        val capacity = camping.participantCapacity ?: 0
        if (capacity <= 0) 0f
        else (camping.participantCount.toFloat() / capacity.toFloat()).coerceAtMost(1f)
    }

    val fillColor = when {
        fillRatio < 0.5f -> MaterialTheme.czColors.success
        fillRatio < 0.8f -> MaterialTheme.czColors.warning
        else -> MaterialTheme.czColors.error
    }

    CzCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        contentDescription = camping.title,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            // Status accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .heightIn(min = 120.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(camping.registrationStatus.badgeTone().containerColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(CzSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            ) {

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.czColors.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Festival,
                            contentDescription = null,
                            tint = MaterialTheme.czColors.textSecondary,
                        )
                    }

                    Spacer(modifier = Modifier.width(CzSpacing.sm))

                    Text(
                        text = camping.title,
                        color = MaterialTheme.czColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        modifier = Modifier.weight(1f),
                    )

                    Spacer(modifier = Modifier.width(CzSpacing.sm))

                    CzBadge(
                        text = camping.registrationStatus.label(),
                        tone = camping.registrationStatus.badgeTone(),
                    )
                }

                // Organizer
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                ) {

                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.czColors.textSecondary,
                    )

                    Text(
                        text = camping.organizerLevel.value,
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.czColors.divider,
                    thickness = 1.dp,
                )

                // Date
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                ) {

                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.czColors.textSecondary,
                    )

                    Text(
                        text = campingDateRange(camping.startDate, camping.endDate),
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Text(
                        text = "·",
                        color = MaterialTheme.czColors.textSecondary,
                    )

                    Text(
                        text = campingDurationText(camping.startDate, camping.endDate),
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                // Location
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                ) {

                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.czColors.primary,
                    )

                    Text(
                        text = camping.location,
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }

                // Capacity
                camping.participantCapacity?.takeIf { it > 0 }?.let { capacity ->

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {

                        LinearProgressIndicator(
                            progress = { fillRatio },
                            modifier = Modifier.fillMaxWidth(),
                            color = fillColor,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {

                            Text(
                                text = "${camping.participantCount} / $capacity registered",
                                color = MaterialTheme.czColors.textSecondary,
                                style = MaterialTheme.typography.labelSmall,
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            if (showAdminInfo && camping.pendingAttendees.isNotEmpty()) {
                                CzBadge(
                                    text = "${camping.pendingAttendees.size} pending",
                                    tone = fr.ziyon.campzone.core.designsystem.BadgeTone.Warning,
                                )
                            }
                        }
                    }
                } ?: run {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                        ) {

                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.czColors.textSecondary,
                            )

                            Text(
                                text = "${camping.participantCount} registered",
                                color = MaterialTheme.czColors.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        if (showAdminInfo && camping.pendingAttendees.isNotEmpty()) {
                            CzBadge(
                                text = "${camping.pendingAttendees.size} pending",
                                tone = fr.ziyon.campzone.core.designsystem.BadgeTone.Warning,
                            )
                        }
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.czColors.textTertiary,
                modifier = Modifier.padding(end = CzSpacing.md),
            )
        }
    }
}

private fun campingDurationText(start: Date, end: Date): String {
    val days = max(
        1,
        java.util.concurrent.TimeUnit.MILLISECONDS.toDays(
            end.time - start.time
        ).toInt()
    )

    return "$days day${if (days > 1) "s" else ""}"
}

// Preview
@Preview(showBackground = true)
@Composable
private fun CampingsScreenPreview() {
    CampzoneTheme {
        CampingsScreen(
            state = CampingsUiState(
                phase = CampingsPhase.Loaded(
                    CampingsViewModel.groupedSections(
                        listOf(
                            previewCamping("summer-2026", "Summer Pathfinder Camp", 2026, 6),
                            previewCamping("fall-2026", "Fall Leaders Retreat", 2026, 9),
                        ),
                    ),
                ),
            ),
            onSearchChange = {},
            onOpenCamping = {},
            onRetry = {},
        )
    }
}

internal fun previewCamping(id: String, title: String, year: Int, monthIndex: Int): Camping {
    val cal = java.util.Calendar.getInstance().apply { clear(); set(year, monthIndex, 18) }
    val start = cal.time
    cal.add(java.util.Calendar.DAY_OF_MONTH, 6)
    return Camping(
        id = id,
        title = title,
        description = "A week of worship, games, service, music, and lake activities.",
        startDate = start,
        endDate = cal.time,
        organizerLevel = OrganizerLevel(OrganizerType.Regional, "South"),
        location = "Lake Annecy",
        registrationStatus = CampingRegistrationStatus.Open,
        participantCapacity = 120,
    )
}
