package fr.ziyon.campzone.ui.attendance

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.hilt.navigation.compose.hiltViewModel
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
import fr.ziyon.campzone.data.model.Program
import fr.ziyon.campzone.data.model.ProgramAttendanceRecord
import fr.ziyon.campzone.data.model.ProgramAttendanceScanResult
import fr.ziyon.campzone.ui.checkin.QrCameraPreview
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramAttendanceRecordsRoute(
    campingId: String,
    programId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenScanner: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProgramAttendanceViewModel = hiltViewModel(),
) {
    LaunchedEffect(campingId, programId, authenticatedUser.uid) {
        viewModel.start(campingId, programId, authenticatedUser)
    }

    val uiState by viewModel.uiState.collectAsState()
    val camping by viewModel.camping.collectAsState()
    val program by viewModel.program.collectAsState()
    val records by viewModel.records.collectAsState()
    val filteredRecords by viewModel.filteredRecords.collectAsState()
    val missingAttendees by viewModel.missingAttendees.collectAsState()
    val searchText by viewModel.searchText.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(operationMessage) {
        operationMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeOperationMessage()
        }
    }
    LaunchedEffect(operationError) {
        operationError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeOperationError()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.program_attendance_title)) },
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
                            contentDescription = stringResource(R.string.program_attendance_open_scanner),
                        )
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
        containerColor = MaterialTheme.czColors.background,
    ) { padding ->
        ProgramAttendanceStateContent(
            uiState = uiState,
            restrictedMessage = stringResource(R.string.program_attendance_restricted_records_message),
            retry = { viewModel.retry(campingId, programId, authenticatedUser) },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            val loadedCamping = camping
            val loadedProgram = program
            if (loadedCamping == null || loadedProgram == null) {
                CzLoadingView(
                    message = stringResource(R.string.program_attendance_loading),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ProgramAttendanceRecordsContent(
                    camping = loadedCamping,
                    program = loadedProgram,
                    records = records,
                    filteredRecords = filteredRecords,
                    missingAttendees = missingAttendees,
                    searchText = searchText,
                    isSaving = isSaving,
                    onSearchChange = viewModel::updateSearch,
                    onManualRecord = viewModel::manualRecord,
                    onRefreshRecord = viewModel::refreshTimestamp,
                    onDeleteRecord = viewModel::deleteRecord,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramAttendanceScannerRoute(
    campingId: String,
    programId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenRecords: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProgramAttendanceViewModel = hiltViewModel(),
) {
    LaunchedEffect(campingId, programId, authenticatedUser.uid) {
        viewModel.start(campingId, programId, authenticatedUser)
    }

    val uiState by viewModel.uiState.collectAsState()
    val camping by viewModel.camping.collectAsState()
    val program by viewModel.program.collectAsState()
    val records by viewModel.records.collectAsState()
    val lastScanResult by viewModel.lastScanResult.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.program_attendance_scanner_title)) },
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
                            Icons.Filled.CheckCircle,
                            contentDescription = stringResource(R.string.program_attendance_open_records),
                        )
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
        containerColor = MaterialTheme.czColors.background,
    ) { padding ->
        ProgramAttendanceStateContent(
            uiState = uiState,
            restrictedMessage = stringResource(R.string.program_attendance_restricted_scanner_message),
            retry = { viewModel.retry(campingId, programId, authenticatedUser) },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            val loadedCamping = camping
            val loadedProgram = program
            if (loadedCamping == null || loadedProgram == null) {
                CzLoadingView(
                    message = stringResource(R.string.program_attendance_loading),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ProgramAttendanceScannerContent(
                    camping = loadedCamping,
                    program = loadedProgram,
                    presentCount = records.size,
                    result = lastScanResult,
                    isSaving = isSaving,
                    onQrScanned = viewModel::handleScan,
                    onDismissResult = viewModel::dismissScanResult,
                    onOpenRecords = onOpenRecords,
                )
            }
        }
    }
}

@Composable
private fun ProgramAttendanceStateContent(
    uiState: ProgramAttendanceUiState,
    restrictedMessage: String,
    retry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        when (uiState) {
            ProgramAttendanceUiState.Loading -> CzLoadingView(
                message = stringResource(R.string.program_attendance_loading),
                modifier = Modifier.fillMaxSize(),
            )

            ProgramAttendanceUiState.Restricted -> CzEmptyState(
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

            is ProgramAttendanceUiState.Error -> CzErrorState(
                title = stringResource(R.string.program_attendance_error_title),
                message = uiState.message,
                onRetry = retry,
                retryLabel = stringResource(R.string.common_retry),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(CzSpacing.lg),
            )

            ProgramAttendanceUiState.Ready -> content()
        }
    }
}

@Composable
private fun ProgramAttendanceScannerContent(
    camping: Camping,
    program: Program,
    presentCount: Int,
    result: ProgramAttendanceScanResult?,
    isSaving: Boolean,
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
            AttendanceCameraCard(isSaving = isSaving, onQrScanned = onQrScanned)
        }
        item("status") {
            AttendanceScanStatusCard(result = result, onDismiss = onDismissResult)
        }
        item("summary") {
            AttendanceSummaryCard(
                title = program.title,
                subtitle = camping.title,
                present = presentCount,
                approved = camping.approvedAttendees.size,
                onOpenRecords = onOpenRecords,
            )
        }
    }
}

