package fr.ziyon.campzone.ui.camping

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Festival
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzCard
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTextField
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.permissions.AppPermission
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import java.util.Date
import kotlin.math.max

@Composable
fun CampingsRoute(
    onOpenCamping: (String) -> Unit,
    modifier: Modifier = Modifier,
    authenticatedUser: AuthenticatedUser? = null,
    onCreateCamping: () -> Unit = {},
    onReviewRegistrations: (() -> Unit)? = null,
    viewModel: CampingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val permissionUser = authenticatedUser?.let {
        PermissionUser(role = it.role, userId = it.uid, church = it.church)
    }
    val evaluator = remember { AppPermissionEvaluator() }
    val canCreate = evaluator.canCreateAnyCamping(permissionUser)
    val canReview = evaluator.can(permissionUser, AppPermission.ApproveRegistrations)
    CampingsScreen(
        state = state,
        onSearchChange = viewModel::updateSearch,
        onOpenCamping = onOpenCamping,
        onRetry = viewModel::retry,
        showAdminInfo = { camping ->
            evaluator.canApproveRegistrations(
                user = permissionUser,
                camping = CampingPermissionContext(
                    organizerLevelType = camping.organizerLevel.type.wireValue,
                    organizerLevelValue = camping.organizerLevel.value,
                ),
            )
        },
        onCreateCamping = if (canCreate) onCreateCamping else null,
        onReviewRegistrations = if (canReview) onReviewRegistrations else null,
        modifier = modifier,
    )
}

@Composable
fun CampingsScreen(
    state: CampingsUiState,
    onSearchChange: (String) -> Unit,
    onOpenCamping: (String) -> Unit,
    onRetry: () -> Unit,
    showAdminInfo: (Camping) -> Boolean,
    modifier: Modifier = Modifier,
    onCreateCamping: (() -> Unit)? = null,
    onReviewRegistrations: (() -> Unit)? = null,
) {
    val showAdminCard = onCreateCamping != null || onReviewRegistrations != null
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = CzSpacing.xl, end = CzSpacing.md, top = CzSpacing.xl, bottom = CzSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.nav_campings),
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        CzTextField(
            value = state.searchText,
            onValueChange = onSearchChange,
            label = stringResource(R.string.camping_search_label),
            placeholder = stringResource(R.string.camping_search_placeholder),
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CzSpacing.xl),
        )

        when (val phase = state.phase) {
            CampingsPhase.Loading -> CzLoadingView(
                modifier = Modifier.fillMaxSize(),
                message = stringResource(R.string.camping_loading),
            )

            is CampingsPhase.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CzErrorState(
                    title = stringResource(R.string.camping_error_title),
                    message = phase.message,
                    onRetry = onRetry,
                    retryLabel = stringResource(R.string.common_retry),
                )
            }

            is CampingsPhase.Empty -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                if (phase.isSearchResult) {
                    CzEmptyState(
                        title = stringResource(R.string.camping_empty_search_title, phase.query),
                        message = stringResource(R.string.camping_empty_search_message),
                    )
                } else {
                    CzEmptyState(
                        title = stringResource(R.string.camping_empty_title),
                        message = stringResource(R.string.camping_empty_message),
                    )
                }
            }

            is CampingsPhase.Loaded -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = CzSpacing.xl,
                    end = CzSpacing.xl,
                    top = CzSpacing.base,
                    bottom = CzSpacing.xxxl,
                ),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            ) {
                if (showAdminCard) {
                    item(key = "admin-card") {
                        AdminActionsCard(
                            onCreateCamping = onCreateCamping,
                            onReviewRegistrations = onReviewRegistrations,
                        )
                    }
                }
                phase.sections.forEach { section ->
                    item(key = "header-${section.id}") {
                        MonthSectionHeader(section.title)
                    }
                    items(section.campings, key = { it.id }) { camping ->
                        CampingCard(
                            camping = camping,
                            onClick = { onOpenCamping(camping.id) },
                            showAdminInfo = showAdminInfo(camping),
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Admin Actions Card

@Composable
private fun AdminActionsCard(
    onCreateCamping: (() -> Unit)?,
    onReviewRegistrations: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(CzRadius.lg),
        color = MaterialTheme.czColors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            if (onCreateCamping != null) {
                AdminActionRow(
                    icon = Icons.Filled.AddCircle,
                    label = stringResource(R.string.camping_editor_create_title),
                    color = MaterialTheme.czColors.ember,
                    onClick = onCreateCamping,
                )
            }
            if (onCreateCamping != null && onReviewRegistrations != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 44.dp),
                    color = MaterialTheme.czColors.divider,
                    thickness = 0.5.dp,
                )
            }
            if (onReviewRegistrations != null) {
                AdminActionRow(
                    icon = Icons.Filled.HowToReg,
                    label = stringResource(R.string.camping_review_registrations),
                    color = MaterialTheme.czColors.amber,
                    onClick = onReviewRegistrations,
                )
            }
        }
    }
}

