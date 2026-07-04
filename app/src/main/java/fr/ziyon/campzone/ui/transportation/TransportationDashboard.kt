package fr.ziyon.campzone.ui.transportation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingTransportationOption
import fr.ziyon.campzone.data.model.TransportationBooking
import fr.ziyon.campzone.data.model.TransportationLeg
import fr.ziyon.campzone.data.model.TransportationLegProgress
import fr.ziyon.campzone.data.model.TransportationPaymentStatus
import fr.ziyon.campzone.ui.camping.localizedDisplayName
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun TransportationDashboardRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenScanner: (String) -> Unit,
    onOpenHistory: (String) -> Unit,
    viewModel: TransportationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val camping by viewModel.camping.collectAsState()
    val bookings by viewModel.bookings.collectAsState()
    val operationError by viewModel.operationError.collectAsState()

    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.loadManaged(campingId, authenticatedUser)
    }

    TransportationDashboardScreen(
        uiState = uiState,
        camping = camping,
        bookings = bookings,
        operationError = operationError,
        onBack = onBack,
        onOpenScanner = { camping?.let { onOpenScanner(it.id) } },
        onOpenHistory = { camping?.let { onOpenHistory(it.id) } },
        onRetry = { viewModel.retryManaged(campingId, authenticatedUser) },
        onPaymentChange = viewModel::updatePaymentStatus,
        onCancel = { viewModel.cancelBooking(it) },
        onAddVoyager = { attendee, option -> camping?.let { viewModel.addVoyager(it, attendee, option) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransportationDashboardScreen(
    uiState: TransportationUiState,
    camping: Camping?,
    bookings: List<TransportationBooking>,
    operationError: String?,
    onBack: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenHistory: () -> Unit,
    onRetry: () -> Unit,
    onPaymentChange: (TransportationBooking, TransportationPaymentStatus) -> Unit,
    onCancel: (TransportationBooking) -> Unit,
    onAddVoyager: (CampingAttendee, CampingTransportationOption?) -> Unit,
) {
    var showAddSheet by remember { mutableStateOf(false) }

    TransportationScaffold(
        title = stringResource(R.string.transportation_dashboard_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = onOpenScanner) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.transportation_open_scanner))
            }
            IconButton(onClick = onOpenHistory) {
                Icon(Icons.Outlined.History, contentDescription = stringResource(R.string.transportation_scan_history))
            }
        },
    ) {
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
                val colors = MaterialTheme.czColors
                val sections = if (bookings.isEmpty()) {
                    emptyList()
                } else {
                    listOf(
                        DashboardSection(stringResource(R.string.transportation_section_needs_payment), bookings.pendingPayment(), colors.warning),
                        DashboardSection(stringResource(R.string.transportation_section_outbound_ready), bookings.notStarted(TransportationLeg.Outbound), colors.textSecondary),
                        DashboardSection(stringResource(R.string.transportation_section_outbound_on_bus), bookings.inTransit(TransportationLeg.Outbound), colors.warning),
                        DashboardSection(stringResource(R.string.transportation_section_at_camp), bookings.atCamp(), colors.success),
                        DashboardSection(stringResource(R.string.transportation_section_return_on_bus), bookings.inTransit(TransportationLeg.Return), colors.warning),
                        DashboardSection(stringResource(R.string.transportation_section_trip_complete), bookings.completedTrips(), colors.success),
                        DashboardSection(stringResource(R.string.transportation_section_cancelled), bookings.inactive(), colors.error),
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(CzSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
                ) {
                    item("header") {
                        TransportationDashboardHeader(camping, bookings, onAddVoyager = { showAddSheet = true })
                    }
                    if (bookings.isEmpty()) {
                        item("empty") {
                            CzEmptyState(
                                title = stringResource(R.string.transportation_empty_dashboard_title),
                                message = stringResource(R.string.transportation_empty_dashboard_message),
                                modifier = Modifier.fillMaxWidth().padding(CzSpacing.xl),
                            )
                        }
                    } else {
                        item("progress") { TransportationTripProgressCard(bookings) }
                        sections.forEach { section ->
                            bookingSection(section, camping, onPaymentChange, onCancel)
                        }
                    }
                    if (operationError != null) {
                        item("op-error") {
                            Text(operationError, color = colors.error, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                if (showAddSheet) {
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    ModalBottomSheet(
                        onDismissRequest = { showAddSheet = false },
                        sheetState = sheetState,
                        containerColor = MaterialTheme.czColors.surface,
                    ) {
                        AddVoyagerSheet(
                            camping = camping,
                            bookings = bookings,
                            onAdd = { attendee, option ->
                                onAddVoyager(attendee, option)
                                showAddSheet = false
                            },
                        )
                    }
                }
            }
        }
    }
}

private data class DashboardSection(
    val title: String,
    val bookings: List<TransportationBooking>,
    val accent: Color,
)

private fun androidx.compose.foundation.lazy.LazyListScope.bookingSection(
    section: DashboardSection,
    camping: Camping,
    onPaymentChange: (TransportationBooking, TransportationPaymentStatus) -> Unit,
    onCancel: (TransportationBooking) -> Unit,
) {
    if (section.bookings.isEmpty()) return
    item("section-${section.title}") {
        TransportationSectionHeader(section.title, Icons.Outlined.BarChart)
    }
    items(section.bookings, key = { "${section.title}-${it.id}" }) { booking ->
        TransportationBookingRow(
            booking = booking,
            camping = camping,
            accent = section.accent,
            onPaymentChange = { onPaymentChange(booking, it) },
            onCancel = { onCancel(booking) },
        )
    }
}

@Composable
private fun TransportationDashboardHeader(
    camping: Camping,
    bookings: List<TransportationBooking>,
    onAddVoyager: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val active = bookings.count { it.isActive }
    Surface(color = colors.surface, shape = RoundedCornerShape(CzRadius.xl)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CzAvatar(
                imageUrl = camping.logoUrl,
                contentDescription = camping.title,
                initials = camping.title.firstOrNull()?.toString(),
                size = CzAvatarSize.Medium,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(dateRangeText(camping), color = colors.ember, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(camping.title, color = colors.textPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.transportation_dashboard_active_bookings, active), color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
            }
            Surface(
                color = colors.ember.copy(alpha = 0.12f),
                shape = RoundedCornerShape(CzRadius.full),
            ) {
                IconButton(onClick = onAddVoyager) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = stringResource(R.string.transportation_add_voyager), tint = colors.ember)
                }
            }
        }
    }
}

@Composable
private fun TransportationTripProgressCard(bookings: List<TransportationBooking>) {
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        TransportationSectionHeader(stringResource(R.string.transportation_trip_progress), Icons.Outlined.BarChart)
        Surface(color = MaterialTheme.czColors.surface, shape = RoundedCornerShape(CzRadius.xl)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(CzSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
            ) {
                TransportationLegProgressRow(bookings, TransportationLeg.Outbound)
                TransportationLegProgressRow(bookings, TransportationLeg.Return)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransportationBookingRow(
    booking: TransportationBooking,
    camping: Camping,
    accent: Color,
    onPaymentChange: (TransportationPaymentStatus) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val photoUrl = camping.attendees.firstOrNull {
        it.id == booking.registrationId || it.id == booking.participantId
    }?.photoUrl
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(CzRadius.xl),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.divider),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
                CzAvatar(
                    imageUrl = photoUrl,
                    contentDescription = booking.participantName,
                    initials = booking.participantName.firstOrNull()?.toString(),
                    size = CzAvatarSize.Small,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(booking.participantName, color = colors.textPrimary, style = MaterialTheme.typography.titleSmall)
                    Text(booking.participantKind.displayName(), color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                }
                StatusPill(text = booking.tripStatusLabel(), color = accent)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                LegBadge(booking.let { TransportationLeg.Outbound to it.progress(TransportationLeg.Outbound) })
                if (booking.coversReturn) {
                    LegBadge(TransportationLeg.Return to booking.progress(TransportationLeg.Return))
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TransportationPaymentStatus.entries.forEachIndexed { index, status ->
                    SegmentedButton(
                        selected = booking.paymentStatus == status,
                        onClick = { onPaymentChange(status) },
                        shape = SegmentedButtonDefaults.itemShape(index, TransportationPaymentStatus.entries.size),
                        label = { Text(status.label(), style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val optionName = booking.transportationOptionName
                if (!optionName.isNullOrBlank()) {
                    Text(optionName, color = colors.textSecondary, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                } else {
                    Box(Modifier.weight(1f))
                }
                if (booking.isActive) {
                    TextButton(onClick = onCancel) {
                        Icon(Icons.Filled.Cancel, contentDescription = null, tint = colors.error, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.transportation_cancel), color = colors.error, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = CzSpacing.xs))
                    }
                }
            }
        }
    }
}

@Composable
private fun LegBadge(state: Pair<TransportationLeg, TransportationLegProgress>) {
    val (leg, progress) = state
    val tint = progress.tint()
    Surface(color = tint.copy(alpha = 0.12f), shape = RoundedCornerShape(CzRadius.full)) {
        Row(
            modifier = Modifier.padding(horizontal = CzSpacing.sm, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(leg.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
            Text("${leg.shortName()} · ${progress.displayName()}", color = tint, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AddVoyagerSheet(
    camping: Camping,
    bookings: List<TransportationBooking>,
    onAdd: (CampingAttendee, CampingTransportationOption?) -> Unit,
) {
    val colors = MaterialTheme.czColors
    val ticketedOptions = camping.transportationOptions.filter { it.issuesTicket }
    val bookedIds = bookings.filter { it.isActive }.map { it.participantId }.toSet()
    val available = camping.approvedAttendees
        .filterNot { it.id in bookedIds }
        .sortedBy { it.displayName.lowercase() }
    var selectedOptionId by remember { mutableStateOf(ticketedOptions.firstOrNull()?.id ?: "") }
    val selectedOption = camping.transportationOption(selectedOptionId) ?: ticketedOptions.firstOrNull()

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CzSpacing.lg).padding(bottom = CzSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Text(stringResource(R.string.transportation_add_voyager_title), color = colors.textPrimary, style = MaterialTheme.typography.titleMedium)

        if (ticketedOptions.isNotEmpty()) {
            TransportationSectionHeader(stringResource(R.string.transportation_add_voyager_option), Icons.Filled.PersonAdd)
            Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                ticketedOptions.forEach { option ->
                    OptionRow(
                        option = option,
                        selected = option.id == selectedOptionId,
                        onSelect = { selectedOptionId = option.id },
                    )
                }
            }
        }

        TransportationSectionHeader(stringResource(R.string.transportation_add_voyager_members), Icons.Filled.PersonAdd)
        if (available.isEmpty()) {
            Text(stringResource(R.string.transportation_add_voyager_all_booked), color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                available.forEach { attendee ->
                    AttendeeRow(attendee = attendee, onClick = { onAdd(attendee, selectedOption) })
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    option: CampingTransportationOption,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val optionName = option.name.trim().takeUnless { it.isBlank() } ?: option.mode.localizedDisplayName()
    Surface(
        color = if (selected) colors.ember.copy(alpha = 0.12f) else colors.background,
        shape = RoundedCornerShape(CzRadius.md),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) colors.ember else colors.divider),
        onClick = onSelect,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(option.mode.icon(), contentDescription = null, tint = colors.ember, modifier = Modifier.size(18.dp))
            Text(optionName, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun AttendeeRow(attendee: CampingAttendee, onClick: () -> Unit) {
    val colors = MaterialTheme.czColors
    Surface(
        color = colors.background,
        shape = RoundedCornerShape(CzRadius.md),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CzAvatar(
                imageUrl = attendee.photoUrl,
                contentDescription = attendee.displayName,
                initials = attendee.displayName.firstOrNull()?.toString(),
                size = CzAvatarSize.Small,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(attendee.displayName, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                Text(attendee.participantKind.displayName(), color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
            }
            Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = colors.ember, modifier = Modifier.size(18.dp))
        }
    }
}

private val dateRangeFormatter = SimpleDateFormat("MMM d", Locale.getDefault())

private fun dateRangeText(camping: Camping): String =
    "${dateRangeFormatter.format(camping.startDate)} – ${dateRangeFormatter.format(camping.endDate)}"

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun TransportationDashboardScreenPreview() {
    fr.ziyon.campzone.core.designsystem.CampzoneTheme {
        TransportationDashboardScreen(
            uiState = TransportationUiState.Ready,
            camping = previewTransportationCamping(),
            bookings = previewTransportationBookings(),
            operationError = null,
            onBack = {},
            onOpenScanner = {},
            onOpenHistory = {},
            onRetry = {},
            onPaymentChange = { _, _ -> },
            onCancel = {},
            onAddVoyager = { _, _ -> },
        )
    }
}