@Composable
private fun ProgramAttendanceRecordsContent(
    camping: Camping,
    program: Program,
    records: List<ProgramAttendanceRecord>,
    filteredRecords: List<ProgramAttendanceRecord>,
    missingAttendees: List<CampingAttendee>,
    searchText: String,
    isSaving: Boolean,
    onSearchChange: (String) -> Unit,
    onManualRecord: (CampingAttendee) -> Unit,
    onRefreshRecord: (ProgramAttendanceRecord) -> Unit,
    onDeleteRecord: (ProgramAttendanceRecord) -> Unit,
) {
    val filteredMissing = remember(missingAttendees, searchText) {
        filterAttendanceAttendees(missingAttendees, searchText)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        item("summary") {
            AttendanceSummaryTiles(present = records.size, approved = camping.approvedAttendees.size)
        }
        item("program") {
            AttendanceProgramHeader(program = program, camping = camping)
        }
        item("search") {
            val searchDescription = stringResource(R.string.program_attendance_search_cd)
            OutlinedTextField(
                value = searchText,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = searchDescription },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.checkin_search_placeholder)) },
                singleLine = true,
            )
        }
        if (filteredMissing.isNotEmpty()) {
            item("missing-header") {
                AttendanceSectionLabel(
                    title = stringResource(R.string.program_attendance_missing_section),
                    icon = Icons.Filled.HourglassEmpty,
                )
            }
            items(filteredMissing, key = { "missing-${it.id}" }) { attendee ->
                MissingAttendanceRow(
                    attendee = attendee,
                    enabled = !isSaving,
                    onManualRecord = { onManualRecord(attendee) },
                )
            }
        }
        item("present-header") {
            AttendanceSectionLabel(
                title = stringResource(R.string.program_attendance_present_section),
                icon = Icons.Filled.CheckCircle,
            )
        }
        if (filteredRecords.isEmpty()) {
            item("empty") {
                CzEmptyState(
                    title = stringResource(R.string.program_attendance_empty_title),
                    message = stringResource(R.string.program_attendance_empty_message),
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
                ProgramAttendanceRecordRow(
                    record = record,
                    enabled = !isSaving,
                    onRefresh = { onRefreshRecord(record) },
                    onDelete = { onDeleteRecord(record) },
                )
            }
        }
    }
}

@Composable
private fun AttendanceCameraCard(
    isSaving: Boolean,
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
            QrCameraPreview(onQrScanned = onQrScanned, modifier = Modifier.fillMaxSize())
        } else {
            AttendancePermissionPrompt(onRequest = { launcher.launch(Manifest.permission.CAMERA) })
        }

        if (isSaving) {
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
private fun AttendancePermissionPrompt(onRequest: () -> Unit) {
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
private fun AttendanceScanStatusCard(
    result: ProgramAttendanceScanResult?,
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
                        text = stringResource(R.string.program_attendance_point_camera),
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
private fun AttendanceSummaryCard(
    title: String,
    subtitle: String,
    present: Int,
    approved: Int,
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
                    text = stringResource(R.string.program_attendance_count, present, approved),
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.program_attendance_program_camp, title, subtitle),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onOpenRecords) {
                Text(stringResource(R.string.program_attendance_view_records))
            }
        }
    }
}

