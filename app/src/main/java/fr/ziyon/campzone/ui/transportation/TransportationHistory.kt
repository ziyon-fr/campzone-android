package fr.ziyon.campzone.ui.transportation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzAvatar
import fr.ziyon.campzone.core.designsystem.CzAvatarSize
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.TransportationBooking
import fr.ziyon.campzone.data.model.TransportationCheckpoint
import fr.ziyon.campzone.data.model.TransportationLeg
import fr.ziyon.campzone.data.model.TransportationScanEvent
import java.text.DateFormat

private enum class HistoryLegFilter(val leg: TransportationLeg?) {
    All(null),
    Outbound(TransportationLeg.Outbound),
    Return(TransportationLeg.Return),
}

@Composable
fun TransportationHistoryRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    viewModel: TransportationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val camping by viewModel.camping.collectAsState()
    val bookings by viewModel.bookings.collectAsState()

    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.loadManaged(campingId, authenticatedUser)
    }

    TransportationHistoryScreen(
        uiState = uiState,
        camping = camping,
        bookings = bookings,
        onBack = onBack,
        onRetry = { viewModel.retryManaged(campingId, authenticatedUser) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransportationHistoryScreen(
    uiState: TransportationUiState,
    camping: Camping?,
    bookings: List<TransportationBooking>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    var filter by remember { mutableStateOf(HistoryLegFilter.All) }

    TransportationScaffold(title = stringResource(R.string.transportation_history_title), onBack = onBack) {
        when (uiState) {
            TransportationUiState.Loading -> CzLoadingView(
                modifier = Modifier.fillMaxSize(),
                message = stringResource(R.string.transportation_loading),
            )
            TransportationUiState.Restricted -> RestrictedTransportationState()
            is TransportationUiState.Error -> CzErrorState(
                title = stringResource(R.string.transportation_error_title),
                message = uiState.message,
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize().padding(CzSpacing.xl),
            )
            TransportationUiState.Ready -> {
                if (camping == null) {
                    RestrictedTransportationState()
                    return@TransportationScaffold
                }
                val events = remember(bookings, filter) {
                    bookings.allScanEvents().filter { (_, event) ->
                        filter.leg == null || event.leg == filter.leg
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(CzSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                ) {
                    item("header") { TransportationHistoryHeader(camping, events.size) }
                    item("filter") {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            HistoryLegFilter.entries.forEachIndexed { index, value ->
                                SegmentedButton(
                                    selected = filter == value,
                                    onClick = { filter = value },
                                    shape = SegmentedButtonDefaults.itemShape(index, HistoryLegFilter.entries.size),
                                    icon = { Icon(value.icon(), contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text(value.label()) },
                                )
                            }
                        }
                    }
                    if (events.isEmpty()) {
                        item("empty") {
                            CzEmptyState(
                                title = stringResource(R.string.transportation_history_empty_title),
                                message = if (filter == HistoryLegFilter.All) {
                                    stringResource(R.string.transportation_history_empty_all)
                                } else {
                                    stringResource(R.string.transportation_history_empty_leg)
                                },
                                modifier = Modifier.fillMaxWidth().padding(CzSpacing.xl),
                            )
                        }
                    } else {
                        items(events, key = { it.second.id }) { (booking, event) ->
                            HistoryRow(booking = booking, event = event, camping = camping)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransportationHistoryHeader(camping: Camping, count: Int) {
    val colors = MaterialTheme.czColors
    Surface(color = colors.surface, shape = RoundedCornerShape(CzRadius.xl)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
                CzAvatar(
                    imageUrl = camping.logoUrl,
                    contentDescription = camping.title,
                    initials = camping.title.firstOrNull()?.toString(),
                    size = CzAvatarSize.Small,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(camping.title, color = colors.textPrimary, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (count == 1) {
                            stringResource(R.string.transportation_history_count_one, count)
                        } else {
                            stringResource(R.string.transportation_history_count_other, count)
                        },
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Text(stringResource(R.string.transportation_history_subtitle), color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun HistoryRow(booking: TransportationBooking, event: TransportationScanEvent, camping: Camping) {
    val colors = MaterialTheme.czColors
    val photoUrl = camping.attendees.firstOrNull {
        it.id == booking.registrationId || it.id == booking.participantId
    }?.photoUrl
    val tint = if (event.checkpoint == TransportationCheckpoint.Arrival) colors.success else colors.warning
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(CzRadius.xl),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.divider),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            CzAvatar(
                imageUrl = photoUrl,
                contentDescription = booking.participantName,
                initials = booking.participantName.firstOrNull()?.toString(),
                size = CzAvatarSize.Small,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(booking.participantName, color = colors.textPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(event.at),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(event.leg.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                    Text(event.leg.displayName(), color = tint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text("·", color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                    Icon(event.checkpoint.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                    Text(checkpointLabel(event.leg, event.checkpoint), color = tint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(13.dp))
                    Text(reviewerLabel(event), color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                    if (!event.location.isNullOrBlank()) {
                        Text("·", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                        Icon(Icons.Filled.Place, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(13.dp))
                        Text(event.location, color = colors.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun checkpointLabel(leg: TransportationLeg, checkpoint: TransportationCheckpoint): String = when {
    leg == TransportationLeg.Outbound && checkpoint == TransportationCheckpoint.Departure ->
        stringResource(R.string.transportation_history_checkpoint_outbound_departure)
    leg == TransportationLeg.Outbound ->
        stringResource(R.string.transportation_history_checkpoint_outbound_arrival)
    checkpoint == TransportationCheckpoint.Departure ->
        stringResource(R.string.transportation_history_checkpoint_return_departure)
    else -> stringResource(R.string.transportation_history_checkpoint_return_arrival)
}

@Composable
private fun reviewerLabel(event: TransportationScanEvent): String {
    val byName = event.byName
    return when {
        !byName.isNullOrBlank() -> byName
        event.by.isBlank() -> stringResource(R.string.transportation_history_unknown_marshal)
        else -> stringResource(R.string.transportation_history_marshal, event.by.takeLast(6))
    }
}

@Composable
private fun HistoryLegFilter.label(): String = when (this) {
    HistoryLegFilter.All -> stringResource(R.string.transportation_history_filter_all)
    HistoryLegFilter.Outbound -> stringResource(R.string.transportation_leg_outbound)
    HistoryLegFilter.Return -> stringResource(R.string.transportation_leg_return)
}

private fun HistoryLegFilter.icon() = when (this) {
    HistoryLegFilter.All -> Icons.AutoMirrored.Filled.List
    HistoryLegFilter.Outbound -> TransportationLeg.Outbound.icon()
    HistoryLegFilter.Return -> TransportationLeg.Return.icon()
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun TransportationHistoryScreenPreview() {
    fr.ziyon.campzone.core.designsystem.CampzoneTheme {
        TransportationHistoryScreen(
            uiState = TransportationUiState.Ready,
            camping = previewTransportationCamping(),
            bookings = previewTransportationBookings(),
            onBack = {},
            onRetry = {},
        )
    }
}
