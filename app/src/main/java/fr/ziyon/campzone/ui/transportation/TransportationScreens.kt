package fr.ziyon.campzone.ui.transportation

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import fr.ziyon.campzone.data.model.TransportationBoardingStatus
import fr.ziyon.campzone.data.model.TransportationBooking
import fr.ziyon.campzone.data.model.TransportationPaymentStatus
import fr.ziyon.campzone.data.model.TransportationScanResult
import fr.ziyon.campzone.data.model.TransportationTicketPayload
import fr.ziyon.campzone.ui.checkin.QrCameraPreview
import java.text.DateFormat

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
    )
}

@Composable
fun TransportationScannerRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    viewModel: TransportationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val camping by viewModel.camping.collectAsState()
    val bookings by viewModel.bookings.collectAsState()
    val result by viewModel.lastScanResult.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.loadScanner(campingId, authenticatedUser)
    }

    TransportationScannerScreen(
        uiState = uiState,
        camping = camping,
        bookings = bookings,
        result = result,
        isScanning = isScanning,
        onQrScanned = viewModel::handleScan,
        onDismissResult = viewModel::dismissScanResult,
        onBack = onBack,
        onRetry = { viewModel.retryScanner(campingId, authenticatedUser) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransportationTicketsScreen(
    uiState: TransportationUiState,
    camping: Camping?,
    bookings: List<TransportationBooking>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
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
                if (bookings.isEmpty()) {
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
                        if (camping != null) {
                            item("header") { TransportationHeader(camping, bookings) }
                        }
                        items(bookings, key = { it.id }) { booking ->
                            TransportationTicketCard(booking = booking)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransportationScannerScreen(
    uiState: TransportationUiState,
    camping: Camping?,
    bookings: List<TransportationBooking>,
    result: TransportationScanResult?,
    isScanning: Boolean,
    onQrScanned: (String) -> Unit,
    onDismissResult: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
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
                item("scanner") { TransportationCameraCard(isScanning = isScanning, onQrScanned = onQrScanned) }
                item("result") { TransportationScanStatusCard(result = result, onDismiss = onDismissResult) }
                if (camping != null) {
                    item("summary") { TransportationScannerSummary(camping = camping, bookings = bookings) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransportationScaffold(
    title: String,
    onBack: () -> Unit,
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
private fun TransportationHeader(camping: Camping, bookings: List<TransportationBooking>) {
    val paid = bookings.count { it.paymentStatus != TransportationPaymentStatus.Unpaid }
    val boarded = bookings.count { it.boardingStatus == TransportationBoardingStatus.Boarded }
    Surface(color = MaterialTheme.czColors.surface, shape = RoundedCornerShape(CzRadius.xl)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Text(camping.title, color = MaterialTheme.czColors.textPrimary, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.transportation_tickets_subtitle),
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                SummaryPill(Icons.Filled.QrCode, stringResource(R.string.transportation_pass_count, bookings.size))
                SummaryPill(Icons.Filled.CreditCard, stringResource(R.string.transportation_paid_count, paid, bookings.size))
                SummaryPill(Icons.Filled.DirectionsBus, stringResource(R.string.transportation_boarded_count, boarded, bookings.size))
            }
        }
    }
}

@Composable
private fun SummaryPill(icon: ImageVector, label: String) {
    Surface(color = MaterialTheme.czColors.ember.copy(alpha = 0.10f), shape = RoundedCornerShape(CzRadius.full)) {
        Row(
            modifier = Modifier.padding(horizontal = CzSpacing.sm, vertical = CzSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.czColors.ember, modifier = Modifier.size(15.dp))
            Text(label, color = MaterialTheme.czColors.textPrimary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TransportationTicketCard(booking: TransportationBooking) {
    val qrPayload = remember(booking) { TransportationTicketPayload.fromBooking(booking).encoded() }
    Surface(color = MaterialTheme.czColors.surface, shape = RoundedCornerShape(CzRadius.xl)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
                Icon(
                    Icons.Filled.DirectionsBus,
                    contentDescription = null,
                    tint = MaterialTheme.czColors.ember,
                    modifier = Modifier.size(34.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(booking.participantName, color = MaterialTheme.czColors.textPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(
                        booking.transportationOptionName ?: stringResource(R.string.transportation_default_option),
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusPill(text = booking.paymentStatus.label(), color = booking.paymentStatus.color())
            }
            QrCodeImage(
                value = qrPayload,
                modifier = Modifier
                    .size(220.dp)
                    .align(Alignment.CenterHorizontally),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                StatusPill(text = booking.boardingStatus.label(), color = booking.boardingStatus.color())
                Text(
                    text = stringResource(
                        R.string.transportation_valid_range,
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(booking.validFrom),
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(booking.validUntil),
                    ),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun QrCodeImage(value: String, modifier: Modifier = Modifier) {
    val bitmap = remember(value) { generateQrCode(value) }
    Surface(
        modifier = modifier.semantics { contentDescription = "Transportation QR code" },
        color = Color.White,
        shape = RoundedCornerShape(CzRadius.xl),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.transportation_qr_code_cd),
                modifier = Modifier.fillMaxSize().padding(CzSpacing.sm),
            )
        } else {
            QrTextBox(value)
        }
    }
}

@Composable
private fun QrTextBox(value: String) {
    Surface(color = MaterialTheme.czColors.background, shape = RoundedCornerShape(CzRadius.md)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.QrCode, contentDescription = null, tint = MaterialTheme.czColors.pine)
            Text(
                text = value,
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun generateQrCode(value: String, sidePx: Int = 768): ImageBitmap? =
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

@Composable
private fun TransportationScannerSummary(camping: Camping, bookings: List<TransportationBooking>) {
    val boarded = bookings.count { it.boardingStatus == TransportationBoardingStatus.Boarded }
    Surface(color = MaterialTheme.czColors.surface, shape = RoundedCornerShape(CzRadius.xl)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = MaterialTheme.czColors.ember)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.transportation_boarded_count, boarded, bookings.size),
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(camping.title, color = MaterialTheme.czColors.textSecondary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun RestrictedTransportationState() {
    CzEmptyState(
        title = stringResource(R.string.transportation_restricted_title),
        message = stringResource(R.string.transportation_restricted_message),
        modifier = Modifier.fillMaxSize().padding(CzSpacing.xl),
    )
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), contentColor = color, shape = RoundedCornerShape(CzRadius.full)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = CzSpacing.sm, vertical = 4.dp),
        )
    }
}

@Composable
private fun TransportationPaymentStatus.label(): String = when (this) {
    TransportationPaymentStatus.Unpaid -> stringResource(R.string.transportation_payment_unpaid)
    TransportationPaymentStatus.Paid -> stringResource(R.string.transportation_payment_paid)
    TransportationPaymentStatus.Waived -> stringResource(R.string.transportation_payment_waived)
}

@Composable
private fun TransportationPaymentStatus.color(): Color = when (this) {
    TransportationPaymentStatus.Unpaid -> MaterialTheme.czColors.warning
    TransportationPaymentStatus.Paid -> MaterialTheme.czColors.success
    TransportationPaymentStatus.Waived -> MaterialTheme.czColors.pine
}

@Composable
private fun TransportationBoardingStatus.label(): String = when (this) {
    TransportationBoardingStatus.NotBoarded -> stringResource(R.string.transportation_boarding_not_boarded)
    TransportationBoardingStatus.Boarded -> stringResource(R.string.transportation_boarding_boarded)
}

@Composable
private fun TransportationBoardingStatus.color(): Color = when (this) {
    TransportationBoardingStatus.NotBoarded -> MaterialTheme.czColors.textSecondary
    TransportationBoardingStatus.Boarded -> MaterialTheme.czColors.success
}

@Composable
private fun TransportationScanResult.title(): String = when (this) {
    is TransportationScanResult.Success -> stringResource(R.string.transportation_scan_success)
    is TransportationScanResult.AlreadyBoarded -> stringResource(R.string.transportation_scan_already_boarded)
    is TransportationScanResult.Unpaid -> stringResource(R.string.transportation_scan_unpaid)
    TransportationScanResult.WrongCamping -> stringResource(R.string.transportation_scan_wrong_camp)
    TransportationScanResult.UnknownBooking -> stringResource(R.string.transportation_scan_unknown)
    TransportationScanResult.TokenMismatch -> stringResource(R.string.transportation_scan_token_mismatch)
    TransportationScanResult.RegistrationNotApproved -> stringResource(R.string.transportation_scan_not_approved)
    TransportationScanResult.Expired -> stringResource(R.string.transportation_scan_expired)
    TransportationScanResult.Malformed -> stringResource(R.string.transportation_scan_malformed)
}

@Composable
private fun TransportationScanResult.message(): String = when (this) {
    is TransportationScanResult.Success -> stringResource(R.string.transportation_scan_success_message, booking.participantName)
    is TransportationScanResult.AlreadyBoarded -> stringResource(R.string.transportation_scan_already_boarded_message, booking.participantName)
    is TransportationScanResult.Unpaid -> stringResource(R.string.transportation_scan_unpaid_message, booking.participantName)
    TransportationScanResult.WrongCamping -> stringResource(R.string.transportation_scan_wrong_camp_message)
    TransportationScanResult.UnknownBooking -> stringResource(R.string.transportation_scan_unknown_message)
    TransportationScanResult.TokenMismatch -> stringResource(R.string.transportation_scan_token_mismatch_message)
    TransportationScanResult.RegistrationNotApproved -> stringResource(R.string.transportation_scan_not_approved_message)
    TransportationScanResult.Expired -> stringResource(R.string.transportation_scan_expired_message)
    TransportationScanResult.Malformed -> stringResource(R.string.transportation_scan_malformed_message)
}

@Composable
private fun TransportationScanResult.color(): Color = when (this) {
    is TransportationScanResult.Success -> MaterialTheme.czColors.success
    is TransportationScanResult.AlreadyBoarded,
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

private fun TransportationScanResult.icon(): ImageVector = when (this) {
    is TransportationScanResult.Success -> Icons.Filled.CheckCircle
    is TransportationScanResult.AlreadyBoarded,
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