@Composable
private fun AdminActionRow(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CzSpacing.md, vertical = CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.czColors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.czColors.textTertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

// MARK: - Month section header

@Composable
private fun MonthSectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = CzSpacing.sm, bottom = CzSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Icon(
            imageVector = Icons.Filled.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.czColors.ember,
        )
        Text(
            text = title.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// MARK: - Camping Card

@Composable
private fun CampingCard(
    camping: Camping,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showAdminInfo: Boolean = false,
) {
    val fillRatio = remember(camping.participantCount, camping.participantCapacity) {
        val capacity = camping.participantCapacity ?: 0
        if (capacity <= 0) 0f
        else (camping.participantCount.toFloat() / capacity.toFloat()).coerceAtMost(1f)
    }

    val fillColor = when {
        fillRatio < 0.5f -> MaterialTheme.czColors.success
        fillRatio < 0.8f -> MaterialTheme.czColors.amber
        else -> MaterialTheme.czColors.error
    }

    val statusColor = camping.registrationStatus.statusColor()

    CzCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
        contentDescription = camping.title,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status accent bar: 2dp, filled with registration status color
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .heightIn(min = 160.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(statusColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(CzSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            ) {
                // Header: logo + title + status badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CampingListLogoBadge(camping)
                    Spacer(modifier = Modifier.width(CzSpacing.sm))
                    Text(
                        text = camping.title,
                        color = MaterialTheme.czColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(CzSpacing.sm))
                    StatusBadge(status = camping.registrationStatus)
                }

                // Organizer with type-specific icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                ) {
                    Icon(
                        imageVector = camping.organizerLevel.type.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.czColors.textSecondary,
                    )
                    Text(
                        text = camping.organizerLevel.value,
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }

                HorizontalDivider(color = MaterialTheme.czColors.divider, thickness = 1.dp)

                // Date + duration
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.czColors.textSecondary,
                    )
                    Text(
                        text = campingDateRange(camping.startDate, camping.endDate),
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("·", color = MaterialTheme.czColors.divider)
                    Text(
                        text = campingDurationText(camping.startDate, camping.endDate),
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                }

                // Location
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.czColors.ember.copy(alpha = 0.7f),
                    )
                    Text(
                        text = camping.location,
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }

                // Capacity bar or participant count
                camping.participantCapacity?.takeIf { it > 0 }?.let { capacity ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Capsule fill bar (matches iOS ZStack Capsule design)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.czColors.divider),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = fillRatio)
                                    .clip(CircleShape)
                                    .background(fillColor),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.camping_capacity_value, camping.participantCount, capacity),
                                color = MaterialTheme.czColors.textSecondary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (showAdminInfo && camping.pendingAttendees.isNotEmpty()) {
                                PendingBadge(camping.pendingAttendees.size)
                            }
                        }
                    }
                } ?: run {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Groups,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.czColors.textSecondary,
                            )
                            Text(
                                text = stringResource(R.string.camping_registered_count, camping.participantCount),
                                color = MaterialTheme.czColors.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (showAdminInfo && camping.pendingAttendees.isNotEmpty()) {
                            PendingBadge(camping.pendingAttendees.size)
                        }
                    }
                }
            }

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.czColors.textTertiary,
                modifier = Modifier.padding(end = CzSpacing.md),
            )
        }
    }
}

