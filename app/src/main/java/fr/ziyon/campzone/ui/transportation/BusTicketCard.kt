package fr.ziyon.campzone.ui.transportation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzAvatar
import fr.ziyon.campzone.core.designsystem.CzAvatarSize
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.TransportationBooking
import fr.ziyon.campzone.data.model.TransportationCheckpoint
import fr.ziyon.campzone.data.model.TransportationLeg
import fr.ziyon.campzone.data.model.TransportationLegProgress
import fr.ziyon.campzone.data.model.TransportationScanEvent
import androidx.compose.ui.res.stringResource
import java.text.DateFormat

/**
 * Round-trip boarding pass mirroring the iOS `BusTicketCard`: brand header,
 * passenger block, OUTBOUND (and RETURN when covered) leg cards each with route
 * + per-checkpoint timeline, a single shared QR (only once the registration is
 * approved), and a perforated stub. The QR encodes only the opaque ticket
 * token; no mutable payment data is trusted from a scan.
 */
@Composable
fun BusTicketCard(
    booking: TransportationBooking,
    camping: Camping,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val approved = camping.attendees.any {
        it.id == booking.registrationId && it.registrationStatus == RegistrationApprovalStatus.Approved
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(CzRadius.xl),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.divider),
    ) {
        Column {
            TicketHeader(booking, camping)
            TicketPassenger(booking, camping)
            Column(
                modifier = Modifier.padding(horizontal = CzSpacing.lg).padding(bottom = CzSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            ) {
                LegCard(booking, camping, TransportationLeg.Outbound)
                if (booking.coversReturn) {
                    LegCard(booking, camping, TransportationLeg.Return)
                }
            }
            TicketQrSection(booking, approved)
            PerforationLine()
            TicketStub(booking, camping)
        }
    }
}

private fun referenceNumber(token: String): String {
    val cleaned = token.uppercase().filter { it.isLetterOrDigit() }
    val core = cleaned.take(8).padEnd(8, '0')
    return "CZ-${core.take(4)}-${core.takeLast(4)}"
}

