package fr.ziyon.campzone.ui.camping.register

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.BusAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
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
import fr.ziyon.campzone.data.model.TransportationChoice
import fr.ziyon.campzone.data.model.TransportationMode
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
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onToggleParticipant = viewModel::toggleParticipant,
        onSelectTransportationChoice = viewModel::selectTransportationChoice,
        onSelectTransportationOption = viewModel::selectTransportationOption,
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
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onToggleParticipant: (String) -> Unit,
    onSelectTransportationChoice: (String, TransportationChoice) -> Unit,
    onSelectTransportationOption: (String, String?) -> Unit,
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
            TopAppBar(
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
            )
        },
        bottomBar = {
            if (camping != null && !state.isLoading) {
                RegistrationSubmitBar(
                    isWaitlist = camping.isAtCapacity,
                    selectedCount = state.selectedParticipants.size,
                    canSubmit = state.canSubmit,
                    isSubmitting = state.isSubmitting,
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
                onToggleParticipant = onToggleParticipant,
                onSelectTransportationChoice = onSelectTransportationChoice,
                onSelectTransportationOption = onSelectTransportationOption,
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
    onToggleParticipant: (String) -> Unit,
    onSelectTransportationChoice: (String, TransportationChoice) -> Unit,
    onSelectTransportationOption: (String, String?) -> Unit,
    onAddParticipant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = CzSpacing.lg,
            top = CzSpacing.lg,
            end = CzSpacing.lg,
            bottom = 112.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
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
        } else {
            item {
                SectionTitle(
                    title = stringResource(R.string.registration_transportation),
                    icon = Icons.Filled.DirectionsBus,
                )
            }
            items(state.selectedParticipants, key = { "transport-${it.id}" }) { participant ->
                TransportationCard(
                    participant = participant,
                    camping = camping,
                    selectedChoice = state.transportationChoices[participant.id] ?: TransportationChoice.OwnCar,
                    selectedOptionId = state.transportationOptionIds[participant.id],
                    onSelectChoice = { onSelectTransportationChoice(participant.id, it) },
                    onSelectOption = { onSelectTransportationOption(participant.id, it) },
                )
            }
            item {
                SectionTitle(
                    title = stringResource(R.string.registration_review),
                    icon = Icons.Filled.Verified,
                )
            }
            item {
                ReviewCard(
                    participants = state.selectedParticipants,
                    camping = camping,
                    state = state,
                )
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
                text = stringResource(R.string.registration_header_copy),
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
    selectedChoice: TransportationChoice,
    selectedOptionId: String?,
    onSelectChoice: (TransportationChoice) -> Unit,
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
            ParticipantHeader(participant = participant, icon = transportIcon(camping, selectedChoice, selectedOptionId))
            if (camping.usesTransportationOptions) {
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
            } else {
                TransportationChoice.entries.forEach { choice ->
                    TransportationOptionRow(
                        label = choice.displayName(),
                        selected = selectedChoice == choice,
                        onClick = { onSelectChoice(choice) },
                    )
                }
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
                        imageVector = transportIcon(
                            camping = camping,
                            choice = state.transportationChoices[participant.id] ?: TransportationChoice.OwnCar,
                            optionId = state.transportationOptionIds[participant.id],
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
private fun RegistrationSubmitBar(
    isWaitlist: Boolean,
    selectedCount: Int,
    canSubmit: Boolean,
    isSubmitting: Boolean,
    onSubmit: () -> Unit,
) {
    Surface(color = MaterialTheme.czColors.background, shadowElevation = 8.dp) {
        CzButton(
            text = when {
                isWaitlist -> stringResource(R.string.registration_join_waitlist)
                selectedCount <= 1 -> stringResource(R.string.registration_submit_one)
                else -> stringResource(R.string.registration_submit_many)
            },
            onClick = onSubmit,
            enabled = canSubmit,
            loading = isSubmitting,
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg).height(54.dp),
            leadingIcon = {
                if (!isSubmitting) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            },
        )
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
private fun TransportationChoice.displayName(): String = when (this) {
    TransportationChoice.OwnCar -> stringResource(R.string.registration_transport_own_car)
    TransportationChoice.ProvidedBus -> stringResource(R.string.registration_transport_provided_bus)
}

private fun transportIcon(
    camping: Camping,
    choice: TransportationChoice,
    optionId: String?,
): ImageVector {
    if (camping.usesTransportationOptions) {
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
    return if (choice == TransportationChoice.ProvidedBus) {
        Icons.Filled.DirectionsBus
    } else {
        Icons.Filled.DirectionsCar
    }
}

@Composable
private fun optionLabel(option: CampingTransportationOption): String {
    val fee = option.feeCents?.takeIf { it > 0 }?.let {
        NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
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
    if (camping.usesTransportationOptions) {
        return camping.transportationOption(state.transportationOptionIds[participant.id])?.resolvedName
            ?: stringResource(R.string.registration_own_arrangement)
    }
    return (state.transportationChoices[participant.id] ?: TransportationChoice.OwnCar).displayName()
}

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
                transportationChoices = mapOf(user.uid to TransportationChoice.OwnCar),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onToggleParticipant = {},
            onSelectTransportationChoice = { _, _ -> },
            onSelectTransportationOption = { _, _ -> },
            onAddParticipant = {},
            onSubmit = {},
        )
    }
}
