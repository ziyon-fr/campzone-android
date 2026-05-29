package fr.ziyon.campzone.ui.checkin

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
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
import fr.ziyon.campzone.core.designsystem.CzAvatar
import fr.ziyon.campzone.core.designsystem.CzAvatarSize
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
import fr.ziyon.campzone.data.model.CheckInMethod
import fr.ziyon.campzone.data.model.CheckInQrPayload
import fr.ziyon.campzone.data.model.CheckInRecord
import fr.ziyon.campzone.data.model.CheckInScanResult
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import fr.ziyon.campzone.ui.camping.CampingDetailUiState
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInScannerRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenRecords: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CheckInViewModel = hiltViewModel(),
) {
    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.start(campingId, authenticatedUser)
    }

    val uiState by viewModel.uiState.collectAsState()
    val camping by viewModel.camping.collectAsState()
    val records by viewModel.records.collectAsState()
    val lastScanResult by viewModel.lastScanResult.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.checkin_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenRecords) {
                        Icon(
                            Icons.Filled.QrCode,
                            contentDescription = stringResource(R.string.checkin_open_records),
                        )
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
        containerColor = MaterialTheme.czColors.background,
    ) { padding ->
        CheckInStateContent(
            uiState = uiState,
            loadingMessage = stringResource(R.string.checkin_loading),
            restrictedMessage = stringResource(R.string.checkin_restricted_scanner_message),
            retry = { viewModel.retry(campingId, authenticatedUser) },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            val loadedCamping = camping
            if (loadedCamping == null) {
                CzLoadingView(
                    message = stringResource(R.string.checkin_loading),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CheckInScannerContent(
                    camping = loadedCamping,
                    checkedInCount = records.size,
                    result = lastScanResult,
                    isRecording = isRecording,
                    onQrScanned = viewModel::handleScan,
                    onDismissResult = viewModel::dismissScanResult,
                    onOpenRecords = onOpenRecords,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInRecordsRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenScanner: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CheckInViewModel = hiltViewModel(),
) {
    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.start(campingId, authenticatedUser)
    }

    val uiState by viewModel.uiState.collectAsState()
    val camping by viewModel.camping.collectAsState()
    val records by viewModel.records.collectAsState()
    val filteredRecords by viewModel.filteredRecords.collectAsState()
    val pendingAttendees by viewModel.pendingAttendees.collectAsState()
    val searchText by viewModel.searchText.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.checkin_records_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenScanner) {
                        Icon(
                            Icons.Filled.QrCode,
                            contentDescription = stringResource(R.string.checkin_open_scanner),
                        )
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
        containerColor = MaterialTheme.czColors.background,
    ) { padding ->
        CheckInStateContent(
            uiState = uiState,
            loadingMessage = stringResource(R.string.checkin_loading),
            restrictedMessage = stringResource(R.string.checkin_restricted_records_message),
            retry = { viewModel.retry(campingId, authenticatedUser) },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            val loadedCamping = camping
            if (loadedCamping == null) {
                CzLoadingView(
                    message = stringResource(R.string.checkin_loading),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CheckInRecordsContent(
                    camping = loadedCamping,
                    records = records,
                    filteredRecords = filteredRecords,
                    pendingAttendees = pendingAttendees,
                    searchText = searchText,
                    isRecording = isRecording,
                    onSearchChange = viewModel::updateSearch,
                    onManualCheckIn = viewModel::manualCheckIn,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInQrPassesRoute(
    state: CampingDetailUiState,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.camping_my_qr_passes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
        containerColor = MaterialTheme.czColors.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                state.isLoading -> CzLoadingView(
                    message = stringResource(R.string.checkin_loading),
                    modifier = Modifier.fillMaxSize(),
                )

                state.errorMessage != null -> CzErrorState(
                    title = stringResource(R.string.checkin_error_title),
                    message = state.errorMessage,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(CzSpacing.lg),
                )

                state.camping == null -> CzEmptyState(
                    title = stringResource(R.string.checkin_qr_camping_not_found_title),
                    message = stringResource(R.string.checkin_qr_camping_not_found_message),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(CzSpacing.lg),
                )

                else -> CheckInQrPassesContent(
                    camping = state.camping,
                    userId = authenticatedUser.uid,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun CheckInStateContent(
    uiState: CheckInUiState,
    loadingMessage: String,
    restrictedMessage: String,
    retry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        when (uiState) {
            CheckInUiState.Loading -> CzLoadingView(
                message = loadingMessage,
                modifier = Modifier.fillMaxSize(),
            )

            CheckInUiState.Restricted -> CzEmptyState(
                title = stringResource(R.string.checkin_restricted_title),
                message = restrictedMessage,
                icon = {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.czColors.warning,
                        modifier = Modifier.size(42.dp),
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(CzSpacing.lg),
            )

            is CheckInUiState.Error -> CzErrorState(
                title = stringResource(R.string.checkin_error_title),
                message = uiState.message,
                onRetry = retry,
                retryLabel = stringResource(R.string.common_retry),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(CzSpacing.lg),
            )

            CheckInUiState.Ready -> content()
        }
    }
}

@Composable
private fun CheckInScannerContent(
    camping: Camping,
    checkedInCount: Int,
    result: CheckInScanResult?,
    isRecording: Boolean,
    onQrScanned: (String) -> Unit,
    onDismissResult: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        item("scanner") {
            CameraScannerCard(
                isRecording = isRecording,
                onQrScanned = onQrScanned,
            )
        }
        item("status") {
            ScanStatusCard(
                result = result,
                onDismiss = onDismissResult,
            )
        }
        item("summary") {
            ScannerSummaryRow(
                camping = camping,
                checkedInCount = checkedInCount,
                onOpenRecords = onOpenRecords,
            )
        }
    }
}

@Composable
private fun CameraScannerCard(
    isRecording: Boolean,
    onQrScanned: (String) -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
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
            QrCameraPreview(
                onQrScanned = onQrScanned,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PermissionPrompt(onRequest = { launcher.launch(Manifest.permission.CAMERA) })
        }

        if (isRecording) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(CzSpacing.sm),
                shape = RoundedCornerShape(CzRadius.full),
                color = MaterialTheme.czColors.surface,
            ) {
                Text(
                    text = stringResource(R.string.checkin_saving),
                    modifier = Modifier.padding(horizontal = CzSpacing.md, vertical = CzSpacing.xs),
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.padding(CzSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Icon(
            Icons.Filled.CameraAlt,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(42.dp),
        )
        Text(
            text = stringResource(R.string.checkin_camera_permission_title),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.checkin_camera_permission_message),
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        CzButton(
            text = stringResource(R.string.checkin_camera_permission_action),
            onClick = onRequest,
            variant = CzButtonVariant.Secondary,
        )
    }
}

@Composable
private fun ScanStatusCard(
    result: CheckInScanResult?,
    onDismiss: () -> Unit,
) {
    val color = result?.scanColor() ?: MaterialTheme.czColors.ember
    Surface(
        color = if (result == null) MaterialTheme.czColors.surface else color.copy(alpha = 0.10f),
        shape = RoundedCornerShape(CzRadius.xl),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CzSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = result?.scanIcon() ?: Icons.Filled.QrCode,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (result == null) {
                    Text(
                        text = stringResource(R.string.checkin_point_camera),
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        text = result.titleText(),
                        color = MaterialTheme.czColors.textPrimary,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = result.messageText(),
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (result != null) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.common_dismiss),
                        tint = MaterialTheme.czColors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannerSummaryRow(
    camping: Camping,
    checkedInCount: Int,
    onOpenRecords: () -> Unit,
) {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.xl),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CzSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.checkin_checked_in_count,
                        checkedInCount,
                        camping.approvedAttendees.size,
                    ),
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = camping.title,
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onOpenRecords) {
                Text(stringResource(R.string.checkin_view_records))
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun CheckInRecordsContent(
    camping: Camping,
    records: List<CheckInRecord>,
    filteredRecords: List<CheckInRecord>,
    pendingAttendees: List<CampingAttendee>,
    searchText: String,
    isRecording: Boolean,
    onSearchChange: (String) -> Unit,
    onManualCheckIn: (CampingAttendee) -> Unit,
) {
    val filteredPending = remember(pendingAttendees, searchText) {
        filterPendingAttendees(pendingAttendees, searchText)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        item("summary") {
            RecordsSummary(checkedIn = records.size, approved = camping.approvedAttendees.size)
        }
        item("search") {
            OutlinedTextField(
                value = searchText,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Search check-in records" },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                placeholder = { Text(stringResource(R.string.checkin_search_placeholder)) },
                singleLine = true,
            )
        }
        if (filteredPending.isNotEmpty()) {
            item("pending-header") {
                SectionLabel(
                    title = stringResource(R.string.checkin_not_checked_in_section),
                    icon = Icons.Filled.HourglassEmpty,
                )
            }
            items(filteredPending, key = { "pending-${it.id}" }) { attendee ->
                PendingAttendeeRow(
                    attendee = attendee,
                    enabled = !isRecording,
                    onManualCheckIn = { onManualCheckIn(attendee) },
                )
            }
        }
        item("checked-header") {
            SectionLabel(
                title = stringResource(R.string.checkin_checked_in_section),
                icon = Icons.Filled.CheckCircle,
            )
        }
        if (filteredRecords.isEmpty()) {
            item("empty") {
                CzEmptyState(
                    title = stringResource(R.string.checkin_empty_title),
                    message = stringResource(R.string.checkin_empty_message),
                    icon = {
                        Icon(
                            Icons.Filled.QrCode,
                            contentDescription = null,
                            tint = MaterialTheme.czColors.ember,
                            modifier = Modifier.size(42.dp),
                        )
                    },
                    modifier = Modifier.padding(top = CzSpacing.md),
                )
            }
        } else {
            items(filteredRecords, key = { it.attendeeId }) { record ->
                CheckInRecordRow(record)
            }
        }
    }
}

@Composable
private fun RecordsSummary(checkedIn: Int, approved: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
        StatTile(
            value = checkedIn.toString(),
            label = stringResource(R.string.checkin_checked_in_label),
            color = MaterialTheme.czColors.success,
            icon = Icons.Filled.CheckCircle,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            value = (approved - checkedIn).coerceAtLeast(0).toString(),
            label = stringResource(R.string.checkin_remaining_label),
            color = MaterialTheme.czColors.warning,
            icon = Icons.Filled.HourglassEmpty,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(
    value: String,
    label: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.xl),
    ) {
        Column(
            modifier = Modifier.padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Text(
                    text = label,
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                text = value,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SectionLabel(title: String, icon: ImageVector) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.czColors.ember, modifier = Modifier.size(16.dp))
        Text(
            text = title,
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PendingAttendeeRow(
    attendee: CampingAttendee,
    enabled: Boolean,
    onManualCheckIn: () -> Unit,
) {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CzSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CzAvatar(
                imageUrl = attendee.photoUrl,
                contentDescription = attendee.displayName,
                initials = attendee.displayName.initials(),
                size = CzAvatarSize.Small,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attendee.displayName,
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (attendee.church.isNotBlank()) {
                    Text(
                        text = attendee.church,
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(onClick = onManualCheckIn, enabled = enabled) {
                Text(stringResource(R.string.checkin_check_in_action))
            }
        }
    }
}

@Composable
private fun CheckInRecordRow(record: CheckInRecord) {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CzSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                CzAvatar(
                    imageUrl = record.photoUrl,
                    contentDescription = record.displayName,
                    initials = record.displayName.initials(),
                    size = CzAvatarSize.Small,
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.czColors.ember)
                        .border(1.dp, MaterialTheme.czColors.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = record.method.methodIcon(),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.displayName,
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (record.church.isNotBlank()) {
                    Text(
                        text = record.church,
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = record.checkedInAt?.shortTime().orEmpty(),
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = record.method.displayName(),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun CheckInQrPassesContent(
    camping: Camping,
    userId: String,
    modifier: Modifier = Modifier,
) {
    val approved = remember(camping.attendees, userId) {
        managedCheckInAttendees(camping, userId, RegistrationApprovalStatus.Approved)
    }
    val pending = remember(camping.attendees, userId) {
        managedCheckInAttendees(camping, userId, RegistrationApprovalStatus.Pending)
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xl),
    ) {
        item("status") {
            QrStatusBanner(approvedCount = approved.size, pendingCount = pending.size)
        }
        when {
            approved.isNotEmpty() -> {
                item("header") {
                    SectionLabel(
                        title = stringResource(R.string.checkin_arrival_section),
                        icon = Icons.Filled.QrCode,
                    )
                }
                items(approved, key = { it.id }) { attendee ->
                    CheckInQrCard(camping = camping, attendee = attendee)
                }
                item("instructions") {
                    QrInstructions()
                }
            }

            pending.isNotEmpty() -> {
                item("pending") {
                    CzEmptyState(
                        title = stringResource(R.string.checkin_qr_awaiting_approval_title),
                        message = stringResource(R.string.checkin_qr_awaiting_approval_message),
                        icon = {
                            Icon(
                                Icons.Filled.HourglassEmpty,
                                contentDescription = null,
                                tint = MaterialTheme.czColors.warning,
                                modifier = Modifier.size(42.dp),
                            )
                        },
                    )
                }
            }

            else -> {
                item("none") {
                    CzEmptyState(
                        title = stringResource(R.string.checkin_qr_not_registered_title),
                        message = stringResource(R.string.checkin_qr_not_registered_message),
                        icon = {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                tint = MaterialTheme.czColors.textSecondary,
                                modifier = Modifier.size(42.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QrStatusBanner(approvedCount: Int, pendingCount: Int) {
    val isApproved = approvedCount > 0
    val color = if (isApproved) MaterialTheme.czColors.success else MaterialTheme.czColors.warning
    val text = when {
        approvedCount > 0 && pendingCount > 0 -> stringResource(
            R.string.checkin_status_approved_pending,
            approvedCount,
            pendingCount,
        )
        approvedCount == 1 -> stringResource(R.string.checkin_status_one_approved)
        approvedCount > 1 -> stringResource(R.string.checkin_status_approved, approvedCount)
        pendingCount == 1 -> stringResource(R.string.checkin_status_one_pending)
        else -> stringResource(R.string.checkin_status_pending, pendingCount)
    }
    Surface(
        color = color.copy(alpha = 0.10f),
        shape = RoundedCornerShape(CzRadius.md),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CzSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isApproved) Icons.Filled.CheckCircle else Icons.Filled.HourglassEmpty,
                contentDescription = null,
                tint = color,
            )
            Text(
                text = text,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun CheckInQrCard(camping: Camping, attendee: CampingAttendee) {
    val payload = remember(camping.id, attendee.id, attendee.userId) {
        CheckInQrPayload(
            campingId = camping.id,
            attendeeId = attendee.id,
            userId = attendee.userId,
        ).encoded()
    }

    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.xl),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CzSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            ParticipantBadge(attendee)
            QrCodeImage(
                value = payload,
                modifier = Modifier
                    .size(240.dp)
                    .padding(CzSpacing.md),
            )
            CzAvatar(
                imageUrl = attendee.photoUrl,
                contentDescription = attendee.displayName,
                initials = attendee.displayName.initials(),
                size = CzAvatarSize.Large,
            )
            Text(
                text = attendee.displayName,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            if (attendee.church.isNotBlank()) {
                Text(
                    text = attendee.church,
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ParticipantBadge(attendee: CampingAttendee) {
    val isChild = attendee.participantKind == RegistrationParticipantKind.Child
    Surface(
        color = MaterialTheme.czColors.ember.copy(alpha = 0.14f),
        shape = RoundedCornerShape(CzRadius.full),
        contentColor = MaterialTheme.czColors.ember,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CzSpacing.sm, vertical = CzSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isChild) Icons.Filled.ChildCare else Icons.Filled.Person,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(if (isChild) R.string.checkin_child else R.string.checkin_you),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun QrCodeImage(value: String, modifier: Modifier = Modifier) {
    val bitmap = remember(value) { generateQrCode(value) }
    Surface(
        modifier = modifier.semantics {
            contentDescription = "QR check-in code"
        },
        color = Color.White,
        shape = RoundedCornerShape(CzRadius.xl),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.checkin_qr_code_cd),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(CzSpacing.sm),
            )
        } else {
            CzEmptyState(
                title = stringResource(R.string.checkin_qr_unavailable_title),
                message = stringResource(R.string.checkin_qr_unavailable_message),
                modifier = Modifier.padding(CzSpacing.md),
            )
        }
    }
}

@Composable
private fun QrInstructions() {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.xl),
    ) {
        Column(
            modifier = Modifier.padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            InstructionRow(step = "1", text = stringResource(R.string.checkin_instruction_find_pass))
            InstructionRow(step = "2", text = stringResource(R.string.checkin_instruction_show_pass))
        }
    }
}

@Composable
private fun InstructionRow(step: String, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.czColors.ember,
            contentColor = Color.White,
        ) {
            Text(
                text = step,
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 2.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = text,
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

internal fun managedCheckInAttendees(
    camping: Camping,
    userId: String,
    status: RegistrationApprovalStatus,
): List<CampingAttendee> =
    camping.attendees
        .filter { attendee ->
            attendee.registrationStatus == status &&
                (attendee.userId == userId ||
                    attendee.guardianId == userId ||
                    (attendee.participantKind == RegistrationParticipantKind.SelfParticipant && attendee.id == userId))
        }
        .sortedWith(compareBy<CampingAttendee> { it.participantKind != RegistrationParticipantKind.SelfParticipant }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })

internal fun filterPendingAttendees(
    attendees: List<CampingAttendee>,
    query: String,
): List<CampingAttendee> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return attendees
    return attendees.filter { attendee ->
        attendee.displayName.contains(trimmed, ignoreCase = true) ||
            attendee.church.contains(trimmed, ignoreCase = true)
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
private fun CheckInScanResult.titleText(): String = when (this) {
    is CheckInScanResult.Success -> stringResource(R.string.checkin_result_checked_in)
    is CheckInScanResult.AlreadyCheckedIn -> stringResource(R.string.checkin_result_already_checked_in)
    CheckInScanResult.UnknownAttendee -> stringResource(R.string.checkin_result_unknown_attendee)
    CheckInScanResult.WrongCamping -> stringResource(R.string.checkin_result_wrong_camp)
    CheckInScanResult.NotApproved -> stringResource(R.string.checkin_result_not_approved)
    CheckInScanResult.Malformed -> stringResource(R.string.checkin_result_invalid_code)
}

@Composable
private fun CheckInScanResult.messageText(): String = when (this) {
    is CheckInScanResult.Success -> stringResource(R.string.checkin_result_checked_in_message, record.displayName)
    is CheckInScanResult.AlreadyCheckedIn ->
        stringResource(R.string.checkin_result_already_checked_in_message, record.displayName)
    CheckInScanResult.UnknownAttendee -> stringResource(R.string.checkin_result_unknown_attendee_message)
    CheckInScanResult.WrongCamping -> stringResource(R.string.checkin_result_wrong_camp_message)
    CheckInScanResult.NotApproved -> stringResource(R.string.checkin_result_not_approved_message)
    CheckInScanResult.Malformed -> stringResource(R.string.checkin_result_invalid_code_message)
}

@Composable
private fun CheckInScanResult.scanColor(): Color = when (this) {
    is CheckInScanResult.Success -> MaterialTheme.czColors.success
    is CheckInScanResult.AlreadyCheckedIn -> MaterialTheme.czColors.warning
    CheckInScanResult.UnknownAttendee,
    CheckInScanResult.WrongCamping,
    CheckInScanResult.NotApproved,
    CheckInScanResult.Malformed,
    -> MaterialTheme.czColors.error
}

private fun CheckInScanResult.scanIcon(): ImageVector = when (this) {
    is CheckInScanResult.Success -> Icons.Filled.CheckCircle
    is CheckInScanResult.AlreadyCheckedIn -> Icons.Filled.Warning
    CheckInScanResult.UnknownAttendee,
    CheckInScanResult.WrongCamping,
    CheckInScanResult.NotApproved,
    CheckInScanResult.Malformed,
    -> Icons.Filled.Error
}

@Composable
private fun CheckInMethod.displayName(): String = when (this) {
    CheckInMethod.Qr -> stringResource(R.string.checkin_method_qr)
    CheckInMethod.Manual -> stringResource(R.string.checkin_method_manual)
}

private fun CheckInMethod.methodIcon(): ImageVector = when (this) {
    CheckInMethod.Qr -> Icons.Filled.QrCode
    CheckInMethod.Manual -> Icons.Filled.TouchApp
}

private fun Date.shortTime(): String =
    DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(this)

private fun String.initials(): String =
    trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "?" }