@Composable
private fun TicketHeader(booking: TransportationBooking, camping: Camping) {
    val colors = MaterialTheme.czColors
    val option = camping.transportationOption(booking.transportationOptionId)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.ember.copy(alpha = 0.06f))
            .padding(CzSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(option?.mode?.icon() ?: Icons.Filled.DirectionsBus, contentDescription = null, tint = colors.ember, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = stringResource(if (booking.coversReturn) R.string.transportation_pass_round_trip else R.string.transportation_pass_one_way),
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(camping.title, color = colors.textPrimary, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (booking.isActive) {
            StatusPill(text = booking.paymentStatus.label(), color = booking.paymentStatus.color())
        } else {
            StatusPill(text = stringResource(R.string.transportation_status_inactive), color = colors.error)
        }
    }
}

@Composable
private fun TicketPassenger(booking: TransportationBooking, camping: Camping) {
    val colors = MaterialTheme.czColors
    val photoUrl = camping.attendees.firstOrNull {
        it.id == booking.registrationId || it.id == booking.participantId
    }?.photoUrl
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CzSpacing.lg, vertical = CzSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CzAvatar(
            imageUrl = photoUrl,
            contentDescription = booking.participantName,
            initials = booking.participantName.firstOrNull()?.toString(),
            size = CzAvatarSize.Medium,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.transportation_passenger), color = colors.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(booking.participantName, color = colors.textPrimary, style = MaterialTheme.typography.titleMedium)
            Text(booking.participantKind.displayName(), color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.transportation_ref), color = colors.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(referenceNumber(booking.ticketToken), color = colors.textPrimary, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun LegCard(booking: TransportationBooking, camping: Camping, leg: TransportationLeg) {
    val colors = MaterialTheme.czColors
    val progress = booking.progress(leg)
    val departure = booking.scanEvent(leg, TransportationCheckpoint.Departure)
    val arrival = booking.scanEvent(leg, TransportationCheckpoint.Arrival)
    val origin = booking.transportationOptionName
        ?: camping.transportationOption(booking.transportationOptionId)?.resolvedName
        ?: stringResource(R.string.transportation_pickup_point)
    val from = if (leg == TransportationLeg.Outbound) origin else camping.location
    val to = if (leg == TransportationLeg.Outbound) camping.location else origin
    val (bg, border) = when (progress) {
        TransportationLegProgress.NotStarted -> colors.background.copy(alpha = 0.6f) to colors.divider
        TransportationLegProgress.InTransit -> colors.warning.copy(alpha = 0.08f) to colors.warning.copy(alpha = 0.30f)
        TransportationLegProgress.Arrived -> colors.success.copy(alpha = 0.08f) to colors.success.copy(alpha = 0.30f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.lg))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(CzRadius.lg))
            .padding(CzSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            Icon(leg.icon(), contentDescription = null, tint = colors.ember, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text(leg.displayName(), color = colors.textPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(leg.subtitle(), color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
            StatusPill(text = progress.displayName(), color = progress.tint())
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            RouteEndpoint(label = stringResource(R.string.transportation_from), value = from, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            RouteEndpoint(label = stringResource(R.string.transportation_to), value = to, modifier = Modifier.weight(1f))
        }
        TimelineRow(
            label = stringResource(R.string.transportation_timeline_boarded),
            event = departure,
            isCompleted = progress != TransportationLegProgress.NotStarted,
            fallback = stringResource(R.string.transportation_timeline_awaiting_departure),
        )
        TimelineRow(
            label = stringResource(R.string.transportation_timeline_arrived),
            event = arrival,
            isCompleted = progress == TransportationLegProgress.Arrived,
            fallback = if (progress == TransportationLegProgress.InTransit) {
                stringResource(R.string.transportation_timeline_awaiting_arrival)
            } else {
                stringResource(R.string.transportation_timeline_dash)
            },
        )
    }
}

@Composable
private fun RouteEndpoint(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.czColors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = colors.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(value, color = colors.textPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TimelineRow(
    label: String,
    event: TransportationScanEvent?,
    isCompleted: Boolean,
    fallback: String,
) {
    val colors = MaterialTheme.czColors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        Icon(
            if (isCompleted) Icons.Filled.CheckCircle else Icons.Filled.DirectionsBus,
            contentDescription = null,
            tint = if (isCompleted) colors.success else colors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Text(label, color = colors.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        if (event != null) {
            Text(
                text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(event.at),
                color = colors.textPrimary,
                style = MaterialTheme.typography.labelSmall,
            )
            if (!event.location.isNullOrBlank()) {
                Text("· ${event.location}", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Text(fallback, color = colors.textSecondary.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TicketQrSection(booking: TransportationBooking, approved: Boolean) {
    val colors = MaterialTheme.czColors
    when {
        !booking.isActive -> TicketStateBlock(
            title = stringResource(R.string.transportation_ticket_cancelled_title),
            message = stringResource(R.string.transportation_ticket_cancelled_message),
        )
        approved -> {
            val qr = fr.ziyon.campzone.data.model.TransportationTicketPayload.fromBooking(booking).encoded()
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = CzSpacing.lg).padding(bottom = CzSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            ) {
                QrCodeImage(value = qr, modifier = Modifier.size(200.dp))
                Text(
                    text = stringResource(
                        if (booking.coversReturn) R.string.transportation_ticket_qr_hint_round_trip else R.string.transportation_ticket_qr_hint_one_way,
                    ),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        else -> TicketStateBlock(
            title = stringResource(R.string.transportation_ticket_awaiting_title),
            message = stringResource(R.string.transportation_ticket_awaiting_message),
        )
    }
}

@Composable
private fun TicketStateBlock(title: String, message: String) {
    val colors = MaterialTheme.czColors
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CzSpacing.lg).padding(bottom = CzSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Text(title, color = colors.textPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(message, color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PerforationLine() {
    val colors = MaterialTheme.czColors
    Box(modifier = Modifier.fillMaxWidth().height(CzSpacing.md), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxWidth().height(1.dp)) {
            drawLine(
                color = colors.ember,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f),
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun BusTicketCardPreview() {
    fr.ziyon.campzone.core.designsystem.CampzoneTheme {
        Column(modifier = Modifier.padding(CzSpacing.lg)) {
            BusTicketCard(
                booking = previewTransportationBookings().first(),
                camping = previewTransportationCamping(),
            )
        }
    }
}

@Composable
private fun TicketStub(booking: TransportationBooking, camping: Camping) {
    val colors = MaterialTheme.czColors
    Column(
        modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StatusPill(text = booking.tripStatusLabel(), color = booking.tripStatusColor())
            Box(Modifier.weight(1f))
            Text(referenceNumber(booking.ticketToken), color = colors.textSecondary, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
        }
        val coverage = stringResource(
            if (booking.coversReturn) R.string.transportation_ticket_stub_round_trip else R.string.transportation_ticket_stub_one_way,
        )
        Text(
            text = stringResource(R.string.transportation_ticket_stub_legal, coverage, camping.title),
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
