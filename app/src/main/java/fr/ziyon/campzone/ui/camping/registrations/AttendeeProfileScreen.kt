package fr.ziyon.campzone.ui.camping.registrations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import fr.ziyon.campzone.data.family.FamilyRelationship
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import fr.ziyon.campzone.ui.camping.label
import fr.ziyon.campzone.ui.common.AllergyChips
import java.text.DateFormat

@Composable
fun AttendeeProfileRoute(
    campingId: String,
    attendeeId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenRelatedAttendee: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AttendeeProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(campingId, attendeeId, authenticatedUser) {
        viewModel.load(campingId, attendeeId, authenticatedUser)
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

    AttendeeProfileScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onOpenRelatedAttendee = onOpenRelatedAttendee,
        onStatusChange = viewModel::updateStatus,
        onDeleteAttendee = { viewModel.deleteAttendee(onDeleted = onBack) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendeeProfileScreen(
    state: AttendeeProfileUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenRelatedAttendee: (String, String) -> Unit,
    onStatusChange: (RegistrationApprovalStatus) -> Unit,
    onDeleteAttendee: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }

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
                        text = stringResource(R.string.attendee_profile_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
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
    ) { innerPadding ->
        when {
            state.isLoading -> CzLoadingView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                message = stringResource(R.string.attendee_profile_loading),
            )

            state.isRestricted -> StateCenter(
                icon = Icons.Filled.Lock,
                title = stringResource(R.string.registration_review_restricted_title),
                message = stringResource(R.string.attendee_profile_restricted_message),
                modifier = Modifier.padding(innerPadding),
            )

            state.camping == null || state.attendee == null -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CzErrorState(
                    title = stringResource(R.string.attendee_profile_not_found_title),
                    message = stringResource(R.string.attendee_profile_not_found_message),
                )
            }

            else -> AttendeeProfileContent(
                state = state,
                camping = state.camping,
                attendee = state.attendee,
                onStatusChange = onStatusChange,
                onOpenRelatedAttendee = onOpenRelatedAttendee,
                onShowDeleteConfirmation = { showDeleteConfirmation = true },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    val attendee = state.attendee
    val camping = state.camping
    if (showDeleteConfirmation && attendee != null && camping != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Text(stringResource(R.string.attendee_profile_remove_title, attendee.displayName))
            },
            text = {
                Text(
                    stringResource(
                        R.string.attendee_profile_remove_message,
                        attendee.displayName,
                        camping.title,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteAttendee()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.attendee_profile_remove_confirm),
                        color = MaterialTheme.czColors.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.attendee_profile_remove_cancel))
                }
            },
        )
    }
}

