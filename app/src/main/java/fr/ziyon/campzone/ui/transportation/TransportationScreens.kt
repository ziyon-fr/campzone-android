package fr.ziyon.campzone.ui.transportation

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
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
import fr.ziyon.campzone.data.model.PaymentKind
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import fr.ziyon.campzone.data.model.TransportationBoardingStatus
import fr.ziyon.campzone.data.model.TransportationBooking
import fr.ziyon.campzone.data.model.TransportationCheckpoint
import fr.ziyon.campzone.data.model.TransportationLeg
import fr.ziyon.campzone.data.model.TransportationLegProgress
import fr.ziyon.campzone.data.model.TransportationMode
import fr.ziyon.campzone.data.model.TransportationPaymentStatus
import fr.ziyon.campzone.data.model.TransportationScanEvent
import fr.ziyon.campzone.data.model.TransportationScanResult
import fr.ziyon.campzone.data.model.TransportationTicketPayload
import fr.ziyon.campzone.data.payments.PaymentRequest
import fr.ziyon.campzone.ui.camping.previewCamping
import fr.ziyon.campzone.ui.checkin.QrCameraPreview
import fr.ziyon.campzone.ui.payments.CzPaymentButton
import java.util.Date

// ---------------------------------------------------------------------------
// Routes
// ---------------------------------------------------------------------------

@Composable
fun TransportationTicketsRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    viewModel: TransportationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val camping by viewModel.camping.collectAsState()
    val bookings by viewModel.bookings.collectAsState()

    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.loadTickets(campingId, authenticatedUser)
    }

    TransportationTicketsScreen(
        uiState = uiState,
        camping = camping,
        bookings = bookings,
        onBack = onBack,
        onRetry = { viewModel.retryTickets(campingId, authenticatedUser) },
        onFarePaid = viewModel::reloadTickets,
    )
}

@Composable
fun TransportationScannerRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenHistory: (String) -> Unit,
    viewModel: TransportationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val camping by viewModel.camping.collectAsState()
    val bookings by viewModel.bookings.collectAsState()
    val result by viewModel.lastScanResult.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.loadManaged(campingId, authenticatedUser)
    }

    TransportationScannerScreen(
        uiState = uiState,
        camping = camping,
        bookings = bookings,
        result = result,
        isScanning = isScanning,
        onScan = { value, leg, checkpoint -> viewModel.handleScan(value, leg, checkpoint) },
        onDismissResult = viewModel::dismissScanResult,
        onBack = onBack,
        onOpenHistory = { camping?.let { onOpenHistory(it.id) } },
        onRetry = { viewModel.retryManaged(campingId, authenticatedUser) },
    )
}

// ---------------------------------------------------------------------------
// My Passes (passenger)
// ---------------------------------------------------------------------------

