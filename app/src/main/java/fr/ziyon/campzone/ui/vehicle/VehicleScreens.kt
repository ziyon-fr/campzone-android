package fr.ziyon.campzone.ui.vehicle

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CarRental
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
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
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingVehicle
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.UserVehicle
import fr.ziyon.campzone.data.model.VehicleCheckInPayload
import fr.ziyon.campzone.data.model.VehicleScanResult
import fr.ziyon.campzone.data.model.VehicleStatus
import fr.ziyon.campzone.ui.checkin.QrCameraPreview
import fr.ziyon.campzone.ui.transportation.QrCodeImage
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTransportationRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenVehicleForm: (String?) -> Unit,
    onOpenVehicleQr: (String) -> Unit,
    initialJoinCode: String? = null,
    initialDecisionKind: String? = null,
    initialVehicleId: String? = null,
    initialRegistrationId: String? = null,
    modifier: Modifier = Modifier,
    viewModel: VehicleViewModel = hiltViewModel(),
) {
    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.load(campingId, authenticatedUser)
        viewModel.loadSavedVehicles(authenticatedUser.uid)
    }
    val state by viewModel.uiState.collectAsState()
    val requestCancelledMessage = stringResource(R.string.vehicle_request_cancelled)
    VehicleScaffold(
        title = stringResource(R.string.transportation_title),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        VehicleStateContent(
            state = state.loadState,
            onRetry = { viewModel.retry(campingId, authenticatedUser) },
            modifier = Modifier.padding(padding),
        ) {
            val attendee = state.actionSubjectAttendee(
                user = authenticatedUser,
                initialDecisionKind = initialDecisionKind,
                initialRegistrationId = initialRegistrationId,
            )
            if (attendee == null) {
                CzEmptyState(
                    title = stringResource(R.string.vehicle_approval_needed_title),
                    message = stringResource(R.string.vehicle_transport_approval_needed_message),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                MyTransportationContent(
                    state = state,
                    attendee = attendee,
                    onOpenVehicleForm = onOpenVehicleForm,
                    onOpenVehicleQr = onOpenVehicleQr,
                    onRequestHelp = { viewModel.requestTransportHelp(campingId, attendee.id) },
                    onClearTransport = { viewModel.clearTransportIntent(campingId, attendee.id, requestCancelledMessage) },
                    onJoinVehicle = { vehicle -> viewModel.requestJoin(vehicle, attendee) },
                    onJoinByCode = { code -> viewModel.joinByInvitationCode(campingId, code, attendee) },
                    onWithdraw = { vehicle -> viewModel.withdrawJoinRequest(vehicle, attendee.id) },
                    onCancelVehicle = { vehicle -> viewModel.cancelVehicle(vehicle, attendee.id) },
                    onApprove = viewModel::approvePassenger,
                    onDeny = viewModel::denyPassenger,
                    onRemovePassenger = viewModel::removePassenger,
                    onAddPassenger = { vehicle, passenger -> viewModel.addPassenger(vehicle, passenger) },
                    onInvitePassenger = { vehicle, passenger -> viewModel.invitePassenger(vehicle, passenger) },
                    currentUser = authenticatedUser,
                    onRespondToInvitation = { vehicle, accept -> viewModel.respondToInvitation(vehicle, attendee, accept) },
                    initialJoinCode = initialJoinCode,
                    initialDecisionKind = initialDecisionKind,
                    initialVehicleId = initialVehicleId,
                    initialRegistrationId = initialRegistrationId,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleFormRoute(
    campingId: String,
    vehicleId: String?,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onShowQr: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VehicleViewModel = hiltViewModel(),
) {
    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.load(campingId, authenticatedUser)
        viewModel.loadSavedVehicles(authenticatedUser.uid)
    }
    val state by viewModel.uiState.collectAsState()
    val savedState by viewModel.savedVehicleState.collectAsState()
    VehicleScaffold(
        title = stringResource(if (vehicleId == null) R.string.vehicle_register_car_title else R.string.vehicle_edit_car_title),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        VehicleStateContent(
            state = state.loadState,
            onRetry = { viewModel.retry(campingId, authenticatedUser) },
            modifier = Modifier.padding(padding),
        ) {
            val attendee = state.selfAttendee(authenticatedUser)
            if (attendee == null) {
                CzEmptyState(
                    title = stringResource(R.string.vehicle_approval_needed_title),
                    message = stringResource(R.string.vehicle_form_approval_needed_message),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                val existingVehicle = vehicleId?.let(state::vehicle)
                VehicleFormContent(
                    existing = existingVehicle,
                    initialDriverName = existingVehicle?.driverName
                        ?: authenticatedUser.preferredDisplayName.ifBlank { attendee.displayName },
                    savedVehicles = savedState.vehicles,
                    familyCandidates = state.camping?.attendees.orEmpty()
                        .filter { it.registrationStatus == RegistrationApprovalStatus.Approved }
                        .filter { it.guardianId == authenticatedUser.uid }
                        .filterNot { it.id == attendee.id }
                        .filterNot { state.isRegistrationClaimed(it.id) },
                    saving = state.savingVehicle,
                    error = state.operationError,
                    onSubmitNew = { input ->
                        viewModel.createVehicle(
                            campingId = campingId,
                            user = authenticatedUser,
                            attendee = attendee,
                            input = input,
                            onCreated = { onShowQr(it.id) },
                        )
                    },
                    onSubmitExisting = { vehicle ->
                        viewModel.updateVehicle(vehicle) { onBack() }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleQrRoute(
    campingId: String,
    vehicleId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VehicleViewModel = hiltViewModel(),
) {
    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.load(campingId, authenticatedUser)
    }
    val state by viewModel.uiState.collectAsState()
    VehicleScaffold(
        title = stringResource(R.string.vehicle_qr_title),
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(onClick = { onEdit(vehicleId) }) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.common_edit))
            }
        },
    ) { padding ->
        VehicleStateContent(
            state = state.loadState,
            onRetry = { viewModel.retry(campingId, authenticatedUser) },
            modifier = Modifier.padding(padding),
        ) {
            val vehicle = state.vehicle(vehicleId)
            if (vehicle == null) {
                CzEmptyState(
                    title = stringResource(R.string.vehicle_unavailable_title),
                    message = stringResource(R.string.vehicle_unavailable_message),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                VehicleQrContent(vehicle = vehicle, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampingVehiclesRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenArrival: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VehicleViewModel = hiltViewModel(),
) {
    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.load(campingId, authenticatedUser, requireManager = true)
    }
    val state by viewModel.uiState.collectAsState()
    VehicleScaffold(
        title = stringResource(R.string.vehicle_dashboard_title),
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(onClick = onOpenScanner) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.vehicle_scan_car_qr))
            }
        },
    ) { padding ->
        VehicleStateContent(
            state = state.loadState,
            restrictedMessage = stringResource(R.string.vehicle_manager_arrivals_restricted),
            onRetry = { viewModel.retry(campingId, authenticatedUser, requireManager = true) },
            modifier = Modifier.padding(padding),
        ) {
            CampingVehiclesContent(
                state = state,
                onOpenScanner = onOpenScanner,
                onOpenArrival = onOpenArrival,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleScanRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenArrival: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VehicleViewModel = hiltViewModel(),
) {
    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.load(campingId, authenticatedUser, requireManager = true)
    }
    val state by viewModel.uiState.collectAsState()
    val filteredVehicles by viewModel.filteredVehicles.collectAsState()
    val query by viewModel.vehicleSearchText.collectAsState()
    VehicleScaffold(
        title = stringResource(R.string.vehicle_scan_car_qr),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        VehicleStateContent(
            state = state.loadState,
            restrictedMessage = stringResource(R.string.vehicle_manager_scan_restricted),
            onRetry = { viewModel.retry(campingId, authenticatedUser, requireManager = true) },
            modifier = Modifier.padding(padding),
        ) {
            VehicleScanContent(
                state = state,
                query = query,
                filteredVehicles = filteredVehicles,
                onQueryChange = { viewModel.vehicleSearchText.value = it },
                onQrScanned = { viewModel.handleScan(it, campingId) },
                onDismissResult = viewModel::dismissScanResult,
                onOpenArrival = onOpenArrival,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleArrivalRoute(
    campingId: String,
    vehicleId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VehicleViewModel = hiltViewModel(),
) {
    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.load(campingId, authenticatedUser, requireManager = true)
    }
    val state by viewModel.uiState.collectAsState()
    VehicleScaffold(
        title = stringResource(R.string.vehicle_confirm_arrival_title),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        VehicleStateContent(
            state = state.loadState,
            restrictedMessage = stringResource(R.string.vehicle_manager_confirm_restricted),
            onRetry = { viewModel.retry(campingId, authenticatedUser, requireManager = true) },
            modifier = Modifier.padding(padding),
        ) {
            val vehicle = state.vehicle(vehicleId)
            if (vehicle == null) {
                CzEmptyState(
                    title = stringResource(R.string.vehicle_unavailable_title),
                    message = stringResource(R.string.vehicle_unavailable_message),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                VehicleArrivalContent(
                    vehicle = vehicle,
                    isUpdating = state.isUpdating,
                    error = state.operationError,
                    onConfirm = { present, plateConfirmed, notes ->
                        viewModel.confirmArrival(
                            vehicle = vehicle,
                            presentRegistrationIds = present,
                            plateNumberConfirmed = plateConfirmed,
                            notes = notes,
                            reviewer = authenticatedUser,
                            onDone = onDone,
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyVehiclesRoute(
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenEditor: (String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VehicleViewModel = hiltViewModel(),
) {
    LaunchedEffect(authenticatedUser.uid) {
        viewModel.loadSavedVehicles(authenticatedUser.uid)
    }
    val state by viewModel.savedVehicleState.collectAsState()
    VehicleScaffold(
        title = stringResource(R.string.profile_my_vehicles),
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(onClick = { onOpenEditor(null) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.vehicle_add_cd))
            }
        },
    ) { padding ->
        VehicleStateContent(
            state = state.loadState,
            onRetry = { viewModel.retrySavedVehicles(authenticatedUser.uid) },
            modifier = Modifier.padding(padding),
        ) {
            MyVehiclesContent(
                state = state,
                onOpenEditor = onOpenEditor,
                onDelete = viewModel::deleteUserVehicle,
                onSetDefault = viewModel::setDefaultUserVehicle,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserVehicleEditorRoute(
    vehicleId: String?,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VehicleViewModel = hiltViewModel(),
) {
    LaunchedEffect(authenticatedUser.uid) {
        viewModel.loadSavedVehicles(authenticatedUser.uid)
    }
    val state by viewModel.savedVehicleState.collectAsState()
    VehicleScaffold(
        title = stringResource(if (vehicleId == null) R.string.vehicle_save_title else R.string.vehicle_edit_title),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        VehicleStateContent(
            state = state.loadState,
            onRetry = { viewModel.retrySavedVehicles(authenticatedUser.uid) },
            modifier = Modifier.padding(padding),
        ) {
            UserVehicleEditorContent(
                ownerUserId = authenticatedUser.uid,
                existing = vehicleId?.let { id -> state.vehicles.firstOrNull { it.id == id } },
                isSaving = state.isSaving,
                error = state.operationError,
                onSave = { vehicle -> viewModel.saveUserVehicle(vehicle, onBack) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun MyVehicleCard(
    state: VehicleUiState,
    user: AuthenticatedUser,
    onOpenTransport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val attendee = state.selfAttendee(user) ?: return
    val driven = state.vehicleDriven(attendee.id)
    val ridden = state.vehicleRidden(attendee.id)
    val pending = state.pendingVehicle(attendee.id)
    val title = when {
        driven != null -> stringResource(R.string.vehicle_card_qr_ready)
        ridden != null -> stringResource(R.string.vehicle_card_your_ride)
        pending != null -> stringResource(R.string.vehicle_card_request_pending)
        attendee.needsTransportHelp -> stringResource(R.string.vehicle_transport_help_requested)
        else -> stringResource(R.string.vehicle_card_setup_transport)
    }
    val subtitle = when {
        driven != null -> pluralStringResource(R.plurals.vehicle_free_seats_summary, driven.offeredSeatCount, driven.plateNumber, driven.offeredSeatCount)
        ridden != null -> stringResource(R.string.vehicle_riding_with, ridden.driverName)
        pending != null -> stringResource(R.string.vehicle_waiting_for, pending.driverName)
        attendee.needsTransportHelp -> stringResource(R.string.vehicle_leadership_can_see_request)
        else -> stringResource(R.string.vehicle_setup_transport_subtitle)
    }
    VehicleCard(
        modifier = modifier.clickable(onClick = onOpenTransport),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBubble(Icons.Filled.DirectionsCar, MaterialTheme.czColors.accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.czColors.textPrimary)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.czColors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Filled.QrCode, contentDescription = null, tint = MaterialTheme.czColors.textSecondary)
        }
    }
}

@Composable
private fun MyTransportationContent(
    state: VehicleUiState,
    attendee: CampingAttendee,
    currentUser: AuthenticatedUser,
    onOpenVehicleForm: (String?) -> Unit,
    onOpenVehicleQr: (String) -> Unit,
    onRequestHelp: () -> Unit,
    onClearTransport: () -> Unit,
    onJoinVehicle: (CampingVehicle) -> Unit,
    onJoinByCode: (String) -> Unit,
    onWithdraw: (CampingVehicle) -> Unit,
    onCancelVehicle: (CampingVehicle) -> Unit,
    onApprove: (CampingVehicle, String) -> Unit,
    onDeny: (CampingVehicle, String) -> Unit,
    onRemovePassenger: (CampingVehicle, String) -> Unit,
    onAddPassenger: (CampingVehicle, CampingAttendee) -> Unit,
    onInvitePassenger: (CampingVehicle, CampingAttendee) -> Unit,
    onRespondToInvitation: (CampingVehicle, Boolean) -> Unit,
    initialJoinCode: String? = null,
    initialDecisionKind: String? = null,
    initialVehicleId: String? = null,
    initialRegistrationId: String? = null,
    modifier: Modifier = Modifier,
) {
    val driven = state.vehicleDriven(attendee.id)
    val ridden = state.vehicleRidden(attendee.id)
    val pending = state.pendingVehicle(attendee.id)
    var showFindRide by remember { mutableStateOf(false) }
    var showJoinCode by remember(initialJoinCode) { mutableStateOf(initialJoinCode != null) }
    var showDecision by remember(initialDecisionKind, initialVehicleId, initialRegistrationId) {
        mutableStateOf(initialDecisionKind != null && initialVehicleId != null && initialRegistrationId != null)
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        state.operationError?.let { error ->
            item("error") { VehicleMessageCard(error, Icons.Filled.Warning, MaterialTheme.czColors.error) }
        }
        state.operationMessage?.let { message ->
            item("message") { VehicleMessageCard(message, Icons.Filled.CheckCircle, MaterialTheme.czColors.success) }
        }
        when {
            driven != null -> {
                item("driver") {
                    DriverVehicleSection(
                        state = state,
                        vehicle = driven,
                        attendee = attendee,
                        onOpenQr = { onOpenVehicleQr(driven.id) },
                        onEdit = { onOpenVehicleForm(driven.id) },
                        onCancel = { onCancelVehicle(driven) },
                        onApprove = onApprove,
                        onDeny = onDeny,
                        onRemovePassenger = onRemovePassenger,
                        onAddPassenger = onAddPassenger,
                        onInvitePassenger = onInvitePassenger,
                        currentUser = currentUser,
                    )
                }
            }
            ridden != null -> {
                item("riding") {
                    PassengerRideSection(
                        vehicle = ridden,
                        onLeaveCar = { onRemovePassenger(ridden, attendee.id) },
                    )
                }
            }
            pending != null -> {
                item("pending") {
                    StatusHeader(
                        icon = Icons.Filled.Warning,
                        title = stringResource(R.string.vehicle_waiting_approval_title),
                        subtitle = stringResource(R.string.vehicle_asked_to_ride_with, pending.driverName),
                        color = MaterialTheme.czColors.warning,
                    )
                    Spacer(Modifier.height(CzSpacing.md))
                    CzButton(
                        text = stringResource(R.string.vehicle_cancel_request),
                        onClick = { onWithdraw(pending) },
                        variant = CzButtonVariant.Destructive,
                    )
                }
            }
            attendee.needsTransportHelp -> {
                item("help") {
                    StatusHeader(
                        icon = Icons.Filled.Help,
                        title = stringResource(R.string.vehicle_transport_help_requested),
                        subtitle = stringResource(R.string.vehicle_organizer_will_contact),
                        color = MaterialTheme.czColors.warning,
                    )
                    Spacer(Modifier.height(CzSpacing.md))
                    CzButton(text = stringResource(R.string.vehicle_find_ride_instead), onClick = { showFindRide = !showFindRide })
                    Spacer(Modifier.height(CzSpacing.sm))
                    CzButton(
                        text = stringResource(R.string.vehicle_cancel_request),
                        onClick = onClearTransport,
                        variant = CzButtonVariant.Destructive,
                    )
                }
                if (showFindRide) {
                    item("find") {
                        FindRideCard(
                            state = state,
                            attendee = attendee,
                            onJoinVehicle = onJoinVehicle,
                            onJoinByCode = { showJoinCode = true },
                        )
                    }
                }
            }
            else -> {
                item("chooser") {
                    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
                        Text(
                            stringResource(R.string.vehicle_transport_choice_title),
                            color = MaterialTheme.czColors.textPrimary,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            stringResource(R.string.vehicle_transport_choice_subtitle),
                            color = MaterialTheme.czColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        ChooserCard(
                            icon = Icons.Filled.DirectionsCar,
                            title = stringResource(R.string.vehicle_choice_driving_title),
                            subtitle = stringResource(R.string.vehicle_choice_driving_subtitle),
                            onClick = { onOpenVehicleForm(null) },
                        )
                        ChooserCard(
                            icon = Icons.Filled.Groups,
                            title = stringResource(R.string.vehicle_choice_passenger_title),
                            subtitle = stringResource(R.string.vehicle_choice_passenger_subtitle),
                            onClick = { showFindRide = !showFindRide },
                        )
                        ChooserCard(
                            icon = Icons.Filled.Help,
                            title = stringResource(R.string.vehicle_choice_need_transport_title),
                            subtitle = stringResource(R.string.vehicle_choice_need_transport_subtitle),
                            onClick = onRequestHelp,
                        )
                    }
                }
                if (showFindRide) {
                    item("find") {
                        FindRideCard(
                            state = state,
                            attendee = attendee,
                            onJoinVehicle = onJoinVehicle,
                            onJoinByCode = { showJoinCode = true },
                        )
                    }
                }
            }
        }
    }

    if (showJoinCode) {
        JoinCodeDialog(
            initialCode = initialJoinCode.orEmpty(),
            onDismiss = { showJoinCode = false },
            onJoin = {
                showJoinCode = false
                onJoinByCode(it)
            },
        )
    }

    if (showDecision) {
        val decisionVehicle = state.vehicle(initialVehicleId.orEmpty())
        TransportationDecisionSheet(
            kind = initialDecisionKind.orEmpty(),
            vehicle = decisionVehicle,
            registrationId = initialRegistrationId.orEmpty(),
            isUpdating = state.isUpdating,
            onDismiss = { showDecision = false },
            onAccept = {
                if (decisionVehicle != null) {
                    if (initialDecisionKind == "invitation") {
                        onRespondToInvitation(decisionVehicle, true)
                    } else {
                        onApprove(decisionVehicle, initialRegistrationId.orEmpty())
                    }
                    showDecision = false
                }
            },
            onDecline = {
                if (decisionVehicle != null) {
                    if (initialDecisionKind == "invitation") {
                        onRespondToInvitation(decisionVehicle, false)
                    } else {
                        onDeny(decisionVehicle, initialRegistrationId.orEmpty())
                    }
                    showDecision = false
                }
            },
        )
    }
}

@Composable
private fun DriverVehicleSection(
    state: VehicleUiState,
    vehicle: CampingVehicle,
    attendee: CampingAttendee,
    currentUser: AuthenticatedUser,
    onOpenQr: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onApprove: (CampingVehicle, String) -> Unit,
    onDeny: (CampingVehicle, String) -> Unit,
    onRemovePassenger: (CampingVehicle, String) -> Unit,
    onAddPassenger: (CampingVehicle, CampingAttendee) -> Unit,
    onInvitePassenger: (CampingVehicle, CampingAttendee) -> Unit,
) {
    var passengerPickerMode by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
        StatusHeader(
            icon = Icons.Filled.DirectionsCar,
            title = stringResource(R.string.vehicle_you_are_driving),
            subtitle = pluralStringResource(R.plurals.vehicle_free_seats_summary, vehicle.offeredSeatCount, vehicle.plateNumber, vehicle.offeredSeatCount),
            color = MaterialTheme.czColors.accent,
        )
        CzButton(text = stringResource(R.string.vehicle_show_car_qr), onClick = onOpenQr)
        if (vehicle.pendingPassengers.isNotEmpty()) {
            VehicleCard {
                SectionTitle(stringResource(R.string.vehicle_requests), Icons.Filled.Warning)
                vehicle.pendingPassengers.forEach { passenger ->
                    PassengerActionRow(
                        name = passenger.name,
                        primary = stringResource(R.string.vehicle_approve),
                        onPrimary = { onApprove(vehicle, passenger.id) },
                        secondary = stringResource(R.string.vehicle_decline),
                        onSecondary = { onDeny(vehicle, passenger.id) },
                    )
                }
            }
        }
        VehicleCard {
            SectionTitle(stringResource(R.string.vehicle_passengers), Icons.Filled.Groups)
            if (vehicle.passengers.isEmpty()) {
                Text(stringResource(R.string.vehicle_no_passengers_yet), color = MaterialTheme.czColors.textSecondary)
            } else {
                vehicle.passengers.forEach { passenger ->
                    PassengerActionRow(
                        name = passenger.name.ifBlank { passenger.id },
                        primary = stringResource(R.string.vehicle_remove_passenger),
                        onPrimary = { onRemovePassenger(vehicle, passenger.id) },
                    )
                }
            }
            if (vehicle.offeredSeatCount > 0) {
                val candidates = state.camping?.attendees.orEmpty()
                    .filter { it.registrationStatus == RegistrationApprovalStatus.Approved }
                    .filterNot { it.id == attendee.id }
                    .filterNot { state.isRegistrationClaimed(it.id) }
                val directCandidates = candidates.filter {
                    state.canManageTransportation || it.guardianId == currentUser.uid
                }
                val inviteCandidates = candidates.filterNot { it.guardianId == currentUser.uid }
                if (directCandidates.isNotEmpty()) TextButton(onClick = { passengerPickerMode = "add" }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.vehicle_add_passenger))
                }
                if (inviteCandidates.isNotEmpty()) TextButton(onClick = { passengerPickerMode = "invite" }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Text(stringResource(R.string.vehicle_invite_passenger))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            CzButton(text = stringResource(R.string.common_edit), onClick = onEdit, modifier = Modifier.weight(1f))
            CzButton(
                text = stringResource(R.string.vehicle_cancel_car),
                onClick = onCancel,
                variant = CzButtonVariant.Destructive,
                modifier = Modifier.weight(1f),
            )
        }
    }
    passengerPickerMode?.let { mode ->
        val candidates = state.camping?.attendees.orEmpty()
            .filter { it.registrationStatus == RegistrationApprovalStatus.Approved }
            .filterNot { it.id == attendee.id }
            .filterNot { state.isRegistrationClaimed(it.id) }
            .filter {
                if (mode == "add") state.canManageTransportation || it.guardianId == currentUser.uid
                else it.guardianId != currentUser.uid
            }
        PassengerPickerDialog(
            candidates = candidates,
            title = stringResource(if (mode == "add") R.string.vehicle_add_passenger else R.string.vehicle_invite_passenger),
            onDismiss = { passengerPickerMode = null },
            onPick = {
                passengerPickerMode = null
                if (mode == "add") onAddPassenger(vehicle, it) else onInvitePassenger(vehicle, it)
            },
        )
    }
}

@Composable
private fun PassengerRideSection(
    vehicle: CampingVehicle,
    onLeaveCar: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
        StatusHeader(
            icon = Icons.Filled.Groups,
            title = stringResource(R.string.vehicle_you_are_passenger),
            subtitle = stringResource(R.string.vehicle_riding_with, vehicle.driverName),
            color = MaterialTheme.czColors.success,
        )
        VehicleCard {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CzAvatar(
                    imageUrl = vehicle.driverPhotoUrl,
                    contentDescription = vehicle.driverName,
                    initials = vehicle.driverName,
                    size = CzAvatarSize.Medium,
                )
                Column(Modifier.weight(1f)) {
                    Text(vehicle.driverName, color = MaterialTheme.czColors.textPrimary, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.vehicle_driver), color = MaterialTheme.czColors.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Text(vehicle.maskedPlate, color = MaterialTheme.czColors.textPrimary, fontWeight = FontWeight.Bold)
            }
            if (vehicle.passengers.isNotEmpty()) {
                Spacer(Modifier.height(CzSpacing.sm))
                vehicle.passengers.forEach { passenger ->
                    Text(passenger.name.ifBlank { passenger.id }, color = MaterialTheme.czColors.textSecondary)
                }
            }
        }
        CzButton(
            text = stringResource(R.string.vehicle_leave_car),
            onClick = onLeaveCar,
            variant = CzButtonVariant.Destructive,
        )
        Text(
            stringResource(R.string.vehicle_passenger_change_hint),
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun FindRideCard(
    state: VehicleUiState,
    attendee: CampingAttendee,
    onJoinVehicle: (CampingVehicle) -> Unit,
    onJoinByCode: () -> Unit,
) {
    VehicleCard {
        SectionTitle(stringResource(R.string.vehicle_cars_with_free_seats), Icons.Filled.CarRental)
        val rides = state.availableSeatVehicles.filter { it.driverRegistrationId != attendee.id }
        if (rides.isEmpty()) {
            Text(stringResource(R.string.vehicle_no_free_seats_hint), color = MaterialTheme.czColors.textSecondary)
        } else {
            rides.forEach { vehicle ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(vehicle.driverName, color = MaterialTheme.czColors.textPrimary, fontWeight = FontWeight.SemiBold)
                        Text(pluralStringResource(R.plurals.vehicle_free_seats_count, vehicle.offeredSeatCount, vehicle.offeredSeatCount), color = MaterialTheme.czColors.textSecondary)
                    }
                    TextButton(onClick = { onJoinVehicle(vehicle) }) { Text(stringResource(R.string.vehicle_request)) }
                }
            }
        }
        TextButton(onClick = onJoinByCode) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Text(stringResource(R.string.vehicle_have_invitation_code))
        }
    }
}

@Composable
private fun VehicleFormContent(
    existing: CampingVehicle?,
    initialDriverName: String,
    savedVehicles: List<UserVehicle>,
    familyCandidates: List<CampingAttendee>,
    saving: Boolean,
    error: String?,
    onSubmitNew: (VehicleFormInput) -> Unit,
    onSubmitExisting: (CampingVehicle) -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableIntStateOf(0) }
    var selectedSavedId by remember(existing?.userVehicleId) { mutableStateOf(existing?.userVehicleId) }
    var driverName by remember(existing?.id, initialDriverName) { mutableStateOf(initialDriverName) }
    var plate by remember(existing?.id) { mutableStateOf(existing?.plateNumber.orEmpty()) }
    var brand by remember(existing?.id) { mutableStateOf(existing?.brand.orEmpty()) }
    var model by remember(existing?.id) { mutableStateOf(existing?.model.orEmpty()) }
    var color by remember(existing?.id) { mutableStateOf(existing?.color.orEmpty()) }
    var totalSeats by remember(existing?.id) { mutableIntStateOf(existing?.totalSeats ?: 5) }
    var peopleInCar by remember(existing?.id) { mutableIntStateOf(existing?.accountedOccupiedSeats ?: 1) }
    var hasAvailableSeats by remember(existing?.id) { mutableStateOf(existing?.hasAvailableSeats ?: true) }
    var offeredSeats by remember(existing?.id) {
        mutableIntStateOf((existing?.offeredSeats ?: existing?.availableSeats ?: 1).coerceAtLeast(1))
    }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var selectedFamilyIds by remember(existing?.id) { mutableStateOf(emptySet<String>()) }
    val availableSeats = maxOf(0, totalSeats - peopleInCar)
    val displayedOfferedSeats = if (hasAvailableSeats) offeredSeats.coerceIn(0, availableSeats) else 0
    val canAdvance = when (step) {
        0 -> driverName.trim().isNotEmpty() && plate.trim().isNotEmpty()
        1 -> totalSeats in 1..9 && peopleInCar in 1..totalSeats
        else -> true
    }

    fun clampSeatOffer() {
        val currentAvailableSeats = maxOf(0, totalSeats - peopleInCar)
        if (currentAvailableSeats <= 0) {
            hasAvailableSeats = false
            offeredSeats = 1
        } else {
            offeredSeats = offeredSeats.coerceIn(1, currentAvailableSeats)
        }
    }

    fun applySaved(vehicle: UserVehicle) {
        selectedSavedId = vehicle.id
        plate = vehicle.plateNumber
        brand = vehicle.brand.orEmpty()
        model = vehicle.model.orEmpty()
        color = vehicle.color.orEmpty()
        totalSeats = vehicle.defaultTotalSeats
        peopleInCar = 1.coerceAtMost(totalSeats)
        clampSeatOffer()
    }

    fun submit() {
        if (saving || !canAdvance) return
        if (step < 3) {
            step += 1
            return
        }
        if (existing == null) {
            onSubmitNew(
                VehicleFormInput(
                    driverName = driverName.trim(),
                    plateNumber = plate.trim().uppercase(Locale.ROOT),
                    brand = brand,
                    model = model,
                    color = color,
                    totalSeats = totalSeats,
                    peopleInCar = peopleInCar,
                    hasAvailableSeats = hasAvailableSeats,
                    offeredSeats = displayedOfferedSeats.takeIf { hasAvailableSeats && availableSeats > 0 },
                    notes = notes,
                    userVehicleId = selectedSavedId,
                    passengers = familyCandidates.filter { it.id in selectedFamilyIds },
                ),
            )
        } else {
            onSubmitExisting(
                existing.copy(
                    driverName = driverName.trim(),
                    plateNumber = plate.trim().uppercase(Locale.ROOT),
                    brand = brand.clean(),
                    model = model.clean(),
                    color = color.clean(),
                    totalSeats = totalSeats,
                    occupiedSeats = peopleInCar,
                    hasAvailableSeats = hasAvailableSeats,
                    offeredSeats = displayedOfferedSeats.takeIf { hasAvailableSeats && availableSeats > 0 },
                    notes = notes.clean(),
                ),
            )
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        VehicleFormProgressHeader(currentStep = step, stepCount = 4)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CzSpacing.lg)
                .padding(top = CzSpacing.lg, bottom = CzSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
        ) {
            error?.let { VehicleMessageCard(it, Icons.Filled.Warning, MaterialTheme.czColors.error) }
            when (step) {
                0 -> {
                    VehicleFormStepHeader(
                        title = stringResource(R.string.vehicle_form_car_title),
                        subtitle = stringResource(R.string.vehicle_form_car_subtitle),
                    )
                    if (existing == null && savedVehicles.isNotEmpty()) {
                        VehicleSavedVehiclePicker(
                            savedVehicles = savedVehicles,
                            selectedSavedId = selectedSavedId,
                            onSelect = ::applySaved,
                        )
                    }
                    VehicleFormCard {
                        VehicleFormTextRow(
                            value = driverName,
                            onValueChange = { driverName = it },
                            label = stringResource(R.string.vehicle_driver),
                            icon = Icons.Filled.Person,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        )
                        VehicleFormDivider()
                        VehicleFormTextRow(
                            value = plate,
                            onValueChange = { plate = it.uppercase(Locale.ROOT) },
                            label = stringResource(R.string.registration_plate_number),
                            icon = Icons.Filled.Info,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                keyboardType = KeyboardType.Text,
                            ),
                        )
                    }
                    VehicleFormCard {
                        VehicleFormTextRow(
                            value = brand,
                            onValueChange = { brand = it },
                            label = stringResource(R.string.vehicle_brand_optional),
                            icon = Icons.Filled.DirectionsCar,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        )
                        VehicleFormDivider()
                        VehicleFormTextRow(
                            value = model,
                            onValueChange = { model = it },
                            label = stringResource(R.string.vehicle_model_optional),
                            icon = Icons.Filled.CarRental,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        )
                        VehicleFormDivider()
                        VehicleFormTextRow(
                            value = color,
                            onValueChange = { color = it },
                            label = stringResource(R.string.vehicle_color_optional),
                            icon = Icons.Filled.Star,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        )
                    }
                }
                1 -> {
                    VehicleFormStepHeader(
                        title = stringResource(R.string.vehicle_form_seats_title),
                        subtitle = stringResource(R.string.vehicle_form_seats_subtitle),
                    )
                    VehicleFormCard {
                        VehicleFormSeatStepperRow(
                            title = stringResource(R.string.vehicle_car_capacity),
                            subtitle = stringResource(R.string.vehicle_car_capacity_subtitle),
                            icon = Icons.Filled.DirectionsCar,
                            value = totalSeats,
                            range = 1..9,
                            onValueChange = {
                                totalSeats = it
                                selectedFamilyIds = selectedFamilyIds.take((totalSeats - 1).coerceAtLeast(0)).toSet()
                                if (peopleInCar > totalSeats) peopleInCar = totalSeats
                                peopleInCar = maxOf(peopleInCar, selectedFamilyIds.size + 1)
                                clampSeatOffer()
                            },
                        )
                        VehicleFormDivider()
                        VehicleFormSeatStepperRow(
                            title = stringResource(R.string.vehicle_people_travelling),
                            subtitle = stringResource(R.string.vehicle_people_travelling_subtitle),
                            icon = Icons.Filled.Groups,
                            value = peopleInCar,
                            range = (selectedFamilyIds.size + 1).coerceAtMost(totalSeats)..totalSeats,
                            onValueChange = {
                                peopleInCar = it
                                clampSeatOffer()
                            },
                        )
                    }
                    if (existing == null && familyCandidates.isNotEmpty()) {
                        VehicleFamilyPassengerPicker(
                            familyCandidates = familyCandidates,
                            selectedFamilyIds = selectedFamilyIds,
                            canSelectMore = selectedFamilyIds.size + 1 < totalSeats,
                            onToggle = { passenger ->
                                selectedFamilyIds = if (passenger.id in selectedFamilyIds) {
                                    selectedFamilyIds - passenger.id
                                } else if (selectedFamilyIds.size + 1 < totalSeats) {
                                    selectedFamilyIds + passenger.id
                                } else {
                                    selectedFamilyIds
                                }
                                peopleInCar = maxOf(peopleInCar, selectedFamilyIds.size + 1)
                                clampSeatOffer()
                            },
                        )
                    }
                    VehicleOfferRideCard(
                        availableSeats = availableSeats,
                        checked = hasAvailableSeats,
                        onCheckedChange = {
                            hasAvailableSeats = it && availableSeats > 0
                            clampSeatOffer()
                        },
                        offeredSeats = offeredSeats.coerceIn(1, maxOf(1, availableSeats)),
                        onOfferedSeatsChange = { offeredSeats = it },
                    )
                }
                2 -> {
                    VehicleFormStepHeader(
                        title = stringResource(R.string.vehicle_form_notes_title),
                        subtitle = stringResource(R.string.vehicle_form_notes_subtitle),
                    )
                    VehicleFormCard {
                        VehicleFormTextRow(
                            value = notes,
                            onValueChange = { notes = it },
                            label = stringResource(R.string.vehicle_notes),
                            icon = Icons.Filled.Info,
                            singleLine = false,
                            minHeight = 120.dp,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                    }
                }
                else -> {
                    VehicleFormReviewStep(
                        driverName = driverName,
                        plate = plate,
                        vehicleDescription = listOf(brand, model, color)
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .joinToString(" "),
                        seatSummary = stringResource(R.string.vehicle_occupied_seats, peopleInCar, totalSeats),
                        offeredSeatSummary = if (hasAvailableSeats) {
                            pluralStringResource(R.plurals.vehicle_review_offering_seats_yes, displayedOfferedSeats, displayedOfferedSeats)
                        } else {
                            stringResource(R.string.common_no)
                        },
                        familyPassengerNames = familyCandidates
                            .filter { it.id in selectedFamilyIds }
                            .joinToString { it.displayName },
                        notes = notes.trim(),
                    )
                }
            }
        }
        VehicleFormBottomBar(
            actionText = when {
                saving -> stringResource(R.string.common_saving_ellipsis)
                step < 3 -> stringResource(R.string.common_continue)
                existing == null -> stringResource(R.string.vehicle_generate_qr_code)
                else -> stringResource(R.string.vehicle_save_changes)
            },
            isSaving = saving,
            actionEnabled = canAdvance && !saving,
            onBack = { if (step > 0) step -= 1 },
            onAction = ::submit,
        )
    }
}

@Composable
private fun VehicleFormProgressHeader(currentStep: Int, stepCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        repeat(stepCount) { index ->
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp),
                color = if (index <= currentStep) MaterialTheme.czColors.accent else MaterialTheme.czColors.divider,
                shape = CircleShape,
            ) {}
        }
    }
}

@Composable
private fun VehicleFormStepHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
        Text(title, color = MaterialTheme.czColors.textPrimary, style = MaterialTheme.typography.headlineSmall)
        Text(subtitle, color = MaterialTheme.czColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun VehicleFormCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.xl),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            content = content,
        )
    }
}

@Composable
private fun VehicleFormDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.czColors.divider),
    )
}

@Composable
private fun VehicleFormTextRow(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minHeight: androidx.compose.ui.unit.Dp = 44.dp,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier
                .padding(top = if (singleLine) 0.dp else 2.dp)
                .width(24.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = minHeight),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = keyboardOptions,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 6,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                ) {
                    if (value.isBlank()) {
                        Text(label, color = colors.textSecondary, style = MaterialTheme.typography.bodyLarge)
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun VehicleSavedVehiclePicker(
    savedVehicles: List<UserVehicle>,
    selectedSavedId: String?,
    onSelect: (UserVehicle) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        SectionTitle(stringResource(R.string.vehicle_use_saved_car), Icons.Filled.CarRental)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            savedVehicles.forEach { saved ->
                val selected = selectedSavedId == saved.id
                Surface(
                    modifier = Modifier.clickable { onSelect(saved) },
                    color = if (selected) {
                        MaterialTheme.czColors.accent.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.czColors.surface
                    },
                    shape = RoundedCornerShape(CzRadius.lg),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (selected) MaterialTheme.czColors.accent else MaterialTheme.czColors.divider,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(CzSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(saved.displayTitle, color = MaterialTheme.czColors.textPrimary, style = MaterialTheme.typography.titleSmall)
                        Text(saved.plateNumber, color = MaterialTheme.czColors.textSecondary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleFormSeatStepperRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon, MaterialTheme.czColors.accent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = MaterialTheme.czColors.textPrimary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.czColors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        VehicleStepperButton("-", enabled = value > range.first) { onValueChange((value - 1).coerceIn(range)) }
        Text(
            text = value.toString(),
            modifier = Modifier.width(28.dp),
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        VehicleStepperButton("+", enabled = value < range.last) { onValueChange((value + 1).coerceIn(range)) }
    }
}

@Composable
private fun VehicleStepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun VehicleFamilyPassengerPicker(
    familyCandidates: List<CampingAttendee>,
    selectedFamilyIds: Set<String>,
    canSelectMore: Boolean,
    onToggle: (CampingAttendee) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        SectionTitle(stringResource(R.string.vehicle_family_participants), Icons.Filled.Groups)
        Text(
            stringResource(R.string.vehicle_family_participants_subtitle),
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        VehicleFormCard {
            familyCandidates.forEachIndexed { index, passenger ->
                val selected = passenger.id in selectedFamilyIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = selected || canSelectMore) { onToggle(passenger) }
                        .padding(vertical = CzSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CzAvatar(
                        imageUrl = passenger.photoUrl,
                        contentDescription = passenger.displayName,
                        initials = passenger.displayName,
                        size = CzAvatarSize.Small,
                    )
                    Text(
                        passenger.displayName,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.czColors.textPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        if (selected) Icons.Filled.CheckCircle else Icons.Filled.Add,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.czColors.accent else MaterialTheme.czColors.textSecondary,
                    )
                }
                if (index != familyCandidates.lastIndex) VehicleFormDivider()
            }
        }
    }
}

@Composable
private fun VehicleOfferRideCard(
    availableSeats: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    offeredSeats: Int,
    onOfferedSeatsChange: (Int) -> Unit,
) {
    val freeSeatText = when (availableSeats) {
        0 -> stringResource(R.string.vehicle_car_full)
        1 -> stringResource(R.string.vehicle_one_free_seat)
        else -> stringResource(R.string.vehicle_free_seats_plain, availableSeats)
    }
    VehicleFormCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBubble(Icons.Filled.Add, if (checked) MaterialTheme.czColors.accent else MaterialTheme.czColors.textSecondary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.vehicle_offer_ride), color = MaterialTheme.czColors.textPrimary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(freeSeatText, color = MaterialTheme.czColors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = checked,
                enabled = availableSeats > 0,
                onCheckedChange = onCheckedChange,
            )
        }
        if (checked && availableSeats > 0) {
            VehicleFormDivider()
            VehicleFormSeatStepperRow(
                title = stringResource(R.string.vehicle_offered_seats),
                subtitle = stringResource(R.string.vehicle_seats_offered_subtitle),
                icon = Icons.Filled.Groups,
                value = offeredSeats,
                range = 1..availableSeats,
                onValueChange = onOfferedSeatsChange,
            )
        }
    }
}

@Composable
private fun VehicleFormReviewStep(
    driverName: String,
    plate: String,
    vehicleDescription: String,
    seatSummary: String,
    offeredSeatSummary: String,
    familyPassengerNames: String,
    notes: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.lg)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VehicleFormStepHeader(
                title = stringResource(R.string.vehicle_form_review_title),
                subtitle = stringResource(R.string.vehicle_form_review_subtitle),
            )
            Spacer(Modifier.weight(1f))
            Surface(
                color = MaterialTheme.czColors.success.copy(alpha = 0.14f),
                shape = CircleShape,
                modifier = Modifier.size(50.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.czColors.success)
                }
            }
        }
        VehicleFormCard {
            VehicleFormReviewRow(stringResource(R.string.vehicle_driver), driverName, Icons.Filled.Person)
            VehicleFormDivider()
            VehicleFormReviewRow(stringResource(R.string.vehicle_plate), plate.uppercase(Locale.ROOT), Icons.Filled.Info)
            if (vehicleDescription.isNotBlank()) {
                VehicleFormDivider()
                VehicleFormReviewRow(stringResource(R.string.vehicle_vehicle_label), vehicleDescription, Icons.Filled.DirectionsCar)
            }
            VehicleFormDivider()
            VehicleFormReviewRow(stringResource(R.string.vehicle_seats), seatSummary, Icons.Filled.Groups)
            VehicleFormDivider()
            VehicleFormReviewRow(stringResource(R.string.vehicle_offering_seats), offeredSeatSummary, Icons.Filled.Add)
            if (familyPassengerNames.isNotBlank()) {
                VehicleFormDivider()
                VehicleFormReviewRow(stringResource(R.string.vehicle_family_participants), familyPassengerNames, Icons.Filled.Groups)
            }
            if (notes.isNotBlank()) {
                VehicleFormDivider()
                VehicleFormReviewRow(stringResource(R.string.vehicle_notes), notes, Icons.Filled.Info)
            }
        }
        Surface(
            color = MaterialTheme.czColors.success.copy(alpha = 0.14f),
            shape = RoundedCornerShape(CzRadius.lg),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = CzSpacing.xl, vertical = CzSpacing.base),
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.QrCode, contentDescription = null, tint = MaterialTheme.czColors.success)
                Text(
                    stringResource(R.string.vehicle_review_qr_hint),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun VehicleFormReviewRow(label: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CzSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.czColors.textSecondary, modifier = Modifier.width(24.dp))
        Text(label, color = MaterialTheme.czColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun VehicleFormBottomBar(
    actionText: String,
    isSaving: Boolean,
    actionEnabled: Boolean,
    onBack: () -> Unit,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.czColors.surface.copy(alpha = 0.96f),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(CzSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(50.dp),
                color = MaterialTheme.czColors.accent,
                shape = CircleShape,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back_cd), tint = Color.White)
                }
            }
            Button(
                onClick = onAction,
                enabled = actionEnabled,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(CzRadius.lg),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.czColors.accent,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.czColors.accent.copy(alpha = 0.35f),
                    disabledContentColor = Color.White.copy(alpha = 0.72f),
                ),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(actionText, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun VehicleQrContent(vehicle: CampingVehicle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val qrValue = remember(vehicle.qrToken) { VehicleCheckInPayload(vehicle.qrToken).encoded() }
    val invitationCode = vehicle.invitationCode.orEmpty()
    val joinUrl = remember(vehicle.campingId, invitationCode) {
        "https://campzone-web.vercel.app/transportation-join/${vehicle.campingId}?code=$invitationCode"
    }
    val shareText = stringResource(R.string.vehicle_share_text, invitationCode, joinUrl)
    val shareChooserTitle = stringResource(R.string.vehicle_share_code_chooser)
    val qrCodeDescription = stringResource(R.string.vehicle_qr_code_cd)
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item("summary") {
            VehicleCard {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CzAvatar(
                        imageUrl = vehicle.driverPhotoUrl,
                        contentDescription = vehicle.driverName,
                        initials = vehicle.driverName,
                        size = CzAvatarSize.Large,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(vehicle.driverName, color = MaterialTheme.czColors.textPrimary, style = MaterialTheme.typography.titleMedium)
                        Text(vehicle.plateNumber, color = MaterialTheme.czColors.textSecondary)
                    }
                    StatusPill(vehicle)
                }
            }
        }
        item("qr") {
            QrCodeImage(
                value = qrValue,
                modifier = Modifier
                    .size(280.dp)
                    .semantics { contentDescription = qrCodeDescription },
            )
        }
        item("code") {
            VehicleCard {
                ReviewLine(stringResource(R.string.vehicle_invitation_code), vehicle.invitationCode ?: stringResource(R.string.vehicle_unavailable_value))
                ReviewLine(stringResource(R.string.vehicle_seats), "${vehicle.accountedOccupiedSeats}/${vehicle.totalSeats}")
                if (vehicle.passengers.isNotEmpty()) {
                    Spacer(Modifier.height(CzSpacing.sm))
                    SectionTitle(stringResource(R.string.vehicle_passengers), Icons.Filled.Groups)
                    vehicle.passengers.forEach {
                        Text(it.name.ifBlank { it.id }, color = MaterialTheme.czColors.textSecondary)
                    }
                }
            }
        }
        item("share") {
            CzButton(
                text = stringResource(R.string.vehicle_share_code),
                onClick = {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            },
                            shareChooserTitle,
                        ),
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CampingVehiclesContent(
    state: VehicleUiState,
    onOpenScanner: () -> Unit,
    onOpenArrival: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
        modifier = Modifier.fillMaxSize(),
    ) {
        item("stats") {
            DashboardStatsGrid(state.dashboardStats)
        }
        item("scan") {
            CzButton(
                text = stringResource(R.string.vehicle_scan_car_qr),
                onClick = onOpenScanner,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
            )
        }
        if (state.peopleNeedingTransport.isNotEmpty()) {
            item("needs") {
                VehicleCard {
                    SectionTitle(stringResource(R.string.vehicle_need_transport), Icons.Filled.Help)
                    state.peopleNeedingTransport.forEach {
                        Text(it.displayName, color = MaterialTheme.czColors.textSecondary)
                    }
                }
            }
        }
        if (state.vehicles.isEmpty()) {
            item("empty") {
                CzEmptyState(
                    title = stringResource(R.string.vehicle_no_cars_title),
                    message = stringResource(R.string.vehicle_no_cars_message),
                )
            }
        } else {
            items(state.vehicles, key = { it.id }) { vehicle ->
                VehicleRow(
                    vehicle = vehicle,
                    showFullPlate = true,
                    onClick = { onOpenArrival(vehicle.id) },
                )
            }
        }
    }
}

@Composable
private fun VehicleScanContent(
    state: VehicleUiState,
    query: String,
    filteredVehicles: List<CampingVehicle>,
    onQueryChange: (String) -> Unit,
    onQrScanned: (String) -> Unit,
    onDismissResult: () -> Unit,
    onOpenArrival: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
        modifier = Modifier.fillMaxSize(),
    ) {
        item("camera") {
            CameraCard(isScanning = state.isScanning, onQrScanned = onQrScanned)
        }
        state.lastScanResult?.let { result ->
            item("result") {
                ScanResultCard(
                    result = result,
                    onDismiss = onDismissResult,
                    onOpenArrival = onOpenArrival,
                )
            }
        }
        item("search") {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.vehicle_search_plate_driver)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(filteredVehicles, key = { it.id }) { vehicle ->
            VehicleRow(
                vehicle = vehicle,
                showFullPlate = true,
                onClick = { onOpenArrival(vehicle.id) },
            )
        }
    }
}

@Composable
private fun VehicleArrivalContent(
    vehicle: CampingVehicle,
    isUpdating: Boolean,
    error: String?,
    onConfirm: (List<String>, Boolean, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expected = remember(vehicle.id, vehicle.passengerRegistrationIds) {
        listOf(ExpectedPerson(vehicle.driverRegistrationId, vehicle.driverName, true, vehicle.driverPhotoUrl)) +
            vehicle.passengers.map { ExpectedPerson(it.id, it.name, false, null) }
    }
    val present = remember(vehicle.id) { mutableStateListOf<String>().apply { addAll(expected.map { it.id }) } }
    var plateConfirmed by remember(vehicle.id) { mutableStateOf(false) }
    var notes by remember(vehicle.id) { mutableStateOf("") }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        if (vehicle.hasArrived) {
            item("arrived") {
                VehicleMessageCard(stringResource(R.string.vehicle_already_marked_arrived), Icons.Filled.Info, MaterialTheme.czColors.warning)
            }
        }
        error?.let {
            item("error") { VehicleMessageCard(it, Icons.Filled.Warning, MaterialTheme.czColors.error) }
        }
        item("summary") {
            VehicleCard {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CzAvatar(
                        imageUrl = vehicle.driverPhotoUrl,
                        contentDescription = vehicle.driverName,
                        initials = vehicle.driverName,
                        size = CzAvatarSize.Large,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(vehicle.driverName, color = MaterialTheme.czColors.textPrimary, style = MaterialTheme.typography.titleMedium)
                        Text(vehicle.plateNumber, color = MaterialTheme.czColors.textSecondary)
                    }
                    Text(pluralStringResource(R.plurals.vehicle_expected_count, vehicle.expectedRegisteredCount, vehicle.expectedRegisteredCount), color = MaterialTheme.czColors.accent)
                }
            }
        }
        item("plate") {
            ToggleRow(
                title = stringResource(R.string.vehicle_plate_confirmed),
                subtitle = vehicle.plateNumber,
                checked = plateConfirmed,
                onCheckedChange = { plateConfirmed = it },
            )
        }
        item("people") {
            VehicleCard {
                SectionTitle(stringResource(R.string.vehicle_people_in_this_car), Icons.Filled.Groups)
                expected.forEach { person ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = person.id in present,
                            onCheckedChange = { checked ->
                                if (checked) present.add(person.id) else present.remove(person.id)
                            },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(person.name.ifBlank { person.id }, color = MaterialTheme.czColors.textPrimary)
                            Text(stringResource(if (person.isDriver) R.string.vehicle_driver else R.string.vehicle_passenger), color = MaterialTheme.czColors.textSecondary)
                        }
                    }
                }
                TextButton(onClick = {
                    present.clear()
                    present.addAll(expected.map { it.id })
                }) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Text(stringResource(R.string.vehicle_everyone_arrived))
                }
            }
        }
        item("notes") {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.vehicle_notes)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item("confirm") {
            CzButton(
                text = stringResource(if (isUpdating) R.string.vehicle_confirming_action else R.string.vehicle_confirm_arrival_title),
                onClick = { onConfirm(present.toList(), plateConfirmed, notes) },
                enabled = !isUpdating,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MyVehiclesContent(
    state: SavedVehicleUiState,
    onOpenEditor: (String?) -> Unit,
    onDelete: (UserVehicle) -> Unit,
    onSetDefault: (UserVehicle) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        modifier = Modifier.fillMaxSize(),
    ) {
        state.operationError?.let {
            item("error") { VehicleMessageCard(it, Icons.Filled.Warning, MaterialTheme.czColors.error) }
        }
        state.operationMessage?.let {
            item("message") { VehicleMessageCard(it, Icons.Filled.CheckCircle, MaterialTheme.czColors.success) }
        }
        if (state.vehicles.isEmpty()) {
            item("empty") {
                CzEmptyState(
                    title = stringResource(R.string.vehicle_empty_saved_title),
                    message = stringResource(R.string.vehicle_empty_saved_message),
                )
            }
        } else {
            items(state.vehicles, key = { it.id }) { vehicle ->
                VehicleCard {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBubble(Icons.Filled.DirectionsCar, MaterialTheme.czColors.accent)
                        Column(Modifier.weight(1f)) {
                            Text(vehicle.displayTitle, color = MaterialTheme.czColors.textPrimary, fontWeight = FontWeight.SemiBold)
                            Text(vehicle.plateNumber, color = MaterialTheme.czColors.textSecondary)
                            if (vehicle.detailSubtitle.isNotBlank()) {
                                Text(vehicle.detailSubtitle, color = MaterialTheme.czColors.textSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (vehicle.isDefault) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = stringResource(R.string.vehicle_default_cd),
                                tint = MaterialTheme.czColors.warning,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                        TextButton(onClick = { onOpenEditor(vehicle.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Text(stringResource(R.string.common_edit))
                        }
                        TextButton(onClick = { onSetDefault(vehicle) }) {
                            Icon(Icons.Filled.Star, contentDescription = null)
                            Text(stringResource(R.string.vehicle_default_action))
                        }
                        TextButton(onClick = { onDelete(vehicle) }) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Text(stringResource(R.string.common_delete))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserVehicleEditorContent(
    ownerUserId: String,
    existing: UserVehicle?,
    isSaving: Boolean,
    error: String?,
    onSave: (UserVehicle) -> Unit,
    modifier: Modifier = Modifier,
) {
    var nickname by remember(existing?.id) { mutableStateOf(existing?.nickname.orEmpty()) }
    var plate by remember(existing?.id) { mutableStateOf(existing?.plateNumber.orEmpty()) }
    var brand by remember(existing?.id) { mutableStateOf(existing?.brand.orEmpty()) }
    var model by remember(existing?.id) { mutableStateOf(existing?.model.orEmpty()) }
    var color by remember(existing?.id) { mutableStateOf(existing?.color.orEmpty()) }
    var seats by remember(existing?.id) { mutableIntStateOf(existing?.defaultTotalSeats ?: 5) }
    var isDefault by remember(existing?.id) { mutableStateOf(existing?.isDefault ?: false) }

    Column(
        modifier = modifier.padding(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        error?.let { VehicleMessageCard(it, Icons.Filled.Warning, MaterialTheme.czColors.error) }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            OutlinedTextField(nickname, { nickname = it }, label = { Text(stringResource(R.string.vehicle_nickname)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(plate, { plate = it.uppercase(Locale.ROOT) }, label = { Text(stringResource(R.string.registration_plate_number)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(brand, { brand = it }, label = { Text(stringResource(R.string.registration_brand)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(model, { model = it }, label = { Text(stringResource(R.string.registration_model)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(color, { color = it }, label = { Text(stringResource(R.string.registration_color)) }, modifier = Modifier.fillMaxWidth())
            SeatStepper(stringResource(R.string.vehicle_default_seats), seats, 1..9) { seats = it }
            ToggleRow(
                title = stringResource(R.string.vehicle_default_vehicle),
                subtitle = stringResource(R.string.vehicle_default_vehicle_subtitle),
                checked = isDefault,
                onCheckedChange = { isDefault = it },
            )
        }
        CzButton(
            text = stringResource(if (isSaving) R.string.vehicle_saving_action else R.string.vehicle_save_action),
            onClick = {
                onSave(
                    UserVehicle(
                        id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                        ownerUserId = ownerUserId,
                        nickname = nickname.clean(),
                        plateNumber = plate,
                        brand = brand.clean(),
                        model = model.clean(),
                        color = color.clean(),
                        defaultTotalSeats = seats,
                        isDefault = isDefault,
                        createdAt = existing?.createdAt ?: java.util.Date(),
                    ),
                )
            },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back_cd))
                    }
                },
                actions = { actions() },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.czColors.background,
                ),
                windowInsets = WindowInsets(0),
            )
        },
        containerColor = MaterialTheme.czColors.background,
        content = content,
    )
}

@Composable
private fun VehicleStateContent(
    state: VehicleLoadState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    restrictedMessage: String? = null,
    content: @Composable () -> Unit,
) {
    val resolvedRestrictedMessage = restrictedMessage ?: stringResource(R.string.vehicle_restricted_message)
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            VehicleLoadState.Loading -> CzLoadingView(
                message = stringResource(R.string.vehicle_loading),
                modifier = Modifier.fillMaxSize(),
            )
            VehicleLoadState.Ready -> content()
            VehicleLoadState.Restricted -> CzEmptyState(
                title = stringResource(R.string.vehicle_restricted_title),
                message = resolvedRestrictedMessage,
                icon = {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.czColors.warning,
                        modifier = Modifier.size(42.dp),
                    )
                },
                modifier = Modifier.fillMaxSize(),
            )
            is VehicleLoadState.Error -> CzErrorState(
                title = stringResource(R.string.vehicle_unavailable_title),
                message = state.message,
                onRetry = onRetry,
                retryLabel = stringResource(R.string.common_retry),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun VehicleCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.lg),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            content = content,
        )
    }
}

@Composable
private fun StatusHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
) {
    androidx.compose.material3.Surface(
        color = color.copy(alpha = 0.10f),
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBubble(icon, color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = MaterialTheme.czColors.textPrimary, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = MaterialTheme.czColors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun IconBubble(icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = color.copy(alpha = 0.14f),
            shape = CircleShape,
        ) {}
        Icon(icon, contentDescription = null, tint = color)
    }
}

@Composable
private fun VehicleMessageCard(message: String, icon: ImageVector, color: Color) {
    androidx.compose.material3.Surface(
        color = color.copy(alpha = 0.10f),
        shape = RoundedCornerShape(CzRadius.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = color)
            Text(message, color = MaterialTheme.czColors.textPrimary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ChooserCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    VehicleCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBubble(icon, MaterialTheme.czColors.accent)
            Column(Modifier.weight(1f)) {
                Text(title, color = MaterialTheme.czColors.textPrimary, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, color = MaterialTheme.czColors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, icon: ImageVector) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.czColors.accent, modifier = Modifier.size(18.dp))
        Text(title, color = MaterialTheme.czColors.textPrimary, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun PassengerActionRow(
    name: String,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val participantFallback = stringResource(R.string.vehicle_participant_fallback)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name.ifBlank { participantFallback }, color = MaterialTheme.czColors.textPrimary, modifier = Modifier.weight(1f))
        TextButton(onClick = onPrimary) { Text(primary) }
        if (secondary != null && onSecondary != null) {
            TextButton(onClick = onSecondary) { Text(secondary) }
        }
    }
}

@Composable
private fun PassengerPickerDialog(
    candidates: List<CampingAttendee>,
    title: String,
    onDismiss: () -> Unit,
    onPick: (CampingAttendee) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                if (candidates.isEmpty()) {
                    Text(stringResource(R.string.vehicle_no_approved_participants))
                } else {
                    candidates.forEach { attendee ->
                        TextButton(onClick = { onPick(attendee) }, modifier = Modifier.fillMaxWidth()) {
                            Text(attendee.displayName, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        },
    )
}

@Composable
private fun JoinCodeDialog(initialCode: String = "", onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var code by remember(initialCode) { mutableStateOf(initialCode) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vehicle_invitation_code)) },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase(Locale.ROOT) },
                label = { Text(stringResource(R.string.vehicle_code)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onJoin(code) }) { Text(stringResource(R.string.vehicle_request)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransportationDecisionSheet(
    kind: String,
    vehicle: CampingVehicle?,
    registrationId: String,
    isUpdating: Boolean,
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val isInvitation = kind == "invitation"
        val participantName = vehicle?.pendingPassengers
            ?.firstOrNull { it.id == registrationId }
            ?.name
            .orEmpty()
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CzSpacing.xl, vertical = CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Text(
                stringResource(if (isInvitation) R.string.vehicle_ride_invitation_title else R.string.vehicle_transportation_request_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            val participantFallback = stringResource(R.string.vehicle_participant_fallback)
            Text(
                when {
                    vehicle == null -> stringResource(R.string.vehicle_loading_details)
                    isInvitation -> stringResource(R.string.vehicle_invited_to_ride, vehicle.driverName, vehicle.plateNumber)
                    else -> stringResource(R.string.vehicle_requested_seat, participantName.ifBlank { participantFallback }, vehicle.plateNumber)
                },
                color = MaterialTheme.czColors.textSecondary,
            )
            CzButton(
                text = stringResource(if (isInvitation) R.string.vehicle_accept else R.string.vehicle_approve),
                onClick = onAccept,
                enabled = vehicle != null && !isUpdating,
                modifier = Modifier.fillMaxWidth(),
            )
            CzButton(
                text = stringResource(R.string.vehicle_decline),
                onClick = onDecline,
                enabled = vehicle != null && !isUpdating,
                variant = CzButtonVariant.Destructive,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.vehicle_view_transportation))
            }
            Spacer(Modifier.height(CzSpacing.lg))
        }
    }
}

@Composable
private fun SeatStepper(
    title: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    VehicleCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = MaterialTheme.czColors.textPrimary, fontWeight = FontWeight.SemiBold)
                Text("$value", color = MaterialTheme.czColors.textSecondary)
            }
            TextButton(onClick = { onValueChange((value - 1).coerceIn(range)) }, enabled = value > range.first) {
                Text("-")
            }
            TextButton(onClick = { onValueChange((value + 1).coerceIn(range)) }, enabled = value < range.last) {
                Text("+")
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    VehicleCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = MaterialTheme.czColors.textPrimary, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.czColors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun ReviewLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Text(label, color = MaterialTheme.czColors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.czColors.textPrimary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun VehicleRow(
    vehicle: CampingVehicle,
    showFullPlate: Boolean,
    onClick: () -> Unit,
) {
    VehicleCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CzAvatar(
                imageUrl = vehicle.driverPhotoUrl,
                contentDescription = vehicle.driverName,
                initials = vehicle.driverName,
                size = CzAvatarSize.Medium,
            )
            Column(Modifier.weight(1f)) {
                Text(vehicle.driverName, color = MaterialTheme.czColors.textPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    if (showFullPlate) vehicle.plateNumber else vehicle.maskedPlate,
                    color = MaterialTheme.czColors.textSecondary,
                )
                Text(stringResource(R.string.vehicle_seats_fraction, vehicle.accountedOccupiedSeats, vehicle.totalSeats), color = MaterialTheme.czColors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            StatusPill(vehicle)
        }
    }
}

@Composable
private fun StatusPill(vehicle: CampingVehicle) {
    val (label, color) = when {
        vehicle.status == VehicleStatus.Cancelled -> stringResource(R.string.vehicle_status_cancelled) to MaterialTheme.czColors.error
        vehicle.hasArrived -> stringResource(R.string.vehicle_status_arrived) to MaterialTheme.czColors.success
        vehicle.status == VehicleStatus.Confirmed -> stringResource(R.string.vehicle_status_confirmed) to MaterialTheme.czColors.accent
        else -> stringResource(R.string.vehicle_status_pending) to MaterialTheme.czColors.warning
    }
    androidx.compose.material3.Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(CzRadius.full),
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = CzSpacing.sm, vertical = CzSpacing.xs),
        )
    }
}

@Composable
private fun DashboardStatsGrid(stats: VehicleDashboardStats) {
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            StatCard(stringResource(R.string.vehicle_stats_cars), "${stats.arrivedVehicles}/${stats.totalVehicles}", Modifier.weight(1f))
            StatCard(stringResource(R.string.vehicle_stats_people), "${stats.peopleArrived}/${stats.peopleExpected}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            StatCard(stringResource(R.string.vehicle_stats_with_seats), "${stats.vehiclesWithSeats}", Modifier.weight(1f))
            StatCard(stringResource(R.string.vehicle_stats_need_ride), "${stats.peopleNeedingTransport}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    VehicleCard(modifier = modifier) {
        Text(value, color = MaterialTheme.czColors.textPrimary, style = MaterialTheme.typography.headlineSmall)
        Text(label, color = MaterialTheme.czColors.textSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ScanResultCard(
    result: VehicleScanResult,
    onDismiss: () -> Unit,
    onOpenArrival: (String) -> Unit,
) {
    val vehicle = when (result) {
        is VehicleScanResult.Resolved -> result.vehicle
        is VehicleScanResult.AlreadyArrived -> result.vehicle
        is VehicleScanResult.Cancelled -> result.vehicle
        else -> null
    }
    val (title, message, color) = when (result) {
        is VehicleScanResult.Resolved -> Triple(stringResource(R.string.vehicle_scan_found_title), stringResource(R.string.vehicle_scan_found_message, result.vehicle.driverName), MaterialTheme.czColors.success)
        is VehicleScanResult.AlreadyArrived -> Triple(stringResource(R.string.vehicle_scan_already_arrived_title), stringResource(R.string.vehicle_scan_already_arrived_message, result.vehicle.driverName), MaterialTheme.czColors.warning)
        is VehicleScanResult.Cancelled -> Triple(stringResource(R.string.vehicle_scan_cancelled_title), stringResource(R.string.vehicle_scan_cancelled_message, result.vehicle.driverName), MaterialTheme.czColors.warning)
        VehicleScanResult.WrongCamping -> Triple(stringResource(R.string.vehicle_scan_wrong_camp_title), stringResource(R.string.vehicle_scan_wrong_camp_message), MaterialTheme.czColors.error)
        VehicleScanResult.UnknownVehicle -> Triple(stringResource(R.string.vehicle_scan_unknown_title), stringResource(R.string.vehicle_scan_unknown_message), MaterialTheme.czColors.error)
        VehicleScanResult.Malformed -> Triple(stringResource(R.string.vehicle_scan_invalid_title), stringResource(R.string.vehicle_scan_invalid_message), MaterialTheme.czColors.error)
    }
    VehicleCard {
        Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.QrCode, contentDescription = null, tint = color)
            Column(Modifier.weight(1f)) {
                Text(title, color = MaterialTheme.czColors.textPrimary, fontWeight = FontWeight.SemiBold)
                Text(message, color = MaterialTheme.czColors.textSecondary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            if (result is VehicleScanResult.Resolved && vehicle != null) {
                CzButton(text = stringResource(R.string.vehicle_confirm_arrival_title), onClick = { onOpenArrival(vehicle.id) }, modifier = Modifier.weight(1f))
            }
            CzButton(text = stringResource(R.string.common_dismiss), onClick = onDismiss, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CameraCard(isScanning: Boolean, onQrScanned: (String) -> Unit) {
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
    VehicleCard {
        if (hasPermission) {
            Box(
                modifier = Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(CzRadius.lg)),
            ) {
                QrCameraPreview(onQrScanned = onQrScanned, modifier = Modifier.fillMaxSize())
                if (isScanning) {
                    androidx.compose.material3.Surface(
                        color = Color.Black.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.vehicle_resolving), color = Color.White)
                        }
                    }
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = MaterialTheme.czColors.textSecondary, modifier = Modifier.size(42.dp))
                Text(stringResource(R.string.vehicle_camera_permission_needed), color = MaterialTheme.czColors.textSecondary)
                CzButton(text = stringResource(R.string.vehicle_allow_camera), onClick = { launcher.launch(Manifest.permission.CAMERA) })
            }
        }
    }
}

private data class ExpectedPerson(
    val id: String,
    val name: String,
    val isDriver: Boolean,
    val photoUrl: String?,
)

private fun String?.clean(): String? = this?.trim()?.takeUnless { it.isBlank() }

@Preview(showBackground = true)
@Composable
private fun VehicleQrPreview() {
    CampzoneTheme {
        VehicleQrContent(
            vehicle = CampingVehicle(
                campingId = "camp-1",
                ownerUserId = "user-1",
                driverUserId = "user-1",
                driverRegistrationId = "user-1",
                driverName = "Leon Fernandes",
                plateNumber = "GE 12345",
                totalSeats = 5,
                occupiedSeats = 2,
                hasAvailableSeats = true,
                passengerRegistrationIds = listOf("p1"),
                passengerNames = listOf("Ava Mendes"),
                qrToken = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                invitationCode = "ABCD23",
            ),
        )
    }
}