// MARK: - Status Badge (mirrors iOS StatusBadge)

@Composable
private fun StatusBadge(status: CampingRegistrationStatus) {
    val color = status.statusColor()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = CzSpacing.sm, vertical = 3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, shape = CircleShape),
        )
        Text(
            text = status.label(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = color,
        )
    }
}

// MARK: - Logo badge

@Composable
private fun CampingListLogoBadge(camping: Camping) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(CzRadius.md))
            .background(MaterialTheme.czColors.secondary.copy(alpha = 0.14f))
            .border(1.dp, MaterialTheme.czColors.divider, RoundedCornerShape(CzRadius.md)),
        contentAlignment = Alignment.Center,
    ) {
        if (!camping.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = camping.logoUrl,
                contentDescription = camping.title,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Festival,
                contentDescription = null,
                tint = MaterialTheme.czColors.textSecondary,
            )
        }
    }
}

// MARK: - Pending badge

@Composable
private fun PendingBadge(count: Int) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.czColors.amber.copy(alpha = 0.12f))
            .padding(horizontal = CzSpacing.xs, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Schedule,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.czColors.amber,
        )
        Text(
            text = stringResource(R.string.camping_pending_registration_count, count),
            color = MaterialTheme.czColors.amber,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// MARK: - Helpers

@Composable
private fun CampingRegistrationStatus.statusColor(): Color = when (this) {
    CampingRegistrationStatus.Open -> MaterialTheme.czColors.success
    CampingRegistrationStatus.Closed -> MaterialTheme.czColors.amber
    CampingRegistrationStatus.Cancelled -> MaterialTheme.czColors.error
}

private fun OrganizerType.icon(): ImageVector = when (this) {
    OrganizerType.Church -> Icons.Filled.AccountBalance
    OrganizerType.Regional -> Icons.Filled.Map
    OrganizerType.International -> Icons.Filled.Language
    OrganizerType.Custom -> Icons.Filled.Person
}

@Composable
private fun campingDurationText(start: Date, end: Date): String {
    val days = max(
        1,
        java.util.concurrent.TimeUnit.MILLISECONDS.toDays(end.time - start.time).toInt(),
    )
    return pluralStringResource(R.plurals.camping_duration_days, days, days)
}

// MARK: - Preview

@Preview(showBackground = true)
@Composable
private fun CampingsScreenPreview() {
    CampzoneTheme {
        CampingsScreen(
            state = CampingsUiState(
                phase = CampingsPhase.Loaded(
                    CampingsViewModel.groupedSections(
                        listOf(
                            previewCamping("summer-2026", "Summer Pathfinder Camp", 2026, 6),
                            previewCamping("fall-2026", "Fall Leaders Retreat", 2026, 9),
                        ),
                    ),
                ),
            ),
            onSearchChange = {},
            onOpenCamping = {},
            onRetry = {},
            showAdminInfo = { true },
            onCreateCamping = {},
            onReviewRegistrations = {},
        )
    }
}

internal fun previewCamping(id: String, title: String, year: Int, monthIndex: Int): Camping {
    val cal = java.util.Calendar.getInstance().apply { clear(); set(year, monthIndex, 18) }
    val start = cal.time
    cal.add(java.util.Calendar.DAY_OF_MONTH, 6)
    return Camping(
        id = id,
        title = title,
        description = "A week of worship, games, service, music, and lake activities.",
        startDate = start,
        endDate = cal.time,
        organizerLevel = OrganizerLevel(OrganizerType.Regional, "South"),
        location = "Lake Annecy",
        registrationStatus = CampingRegistrationStatus.Open,
        participantCapacity = 120,
    )
}
