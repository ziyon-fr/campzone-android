package fr.ziyon.campzone.ui.camping.register

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BusAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTextField
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.CampingTransportationOption
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.RegistrationParticipant
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import fr.ziyon.campzone.data.model.TransportationMode
import fr.ziyon.campzone.data.model.UserVehicle
import fr.ziyon.campzone.ui.camping.campingDateRange
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

@Composable
fun CampingRegistrationRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onAddParticipant: () -> Unit,
    onOpenPayment: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CampingRegistrationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(campingId, authenticatedUser) {
        viewModel.load(campingId, authenticatedUser)
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    CampingRegistrationScreen(
        state = state,
        authenticatedUser = authenticatedUser,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onToggleParticipant = viewModel::toggleParticipant,
        onSelectTransportationOption = viewModel::selectTransportationOption,
        onGoBackStep = viewModel::goBack,
        onGoNextStep = viewModel::goNext,
        onToggleInlineVehicle = viewModel::toggleInlineVehicle,
        onApplySavedVehicle = viewModel::applySavedVehicle,
        onUpdateVehiclePlate = viewModel::updateInlineVehiclePlate,
        onUpdateVehicleBrand = viewModel::updateInlineVehicleBrand,
        onUpdateVehicleModel = viewModel::updateInlineVehicleModel,
        onUpdateVehicleColor = viewModel::updateInlineVehicleColor,
        onUpdateVehicleTotalSeats = viewModel::updateInlineVehicleTotalSeats,
        onUpdateVehiclePeopleInCar = viewModel::updateInlineVehiclePeopleInCar,
        onUpdateVehicleHasSeats = viewModel::updateInlineVehicleHasSeats,
        onUpdateVehicleNotes = viewModel::updateInlineVehicleNotes,
        onAddParticipant = onAddParticipant,
        onSubmit = {
            viewModel.submit(authenticatedUser) { requiresPayment ->
                if (requiresPayment) {
                    onOpenPayment(campingId)
                } else {
                    onBack()
                }
            }
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampingRegistrationScreen(
    state: CampingRegistrationUiState,
    authenticatedUser: AuthenticatedUser,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onToggleParticipant: (String) -> Unit,
    onSelectTransportationOption: (String, String?) -> Unit,
    onGoBackStep: () -> Unit,
    onGoNextStep: () -> Unit,
    onToggleInlineVehicle: (Boolean) -> Unit,
    onApplySavedVehicle: (UserVehicle) -> Unit,
    onUpdateVehiclePlate: (String) -> Unit,
    onUpdateVehicleBrand: (String) -> Unit,
    onUpdateVehicleModel: (String) -> Unit,
    onUpdateVehicleColor: (String) -> Unit,
    onUpdateVehicleTotalSeats: (Int) -> Unit,
    onUpdateVehiclePeopleInCar: (Int) -> Unit,
    onUpdateVehicleHasSeats: (Boolean) -> Unit,
    onUpdateVehicleNotes: (String) -> Unit,
    onAddParticipant: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val camping = state.camping

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.czColors.background,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.registration_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.czColors.background,
                ),
                windowInsets = WindowInsets(),
            )
        },
        bottomBar = {
            if (camping != null && !state.isLoading) {
                RegistrationWizardBar(
                    step = state.step,
                    isWaitlist = camping.isAtCapacity,
                    selectedCount = state.selectedParticipants.size,
                    canProceed = state.canProceed,
                    canGoBack = state.canGoBack,
                    isSubmitting = state.isSubmitting,
                    onBack = onGoBackStep,
                    onNext = onGoNextStep,
                    onSubmit = onSubmit,
                )
            }
        },
    ) { innerPadding ->
        when {
            state.isLoading -> CzLoadingView(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                message = stringResource(R.string.camping_loading),
            )

            state.errorMessage != null && camping == null -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CzErrorState(
                    title = stringResource(R.string.camping_error_title),
                    message = state.errorMessage,
                    onRetry = onBack,
                    retryLabel = stringResource(R.string.common_back),
                )
            }

            camping == null -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CzEmptyState(
                    title = stringResource(R.string.registration_camping_not_found_title),
                    message = stringResource(R.string.registration_camping_not_found_message),
                )
            }

            else -> RegistrationContent(
                state = state,
                camping = camping,
                authenticatedUser = authenticatedUser,
                onToggleParticipant = onToggleParticipant,
                onSelectTransportationOption = onSelectTransportationOption,
                onToggleInlineVehicle = onToggleInlineVehicle,
                onApplySavedVehicle = onApplySavedVehicle,
                onUpdateVehiclePlate = onUpdateVehiclePlate,
                onUpdateVehicleBrand = onUpdateVehicleBrand,
                onUpdateVehicleModel = onUpdateVehicleModel,
                onUpdateVehicleColor = onUpdateVehicleColor,
                onUpdateVehicleTotalSeats = onUpdateVehicleTotalSeats,
                onUpdateVehiclePeopleInCar = onUpdateVehiclePeopleInCar,
                onUpdateVehicleHasSeats = onUpdateVehicleHasSeats,
                onUpdateVehicleNotes = onUpdateVehicleNotes,
                onAddParticipant = onAddParticipant,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun RegistrationContent(
    state: CampingRegistrationUiState,
    camping: Camping,
    authenticatedUser: AuthenticatedUser,
    onToggleParticipant: (String) -> Unit,
    onSelectTransportationOption: (String, String?) -> Unit,
    onToggleInlineVehicle: (Boolean) -> Unit,
    onApplySavedVehicle: (UserVehicle) -> Unit,
    onUpdateVehiclePlate: (String) -> Unit,
    onUpdateVehicleBrand: (String) -> Unit,
    onUpdateVehicleModel: (String) -> Unit,
    onUpdateVehicleColor: (String) -> Unit,
    onUpdateVehicleTotalSeats: (Int) -> Unit,
    onUpdateVehiclePeopleInCar: (Int) -> Unit,
    onUpdateVehicleHasSeats: (Boolean) -> Unit,
    onUpdateVehicleNotes: (String) -> Unit,
    onAddParticipant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = CzSpacing.lg,
            top = CzSpacing.sm,
            end = CzSpacing.lg,
            bottom = 112.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        item { RegistrationStepProgress(currentStep = state.step) }

        when (state.step) {
            RegistrationStep.Who -> {
                item { RegistrationHeader(camping) }
                item {
                    SectionTitle(
                        title = stringResource(R.string.registration_participants),
                        icon = Icons.Filled.People,
                    )
                }
                items(state.participants, key = { it.id }) { participant ->
                    ParticipantSelectionCard(
                        participant = participant,
                        existingStatus = state.existingRegistration(participant)?.registrationStatus,
                        isSelected = participant.id in state.selectedParticipantIds,
                        onClick = { onToggleParticipant(participant.id) },
                    )
                }
                item {
                    CzButton(
                        text = stringResource(R.string.registration_add_participant),
                        onClick = onAddParticipant,
                        modifier = Modifier.fillMaxWidth(),
                        variant = CzButtonVariant.Secondary,
                        leadingIcon = {
                            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                    )
                }
                if (state.selectedParticipants.isEmpty()) {
                    item { EmptySelectionHint() }
                }
            }

            RegistrationStep.Transport -> {
                item {
                    StepIntroCard(
                        title = stringResource(R.string.registration_transport_step_title),
                        message = stringResource(
                            if (camping.usesTransportationOptions) {
                                R.string.registration_transport_step_message_options
                            } else {
                                R.string.registration_transport_step_message_no_options
                            },
                        ),
                        icon = Icons.Filled.DirectionsBus,
                    )
                }
                if (state.selectedParticipants.isEmpty()) {
                    item { EmptySelectionHint() }
                } else {
                    if (camping.usesTransportationOptions) {
                        items(state.selectedParticipants, key = { "transport-${it.id}" }) { participant ->
                            TransportationCard(
                                participant = participant,
                                camping = camping,
                                selectedOptionId = state.transportationOptionIds[participant.id],
                                onSelectOption = { onSelectTransportationOption(participant.id, it) },
                            )
                        }
                    } else if (!state.shouldOfferInlineVehicle) {
                        item { NoTransportSetupCard() }
                    }
                    if (state.shouldOfferInlineVehicle) {
                        item {
                            InlineVehicleCaptureCard(
                                user = authenticatedUser,
                                draft = state.inlineVehicle,
                                savedVehicles = state.savedVehicles,
                                onEnabledChange = onToggleInlineVehicle,
                                onSavedVehicleSelected = onApplySavedVehicle,
                                onPlateChange = onUpdateVehiclePlate,
                                onBrandChange = onUpdateVehicleBrand,
                                onModelChange = onUpdateVehicleModel,
                                onColorChange = onUpdateVehicleColor,
                                onTotalSeatsChange = onUpdateVehicleTotalSeats,
                                onPeopleInCarChange = onUpdateVehiclePeopleInCar,
                                onHasSeatsChange = onUpdateVehicleHasSeats,
                                onNotesChange = onUpdateVehicleNotes,
                            )
                        }
                    }
                }
            }

            RegistrationStep.Review -> {
                item {
                    StepIntroCard(
                        title = stringResource(R.string.registration_review_step_title),
                        message = stringResource(R.string.registration_review_step_message),
                        icon = Icons.Filled.Verified,
                    )
                }
                if (state.selectedParticipants.isEmpty()) {
                    item { EmptySelectionHint() }
                } else {
                    item {
                        ReviewCard(
                            participants = state.selectedParticipants,
                            camping = camping,
                            state = state,
                        )
                    }
                    if (state.wantsInlineVehicle) {
                        item { InlineVehicleReviewCard(state.inlineVehicle) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegistrationHeader(camping: Camping) {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Text(
                text = campingDateRange(camping.startDate, camping.endDate),
                color = MaterialTheme.czColors.ember,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = camping.title,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    if (camping.usesTransportationOptions) {
                        R.string.registration_header_copy_with_transport
                    } else {
                        R.string.registration_header_copy
                    },
                ),
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.czColors.ember, modifier = Modifier.size(18.dp))
        Text(
            text = title,
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun RegistrationStepProgress(currentStep: RegistrationStep) {
    val steps = listOf(RegistrationStep.Who, RegistrationStep.Transport, RegistrationStep.Review)
    val currentIndex = steps.indexOf(currentStep).coerceAtLeast(0)
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                steps.forEachIndexed { index, step ->
                    Surface(
                        modifier = Modifier.weight(1f).height(5.dp),
                        color = if (index <= currentIndex) {
                            MaterialTheme.czColors.ember
                        } else {
                            MaterialTheme.czColors.divider
                        },
                        shape = RoundedCornerShape(CzRadius.full),
                        content = {},
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                steps.forEach { step ->
                    Text(
                        text = step.displayName(),
                        color = if (step == currentStep) {
                            MaterialTheme.czColors.textPrimary
                        } else {
                            MaterialTheme.czColors.textSecondary
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (step == currentStep) FontWeight.SemiBold else FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepIntroCard(
    title: String,
    message: String,
    icon: ImageVector,
) {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                color = MaterialTheme.czColors.ember.copy(alpha = 0.12f),
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.czColors.ember)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = message,
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ParticipantSelectionCard(
    participant: RegistrationParticipant,
    existingStatus: RegistrationApprovalStatus?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val disabled = existingStatus != null
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !disabled, onClick = onClick),
        shape = RoundedCornerShape(CzRadius.lg),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.czColors.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) MaterialTheme.czColors.ember else MaterialTheme.czColors.divider,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            ParticipantAvatar(participant)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                    Text(
                        text = participant.displayName,
                        color = MaterialTheme.czColors.textPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    ParticipantKindPill(participant.kind)
                }
                Text(
                    text = stringResource(
                        R.string.registration_participant_meta,
                        participant.age,
                        participant.ageGroup.displayName,
                    ),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (existingStatus != null) {
                Text(
                    text = existingStatus.displayName(),
                    color = existingStatus.statusColor(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.Circle,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.czColors.success else MaterialTheme.czColors.textSecondary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun TransportationCard(
    participant: RegistrationParticipant,
    camping: Camping,
    selectedOptionId: String?,
    onSelectOption: (String?) -> Unit,
) {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            ParticipantHeader(participant = participant, icon = transportIcon(camping, selectedOptionId))
            TransportationOptionRow(
                label = stringResource(R.string.registration_own_arrangement),
                selected = selectedOptionId == null,
                onClick = { onSelectOption(null) },
            )
            camping.transportationOptions.forEach { option ->
                TransportationOptionRow(
                    label = optionLabel(option),
                    selected = selectedOptionId == option.id,
                    onClick = { onSelectOption(option.id) },
                )
            }
        }
    }
}

@Composable
private fun NoTransportSetupCard() {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.czColors.textSecondary)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.registration_no_transport_needed_title),
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.registration_no_transport_needed_message),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun InlineVehicleCaptureCard(
    user: AuthenticatedUser,
    draft: InlineVehicleDraft,
    savedVehicles: List<UserVehicle>,
    onEnabledChange: (Boolean) -> Unit,
    onSavedVehicleSelected: (UserVehicle) -> Unit,
    onPlateChange: (String) -> Unit,
    onBrandChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onTotalSeatsChange: (Int) -> Unit,
    onPeopleInCarChange: (Int) -> Unit,
    onHasSeatsChange: (Boolean) -> Unit,
    onNotesChange: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    color = MaterialTheme.czColors.ember.copy(alpha = 0.12f),
                    shape = CircleShape,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = MaterialTheme.czColors.ember)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.registration_inline_car_title),
                        color = MaterialTheme.czColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.registration_inline_car_message, user.preferredDisplayName),
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onEnabledChange(!draft.enabled) },
                color = MaterialTheme.czColors.background,
                shape = RoundedCornerShape(CzRadius.md),
                border = BorderStroke(1.dp, MaterialTheme.czColors.divider),
            ) {
                Row(
                    modifier = Modifier.padding(CzSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.registration_inline_car_toggle),
                            color = MaterialTheme.czColors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.registration_inline_car_toggle_subtitle),
                            color = MaterialTheme.czColors.textSecondary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Switch(checked = draft.enabled, onCheckedChange = onEnabledChange)
                }
            }

            if (draft.enabled) {
                if (savedVehicles.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                        Text(
                            text = stringResource(R.string.registration_saved_vehicles),
                            color = MaterialTheme.czColors.textSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                            items(savedVehicles, key = { it.id }) { vehicle ->
                                SavedVehicleChip(
                                    vehicle = vehicle,
                                    selected = draft.selectedSavedVehicleId == vehicle.id,
                                    onClick = { onSavedVehicleSelected(vehicle) },
                                )
                            }
                        }
                    }
                }

                CzTextField(
                    value = draft.plateNumber,
                    onValueChange = onPlateChange,
                    label = stringResource(R.string.registration_plate_number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = draft.plateIsBlank,
                    supportingText = if (draft.plateIsBlank) {
                        stringResource(R.string.registration_vehicle_plate_required)
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Text,
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                    CzTextField(
                        value = draft.brand,
                        onValueChange = onBrandChange,
                        label = stringResource(R.string.registration_brand),
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    )
                    CzTextField(
                        value = draft.model,
                        onValueChange = onModelChange,
                        label = stringResource(R.string.registration_model),
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    )
                }
                CzTextField(
                    value = draft.color,
                    onValueChange = onColorChange,
                    label = stringResource(R.string.registration_color),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                SeatStepperRow(
                    label = stringResource(R.string.registration_total_seats),
                    value = draft.totalSeats,
                    onDecrease = { onTotalSeatsChange(draft.totalSeats - 1) },
                    onIncrease = { onTotalSeatsChange(draft.totalSeats + 1) },
                )
                SeatStepperRow(
                    label = stringResource(R.string.registration_people_in_car),
                    value = draft.peopleInCar,
                    onDecrease = { onPeopleInCarChange(draft.peopleInCar - 1) },
                    onIncrease = { onPeopleInCarChange(draft.peopleInCar + 1) },
                )
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onHasSeatsChange(!draft.hasAvailableSeats) },
                    color = MaterialTheme.czColors.background,
                    shape = RoundedCornerShape(CzRadius.md),
                    border = BorderStroke(1.dp, MaterialTheme.czColors.divider),
                ) {
                    Row(
                        modifier = Modifier.padding(CzSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                    ) {
                        Text(
                            text = stringResource(R.string.registration_seats_available),
                            color = MaterialTheme.czColors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = draft.hasAvailableSeats, onCheckedChange = onHasSeatsChange)
                    }
                }
                CzTextField(
                    value = draft.notes,
                    onValueChange = onNotesChange,
                    label = stringResource(R.string.registration_notes_optional),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
        }
    }
}

@Composable
private fun SavedVehicleChip(
    vehicle: UserVehicle,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.width(190.dp).clickable(onClick = onClick),
        color = if (selected) MaterialTheme.czColors.ember.copy(alpha = 0.12f) else MaterialTheme.czColors.background,
        shape = RoundedCornerShape(CzRadius.md),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.czColors.ember else MaterialTheme.czColors.divider,
        ),
    ) {
        Row(
            modifier = Modifier.padding(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.DirectionsCar,
                contentDescription = null,
                tint = if (selected) MaterialTheme.czColors.ember else MaterialTheme.czColors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = vehicle.displayTitle,
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = vehicle.plateNumber,
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SeatStepperRow(
    label: String,
    value: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Surface(
        color = MaterialTheme.czColors.background,
        shape = RoundedCornerShape(CzRadius.md),
        border = BorderStroke(1.dp, MaterialTheme.czColors.divider),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Text(
                text = label,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDecrease, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Remove, contentDescription = null)
            }
            Text(
                text = value.toString(),
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.defaultMinSize(minWidth = 28.dp),
            )
            IconButton(onClick = onIncrease, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
    }
}

@Composable
private fun ReviewCard(
    participants: List<RegistrationParticipant>,
    camping: Camping,
    state: CampingRegistrationUiState,
) {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            participants.forEachIndexed { index, participant ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(CzSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                ) {
                    ParticipantAvatar(participant, size = 36)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = participant.displayName,
                            color = MaterialTheme.czColors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = transportSummary(participant, camping, state),
                            color = MaterialTheme.czColors.textSecondary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Icon(
                        imageVector = participantTransportIcon(
                            participant = participant,
                            camping = camping,
                            state = state,
                        ),
                        contentDescription = null,
                        tint = MaterialTheme.czColors.ember,
                    )
                }
                if (index != participants.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 52.dp),
                        color = MaterialTheme.czColors.divider,
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineVehicleReviewCard(draft: InlineVehicleDraft) {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = MaterialTheme.czColors.ember)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.registration_vehicle_review_title),
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        R.string.registration_vehicle_review_detail,
                        draft.normalizedPlate,
                        draft.peopleInCar,
                        draft.totalSeats,
                    ),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun RegistrationWizardBar(
    step: RegistrationStep,
    isWaitlist: Boolean,
    selectedCount: Int,
    canProceed: Boolean,
    canGoBack: Boolean,
    isSubmitting: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSubmit: () -> Unit,
) {
    Surface(color = MaterialTheme.czColors.background, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            if (canGoBack) {
                CzButton(
                    text = stringResource(R.string.common_back),
                    onClick = onBack,
                    enabled = !isSubmitting,
                    modifier = Modifier.weight(0.42f).height(54.dp),
                    variant = CzButtonVariant.Secondary,
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                )
            }
            CzButton(
                text = if (step == RegistrationStep.Review) {
                    when {
                        isWaitlist -> stringResource(R.string.registration_join_waitlist)
                        selectedCount <= 1 -> stringResource(R.string.registration_submit_one)
                        else -> stringResource(R.string.registration_submit_many)
                    }
                } else {
                    stringResource(R.string.registration_continue)
                },
                onClick = if (step == RegistrationStep.Review) onSubmit else onNext,
                enabled = canProceed,
                loading = isSubmitting,
                modifier = Modifier.weight(1f).height(54.dp),
                leadingIcon = {
                    if (!isSubmitting) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                },
            )
        }
    }
}

@Composable
private fun EmptySelectionHint() {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.czColors.textSecondary)
            Text(
                text = stringResource(R.string.registration_empty_selection_hint),
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun ParticipantHeader(participant: RegistrationParticipant, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        ParticipantAvatar(participant, size = 36)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = participant.displayName,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = participant.kind.displayName(),
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Icon(icon, contentDescription = null, tint = MaterialTheme.czColors.ember)
    }
}

@Composable
private fun TransportationOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = if (selected) MaterialTheme.czColors.ember.copy(alpha = 0.10f) else MaterialTheme.czColors.background,
        shape = RoundedCornerShape(CzRadius.md),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.czColors.ember else MaterialTheme.czColors.divider,
        ),
    ) {
        Row(
            modifier = Modifier.padding(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.Circle,
                contentDescription = null,
                tint = if (selected) MaterialTheme.czColors.ember else MaterialTheme.czColors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ParticipantAvatar(participant: RegistrationParticipant, size: Int = 44) {
    Surface(
        modifier = Modifier.size(size.dp).clip(CircleShape),
        color = MaterialTheme.czColors.ember.copy(alpha = 0.14f),
        shape = CircleShape,
    ) {
        if (participant.photoUrl != null) {
            AsyncImage(
                model = participant.photoUrl,
                contentDescription = participant.displayName,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = participant.displayName.take(1).uppercase(),
                    color = MaterialTheme.czColors.ember,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ParticipantKindPill(kind: RegistrationParticipantKind) {
    Surface(
        color = MaterialTheme.czColors.ember.copy(alpha = 0.10f),
        shape = RoundedCornerShape(CzRadius.full),
    ) {
        Text(
            text = kind.displayName(),
            color = MaterialTheme.czColors.ember,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = CzSpacing.xs, vertical = 2.dp),
        )
    }
}

@Composable
private fun RegistrationApprovalStatus.displayName(): String = when (this) {
    RegistrationApprovalStatus.Pending -> stringResource(R.string.registration_status_pending)
    RegistrationApprovalStatus.Approved -> stringResource(R.string.registration_status_approved)
    RegistrationApprovalStatus.Rejected -> stringResource(R.string.registration_status_rejected)
    RegistrationApprovalStatus.Waitlisted -> stringResource(R.string.registration_status_waitlisted)
}

@Composable
private fun RegistrationApprovalStatus.statusColor() = when (this) {
    RegistrationApprovalStatus.Approved -> MaterialTheme.czColors.success
    RegistrationApprovalStatus.Pending -> MaterialTheme.czColors.warning
    RegistrationApprovalStatus.Waitlisted -> MaterialTheme.czColors.textSecondary
    RegistrationApprovalStatus.Rejected -> MaterialTheme.czColors.error
}

@Composable
private fun RegistrationParticipantKind.displayName(): String = when (this) {
    RegistrationParticipantKind.SelfParticipant -> stringResource(R.string.registration_kind_self)
    RegistrationParticipantKind.Child -> stringResource(R.string.registration_kind_child)
}

@Composable
private fun RegistrationStep.displayName(): String = when (this) {
    RegistrationStep.Who -> stringResource(R.string.registration_step_who)
    RegistrationStep.Transport -> stringResource(R.string.registration_step_transport)
    RegistrationStep.Review -> stringResource(R.string.registration_step_review)
}

private fun participantTransportIcon(
    participant: RegistrationParticipant,
    camping: Camping,
    state: CampingRegistrationUiState,
): ImageVector =
    if (participant.usesInlineVehicle(state)) {
        Icons.Filled.DirectionsCar
    } else {
        transportIcon(camping, state.transportationOptionIds[participant.id])
    }

private fun transportIcon(
    camping: Camping,
    optionId: String?,
): ImageVector {
    val option = camping.transportationOption(optionId)
    return when (option?.mode) {
        TransportationMode.Bus,
        TransportationMode.Coach,
        TransportationMode.Minibus,
        TransportationMode.Shuttle,
        -> Icons.Filled.DirectionsBus
        TransportationMode.Train -> Icons.Filled.DirectionsBus
        TransportationMode.OwnCar,
        TransportationMode.Carpool,
        -> Icons.Filled.DirectionsCar
        TransportationMode.Plane,
        TransportationMode.Boat,
        TransportationMode.Bike,
        TransportationMode.OnFoot,
        TransportationMode.Other,
        null,
        -> Icons.Filled.BusAlert
    }
}

@Composable
private fun optionLabel(option: CampingTransportationOption): String {
    val fee = option.feeCents?.takeIf { it > 0 }?.let {
        val locale = Locale.forLanguageTag(ComposeLocale.current.toLanguageTag())
        NumberFormat.getCurrencyInstance(locale).apply {
            currency = java.util.Currency.getInstance(option.currency.ifBlank { "EUR" }.uppercase())
        }.format(it / 100.0)
    }
    return if (fee == null) option.resolvedName else "${option.resolvedName} · $fee"
}

@Composable
private fun transportSummary(
    participant: RegistrationParticipant,
    camping: Camping,
    state: CampingRegistrationUiState,
): String {
    if (participant.usesInlineVehicle(state)) {
        return stringResource(
            R.string.registration_vehicle_review_detail,
            state.inlineVehicle.normalizedPlate,
            state.inlineVehicle.peopleInCar,
            state.inlineVehicle.totalSeats,
        )
    }
    if (camping.usesTransportationOptions) {
        return camping.transportationOption(state.transportationOptionIds[participant.id])?.resolvedName
            ?: stringResource(R.string.registration_own_arrangement)
    }
    return stringResource(R.string.registration_own_arrangement)
}

private fun RegistrationParticipant.usesInlineVehicle(state: CampingRegistrationUiState): Boolean =
    state.wantsInlineVehicle && id == state.selfParticipantForInlineVehicle?.id

@Preview(showBackground = true)
@Composable
private fun CampingRegistrationScreenPreview() {
    val camping = Camping(
        id = "camp-1",
        title = "Summer Camp",
        description = "A week together",
        startDate = Date(1_000_000),
        endDate = Date(2_000_000),
        organizerLevel = OrganizerLevel(OrganizerType.Church, "Paris Central SDA"),
        location = "Annecy",
        registrationStatus = CampingRegistrationStatus.Open,
        transportationOptions = listOf(
            CampingTransportationOption(
                id = "bus-1",
                name = "Coach from Paris-Bercy",
                mode = TransportationMode.Coach,
                details = "Departs 08:00",
                requiresTicket = true,
                feeCents = 2500,
            ),
        ),
    )
    val user = AuthenticatedUser(
        uid = "u1",
        email = "maria@example.com",
        displayName = "Maria Santos",
        photoUrl = null,
        role = fr.ziyon.campzone.core.permissions.UserRole.Adult,
        church = "Paris Central SDA",
        age = 30,
        preferredLanguage = "fr",
        gender = UserGender.Female,
        onboardingCompleted = true,
    )
    CampzoneTheme {
        CampingRegistrationScreen(
            state = CampingRegistrationUiState(
                isLoading = false,
                camping = camping,
                participants = listOf(RegistrationParticipant.from(user)),
                selectedParticipantIds = setOf(user.uid),
            ),
            authenticatedUser = user,
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onToggleParticipant = {},
            onSelectTransportationOption = { _, _ -> },
            onGoBackStep = {},
            onGoNextStep = {},
            onToggleInlineVehicle = {},
            onApplySavedVehicle = {},
            onUpdateVehiclePlate = {},
            onUpdateVehicleBrand = {},
            onUpdateVehicleModel = {},
            onUpdateVehicleColor = {},
            onUpdateVehicleTotalSeats = {},
            onUpdateVehiclePeopleInCar = {},
            onUpdateVehicleHasSeats = {},
            onUpdateVehicleNotes = {},
            onAddParticipant = {},
            onSubmit = {},
        )
    }
}
