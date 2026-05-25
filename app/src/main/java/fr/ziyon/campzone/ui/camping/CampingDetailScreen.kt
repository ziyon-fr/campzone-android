package fr.ziyon.campzone.ui.camping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.LaunchedEffect
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzAvatar
import fr.ziyon.campzone.core.designsystem.CzBadge
import fr.ziyon.campzone.core.designsystem.CzCard
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTextField
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.OrganizerType

@Composable
fun CampingDetailRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CampingDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(campingId) { viewModel.load(campingId, authenticatedUser) }
    val state by viewModel.uiState.collectAsState()
    CampingDetailScreen(
        state = state,
        onBack = onBack,
        onAttendeeSearchChange = viewModel::updateAttendeeSearch,
        onRetry = { viewModel.load(campingId, authenticatedUser) },
        modifier = modifier,
    )
}

@Composable
fun CampingDetailScreen(
    state: CampingDetailUiState,
    onBack: () -> Unit,
    onAttendeeSearchChange: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CzSpacing.sm, vertical = CzSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.czColors.textPrimary,
                )
            }
            Text(
                text = state.camping?.title ?: stringResource(R.string.nav_campings),
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = CzSpacing.xs),
            )
        }

        when {
            state.isLoading -> CzLoadingView(
                modifier = Modifier.fillMaxSize(),
                message = stringResource(R.string.camping_loading),
            )

            state.errorMessage != null || state.camping == null -> Box(
                Modifier.fillMaxSize(),
                Alignment.Center,
            ) {
                CzErrorState(
                    title = stringResource(R.string.camping_error_title),
                    message = state.errorMessage,
                    onRetry = onRetry,
                    retryLabel = stringResource(R.string.common_retry),
                )
            }

            else -> CampingDetailContent(
                state = state,
                camping = state.camping,
                onAttendeeSearchChange = onAttendeeSearchChange,
            )
        }
    }
}

@Composable
private fun CampingDetailContent(
    state: CampingDetailUiState,
    camping: Camping,
    onAttendeeSearchChange: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = CzSpacing.xl,
            end = CzSpacing.xl,
            top = CzSpacing.sm,
            bottom = CzSpacing.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.base),
    ) {
        item(key = "summary") {
            CzCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                    CzBadge(
                        text = camping.registrationStatus.label(),
                        tone = camping.registrationStatus.badgeTone(),
                    )
                    Text(
                        text = campingDateRange(camping.startDate, camping.endDate),
                        color = MaterialTheme.czColors.textPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = CzSpacing.xs),
                    )
                    DetailRow(stringResource(R.string.camping_location), camping.location)
                    DetailRow(
                        stringResource(R.string.camping_organizer),
                        "${organizerTypeLabel(camping.organizerLevel.type)} · ${camping.organizerLevel.value}",
                    )
                    CapacityRow(state)
                }
            }
        }

        if (camping.description.isNotBlank()) {
            item(key = "about") {
                SectionCard(title = stringResource(R.string.camping_about)) {
                    Text(
                        text = camping.description,
                        color = MaterialTheme.czColors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        item(key = "guidelines") {
            SectionCard(title = stringResource(R.string.camping_guidelines)) {
                Text(
                    text = camping.guidelines.ifBlank { stringResource(R.string.camping_no_guidelines) },
                    color = if (camping.guidelines.isBlank()) {
                        MaterialTheme.czColors.textSecondary
                    } else {
                        MaterialTheme.czColors.textPrimary
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item(key = "attendees-header") {
            Text(
                text = stringResource(R.string.camping_attendees),
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = CzSpacing.sm),
            )
        }

        if (state.canViewAttendees) {
            item(key = "attendee-search") {
                CzTextField(
                    value = state.attendeeSearch,
                    onValueChange = onAttendeeSearchChange,
                    label = stringResource(R.string.camping_attendee_search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(state.visibleAttendees, key = { it.id }) { attendee ->
                AttendeeRow(attendee)
            }
        } else {
            item(key = "attendees-locked") {
                Text(
                    text = stringResource(R.string.camping_attendees_locked),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun CapacityRow(state: CampingDetailUiState) {
    val capacity = state.camping?.participantCapacity ?: return
    val text = when {
        state.canViewParticipantProfiles && state.isAtCapacity ->
            stringResource(R.string.camping_full)

        state.canViewParticipantProfiles ->
            stringResource(R.string.camping_capacity_value, state.approvedAttendeeCount, capacity)

        else -> stringResource(R.string.camping_capacity_only, capacity)
    }
    DetailRow(stringResource(R.string.camping_capacity), text)
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = CzSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Text(
            text = label,
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f),
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    CzCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            Text(
                text = title,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.titleSmall,
            )
            content()
        }
    }
}

@Composable
private fun AttendeeRow(attendee: CampingAttendee) {
    CzCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            CzAvatar(
                imageUrl = attendee.photoUrl,
                contentDescription = attendee.displayName,
                initials = attendee.displayName,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attendee.displayName,
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = attendee.church,
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            CzBadge(
                text = attendee.registrationStatus.label(),
                tone = attendee.registrationStatus.badgeTone(),
            )
        }
    }
}

@Composable
private fun organizerTypeLabel(type: OrganizerType): String = stringResource(
    when (type) {
        OrganizerType.Church -> R.string.organizer_type_church
        OrganizerType.Regional -> R.string.organizer_type_regional
        OrganizerType.International -> R.string.organizer_type_international
        OrganizerType.Custom -> R.string.organizer_type_custom
    },
)

@Preview(showBackground = true)
@Composable
private fun CampingDetailScreenPreview() {
    CampzoneTheme {
        CampingDetailScreen(
            state = CampingDetailUiState(
                isLoading = false,
                camping = previewCamping("summer-2026", "Summer Pathfinder Camp", 2026, 6)
                    .copy(guidelines = "Bring a sleeping bag and a flashlight."),
                canViewParticipantProfiles = true,
            ),
            onBack = {},
            onAttendeeSearchChange = {},
            onRetry = {},
        )
    }
}