@Composable
private fun AttendeeProfileContent(
    state: AttendeeProfileUiState,
    camping: Camping,
    attendee: CampingAttendee,
    onStatusChange: (RegistrationApprovalStatus) -> Unit,
    onOpenRelatedAttendee: (String, String) -> Unit,
    onShowDeleteConfirmation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val relatedAttendees = remember(camping.attendees, attendee.id) {
        relatedAttendees(camping, attendee)
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = CzSpacing.lg,
            top = CzSpacing.sm,
            end = CzSpacing.lg,
            bottom = CzSpacing.lg,
        ),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        item { AttendeeHeader(attendee) }
        item {
            InfoSection(
                title = stringResource(R.string.attendee_profile_identity),
                icon = Icons.Filled.Person,
            ) {
                InfoRow(stringResource(R.string.attendee_profile_age), attendee.age.toString())
                InfoRow(stringResource(R.string.attendee_profile_age_group), attendee.ageGroup.label())
                attendee.gender?.let {
                    InfoRow(stringResource(R.string.attendee_profile_gender), it.wireValue)
                }
                InfoRow(stringResource(R.string.attendee_profile_church), attendee.church)
                if (attendee.languages.isNotEmpty()) {
                    InfoRow(
                        stringResource(R.string.attendee_profile_languages),
                        attendee.languages.joinToString(),
                    )
                }
                attendee.relationship?.let { relationship ->
                    InfoRow(
                        stringResource(R.string.attendee_profile_relationship),
                        attendee.relationshipDisplayName(relationship),
                    )
                }
            }
        }
        if (attendee.participantKind == RegistrationParticipantKind.Child) {
            item {
                InfoSection(
                    title = stringResource(R.string.attendee_profile_guardian),
                    icon = Icons.Filled.FamilyRestroom,
                ) {
                    InfoRow(
                        stringResource(R.string.attendee_profile_guardian_id),
                        attendee.guardianId.orEmpty().ifBlank { "-" },
                    )
                    InfoRow(
                        stringResource(R.string.attendee_profile_consent),
                        attendee.guardianConsentAt?.let(DateFormat.getDateTimeInstance()::format)
                            ?: stringResource(R.string.attendee_profile_not_recorded),
                    )
                }
            }
        }
        if (relatedAttendees.isNotEmpty()) {
            item {
                RelatedAttendeesSection(
                    related = relatedAttendees,
                    onOpen = { relatedAttendee ->
                        onOpenRelatedAttendee(camping.id, relatedAttendee.attendee.id)
                    },
                )
            }
        }
        item {
            InfoSection(
                title = stringResource(R.string.attendee_profile_emergency),
                icon = Icons.Filled.Emergency,
            ) {
                InfoRow(
                    stringResource(R.string.attendee_profile_contact),
                    attendee.emergencyContactName.ifBlank { "-" },
                )
                InfoRow(
                    stringResource(R.string.attendee_profile_phone),
                    attendee.emergencyContactPhone.ifBlank { "-" },
                )
                if (attendee.medicalNotes.isNotBlank()) {
                    InfoRow(stringResource(R.string.attendee_profile_medical_notes), attendee.medicalNotes)
                }
            }
        }
        if (attendee.allergies.isNotEmpty()) {
            item {
                InfoSection(
                    title = stringResource(R.string.profile_allergies),
                    icon = Icons.Filled.WarningAmber,
                ) {
                    AllergyChips(tokens = attendee.allergies)
                }
            }
        }
        item {
            InfoSection(
                title = stringResource(R.string.attendee_profile_logistics),
                icon = Icons.Filled.DirectionsBus,
            ) {
                InfoRow(
                    stringResource(R.string.attendee_profile_transportation),
                    attendee.transportationOptionName ?: attendee.transportationChoice.wireValue,
                )
                attendee.transportationBookingId?.let {
                    InfoRow(stringResource(R.string.attendee_profile_bus_booking), it)
                }
            }
        }
        item {
            InfoSection(
                title = stringResource(R.string.attendee_profile_review),
                icon = Icons.Filled.Verified,
            ) {
                InfoRow(stringResource(R.string.attendee_profile_current_status), attendee.registrationStatus.label())
                Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                    StatusActionButton(
                        text = stringResource(R.string.registration_review_approve),
                        icon = Icons.Filled.CheckCircle,
                        color = MaterialTheme.czColors.success,
                        enabled = state.canRemoveAttendee &&
                            attendee.registrationStatus != RegistrationApprovalStatus.Approved &&
                            !state.isSaving,
                        onClick = { onStatusChange(RegistrationApprovalStatus.Approved) },
                        modifier = Modifier.weight(1f),
                    )
                    StatusActionButton(
                        text = stringResource(R.string.registration_review_reject),
                        icon = Icons.Filled.Lock,
                        color = MaterialTheme.czColors.error,
                        enabled = state.canRemoveAttendee &&
                            attendee.registrationStatus != RegistrationApprovalStatus.Rejected &&
                            !state.isSaving,
                        onClick = { onStatusChange(RegistrationApprovalStatus.Rejected) },
                        modifier = Modifier.weight(1f),
                    )
                    StatusActionButton(
                        text = stringResource(R.string.registration_status_pending),
                        icon = Icons.Filled.Schedule,
                        color = MaterialTheme.czColors.warning,
                        enabled = state.canRemoveAttendee &&
                            attendee.registrationStatus != RegistrationApprovalStatus.Pending &&
                            !state.isSaving,
                        onClick = { onStatusChange(RegistrationApprovalStatus.Pending) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            InfoSection(
                title = stringResource(R.string.attendee_profile_payments),
                icon = Icons.Filled.CreditCard,
            ) {
                Text(
                    text = stringResource(R.string.attendee_profile_no_payment_records),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (state.canRemoveAttendee) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                    Text(
                        text = stringResource(R.string.attendee_profile_danger_zone),
                        color = MaterialTheme.czColors.error,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    CzButton(
                        text = stringResource(R.string.attendee_profile_remove_action),
                        onClick = onShowDeleteConfirmation,
                        enabled = !state.isSaving,
                        loading = state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        variant = CzButtonVariant.Destructive,
                        leadingIcon = {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                    )
                }
            }
        }
    }
}

internal enum class RelatedAttendeeKind { Guardian, Participant }

internal data class RelatedAttendee(
    val attendee: CampingAttendee,
    val kind: RelatedAttendeeKind,
    val sourceRelationship: FamilyRelationship? = null,
)

internal fun relatedAttendees(camping: Camping, attendee: CampingAttendee): List<RelatedAttendee> {
    val related = mutableListOf<RelatedAttendee>()
    val seen = mutableSetOf(attendee.id)
    val guardianId = attendee.guardianId?.takeUnless { it.isBlank() }
    if (guardianId != null) {
        val guardian = camping.attendees.firstOrNull {
            it.userId == guardianId && it.participantKind == RegistrationParticipantKind.SelfParticipant
        } ?: camping.attendees.firstOrNull { it.id == guardianId }
        if (guardian != null && seen.add(guardian.id)) {
            related += RelatedAttendee(
                attendee = guardian,
                kind = RelatedAttendeeKind.Guardian,
                sourceRelationship = attendee.relationship,
            )
        }
        camping.attendees
            .filter { it.guardianId == guardianId }
            .forEach { sibling ->
                if (seen.add(sibling.id)) {
                    related += RelatedAttendee(
                        attendee = sibling,
                        kind = RelatedAttendeeKind.Participant,
                        sourceRelationship = sibling.relationship,
                    )
                }
            }
    }
    camping.attendees
        .filter { it.guardianId == attendee.userId }
        .forEach { dependent ->
            if (seen.add(dependent.id)) {
                related += RelatedAttendee(
                    attendee = dependent,
                    kind = RelatedAttendeeKind.Participant,
                    sourceRelationship = dependent.relationship,
                )
            }
        }
    return related
}

@Composable
private fun RelatedAttendeesSection(
    related: List<RelatedAttendee>,
    onOpen: (RelatedAttendee) -> Unit,
) {
    InfoSection(
        title = stringResource(R.string.attendee_profile_family_at_camp),
        icon = Icons.Filled.FamilyRestroom,
    ) {
        related.forEachIndexed { index, relative ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(relative) }
                    .padding(vertical = CzSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            ) {
                CzAvatar(
                    imageUrl = relative.attendee.photoUrl,
                    contentDescription = relative.attendee.displayName,
                    initials = relative.attendee.displayName,
                    size = CzAvatarSize.Small,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = relative.attendee.displayName,
                        color = MaterialTheme.czColors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = relative.caption(),
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(relative.attendee.registrationStatus.statusColor(), CircleShape),
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.czColors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (index < related.lastIndex) {
                HorizontalDivider(color = MaterialTheme.czColors.divider)
            }
        }
    }
}

@Composable
private fun RelatedAttendee.caption(): String {
    val relationship = sourceRelationship?.let { attendee.relationshipDisplayName(it) }
    return when (kind) {
        RelatedAttendeeKind.Guardian -> relationship?.let {
            stringResource(R.string.attendee_profile_guardian_relationship, it)
        } ?: stringResource(R.string.attendee_profile_guardian)
        RelatedAttendeeKind.Participant -> relationship
            ?: stringResource(R.string.attendee_profile_participant)
    }
}

@Composable
private fun CampingAttendee.relationshipDisplayName(relationship: FamilyRelationship): String {
    if (relationship == FamilyRelationship.Other && !customRelationshipLabel.isNullOrBlank()) {
        return customRelationshipLabel.orEmpty()
    }
    return stringResource(
        when (relationship) {
            FamilyRelationship.Parent -> R.string.relationship_parent
            FamilyRelationship.StepParent -> R.string.relationship_step_parent
            FamilyRelationship.LegalGuardian -> R.string.relationship_legal_guardian
            FamilyRelationship.Grandparent -> R.string.relationship_grandparent
            FamilyRelationship.Sibling -> R.string.relationship_sibling
            FamilyRelationship.Aunt -> R.string.relationship_aunt
            FamilyRelationship.Uncle -> R.string.relationship_uncle
            FamilyRelationship.Cousin -> R.string.relationship_cousin
            FamilyRelationship.Friend -> R.string.relationship_friend
            FamilyRelationship.Other -> R.string.relationship_other
        },
    )
}

@Composable
private fun AttendeeHeader(attendee: CampingAttendee) {
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CzAvatar(
                imageUrl = attendee.photoUrl,
                contentDescription = attendee.displayName,
                initials = attendee.displayName,
                size = CzAvatarSize.Large,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attendee.displayName,
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(attendee.registrationStatus.statusColor(), CircleShape)
                            .clip(CircleShape)
                    )
                    Surface(
                        color = attendee.registrationStatus.statusColor().copy(alpha = 0.12f),
                        contentColor = attendee.registrationStatus.statusColor(),
                        shape = RoundedCornerShape(CzRadius.full),
                    ) {
                        Text(
                            text = attendee.registrationStatus.label(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = CzSpacing.sm, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
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
        Surface(
            color = MaterialTheme.czColors.surface,
            shape = RoundedCornerShape(CzRadius.lg),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                content = content,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Text(
            text = label,
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.42f),
        )
        Text(
            text = value,
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.58f),
        )
    }
}

@Composable
private fun StatusActionButton(
    text: String,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        color = color.copy(alpha = if (enabled) 0.12f else 0.06f),
        contentColor = color.copy(alpha = if (enabled) 1f else 0.45f),
        shape = RoundedCornerShape(CzRadius.sm),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 9.dp, horizontal = CzSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StateCenter(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CzEmptyState(
            title = title,
            message = message,
            icon = {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.czColors.textSecondary,
                    modifier = Modifier.size(40.dp),
                )
            },
        )
    }
}