@Composable
private fun AttendanceSummaryTiles(present: Int, approved: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
        AttendanceStatTile(
            value = present.toString(),
            label = stringResource(R.string.program_attendance_present_label),
            color = MaterialTheme.czColors.success,
            icon = Icons.Filled.CheckCircle,
            modifier = Modifier.weight(1f),
        )
        AttendanceStatTile(
            value = (approved - present).coerceAtLeast(0).toString(),
            label = stringResource(R.string.program_attendance_missing_label),
            color = MaterialTheme.czColors.warning,
            icon = Icons.Filled.HourglassEmpty,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AttendanceStatTile(
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
private fun AttendanceProgramHeader(program: Program, camping: Camping) {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.xl),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = program.title,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = camping.title,
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun AttendanceSectionLabel(title: String, icon: ImageVector) {
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
private fun MissingAttendanceRow(
    attendee: CampingAttendee,
    enabled: Boolean,
    onManualRecord: () -> Unit,
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
            AttendancePersonText(
                name = attendee.displayName,
                church = attendee.church,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onManualRecord, enabled = enabled) {
                Text(stringResource(R.string.program_attendance_mark_present))
            }
        }
    }
}

@Composable
private fun ProgramAttendanceRecordRow(
    record: ProgramAttendanceRecord,
    enabled: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
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
            AttendancePersonText(
                name = record.displayName,
                church = record.church,
                modifier = Modifier.weight(1f),
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = record.checkedInAt.shortTime(),
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                    TextButton(onClick = onRefresh, enabled = enabled) {
                        Text(stringResource(R.string.program_attendance_correct))
                    }
                    TextButton(onClick = onDelete, enabled = enabled) {
                        Text(stringResource(R.string.program_attendance_remove))
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendancePersonText(
    name: String,
    church: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = name,
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (church.isNotBlank()) {
            Text(
                text = church,
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProgramAttendanceScanResult.titleText(): String = when (this) {
    is ProgramAttendanceScanResult.Success -> stringResource(R.string.program_attendance_result_success)
    is ProgramAttendanceScanResult.AlreadyRecorded -> stringResource(R.string.program_attendance_result_already)
    ProgramAttendanceScanResult.UnknownAttendee -> stringResource(R.string.checkin_result_unknown_attendee)
    ProgramAttendanceScanResult.WrongCamping -> stringResource(R.string.checkin_result_wrong_camp)
    ProgramAttendanceScanResult.NotApproved -> stringResource(R.string.checkin_result_not_approved)
    ProgramAttendanceScanResult.Malformed -> stringResource(R.string.checkin_result_invalid_code)
    ProgramAttendanceScanResult.SaveFailed -> stringResource(R.string.program_attendance_result_save_failed)
}

@Composable
private fun ProgramAttendanceScanResult.messageText(): String = when (this) {
    is ProgramAttendanceScanResult.Success ->
        stringResource(R.string.program_attendance_result_success_message, record.displayName, record.programTitle)
    is ProgramAttendanceScanResult.AlreadyRecorded ->
        stringResource(R.string.program_attendance_result_already_message, record.displayName)
    ProgramAttendanceScanResult.UnknownAttendee -> stringResource(R.string.checkin_result_unknown_attendee_message)
    ProgramAttendanceScanResult.WrongCamping -> stringResource(R.string.checkin_result_wrong_camp_message)
    ProgramAttendanceScanResult.NotApproved -> stringResource(R.string.checkin_result_not_approved_message)
    ProgramAttendanceScanResult.Malformed -> stringResource(R.string.checkin_result_invalid_code_message)
    ProgramAttendanceScanResult.SaveFailed -> stringResource(R.string.program_attendance_result_save_failed_message)
}

@Composable
private fun ProgramAttendanceScanResult.scanColor(): Color = when (this) {
    is ProgramAttendanceScanResult.Success -> MaterialTheme.czColors.success
    is ProgramAttendanceScanResult.AlreadyRecorded,
    ProgramAttendanceScanResult.NotApproved,
    -> MaterialTheme.czColors.warning
    ProgramAttendanceScanResult.UnknownAttendee,
    ProgramAttendanceScanResult.WrongCamping,
    ProgramAttendanceScanResult.Malformed,
    ProgramAttendanceScanResult.SaveFailed,
    -> MaterialTheme.czColors.error
}

private fun ProgramAttendanceScanResult.scanIcon(): ImageVector = when (this) {
    is ProgramAttendanceScanResult.Success -> Icons.Filled.CheckCircle
    is ProgramAttendanceScanResult.AlreadyRecorded -> Icons.Filled.Update
    ProgramAttendanceScanResult.NotApproved -> Icons.Filled.Warning
    ProgramAttendanceScanResult.UnknownAttendee,
    ProgramAttendanceScanResult.WrongCamping,
    ProgramAttendanceScanResult.Malformed,
    ProgramAttendanceScanResult.SaveFailed,
    -> Icons.Filled.Error
}

private fun CheckInMethod.methodIcon(): ImageVector = when (this) {
    CheckInMethod.Qr -> Icons.Filled.QrCode
    CheckInMethod.Manual -> Icons.Filled.TouchApp
}

private fun filterAttendanceAttendees(
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

private fun Date.shortTime(): String =
    DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(this)

private fun String.initials(): String =
    trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }
