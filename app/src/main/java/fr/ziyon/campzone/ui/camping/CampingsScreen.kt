package fr.ziyon.campzone.ui.camping

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Festival
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzButton
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
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import java.util.Date
import kotlin.math.max

@Composable
fun CampingsRoute(
    onOpenCamping: (String) -> Unit,
    modifier: Modifier = Modifier,
    authenticatedUser: AuthenticatedUser? = null,
    onRegisterCamping: (String) -> Unit = {},
    onEditCamping: (String) -> Unit = {},
    onCreateCamping: () -> Unit = {},
    onReviewRegistrations: (() -> Unit)? = null,
    viewModel: CampingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val permissionUser = authenticatedUser?.let {
        PermissionUser(role = it.role, userId = it.uid, church = it.church)
    }
    val evaluator = remember { AppPermissionEvaluator() }
    fun Camping.permissionContext() = CampingPermissionContext(
        organizerLevelType = organizerLevel.type.wireValue,
        organizerLevelValue = organizerLevel.value,
        createdByUid = createdByUid,
    )
    val canCreate = evaluator.canCreateAnyCamping(permissionUser)
    val canReview = evaluator.can(permissionUser, AppPermission.ApproveRegistrations)
    CampingsScreen(
        state = state,
        onSearchChange = viewModel::updateSearch,
        onOpenCamping = onOpenCamping,
        currentUser = authenticatedUser,
        onRegisterCamping = onRegisterCamping,
        onEditCamping = onEditCamping,
        onRetry = viewModel::retry,
        showAdminInfo = { camping ->
            evaluator.canApproveRegistrations(user = permissionUser, camping = camping.permissionContext())
        },
        canEditCamping = { camping ->
            evaluator.canEditCamping(user = permissionUser, camping = camping.permissionContext())
        },
        onCreateCamping = if (canCreate) onCreateCamping else null,
        onReviewRegistrations = if (canReview) onReviewRegistrations else null,
        modifier = modifier,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CampingsScreen(
    state: CampingsUiState,
    onSearchChange: (String) -> Unit,
    onOpenCamping: (String) -> Unit,
    onRetry: () -> Unit,
    showAdminInfo: (Camping) -> Boolean,
    canEditCamping: (Camping) -> Boolean,
    modifier: Modifier = Modifier,
    currentUser: AuthenticatedUser? = null,
    onRegisterCamping: (String) -> Unit = {},
    onEditCamping: (String) -> Unit = {},
    onCreateCamping: (() -> Unit)? = null,
    onReviewRegistrations: (() -> Unit)? = null,
) {
    val showAdminCard = onCreateCamping != null || onReviewRegistrations != null
    var sheetCamping by remember { mutableStateOf<Camping?>(null) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
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
                    item(key = "section-${section.id}") {
                        MonthSection(
                            section = section,
                            currentUser = currentUser,
                            onOpenCamping = onOpenCamping,
                            onRequestEventSheet = { sheetCamping = it },
                            showAdminInfo = showAdminInfo,
                        )
                    }
                }
            }
        }
    }

    sheetCamping?.let { camping ->
        ModalBottomSheet(
            onDismissRequest = { sheetCamping = null },
            sheetState = bottomSheetState,
            containerColor = MaterialTheme.czColors.background,
        ) {
            CampingEventSheet(
                camping = camping,
                canEdit = canEditCamping(camping),
                onDismiss = { sheetCamping = null },
                onRegister = {
                    sheetCamping = null
                    onRegisterCamping(camping.id)
                },
                onEdit = {
                    sheetCamping = null
                    onEditCamping(camping.id)
                },
            )
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
                    color = MaterialTheme.czColors.accent,
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
            tint = MaterialTheme.czColors.accent,
        )
        Text(
            text = title.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MonthSection(
    section: CampingMonthSection,
    currentUser: AuthenticatedUser?,
    onOpenCamping: (String) -> Unit,
    onRequestEventSheet: (Camping) -> Unit,
    showAdminInfo: (Camping) -> Boolean,
) {
    val listState = rememberLazyListState()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        MonthSectionHeader(section.title)
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalBleed(CzSpacing.lg),
            contentPadding = PaddingValues(horizontal = CzSpacing.base),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
        ) {
            items(section.campings, key = { it.id }) { camping ->
                CampingCard(
                    camping = camping,
                    onClick = {
                        if (camping.isApprovedParticipant(currentUser?.uid)) {
                            onOpenCamping(camping.id)
                        } else {
                            onRequestEventSheet(camping)
                        }
                    },
                    showAdminInfo = showAdminInfo(camping),
                    modifier = Modifier.fillParentMaxWidth(),
                )
            }
        }
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
    val isDarkMode = isSystemInDarkTheme()
    val cardColor = if (isDarkMode) MaterialTheme.czColors.surface else Color.White
    val fillBrush = campingFillBrush(fillRatio)

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.lg)),
        shape = RoundedCornerShape(CzRadius.lg),
        color = cardColor,
        border = androidx.compose.foundation.BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.czColors.divider.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            CampingCardBanner(camping = camping)
            CampingCardContent(
                camping = camping,
                fillRatio = fillRatio,
                fillBrush = fillBrush,
                showAdminInfo = showAdminInfo,
                modifier = Modifier.padding(CzSpacing.lg),
            )
        }
    }
}

@Composable
private fun CampingCardBanner(
    camping: Camping,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp),
    ) {
        if (!camping.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = camping.logoUrl,
                contentDescription = stringResource(R.string.camping_logo_content_description, camping.title),
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            CampingImageFallback(
                iconSize = 32.dp,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.46f to Color.Black.copy(alpha = 0.18f),
                            1f to Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
                ),
        )
        CampingStatusPill(
            status = camping.effectiveRegistrationStatus,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(CzSpacing.sm),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = CzSpacing.sm, vertical = CzSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.czColors.accent,
            )
            Text(
                text = camping.location,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    shadow = campingImageTextShadow(),
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CampingCardContent(
    camping: Camping,
    fillRatio: Float,
    fillBrush: Brush,
    showAdminInfo: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Text(
            text = camping.title,
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            CampingInfoChipRow(
                icon = Icons.Filled.CalendarMonth,
                iconTint = MaterialTheme.czColors.accent,
            ) {
                Text(
                    text = campingDateRange(camping.startDate, camping.endDate),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("•", color = MaterialTheme.czColors.textSecondary.copy(alpha = 0.4f))
                Text(
                    text = campingDurationText(camping.startDate, camping.endDate),
                    color = MaterialTheme.czColors.accent,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.czColors.accent.copy(alpha = 0.12f))
                        .padding(horizontal = CzSpacing.xs, vertical = 2.dp),
                )
            }
            CampingInfoChipRow(
                icon = camping.organizerLevel.type.icon(),
                iconTint = MaterialTheme.czColors.amber,
            ) {
                Text(
                    text = camping.organizerLevel.value,
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.czColors.divider, thickness = 1.dp)
        CampingCapacityView(
            camping = camping,
            fillRatio = fillRatio,
            fillBrush = fillBrush,
            showAdminInfo = showAdminInfo,
        )
    }
}

@Composable
private fun CampingInfoChipRow(
    icon: ImageVector,
    iconTint: Color,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(iconTint.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = iconTint,
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            content = content,
        )
    }
}

@Composable
private fun CampingCapacityView(
    camping: Camping,
    fillRatio: Float,
    fillBrush: Brush,
    showAdminInfo: Boolean,
) {
    camping.participantCapacity?.takeIf { it > 0 }?.let { capacity ->
        Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.czColors.divider),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = fillRatio)
                        .clip(CircleShape)
                        .background(fillBrush),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            ) {
                Icon(
                    imageVector = Icons.Filled.Groups,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.czColors.textSecondary,
                )
                Text(
                    text = stringResource(R.string.camping_capacity_short, camping.participantCount, capacity),
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = stringResource(R.string.camping_registered_suffix),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.camping_capacity_percent, (fillRatio * 100).toInt()),
                    color = MaterialTheme.czColors.accent,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                )
                if (showAdminInfo && camping.pendingAttendees.isNotEmpty()) {
                    PendingBadge(camping.pendingAttendees.size)
                }
            }
        }
    } ?: Row(
        modifier = Modifier.fillMaxWidth(),
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
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
        )
        Spacer(modifier = Modifier.weight(1f))
        if (showAdminInfo && camping.pendingAttendees.isNotEmpty()) {
            PendingBadge(camping.pendingAttendees.size)
        }
    }
}

@Composable
private fun CampingEventSheet(
    camping: Camping,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onRegister: () -> Unit,
    onEdit: () -> Unit,
) {
    val fillRatio = remember(camping.participantCount, camping.participantCapacity) {
        val capacity = camping.participantCapacity ?: 0
        if (capacity <= 0) 0f
        else (camping.participantCount.toFloat() / capacity.toFloat()).coerceAtMost(1f)
    }
    val fillBrush = campingFillBrush(fillRatio)
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CzSpacing.lg)
                .padding(bottom = CzSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
        ) {
            CampingEventToolbar(
                camping = camping,
                canEdit = canEdit,
                onDismiss = onDismiss,
                onEdit = onEdit,
            )
            CampingEventCover(camping = camping)
            if (camping.description.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                    Text(
                        text = stringResource(R.string.camping_event_about_title).uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.czColors.textPrimary,
                    )
                    CampingMarkdownText(
                        text = camping.description,
                        textColor = MaterialTheme.czColors.textSecondary.toArgb(),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                    )
                }
            }
            CampingEventInfoCard(camping = camping)
            if ((camping.participantCapacity ?: 0) > 0) {
                CampingEventCapacity(
                    camping = camping,
                    fillRatio = fillRatio,
                    fillBrush = fillBrush,
                )
            }
        }
        Surface(
            color = MaterialTheme.czColors.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CzButton(
                text = stringResource(R.string.camping_event_secure_place),
                onClick = onRegister,
                enabled = camping.acceptsRegistrations,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.md),
            )
        }
    }
}

@Composable
private fun CampingEventToolbar(
    camping: Camping,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .size(CzSpacing.minTouchTarget)
                .clip(CircleShape)
                .background(MaterialTheme.czColors.surface),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.common_close),
                tint = MaterialTheme.czColors.error,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.camping_event_details_title),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.czColors.textPrimary,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (canEdit) {
            TextButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(CzSpacing.xs))
                Text(stringResource(R.string.common_edit))
            }
        } else {
            StatusBadge(status = camping.effectiveRegistrationStatus)
        }
    }
}

@Composable
private fun CampingEventCover(
    camping: Camping,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(CzRadius.lg)),
    ) {
        if (!camping.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = camping.logoUrl,
                contentDescription = stringResource(R.string.camping_logo_content_description, camping.title),
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            CampingImageFallback(
                iconSize = 52.dp,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.55f to Color.Black.copy(alpha = 0.10f),
                            1f to Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
                ),
        )
        Text(
            text = camping.title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                shadow = campingImageTextShadow(),
            ),
            color = Color.White,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(CzSpacing.lg),
        )
    }
}

@Composable
private fun CampingEventInfoCard(camping: Camping) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.lg))
            .background(MaterialTheme.czColors.surface)
            .padding(CzSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        CampingEventInfoRow(
            icon = Icons.Filled.CalendarMonth,
            title = stringResource(R.string.camping_event_date_period),
        ) {
            Text(
                text = campingDateRange(camping.startDate, camping.endDate),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.czColors.textSecondary,
            )
            Text("•", color = MaterialTheme.czColors.textSecondary.copy(alpha = 0.4f))
            Text(
                text = campingDurationText(camping.startDate, camping.endDate),
                color = MaterialTheme.czColors.ember,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.czColors.amber.copy(alpha = 0.15f))
                    .padding(horizontal = CzSpacing.xs, vertical = 2.dp),
            )
        }
        CampingEventInfoRow(
            icon = Icons.Filled.LocationOn,
            title = stringResource(R.string.camping_event_location),
        ) {
            Text(
                text = camping.location,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.czColors.textSecondary,
            )
        }
    }
}

@Composable
private fun CampingEventInfoRow(
    icon: ImageVector,
    title: String,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.czColors.ember.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.czColors.ember,
                modifier = Modifier.size(15.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.czColors.textPrimary,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                content = content,
            )
        }
    }
}

@Composable
private fun CampingEventCapacity(
    camping: Camping,
    fillRatio: Float,
    fillBrush: Brush,
) {
    val capacity = camping.participantCapacity ?: return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.camping_event_current_occupancy),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.czColors.textPrimary,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.czColors.divider),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fillRatio)
                    .clip(CircleShape)
                    .background(fillBrush),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            Icon(
                imageVector = Icons.Filled.Groups,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.czColors.textSecondary,
            )
            Text(
                text = stringResource(R.string.camping_capacity_short, camping.participantCount, capacity),
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = stringResource(R.string.camping_event_spots_taken),
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.camping_capacity_percent, (fillRatio * 100).toInt()),
                color = MaterialTheme.czColors.ember,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
            )
        }
    }
}

@Composable
private fun CampingImageFallback(
    iconSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.czColors.ember.copy(alpha = 0.35f),
                    MaterialTheme.czColors.amber.copy(alpha = 0.18f),
                ),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Terrain,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.czColors.ember.copy(alpha = 0.40f),
        )
    }
}

// MARK: - Status Badge (mirrors iOS StatusBadge)

@Composable
private fun StatusBadge(status: CampingRegistrationStatus) {
    val color = status.statusColor()
    Text(
        text = status.label(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = CzSpacing.sm, vertical = 3.dp),
    )
}

@Composable
private fun CampingStatusPill(
    status: CampingRegistrationStatus,
    modifier: Modifier = Modifier,
) {
    val color = status.statusColor()
    Text(
        text = status.label(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = Color.White,
        modifier = modifier
            .clip(CircleShape)
            .background(color)
            .border(1.dp, color.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = CzSpacing.sm, vertical = 4.dp),
    )
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
private fun campingFillBrush(ratio: Float): Brush {
    val colors = MaterialTheme.czColors
    return when {
        ratio < 0.5f -> Brush.horizontalGradient(
            listOf(colors.leaf, colors.leaf.copy(alpha = 0.85f)),
        )
        ratio < 0.75f -> Brush.horizontalGradient(
            listOf(colors.amber, colors.flame.copy(alpha = 0.80f)),
        )
        else -> Brush.horizontalGradient(
            listOf(colors.flame, colors.error),
        )
    }
}

@Composable
private fun campingFillAccentColor(ratio: Float): Color {
    val colors = MaterialTheme.czColors
    return when {
        ratio < 0.5f -> colors.leaf
        ratio < 0.75f -> colors.amber
        else -> colors.error
    }
}

private fun campingImageTextShadow(): Shadow = Shadow(
    color = Color.Black.copy(alpha = 0.42f),
    offset = Offset(x = 0f, y = 1f),
    blurRadius = 3f,
)

private fun Modifier.horizontalBleed(amount: Dp): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    } else {
        val bleedPx = amount.roundToPx()
        val expandedWidth = constraints.maxWidth + bleedPx * 2
        val placeable = measurable.measure(
            constraints.copy(
                minWidth = expandedWidth,
                maxWidth = expandedWidth,
            ),
        )
        layout(constraints.maxWidth, placeable.height) {
            placeable.placeRelative(-bleedPx, 0)
        }
    }
}

private fun Camping.isApprovedParticipant(userId: String?): Boolean {
    if (userId.isNullOrBlank()) return false
    return attendees.any { attendee ->
        attendee.registrationStatus == RegistrationApprovalStatus.Approved &&
            (attendee.userId == userId ||
                attendee.guardianId == userId ||
                (attendee.id == userId && !attendee.isChild))
    }
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
            canEditCamping = { true },
            onRegisterCamping = {},
            onEditCamping = {},
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
