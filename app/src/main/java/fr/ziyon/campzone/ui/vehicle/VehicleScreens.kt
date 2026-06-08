package fr.ziyon.campzone.ui.vehicle

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
    modifier: Modifier = Modifier,
    viewModel: VehicleViewModel = hiltViewModel(),
) {
    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.load(campingId, authenticatedUser)
        viewModel.loadSavedVehicles(authenticatedUser.uid)
    }
    val state by viewModel.uiState.collectAsState()
    VehicleScaffold(
        title = "Transport",
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
                    title = "Approval needed",
                    message = "Register and get approved to set up your transport.",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                MyTransportationContent(
                    state = state,
                    attendee = attendee,
                    onOpenVehicleForm = onOpenVehicleForm,
                    onOpenVehicleQr = onOpenVehicleQr,
                    onRequestHelp = { viewModel.requestTransportHelp(campingId, attendee.id) },
                    onClearTransport = { viewModel.clearTransportIntent(campingId, attendee.id, "Request cancelled.") },
                    onJoinVehicle = { vehicle -> viewModel.requestJoin(vehicle, attendee) },
                    onJoinByCode = { code -> viewModel.joinByInvitationCode(campingId, code, attendee) },
                    onWithdraw = { vehicle -> viewModel.withdrawJoinRequest(vehicle, attendee.id) },
                    onCancelVehicle = { vehicle -> viewModel.cancelVehicle(vehicle, attendee.id) },
                    onApprove = viewModel::approvePassenger,
                    onDeny = viewModel::denyPassenger,
                    onRemovePassenger = viewModel::removePassenger,
                    onAddPassenger = { vehicle, passenger -> viewModel.addPassenger(vehicle, passenger) },
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
        title = if (vehicleId == null) "Register car" else "Edit car",
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
                    title = "Approval needed",
                    message = "You need an approved self registration before adding a car.",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                VehicleFormContent(
                    existing = vehicleId?.let(state::vehicle),
                    savedVehicles = savedState.vehicles,
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
        title = "Car QR code",
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(onClick = { onEdit(vehicleId) }) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit")
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
                    title = "Vehicle unavailable",
                    message = "This car could not be found.",
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
        title = "Vehicles",
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(onClick = onOpenScanner) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan a car QR")
            }
        },
    ) { padding ->
        VehicleStateContent(
            state = state.loadState,
            restrictedMessage = "Only transport managers can manage vehicle arrivals.",
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
        title = "Scan car QR",
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        VehicleStateContent(
            state = state.loadState,
            restrictedMessage = "Only transport managers can scan vehicle arrivals.",
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
        title = "Confirm arrival",
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        VehicleStateContent(
            state = state.loadState,
            restrictedMessage = "Only transport managers can confirm vehicle arrivals.",
            onRetry = { viewModel.retry(campingId, authenticatedUser, requireManager = true) },
            modifier = Modifier.padding(padding),
        ) {
            val vehicle = state.vehicle(vehicleId)
            if (vehicle == null) {
                CzEmptyState(
                    title = "Vehicle unavailable",
                    message = "This car could not be found.",
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
        driven != null -> "Car QR ready"
        ridden != null -> "Your ride"
        pending != null -> "Ride request pending"
        attendee.needsTransportHelp -> "Transport help requested"
        else -> "Set up transport"
    }
    val subtitle = when {
        driven != null -> "${driven.plateNumber} - ${driven.availableSeats} free seat(s)"
        ridden != null -> "Riding with ${ridden.driverName}"
        pending != null -> "Waiting for ${pending.driverName}"
        attendee.needsTransportHelp -> "Camp leadership can see your request."
        else -> "Driving, passenger, or need help."
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
    modifier: Modifier = Modifier,
) {
    val driven = state.vehicleDriven(attendee.id)
    val ridden = state.vehicleRidden(attendee.id)
    val pending = state.pendingVehicle(attendee.id)
    var showFindRide by remember { mutableStateOf(false) }
    var showJoinCode by remember { mutableStateOf(false) }

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
                    )
                }
            }
            ridden != null -> {
                item("riding") {
                    PassengerRideSection(vehicle = ridden)
                }
            }
            pending != null -> {
                item("pending") {
                    StatusHeader(
                        icon = Icons.Filled.Warning,
                        title = "Waiting for approval",
                        subtitle = "You asked to ride with ${pending.driverName}.",
                        color = MaterialTheme.czColors.warning,
                    )
                    Spacer(Modifier.height(CzSpacing.md))
                    CzButton(
                        text = "Cancel request",
                        onClick = { onWithdraw(pending) },
                        variant = CzButtonVariant.Destructive,
                    )
                }
            }
            attendee.needsTransportHelp -> {
                item("help") {
                    StatusHeader(
                        icon = Icons.Filled.Help,
                        title = "Transport help requested",
                        subtitle = "A camp organizer will be in touch.",
                        color = MaterialTheme.czColors.warning,
                    )
                    Spacer(Modifier.height(CzSpacing.md))
                    CzButton(text = "Find a ride instead", onClick = { showFindRide = !showFindRide })
                    Spacer(Modifier.height(CzSpacing.sm))
                    CzButton(
                        text = "Cancel request",
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
                            "How are you getting to camp?",
                            color = MaterialTheme.czColors.textPrimary,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            "Pick one. You can change it later.",
                            color = MaterialTheme.czColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        ChooserCard(
                            icon = Icons.Filled.DirectionsCar,
                            title = "I'm driving",
                            subtitle = "Register your car and get a QR code.",
                            onClick = { onOpenVehicleForm(null) },
                        )
                        ChooserCard(
                            icon = Icons.Filled.Groups,
                            title = "I'm a passenger",
                            subtitle = "Join a car with a free seat.",
                            onClick = { showFindRide = !showFindRide },
                        )
                        ChooserCard(
                            icon = Icons.Filled.Help,
                            title = "I need transport",
                            subtitle = "Ask the organizers to arrange a ride.",
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
            onDismiss = { showJoinCode = false },
            onJoin = {
                showJoinCode = false
                onJoinByCode(it)
            },
        )
    }
}

@Composable
private fun DriverVehicleSection(
    state: VehicleUiState,
    vehicle: CampingVehicle,
    attendee: CampingAttendee,
    onOpenQr: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onApprove: (CampingVehicle, String) -> Unit,
    onDeny: (CampingVehicle, String) -> Unit,
    onRemovePassenger: (CampingVehicle, String) -> Unit,
    onAddPassenger: (CampingVehicle, CampingAttendee) -> Unit,
) {
    var showPassengerPicker by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
        StatusHeader(
            icon = Icons.Filled.DirectionsCar,
            title = "You're driving",
            subtitle = "${vehicle.plateNumber} - ${vehicle.availableSeats} free seat(s)",
            color = MaterialTheme.czColors.accent,
        )
        CzButton(text = "Show car QR code", onClick = onOpenQr)
        if (vehicle.pendingPassengers.isNotEmpty()) {
            VehicleCard {
                SectionTitle("Requests", Icons.Filled.Warning)
                vehicle.pendingPassengers.forEach { passenger ->
                    PassengerActionRow(
                        name = passenger.name,
                        primary = "Approve",
                        onPrimary = { onApprove(vehicle, passenger.id) },
                        secondary = "Decline",
                        onSecondary = { onDeny(vehicle, passenger.id) },
                    )
                }
            }
        }
        VehicleCard {
            SectionTitle("Passengers", Icons.Filled.Groups)
            if (vehicle.passengers.isEmpty()) {
                Text("No passengers yet.", color = MaterialTheme.czColors.textSecondary)
            } else {
                vehicle.passengers.forEach { passenger ->
                    PassengerActionRow(
                        name = passenger.name.ifBlank { passenger.id },
                        primary = "Remove",
                        onPrimary = { onRemovePassenger(vehicle, passenger.id) },
                    )
                }
            }
            if (vehicle.availableSeats > 0) {
                TextButton(onClick = { showPassengerPicker = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Add passenger")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            CzButton(text = "Edit", onClick = onEdit, modifier = Modifier.weight(1f))
            CzButton(
                text = "Cancel car",
                onClick = onCancel,
                variant = CzButtonVariant.Destructive,
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (showPassengerPicker) {
        val candidates = state.camping?.attendees.orEmpty()
            .filter { it.registrationStatus == RegistrationApprovalStatus.Approved }
            .filterNot { it.id == attendee.id }
            .filterNot { it.id in vehicle.passengerRegistrationIds }
        PassengerPickerDialog(
            candidates = candidates,
            onDismiss = { showPassengerPicker = false },
            onPick = {
                showPassengerPicker = false
                onAddPassenger(vehicle, it)
            },
        )
    }
}

@Composable
private fun PassengerRideSection(vehicle: CampingVehicle) {
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
        StatusHeader(
            icon = Icons.Filled.Groups,
            title = "You're a passenger",
            subtitle = "Riding with ${vehicle.driverName}.",
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
                    Text("Driver", color = MaterialTheme.czColors.textSecondary, style = MaterialTheme.typography.bodySmall)
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
        Text(
            "To change or leave this car, ask the driver or a camp leader.",
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
        SectionTitle("Cars with free seats", Icons.Filled.CarRental)
        val rides = state.availableSeatVehicles.filter { it.driverRegistrationId != attendee.id }
        if (rides.isEmpty()) {
            Text("No cars with free seats yet. Try an invitation code.", color = MaterialTheme.czColors.textSecondary)
        } else {
            rides.forEach { vehicle ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(vehicle.driverName, color = MaterialTheme.czColors.textPrimary, fontWeight = FontWeight.SemiBold)
                        Text("${vehicle.availableSeats} free seat(s)", color = MaterialTheme.czColors.textSecondary)
                    }
                    TextButton(onClick = { onJoinVehicle(vehicle) }) { Text("Request") }
                }
            }
        }
        TextButton(onClick = onJoinByCode) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Text("Have an invitation code?")
        }
    }
}

@Composable
private fun VehicleFormContent(
    existing: CampingVehicle?,
    savedVehicles: List<UserVehicle>,
    saving: Boolean,
    error: String?,
    onSubmitNew: (VehicleFormInput) -> Unit,
    onSubmitExisting: (CampingVehicle) -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableIntStateOf(0) }
    var selectedSavedId by remember(existing?.userVehicleId) { mutableStateOf(existing?.userVehicleId) }
    var plate by remember(existing?.id) { mutableStateOf(existing?.plateNumber.orEmpty()) }
    var brand by remember(existing?.id) { mutableStateOf(existing?.brand.orEmpty()) }
    var model by remember(existing?.id) { mutableStateOf(existing?.model.orEmpty()) }
    var color by remember(existing?.id) { mutableStateOf(existing?.color.orEmpty()) }
    var totalSeats by remember(existing?.id) { mutableIntStateOf(existing?.totalSeats ?: 5) }
    var peopleInCar by remember(existing?.id) { mutableIntStateOf(existing?.occupiedSeats ?: 1) }
    var hasAvailableSeats by remember(existing?.id) { mutableStateOf(existing?.hasAvailableSeats ?: true) }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }

    fun applySaved(vehicle: UserVehicle) {
        selectedSavedId = vehicle.id
        plate = vehicle.plateNumber
        brand = vehicle.brand.orEmpty()
        model = vehicle.model.orEmpty()
        color = vehicle.color.orEmpty()
        totalSeats = vehicle.defaultTotalSeats
        peopleInCar = 1.coerceAtMost(totalSeats)
    }

    Column(
        modifier = modifier.padding(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        StepHeader(step = step)
        error?.let { VehicleMessageCard(it, Icons.Filled.Warning, MaterialTheme.czColors.error) }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            when (step) {
                0 -> {
                    if (existing == null && savedVehicles.isNotEmpty()) {
                        Text("Saved vehicles", color = MaterialTheme.czColors.textSecondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                            savedVehicles.forEach { saved ->
                                FilterChip(
                                    selected = selectedSavedId == saved.id,
                                    onClick = { applySaved(saved) },
                                    label = { Text(saved.displayTitle) },
                                )
                            }
                        }
                    }
                    OutlinedTextField(plate, { plate = it.uppercase(Locale.ROOT) }, label = { Text("Plate number") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(brand, { brand = it }, label = { Text("Brand") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(model, { model = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(color, { color = it }, label = { Text("Color") }, modifier = Modifier.fillMaxWidth())
                }
                1 -> {
                    SeatStepper("Total seats", totalSeats, 1..9) {
                        totalSeats = it
                        if (peopleInCar > totalSeats) peopleInCar = totalSeats
                    }
                    SeatStepper("People in the car", peopleInCar, 1..totalSeats) { peopleInCar = it }
                    ToggleRow(
                        title = "Seats for others",
                        subtitle = "Let approved participants request a seat.",
                        checked = hasAvailableSeats,
                        onCheckedChange = { hasAvailableSeats = it },
                    )
                }
                2 -> {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> {
                    ReviewLine("Plate", plate)
                    ReviewLine("Vehicle", listOf(brand, model, color).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Not specified" })
                    ReviewLine("Seats", "$peopleInCar of $totalSeats occupied")
                    ReviewLine("Open seats", if (hasAvailableSeats) "Yes" else "No")
                    ReviewLine("Notes", notes.ifBlank { "None" })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            if (step > 0) {
                CzButton(text = "Back", onClick = { step -= 1 }, modifier = Modifier.weight(1f))
            }
            CzButton(
                text = when {
                    step < 3 -> "Continue"
                    existing == null -> if (saving) "Creating..." else "Create my car"
                    else -> if (saving) "Saving..." else "Save changes"
                },
                onClick = {
                    if (step < 3) {
                        step += 1
                    } else if (existing == null) {
                        onSubmitNew(
                            VehicleFormInput(
                                plateNumber = plate,
                                brand = brand,
                                model = model,
                                color = color,
                                totalSeats = totalSeats,
                                peopleInCar = peopleInCar,
                                hasAvailableSeats = hasAvailableSeats,
                                notes = notes,
                                userVehicleId = selectedSavedId,
                            ),
                        )
                    } else {
                        onSubmitExisting(
                            existing.copy(
                                plateNumber = plate,
                                brand = brand.clean(),
                                model = model.clean(),
                                color = color.clean(),
                                totalSeats = totalSeats,
                                occupiedSeats = peopleInCar,
                                hasAvailableSeats = hasAvailableSeats,
                                notes = notes.clean(),
                            ),
                        )
                    }
                },
                enabled = !saving,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun VehicleQrContent(vehicle: CampingVehicle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val qrValue = remember(vehicle.qrToken) { VehicleCheckInPayload(vehicle.qrToken).encoded() }
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
                    .semantics { contentDescription = "Vehicle QR code" },
            )
        }
        item("code") {
            VehicleCard {
                ReviewLine("Invitation code", vehicle.invitationCode ?: "Unavailable")
                ReviewLine("Seats", "${vehicle.occupiedSeats}/${vehicle.totalSeats}")
                if (vehicle.passengers.isNotEmpty()) {
                    Spacer(Modifier.height(CzSpacing.sm))
                    SectionTitle("Passengers", Icons.Filled.Groups)
                    vehicle.passengers.forEach {
                        Text(it.name.ifBlank { it.id }, color = MaterialTheme.czColors.textSecondary)
                    }
                }
            }
        }
        item("share") {
            CzButton(
                text = "Share code",
                onClick = {
                    val shareText = buildString {
                        append("Campzone vehicle code: ")
                        append(vehicle.invitationCode.orEmpty())
                        append("\n")
                        append(VehicleCheckInPayload(vehicle.qrToken).webUrl())
                    }
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            },
                            "Share vehicle code",
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
                text = "Scan a car QR",
                onClick = onOpenScanner,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
            )
        }
        if (state.peopleNeedingTransport.isNotEmpty()) {
            item("needs") {
                VehicleCard {
                    SectionTitle("Need transport", Icons.Filled.Help)
                    state.peopleNeedingTransport.forEach {
                        Text(it.displayName, color = MaterialTheme.czColors.textSecondary)
                    }
                }
            }
        }
        if (state.vehicles.isEmpty()) {
            item("empty") {
                CzEmptyState(
                    title = "No cars yet",
                    message = "Cars added by drivers will appear here.",
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
                label = { Text("Search by plate or driver") },
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
                VehicleMessageCard("This vehicle is already marked arrived.", Icons.Filled.Info, MaterialTheme.czColors.warning)
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
                    Text("${vehicle.expectedRegisteredCount} expected", color = MaterialTheme.czColors.accent)
                }
            }
        }
        item("plate") {
            ToggleRow(
                title = "Plate confirmed",
                subtitle = vehicle.plateNumber,
                checked = plateConfirmed,
                onCheckedChange = { plateConfirmed = it },
            )
        }
        item("people") {
            VehicleCard {
                SectionTitle("People in this car", Icons.Filled.Groups)
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
                            Text(if (person.isDriver) "Driver" else "Passenger", color = MaterialTheme.czColors.textSecondary)
                        }
                    }
                }
                TextButton(onClick = {
                    present.clear()
                    present.addAll(expected.map { it.id })
                }) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Text("Everyone arrived")
                }
            }
        }
        item("notes") {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item("confirm") {
            CzButton(
                text = if (isUpdating) "Confirming..." else "Confirm arrival",
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name.ifBlank { "Participant" }, color = MaterialTheme.czColors.textPrimary, modifier = Modifier.weight(1f))
        TextButton(onClick = onPrimary) { Text(primary) }
        if (secondary != null && onSecondary != null) {
            TextButton(onClick = onSecondary) { Text(secondary) }
        }
    }
}

@Composable
private fun PassengerPickerDialog(
    candidates: List<CampingAttendee>,
    onDismiss: () -> Unit,
    onPick: (CampingAttendee) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add passenger") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                if (candidates.isEmpty()) {
                    Text("No approved participants are available.")
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
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun JoinCodeDialog(onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invitation code") },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase(Locale.ROOT) },
                label = { Text("Code") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onJoin(code) }) { Text("Request") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
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
private fun StepHeader(step: Int) {
    val labels = listOf("Car", "Seats", "Notes", "Review")
    Row(
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            AssistChip(
                onClick = {},
                label = { Text(label) },
                leadingIcon = if (index <= step) {
                    { Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else {
                    null
                },
            )
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
                Text("${vehicle.occupiedSeats}/${vehicle.totalSeats} seats", color = MaterialTheme.czColors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            StatusPill(vehicle)
        }
    }
}

@Composable
private fun StatusPill(vehicle: CampingVehicle) {
    val (label, color) = when {
        vehicle.status == VehicleStatus.Cancelled -> "Cancelled" to MaterialTheme.czColors.error
        vehicle.hasArrived -> "Arrived" to MaterialTheme.czColors.success
        vehicle.status == VehicleStatus.Confirmed -> "Confirmed" to MaterialTheme.czColors.accent
        else -> "Pending" to MaterialTheme.czColors.warning
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
            StatCard("Cars", "${stats.arrivedVehicles}/${stats.totalVehicles}", Modifier.weight(1f))
            StatCard("People", "${stats.peopleArrived}/${stats.peopleExpected}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            StatCard("With seats", "${stats.vehiclesWithSeats}", Modifier.weight(1f))
            StatCard("Need ride", "${stats.peopleNeedingTransport}", Modifier.weight(1f))
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
        is VehicleScanResult.Resolved -> Triple("Vehicle found", "${result.vehicle.driverName}'s car is ready to confirm.", MaterialTheme.czColors.success)
        is VehicleScanResult.AlreadyArrived -> Triple("Already arrived", "${result.vehicle.driverName}'s car was already checked in.", MaterialTheme.czColors.warning)
        is VehicleScanResult.Cancelled -> Triple("Vehicle cancelled", "${result.vehicle.driverName}'s car was cancelled.", MaterialTheme.czColors.warning)
        VehicleScanResult.WrongCamping -> Triple("Wrong camp", "This vehicle code belongs to another camp.", MaterialTheme.czColors.error)
        VehicleScanResult.UnknownVehicle -> Triple("Unknown vehicle", "No vehicle matches this code in this camp.", MaterialTheme.czColors.error)
        VehicleScanResult.Malformed -> Triple("Invalid code", "The scanned code is not a Campzone vehicle code.", MaterialTheme.czColors.error)
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
                CzButton(text = "Confirm arrival", onClick = { onOpenArrival(vehicle.id) }, modifier = Modifier.weight(1f))
            }
            CzButton(text = "Dismiss", onClick = onDismiss, modifier = Modifier.weight(1f))
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
                            Text("Resolving vehicle...", color = Color.White)
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
                Text("Camera permission is needed to scan QR codes.", color = MaterialTheme.czColors.textSecondary)
                CzButton(text = "Allow camera", onClick = { launcher.launch(Manifest.permission.CAMERA) })
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