@Composable
private fun TransportationTicketsScreen(
    uiState: TransportationUiState,
    camping: Camping?,
    bookings: List<TransportationBooking>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onFarePaid: () -> Unit,
) {
    TransportationScaffold(title = stringResource(R.string.transportation_tickets_title), onBack = onBack) {
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
                if (bookings.isEmpty() || camping == null) {
                    CzEmptyState(
                        title = stringResource(R.string.transportation_empty_tickets_title),
                        message = stringResource(R.string.transportation_empty_tickets_message),
                        modifier = Modifier.fillMaxSize().padding(CzSpacing.xl),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(CzSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
                    ) {
                        item("header") { TransportationTicketsHeader(camping) }
                        item("summary") { TransportationTripSummary(bookings) }
                        items(bookings, key = { it.id }) { booking ->
                            Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                                BusTicketCard(booking = booking, camping = camping)
                                TransportationFareCta(
                                    booking = booking,
                                    camping = camping,
                                    onPaid = onFarePaid,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Per-booking fare CTA shown beneath each [BusTicketCard], mirroring the iOS
 * `TransportationTicketsView.transportationFee`. When the option the booking was
 * made under carries a fee and the fare is still unpaid, it presents the Stripe
 * PaymentSheet ([CzPaymentButton]) for `kind = transportation`,
 * `referenceID = booking.id`; the backend flips `paymentStatus` to `paid`.
 * Paid / waived fares are already reflected by the status pill in the card
 * header, so the CTA only renders for the actionable unpaid case.
 */
@Composable
private fun TransportationFareCta(
    booking: TransportationBooking,
    camping: Camping,
    onPaid: () -> Unit,
) {
    if (!booking.isActive) return
    if (booking.paymentStatus != TransportationPaymentStatus.Unpaid) return

    val option = camping.transportationOption(booking.transportationOptionId)
        ?: camping.attendees.firstOrNull {
            it.id == booking.participantId || it.id == booking.registrationId
        }?.let { camping.transportationOption(it.transportationOptionId) }
    val feeCents = option?.feeCents ?: return
    if (feeCents <= 0) return

    CzPaymentButton(
        request = PaymentRequest(
            kind = PaymentKind.Transportation,
            campingId = camping.id,
            referenceId = booking.id,
            amountCents = feeCents,
            currency = option.currency,
        ),
        onPaid = onPaid,
        modifier = Modifier.fillMaxWidth().padding(horizontal = CzSpacing.xs),
    )
}

@Composable
private fun TransportationTicketsHeader(camping: Camping) {
    Surface(color = MaterialTheme.czColors.surface, shape = RoundedCornerShape(CzRadius.xl)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Text(camping.title, color = MaterialTheme.czColors.textPrimary, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.transportation_tickets_header_hint),
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TransportationTripSummary(bookings: List<TransportationBooking>) {
    val total = bookings.size
    val outboundArrived = bookings.count { it.progress(TransportationLeg.Outbound) == TransportationLegProgress.Arrived }
    val home = bookings.count {
        it.coversReturn && it.progress(TransportationLeg.Return) == TransportationLegProgress.Arrived
    }
    val unpaid = bookings.count { it.paymentStatus == TransportationPaymentStatus.Unpaid }
    Surface(color = MaterialTheme.czColors.surface, shape = RoundedCornerShape(CzRadius.xl)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            TransportationSectionHeader(stringResource(R.string.transportation_tickets_overview), Icons.Outlined.BarChart)
            Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                SummaryChip(
                    icon = Icons.Filled.QrCode,
                    value = total.toString(),
                    label = stringResource(if (total == 1) R.string.transportation_summary_pass else R.string.transportation_summary_passes),
                    tint = MaterialTheme.czColors.ember,
                    modifier = Modifier.weight(1f),
                )
                SummaryChip(
                    icon = Icons.Rounded.ArrowUpward,
                    value = "$outboundArrived/$total",
                    label = stringResource(R.string.transportation_summary_at_camp),
                    tint = MaterialTheme.czColors.success,
                    modifier = Modifier.weight(1f),
                )
                SummaryChip(
                    icon = Icons.Rounded.ArrowDownward,
                    value = "$home/$total",
                    label = stringResource(R.string.transportation_summary_home),
                    tint = MaterialTheme.czColors.pine,
                    modifier = Modifier.weight(1f),
                )
            }
            if (unpaid > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CreditCard, contentDescription = null, tint = MaterialTheme.czColors.warning, modifier = Modifier.size(16.dp))
                    Text(
                        text = if (unpaid == 1) {
                            stringResource(R.string.transportation_summary_unpaid_one)
                        } else {
                            stringResource(R.string.transportation_summary_unpaid_other, unpaid)
                        },
                        color = MaterialTheme.czColors.warning,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(
    icon: ImageVector,
    value: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(color = tint.copy(alpha = 0.10f), shape = RoundedCornerShape(CzRadius.md), modifier = modifier) {
        Column(
            modifier = Modifier.padding(CzSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                Text(label.uppercase(), color = tint, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(value, color = MaterialTheme.czColors.textPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------
// Scanner (marshal)
// ---------------------------------------------------------------------------

@Composable
private fun TransportationScannerScreen(
    uiState: TransportationUiState,
    camping: Camping?,
    bookings: List<TransportationBooking>,
    result: TransportationScanResult?,
    isScanning: Boolean,
    onScan: (String, TransportationLeg, TransportationCheckpoint) -> Unit,
    onDismissResult: () -> Unit,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onRetry: () -> Unit,
) {
    var leg by remember { mutableStateOf(TransportationLeg.Outbound) }
    var checkpoint by remember { mutableStateOf(TransportationCheckpoint.Departure) }

    TransportationScaffold(title = stringResource(R.string.transportation_scanner_title), onBack = onBack) {
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
            TransportationUiState.Ready -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(CzSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
            ) {
                item("mode") {
                    ScannerModeCard(
                        leg = leg,
                        checkpoint = checkpoint,
                        onLegChange = { leg = it },
                        onCheckpointChange = { checkpoint = it },
                    )
                }
                item("scanner") {
                    TransportationCameraCard(
                        isScanning = isScanning,
                        onQrScanned = { onScan(it, leg, checkpoint) },
                    )
                }
                item("result") { TransportationScanStatusCard(result = result, onDismiss = onDismissResult) }
                item("tally") { TransportationLiveTally(bookings = bookings, onOpenHistory = onOpenHistory) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannerModeCard(
    leg: TransportationLeg,
    checkpoint: TransportationCheckpoint,
    onLegChange: (TransportationLeg) -> Unit,
    onCheckpointChange: (TransportationCheckpoint) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        TransportationSectionHeader(stringResource(R.string.transportation_scan_mode), Icons.Filled.QrCodeScanner)
        Surface(color = MaterialTheme.czColors.surface, shape = RoundedCornerShape(CzRadius.xl)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(CzSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    TransportationLeg.entries.forEachIndexed { index, value ->
                        SegmentedButton(
                            selected = leg == value,
                            onClick = { onLegChange(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, TransportationLeg.entries.size),
                            icon = { Icon(value.icon(), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(value.displayName()) },
                        )
                    }
                }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    TransportationCheckpoint.entries.forEachIndexed { index, value ->
                        SegmentedButton(
                            selected = checkpoint == value,
                            onClick = { onCheckpointChange(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, TransportationCheckpoint.entries.size),
                            icon = { Icon(value.icon(), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(value.displayName()) },
                        )
                    }
                }
                Surface(color = MaterialTheme.czColors.ember.copy(alpha = 0.10f), shape = RoundedCornerShape(CzRadius.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(leg.icon(), contentDescription = null, tint = MaterialTheme.czColors.ember)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(scanModeTitle(leg, checkpoint), color = MaterialTheme.czColors.textPrimary, style = MaterialTheme.typography.titleSmall)
                            Text(scanModeSubtitle(leg, checkpoint), color = MaterialTheme.czColors.textSecondary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransportationLiveTally(
    bookings: List<TransportationBooking>,
    onOpenHistory: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        TransportationSectionHeader(stringResource(R.string.transportation_live_progress), Icons.Outlined.BarChart)
        Surface(color = MaterialTheme.czColors.surface, shape = RoundedCornerShape(CzRadius.xl)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(CzSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
            ) {
                TransportationLegProgressRow(bookings, TransportationLeg.Outbound)
                TransportationLegProgressRow(bookings, TransportationLeg.Return)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    CzButton(
                        text = stringResource(R.string.transportation_scan_history),
                        onClick = onOpenHistory,
                        variant = CzButtonVariant.Outline,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared composables (internal - reused by Dashboard / History / BusTicketCard)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransportationScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.czColors.background,
        contentWindowInsets = WindowInsets(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, color = MaterialTheme.czColors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = { actions() },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.czColors.background,
                    scrolledContainerColor = MaterialTheme.czColors.background,
                ),
                windowInsets = WindowInsets(),
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) { content() }
    }
}

@Composable
internal fun RestrictedTransportationState() {
    CzEmptyState(
        title = stringResource(R.string.transportation_restricted_title),
        message = stringResource(R.string.transportation_restricted_message),
        modifier = Modifier.fillMaxSize().padding(CzSpacing.xl),
    )
}

@Composable
internal fun TransportationSectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.czColors.ember, modifier = Modifier.size(18.dp))
        Text(title, color = MaterialTheme.czColors.textSecondary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun StatusPill(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), contentColor = color, shape = RoundedCornerShape(CzRadius.full)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = CzSpacing.sm, vertical = 4.dp),
        )
    }
}

/** One leg's progress bar + Boarded/Arrived/Booked tally line. */
@Composable
internal fun TransportationLegProgressRow(
    bookings: List<TransportationBooking>,
    leg: TransportationLeg,
) {
    val total = bookings.legTotal(leg)
    val arrived = bookings.arrived(leg).size
    val boarded = bookings.inTransit(leg).size + arrived
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            Icon(leg.icon(), contentDescription = null, tint = MaterialTheme.czColors.ember, modifier = Modifier.size(18.dp))
            Text(leg.displayName(), color = MaterialTheme.czColors.textPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                text = if (total == 0) {
                    stringResource(R.string.transportation_progress_no_bookings)
                } else {
                    stringResource(R.string.transportation_progress_arrived_count, arrived, total)
                },
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else arrived.toFloat() / total.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.czColors.success,
            trackColor = MaterialTheme.czColors.divider,
        )
        Text(
            text = stringResource(R.string.transportation_tally_line, boarded, arrived, total),
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
internal fun QrCodeImage(value: String, modifier: Modifier = Modifier) {
    val bitmap = remember(value) { generateQrCode(value) }
    Surface(
        modifier = modifier.semantics { contentDescription = "Transportation QR code" },
        color = Color.White,
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.transportation_qr_code_cd),
                modifier = Modifier.fillMaxSize().padding(CzSpacing.sm),
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(CzSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.QrCode, contentDescription = null, tint = MaterialTheme.czColors.pine)
                Text(value, color = MaterialTheme.czColors.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

internal fun generateQrCode(value: String, sidePx: Int = 768): ImageBitmap? =
    runCatching {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to 2,
        )
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, sidePx, sidePx, hints)
        val bitmap = createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                bitmap[x, y] = if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
            }
        }
        bitmap.asImageBitmap()
    }.getOrNull()

@Composable
private fun TransportationCameraCard(
    isScanning: Boolean,
    onQrScanned: (String) -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(CzRadius.xl))
            .background(Color.Black)
            .border(2.dp, MaterialTheme.czColors.ember.copy(alpha = 0.6f), RoundedCornerShape(CzRadius.xl)),
        contentAlignment = Alignment.Center,
    ) {
        if (hasPermission) {
            QrCameraPreview(onQrScanned = onQrScanned, modifier = Modifier.fillMaxSize())
        } else {
            Column(
                modifier = Modifier.padding(CzSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
            ) {
                Icon(Icons.Filled.QrCode, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
                Text(stringResource(R.string.checkin_camera_permission_title), color = Color.White)
                CzButton(
                    text = stringResource(R.string.checkin_camera_permission_action),
                    onClick = { launcher.launch(Manifest.permission.CAMERA) },
                    variant = CzButtonVariant.Secondary,
                )
            }
        }
        if (isScanning) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(CzSpacing.sm),
                color = MaterialTheme.czColors.surface,
                shape = RoundedCornerShape(CzRadius.full),
            ) {
                Text(
                    text = stringResource(R.string.transportation_scanning),
                    modifier = Modifier.padding(horizontal = CzSpacing.md, vertical = CzSpacing.xs),
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun TransportationScanStatusCard(
    result: TransportationScanResult?,
    onDismiss: () -> Unit,
) {
    val color = result?.color() ?: MaterialTheme.czColors.ember
    Surface(
        color = if (result == null) MaterialTheme.czColors.surface else color.copy(alpha = 0.10f),
        shape = RoundedCornerShape(CzRadius.xl),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(result?.icon() ?: Icons.Filled.QrCode, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = result?.title() ?: stringResource(R.string.transportation_point_camera),
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = result?.message() ?: stringResource(R.string.transportation_point_camera_message),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (result != null) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_dismiss))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Formatting helpers (internal - shared across transportation views)
// ---------------------------------------------------------------------------

internal fun TransportationLeg.icon(): ImageVector = when (this) {
    TransportationLeg.Outbound -> Icons.Rounded.ArrowUpward
    TransportationLeg.Return -> Icons.Rounded.ArrowDownward
}

internal fun TransportationCheckpoint.icon(): ImageVector = when (this) {
    TransportationCheckpoint.Departure -> Icons.Filled.DirectionsBus
    TransportationCheckpoint.Arrival -> Icons.Filled.Map
}

internal fun TransportationMode.icon(): ImageVector = when (this) {
    TransportationMode.OwnCar, TransportationMode.Carpool -> Icons.Filled.DirectionsCar
    else -> Icons.Filled.DirectionsBus
}

@Composable
internal fun RegistrationParticipantKind.displayName(): String = when (this) {
    RegistrationParticipantKind.SelfParticipant -> stringResource(R.string.registration_kind_self)
    RegistrationParticipantKind.Child -> stringResource(R.string.registration_kind_child)
}

@Composable
internal fun TransportationLeg.displayName(): String = when (this) {
    TransportationLeg.Outbound -> stringResource(R.string.transportation_leg_outbound)
    TransportationLeg.Return -> stringResource(R.string.transportation_leg_return)
}

@Composable
internal fun TransportationLeg.shortName(): String = when (this) {
    TransportationLeg.Outbound -> stringResource(R.string.transportation_leg_outbound_short)
    TransportationLeg.Return -> stringResource(R.string.transportation_leg_return_short)
}

@Composable
internal fun TransportationLeg.subtitle(): String = when (this) {
    TransportationLeg.Outbound -> stringResource(R.string.transportation_leg_outbound_subtitle)
    TransportationLeg.Return -> stringResource(R.string.transportation_leg_return_subtitle)
}

@Composable
internal fun TransportationCheckpoint.displayName(): String = when (this) {
    TransportationCheckpoint.Departure -> stringResource(R.string.transportation_checkpoint_departure)
    TransportationCheckpoint.Arrival -> stringResource(R.string.transportation_checkpoint_arrival)
}

@Composable
internal fun TransportationLegProgress.displayName(): String = when (this) {
    TransportationLegProgress.NotStarted -> stringResource(R.string.transportation_progress_not_started)
    TransportationLegProgress.InTransit -> stringResource(R.string.transportation_progress_in_transit)
    TransportationLegProgress.Arrived -> stringResource(R.string.transportation_progress_arrived)
}

@Composable
internal fun TransportationLegProgress.tint(): Color = when (this) {
    TransportationLegProgress.NotStarted -> MaterialTheme.czColors.textSecondary
    TransportationLegProgress.InTransit -> MaterialTheme.czColors.warning
    TransportationLegProgress.Arrived -> MaterialTheme.czColors.success
}

@Composable
internal fun TransportationPaymentStatus.label(): String = when (this) {
    TransportationPaymentStatus.Unpaid -> stringResource(R.string.transportation_payment_unpaid)
    TransportationPaymentStatus.Paid -> stringResource(R.string.transportation_payment_paid)
    TransportationPaymentStatus.Waived -> stringResource(R.string.transportation_payment_waived)
}

@Composable
internal fun TransportationPaymentStatus.color(): Color = when (this) {
    TransportationPaymentStatus.Unpaid -> MaterialTheme.czColors.warning
    TransportationPaymentStatus.Paid -> MaterialTheme.czColors.success
    TransportationPaymentStatus.Waived -> MaterialTheme.czColors.pine
}

@Composable
internal fun TransportationBoardingStatus.label(): String = when (this) {
    TransportationBoardingStatus.NotBoarded -> stringResource(R.string.transportation_boarding_not_boarded)
    TransportationBoardingStatus.Boarded -> stringResource(R.string.transportation_boarding_boarded)
}

@Composable
internal fun scanModeTitle(leg: TransportationLeg, checkpoint: TransportationCheckpoint): String = when {
    leg == TransportationLeg.Outbound && checkpoint == TransportationCheckpoint.Departure ->
        stringResource(R.string.transportation_mode_outbound_departure_title)
    leg == TransportationLeg.Outbound ->
        stringResource(R.string.transportation_mode_outbound_arrival_title)
    checkpoint == TransportationCheckpoint.Departure ->
        stringResource(R.string.transportation_mode_return_departure_title)
    else -> stringResource(R.string.transportation_mode_return_arrival_title)
}

@Composable
internal fun scanModeSubtitle(leg: TransportationLeg, checkpoint: TransportationCheckpoint): String = when {
    leg == TransportationLeg.Outbound && checkpoint == TransportationCheckpoint.Departure ->
        stringResource(R.string.transportation_mode_outbound_departure_subtitle)
    leg == TransportationLeg.Outbound ->
        stringResource(R.string.transportation_mode_outbound_arrival_subtitle)
    checkpoint == TransportationCheckpoint.Departure ->
        stringResource(R.string.transportation_mode_return_departure_subtitle)
    else -> stringResource(R.string.transportation_mode_return_arrival_subtitle)
}

/** Whole-trip status used by the ticket header + dashboard row pill. */
@Composable
internal fun TransportationBooking.tripStatusLabel(): String = when {
    !isActive -> stringResource(R.string.transportation_status_inactive)
    isTripComplete -> stringResource(R.string.transportation_status_trip_complete)
    progress(TransportationLeg.Outbound) == TransportationLegProgress.NotStarted ->
        stringResource(R.string.transportation_status_awaiting_boarding)
    progress(TransportationLeg.Outbound) == TransportationLegProgress.InTransit ->
        stringResource(R.string.transportation_status_on_bus)
    progress(TransportationLeg.Return) == TransportationLegProgress.InTransit ->
        stringResource(R.string.transportation_status_returning)
    else -> stringResource(R.string.transportation_status_at_camp)
}

@Composable
internal fun TransportationBooking.tripStatusColor(): Color = when {
    !isActive -> MaterialTheme.czColors.error
    isTripComplete -> MaterialTheme.czColors.success
    progress(TransportationLeg.Outbound) == TransportationLegProgress.Arrived -> MaterialTheme.czColors.success
    progress(TransportationLeg.Outbound) == TransportationLegProgress.InTransit -> MaterialTheme.czColors.warning
    else -> MaterialTheme.czColors.textSecondary
}

@Composable
internal fun TransportationScanResult.title(): String = when (this) {
    is TransportationScanResult.Success -> stringResource(R.string.transportation_scan_success)
    is TransportationScanResult.ArrivalSuccess -> stringResource(R.string.transportation_scan_arrival_success)
    is TransportationScanResult.AlreadyBoarded -> stringResource(R.string.transportation_scan_already_boarded)
    is TransportationScanResult.AlreadyArrived -> stringResource(R.string.transportation_scan_already_arrived)
    is TransportationScanResult.NotBoardedForArrival -> stringResource(R.string.transportation_scan_not_boarded_for_arrival)
    is TransportationScanResult.Inactive -> stringResource(R.string.transportation_scan_inactive)
    is TransportationScanResult.Unpaid -> stringResource(R.string.transportation_scan_unpaid)
    TransportationScanResult.WrongCamping -> stringResource(R.string.transportation_scan_wrong_camp)
    TransportationScanResult.UnknownBooking -> stringResource(R.string.transportation_scan_unknown)
    TransportationScanResult.TokenMismatch -> stringResource(R.string.transportation_scan_token_mismatch)
    TransportationScanResult.RegistrationNotApproved -> stringResource(R.string.transportation_scan_not_approved)
    TransportationScanResult.Expired -> stringResource(R.string.transportation_scan_expired)
    TransportationScanResult.Malformed -> stringResource(R.string.transportation_scan_malformed)
}

@Composable
internal fun TransportationScanResult.message(): String = when (this) {
    is TransportationScanResult.Success -> stringResource(R.string.transportation_scan_success_message, booking.participantName)
    is TransportationScanResult.ArrivalSuccess -> stringResource(R.string.transportation_scan_arrival_success_message, booking.participantName)
    is TransportationScanResult.AlreadyBoarded -> stringResource(R.string.transportation_scan_already_boarded_message, booking.participantName)
    is TransportationScanResult.AlreadyArrived -> stringResource(R.string.transportation_scan_already_arrived_message, booking.participantName)
    is TransportationScanResult.NotBoardedForArrival -> stringResource(R.string.transportation_scan_not_boarded_for_arrival_message, booking.participantName)
    is TransportationScanResult.Inactive -> stringResource(R.string.transportation_scan_inactive_message, booking.participantName)
    is TransportationScanResult.Unpaid -> stringResource(R.string.transportation_scan_unpaid_message, booking.participantName)
    TransportationScanResult.WrongCamping -> stringResource(R.string.transportation_scan_wrong_camp_message)
    TransportationScanResult.UnknownBooking -> stringResource(R.string.transportation_scan_unknown_message)
    TransportationScanResult.TokenMismatch -> stringResource(R.string.transportation_scan_token_mismatch_message)
    TransportationScanResult.RegistrationNotApproved -> stringResource(R.string.transportation_scan_not_approved_message)
    TransportationScanResult.Expired -> stringResource(R.string.transportation_scan_expired_message)
    TransportationScanResult.Malformed -> stringResource(R.string.transportation_scan_malformed_message)
}

@Composable
internal fun TransportationScanResult.color(): Color = when (this) {
    is TransportationScanResult.Success,
    is TransportationScanResult.ArrivalSuccess,
    -> MaterialTheme.czColors.success
    is TransportationScanResult.AlreadyBoarded,
    is TransportationScanResult.AlreadyArrived,
    is TransportationScanResult.NotBoardedForArrival,
    is TransportationScanResult.Inactive,
    is TransportationScanResult.Unpaid,
    TransportationScanResult.RegistrationNotApproved,
    TransportationScanResult.Expired,
    -> MaterialTheme.czColors.warning
    TransportationScanResult.WrongCamping,
    TransportationScanResult.UnknownBooking,
    TransportationScanResult.TokenMismatch,
    TransportationScanResult.Malformed,
    -> MaterialTheme.czColors.error
}

internal fun TransportationScanResult.icon(): ImageVector = when (this) {
    is TransportationScanResult.Success,
    is TransportationScanResult.ArrivalSuccess,
    -> Icons.Filled.CheckCircle
    is TransportationScanResult.AlreadyBoarded,
    is TransportationScanResult.AlreadyArrived,
    is TransportationScanResult.NotBoardedForArrival,
    is TransportationScanResult.Inactive,
    is TransportationScanResult.Unpaid,
    TransportationScanResult.RegistrationNotApproved,
    TransportationScanResult.Expired,
    -> Icons.Filled.Warning
    TransportationScanResult.WrongCamping,
    TransportationScanResult.UnknownBooking,
    TransportationScanResult.TokenMismatch,
    TransportationScanResult.Malformed,
    -> Icons.Filled.Error
}

// ---------------------------------------------------------------------------
// Preview fixtures + previews
// ---------------------------------------------------------------------------

internal fun previewTransportationCamping(): Camping =
    previewCamping("summer-2026", "Summer Pathfinder Camp", 2026, 6).copy(
        attendees = listOf(
            CampingAttendee(
                id = "att-1",
                userId = "u1",
                displayName = "Ana Ferreira",
                church = "Paris Central SDA",
                age = 16,
                languages = listOf("fr"),
                registrationStatus = RegistrationApprovalStatus.Approved,
            ),
            CampingAttendee(
                id = "att-2",
                userId = "u2",
                displayName = "Ben Costa",
                church = "Paris Central SDA",
                age = 11,
                languages = listOf("fr"),
                registrationStatus = RegistrationApprovalStatus.Approved,
                participantKind = RegistrationParticipantKind.Child,
                guardianId = "u1",
            ),
        ),
        transportationOptions = listOf(
            CampingTransportationOption(
                id = "coach",
                name = "Coach from Paris-Bercy",
                mode = TransportationMode.Coach,
                details = "Departs 08:00",
                requiresTicket = true,
                feeCents = 2500,
            ),
        ),
    )

internal fun previewTransportationBookings(): List<TransportationBooking> {
    val depart = Date(System.currentTimeMillis() - 2L * 24 * 3600 * 1000)
    val arrive = Date(depart.time + 3L * 3600 * 1000)
    return listOf(
        TransportationBooking(
            id = "att-1-bus",
            campingId = "summer-2026",
            registrationId = "att-1",
            participantId = "att-1",
            participantKind = RegistrationParticipantKind.SelfParticipant,
            participantName = "Ana Ferreira",
            userId = "u1",
            ticketToken = "AB12CD34EF56",
            validFrom = depart,
            validUntil = Date(depart.time + 7L * 24 * 3600 * 1000),
            transportationOptionId = "coach",
            transportationOptionName = "Coach from Paris-Bercy",
            paymentStatus = TransportationPaymentStatus.Paid,
            boardingStatus = TransportationBoardingStatus.Boarded,
            boardedBy = "marshal-leon",
            boardedAt = depart,
            arrivedBy = "marshal-leon",
            arrivedAt = arrive,
            scanHistory = listOf(
                TransportationScanEvent(leg = TransportationLeg.Outbound, checkpoint = TransportationCheckpoint.Departure, at = depart, by = "marshal-leon", byName = "Leon", location = "Paris-Bercy"),
                TransportationScanEvent(leg = TransportationLeg.Outbound, checkpoint = TransportationCheckpoint.Arrival, at = arrive, by = "marshal-leon", byName = "Leon", location = "Camp gate"),
            ),
        ),
        TransportationBooking(
            id = "att-2-bus",
            campingId = "summer-2026",
            registrationId = "att-2",
            participantId = "att-2",
            participantKind = RegistrationParticipantKind.Child,
            participantName = "Ben Costa",
            guardianId = "u1",
            userId = "u2",
            ticketToken = "ZZ99YY88XX77",
            validFrom = depart,
            validUntil = Date(depart.time + 7L * 24 * 3600 * 1000),
            transportationOptionId = "coach",
            transportationOptionName = "Coach from Paris-Bercy",
            paymentStatus = TransportationPaymentStatus.Unpaid,
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun TransportationTicketsScreenPreview() {
    CampzoneTheme {
        TransportationTicketsScreen(
            uiState = TransportationUiState.Ready,
            camping = previewTransportationCamping(),
            bookings = previewTransportationBookings(),
            onBack = {},
            onRetry = {},
            onFarePaid = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TransportationScannerScreenPreview() {
    CampzoneTheme {
        TransportationScannerScreen(
            uiState = TransportationUiState.Ready,
            camping = previewTransportationCamping(),
            bookings = previewTransportationBookings(),
            result = null,
            isScanning = false,
            onScan = { _, _, _ -> },
            onDismissResult = {},
            onBack = {},
            onOpenHistory = {},
            onRetry = {},
        )
    }
}
