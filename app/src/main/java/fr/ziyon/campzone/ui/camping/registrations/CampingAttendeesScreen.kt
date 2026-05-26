package fr.ziyon.campzone.ui.camping.registrations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.CampingAgeGroup
import fr.ziyon.campzone.data.model.CampingAttendee

@Composable
fun CampingAttendeesRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    onOpenProfile: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CampingAttendeesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(campingId, authenticatedUser) {
        viewModel.load(campingId, authenticatedUser)
    }
    CampingAttendeesScreen(
        state = state,
        onBack = onBack,
        onSearchChange = viewModel::updateSearch,
        onFilterChange = viewModel::updateFilters,
        onOpenProfile = onOpenProfile,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampingAttendeesScreen(
    state: CampingAttendeesUiState,
    onBack: () -> Unit,
    onSearchChange: (String) -> Unit,
    onFilterChange: (AttendeeFilters) -> Unit,
    onOpenProfile: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.czColors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.camping_attendees),
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.czColors.background,
                ),
                windowInsets = WindowInsets(),
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> CzLoadingView(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                message = stringResource(R.string.camping_loading),
            )

            state.errorMessage != null || state.camping == null -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CzErrorState(
                    title = stringResource(R.string.camping_error_title),
                    message = state.errorMessage,
                )
            }

            !state.canViewAttendees -> LockedAttendeesState(
                modifier = Modifier.padding(innerPadding),
            )

            else -> AttendeesContent(
                state = state,
                onSearchChange = onSearchChange,
                onFilterChange = onFilterChange,
                onOpenProfile = onOpenProfile,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun AttendeesContent(
    state: CampingAttendeesUiState,
    onSearchChange: (String) -> Unit,
    onFilterChange: (AttendeeFilters) -> Unit,
    onOpenProfile: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val camping = state.camping ?: return
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
        item {
            AttendeeSummaryCard(state)
        }
        item {
            OutlinedTextField(
                value = state.searchText,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.camping_attendees_search)) },
                leadingIcon = { Icon(Icons.Filled.PersonSearch, contentDescription = null) },
                singleLine = true,
            )
        }
        item {
            FilterChips(
                filters = state.filters,
                onFilterChange = onFilterChange,
            )
        }
        item {
            AttendeeListCard(
                attendees = state.visibleAttendees,
                canOpenProfile = state.canViewProfiles,
                campingId = camping.id,
                onOpenProfile = onOpenProfile,
            )
        }
    }
}

@Composable
private fun AttendeeSummaryCard(state: CampingAttendeesUiState) {
    val camping = state.camping ?: return
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.czColors.ember.copy(alpha = 0.25f),
                                MaterialTheme.czColors.amber.copy(alpha = 0.18f),
                            ),
                        ),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Groups,
                    contentDescription = null,
                    tint = MaterialTheme.czColors.ember,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = camping.title,
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (state.visibleAttendees.size == state.totalVisibleScopeCount) {
                        stringResource(R.string.camping_registered_count, state.totalVisibleScopeCount)
                    } else {
                        stringResource(
                            R.string.camping_attendees_showing_count,
                            state.visibleAttendees.size,
                            state.totalVisibleScopeCount,
                        )
                    },
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            camping.participantCapacity?.let { capacity ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${state.approvedCount}/$capacity",
                        color = MaterialTheme.czColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.camping_capacity),
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChips(
    filters: AttendeeFilters,
    onFilterChange: (AttendeeFilters) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        CampingAgeGroup.entries.forEach { group ->
            FilterChip(
                icon = Icons.Filled.Groups,
                label = group.label(),
                selected = filters.ageGroup == group,
                onClick = {
                    onFilterChange(
                        filters.copy(ageGroup = if (filters.ageGroup == group) null else group),
                    )
                },
            )
        }
        if (!filters.isEmpty) {
            FilterChip(
                icon = Icons.Filled.Close,
                label = stringResource(R.string.common_clear),
                selected = true,
                onClick = { onFilterChange(AttendeeFilters()) },
            )
        }
    }
}

@Composable
private fun FilterChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = if (selected) MaterialTheme.czColors.ember else MaterialTheme.czColors.textSecondary
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = color.copy(alpha = if (selected) 0.12f else 0.08f),
        contentColor = color,
        shape = RoundedCornerShape(CzRadius.full),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CzSpacing.sm, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AttendeeListCard(
    attendees: List<CampingAttendee>,
    canOpenProfile: Boolean,
    campingId: String,
    onOpenProfile: (String, String) -> Unit,
) {
    if (attendees.isEmpty()) {
        Surface(
            color = MaterialTheme.czColors.surface,
            shape = RoundedCornerShape(CzRadius.lg),
        ) {
            CzEmptyState(
                title = stringResource(R.string.camping_attendees_no_matches),
                message = stringResource(R.string.camping_attendees_no_matches_message),
                icon = {
                    Icon(
                        Icons.Filled.PersonSearch,
                        contentDescription = null,
                        tint = MaterialTheme.czColors.textSecondary,
                        modifier = Modifier.size(40.dp),
                    )
                },
            )
        }
        return
    }

    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.lg),
    ) {
        Column {
            attendees.forEachIndexed { index, attendee ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = canOpenProfile) {
                            onOpenProfile(campingId, attendee.id)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RegistrationAttendeeRow(attendee, modifier = Modifier.weight(1f))
                    if (canOpenProfile) {
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.czColors.textSecondary,
                            modifier = Modifier.padding(end = CzSpacing.md).size(18.dp),
                        )
                    }
                }
                if (index < attendees.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 64.dp),
                        color = MaterialTheme.czColors.divider,
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun LockedAttendeesState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CzEmptyState(
            title = stringResource(R.string.registration_review_restricted_title),
            message = stringResource(R.string.camping_attendees_locked_ios),
            icon = {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.czColors.textSecondary,
                    modifier = Modifier.size(40.dp),
                )
            },
        )
    }
}
