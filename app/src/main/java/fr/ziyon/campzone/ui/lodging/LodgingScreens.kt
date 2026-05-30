package fr.ziyon.campzone.ui.lodging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Cabin
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Festival
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzAvatar
import fr.ziyon.campzone.core.designsystem.CzAvatarSize
import fr.ziyon.campzone.core.designsystem.CzBadge
import fr.ziyon.campzone.core.designsystem.CzBadgeTone
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
import fr.ziyon.campzone.data.model.LodgingGenderPolicy
import fr.ziyon.campzone.data.model.LodgingKind
import fr.ziyon.campzone.data.model.LodgingUnit

@Composable
fun LodgingRoute(
    campingId: String,
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LodgingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(campingId, authenticatedUser.uid) {
        viewModel.load(campingId, authenticatedUser)
    }

    LodgingScreen(
        state = state,
        onBack = onBack,
        onRetry = { viewModel.retry(campingId, authenticatedUser) },
        onSave = viewModel::saveUnit,
        onDelete = viewModel::deleteUnit,
        onSetOccupants = viewModel::setOccupants,
        onFilter = viewModel::setFilter,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LodgingScreen(
    state: LodgingUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSave: (LodgingForm) -> Unit,
    onDelete: (LodgingUnit) -> Unit,
    onSetOccupants: (String, List<String>) -> Unit,
    onFilter: (LodgingGenderPolicy?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheet by remember { mutableStateOf<LodgingSheet>(LodgingSheet.None) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.czColors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.lodging_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (state is LodgingUiState.Ready) {
                        IconButton(onClick = { sheet = LodgingSheet.Editor(LodgingForm()) }) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = stringResource(R.string.lodging_add_unit),
                            )
                        }
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                LodgingUiState.Loading -> CzLoadingView(
                    message = stringResource(R.string.lodging_loading),
                    modifier = Modifier.fillMaxSize(),
                )

                LodgingUiState.Restricted -> CzEmptyState(
                    title = stringResource(R.string.lodging_restricted_title),
                    message = stringResource(R.string.lodging_restricted_message),
                    modifier = Modifier.fillMaxSize().padding(CzSpacing.lg),
                )

                is LodgingUiState.Error -> CzErrorState(
                    title = stringResource(R.string.lodging_error_title),
                    message = state.message,
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize().padding(CzSpacing.lg),
                )

                is LodgingUiState.Ready -> LodgingReadyContent(
                    state = state,
                    onFilter = onFilter,
                    onAddUnit = { sheet = LodgingSheet.Editor(LodgingForm()) },
                    onEditUnit = { sheet = LodgingSheet.Editor(LodgingForm.of(it)) },
                    onDeleteUnit = onDelete,
                    onAddOccupant = { sheet = LodgingSheet.AssignToUnit(it.id) },
                    onRemoveOccupant = { unit, id -> onSetOccupants(unit.id, unit.occupantIds - id) },
                    onAssignPerson = { sheet = LodgingSheet.AssignPerson(it.id) },
                )
            }
        }
    }

    if (state is LodgingUiState.Ready) {
        when (val current = sheet) {
            LodgingSheet.None -> Unit

            is LodgingSheet.Editor -> LodgingEditorSheet(
                initialForm = current.form,
                isSaving = state.isSaving,
                onSave = {
                    onSave(it)
                    sheet = LodgingSheet.None
                },
                onDismiss = { sheet = LodgingSheet.None },
            )

            is LodgingSheet.AssignToUnit -> {
                val unit = state.units.firstOrNull { it.id == current.unitId }
                if (unit == null) {
                    sheet = LodgingSheet.None
                } else {
                    val eligible = state.attendees.filter {
                        it.id !in state.assignedIds && unit.genderPolicy.accepts(it.gender)
                    }
                    AssignToUnitSheet(
                        unit = unit,
                        candidates = eligible,
                        onAssign = { onSetOccupants(unit.id, unit.occupantIds + it) },
                        onDismiss = { sheet = LodgingSheet.None },
                    )
                }
            }

            is LodgingSheet.AssignPerson -> {
                val person = state.attendeesById[current.attendeeId]
                if (person == null) {
                    sheet = LodgingSheet.None
                } else {
                    val eligible = state.units.filter {
                        !it.isFull && it.genderPolicy.accepts(person.gender)
                    }
                    AssignPersonSheet(
                        person = person,
                        units = eligible,
                        onAssign = { onSetOccupants(it.id, it.occupantIds + person.id); sheet = LodgingSheet.None },
                        onDismiss = { sheet = LodgingSheet.None },
                    )
                }
            }
        }
    }
}

@Composable
private fun LodgingReadyContent(
    state: LodgingUiState.Ready,
    onFilter: (LodgingGenderPolicy?) -> Unit,
    onAddUnit: () -> Unit,
    onEditUnit: (LodgingUnit) -> Unit,
    onDeleteUnit: (LodgingUnit) -> Unit,
    onAddOccupant: (LodgingUnit) -> Unit,
    onRemoveOccupant: (LodgingUnit, String) -> Unit,
    onAssignPerson: (CampingAttendee) -> Unit,
) {
    val unassigned = remember(state.units, state.attendees) {
        val lodged = state.assignedIds
        state.attendees.filter { it.id !in lodged }
    }

    if (state.units.isEmpty()) {
        CzEmptyState(
            title = stringResource(R.string.lodging_empty_title),
            message = stringResource(R.string.lodging_empty_message),
            icon = {
                Icon(
                    Icons.Filled.Cabin,
                    contentDescription = null,
                    tint = MaterialTheme.czColors.ember,
                    modifier = Modifier.size(42.dp),
                )
            },
            action = {
                CzButton(
                    text = stringResource(R.string.lodging_add_unit),
                    onClick = onAddUnit,
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            },
            modifier = Modifier.fillMaxSize().padding(CzSpacing.lg),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
    ) {
        item("summary") { LodgingSummaryCard(state.units, unassigned.size) }
        item("filter") { GenderPolicyFilterRow(selected = state.filter, onSelect = onFilter) }

        items(state.filteredUnits, key = { it.id }) { unit ->
            LodgingUnitCard(
                unit = unit,
                occupants = unit.occupantIds.mapNotNull { state.attendeesById[it] },
                hasAssignable = unassigned.any { unit.genderPolicy.accepts(it.gender) } && !unit.isFull,
                onEdit = { onEditUnit(unit) },
                onDelete = { onDeleteUnit(unit) },
                onAddOccupant = { onAddOccupant(unit) },
                onRemoveOccupant = { onRemoveOccupant(unit, it) },
            )
        }

        if (unassigned.isNotEmpty()) {
            item("unassigned-header") {
                SectionLabel(
                    title = stringResource(R.string.lodging_needs_bed),
                    icon = Icons.Filled.PersonSearch,
                )
            }
            items(unassigned, key = { "u-${it.id}" }) { person ->
                UnassignedPersonRow(person = person, onAssign = { onAssignPerson(person) })
            }
        }
    }
}

@Composable
private fun LodgingSummaryCard(units: List<LodgingUnit>, unassignedCount: Int) {
    val beds = units.sumOf { it.capacity }
    val filled = units.sumOf { it.occupancy }
    Surface(
        color = MaterialTheme.czColors.surface,
        shape = RoundedCornerShape(CzRadius.xl),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.lg),
        ) {
            SummaryStat(value = units.size.toString(), label = stringResource(R.string.lodging_stat_units))
            SummaryStat(value = "$filled/$beds", label = stringResource(R.string.lodging_stat_beds))
            SummaryStat(value = unassignedCount.toString(), label = stringResource(R.string.lodging_stat_unassigned))
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String) {
    Column(
        modifier = Modifier.fillMaxWidth(1f / 3f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.czColors.textPrimary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.czColors.textSecondary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenderPolicyFilterRow(
    selected: LodgingGenderPolicy?,
    onSelect: (LodgingGenderPolicy?) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.lodging_filter_all)) },
        )
        LodgingGenderPolicy.entries.forEach { policy ->
            FilterChip(
                selected = selected == policy,
                onClick = { onSelect(policy) },
                label = { Text(policy.label()) },
            )
        }
    }
}

@Composable
private fun LodgingUnitCard(
    unit: LodgingUnit,
    occupants: List<CampingAttendee>,
    hasAssignable: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddOccupant: () -> Unit,
    onRemoveOccupant: (String) -> Unit,
) {
    val colors = MaterialTheme.czColors
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(CzRadius.xl),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                Icon(unit.kind.icon(), contentDescription = null, tint = colors.ember, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = unit.name.ifBlank { stringResource(R.string.lodging_unnamed_unit) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${unit.kind.label()} · ${stringResource(R.string.lodging_occupancy, unit.occupancy, unit.capacity)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.common_edit), tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.common_delete), tint = colors.error, modifier = Modifier.size(20.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                CzBadge(text = unit.genderPolicy.label(), tone = unit.genderPolicy.tone())
                if (unit.isFull) {
                    CzBadge(text = stringResource(R.string.lodging_full), tone = CzBadgeTone.Warning)
                } else {
                    CzBadge(
                        text = stringResource(R.string.lodging_spots_left, unit.availableSpots),
                        tone = CzBadgeTone.Success,
                    )
                }
            }

            if (unit.notes.isNotBlank()) {
                Text(
                    text = unit.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }

            occupants.forEach { occupant ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                    CzAvatar(
                        imageUrl = occupant.photoUrl,
                        contentDescription = occupant.displayName,
                        initials = occupant.displayName.firstOrNull()?.toString(),
                        size = CzAvatarSize.Small,
                    )
                    Text(
                        text = occupant.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { onRemoveOccupant(occupant.id) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.lodging_remove_occupant), tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (hasAssignable) {
                CzButton(
                    text = stringResource(R.string.lodging_add_occupant),
                    onClick = onAddOccupant,
                    variant = CzButtonVariant.Outline,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
    }
}

@Composable
private fun UnassignedPersonRow(person: CampingAttendee, onAssign: () -> Unit) {
    val colors = MaterialTheme.czColors
    Surface(color = colors.surface, shape = RoundedCornerShape(CzRadius.lg), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            CzAvatar(
                imageUrl = person.photoUrl,
                contentDescription = person.displayName,
                initials = person.displayName.firstOrNull()?.toString(),
                size = CzAvatarSize.Small,
            )
            Column(Modifier.weight(1f)) {
                Text(person.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (person.church.isNotBlank()) {
                    Text(person.church, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            CzButton(
                text = stringResource(R.string.lodging_assign),
                onClick = onAssign,
                variant = CzButtonVariant.Outline,
            )
        }
    }
}

@Composable
internal fun SectionLabel(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.czColors.ember, modifier = Modifier.size(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.czColors.textPrimary,
        )
    }
}

// MARK: - Kind / policy UI mapping

@Composable
internal fun LodgingKind.label(): String = stringResource(
    when (this) {
        LodgingKind.Tent -> R.string.lodging_kind_tent
        LodgingKind.Cabin -> R.string.lodging_kind_cabin
        LodgingKind.Room -> R.string.lodging_kind_room
        LodgingKind.Dorm -> R.string.lodging_kind_dorm
    },
)

internal fun LodgingKind.icon(): ImageVector = when (this) {
    LodgingKind.Tent -> Icons.Filled.Festival
    LodgingKind.Cabin -> Icons.Filled.Cabin
    LodgingKind.Room -> Icons.Filled.Hotel
    LodgingKind.Dorm -> Icons.Filled.Apartment
}

@Composable
internal fun LodgingGenderPolicy.label(): String = stringResource(
    when (this) {
        LodgingGenderPolicy.Any -> R.string.lodging_policy_any
        LodgingGenderPolicy.Male -> R.string.lodging_policy_male
        LodgingGenderPolicy.Female -> R.string.lodging_policy_female
        LodgingGenderPolicy.Family -> R.string.lodging_policy_family
    },
)

internal fun LodgingGenderPolicy.icon(): ImageVector = when (this) {
    LodgingGenderPolicy.Any -> Icons.Filled.Groups
    LodgingGenderPolicy.Male -> Icons.Filled.Male
    LodgingGenderPolicy.Female -> Icons.Filled.Female
    LodgingGenderPolicy.Family -> Icons.Filled.FamilyRestroom
}

internal fun LodgingGenderPolicy.tone(): CzBadgeTone = when (this) {
    LodgingGenderPolicy.Any -> CzBadgeTone.Neutral
    LodgingGenderPolicy.Male -> CzBadgeTone.Primary
    LodgingGenderPolicy.Female -> CzBadgeTone.Secondary
    LodgingGenderPolicy.Family -> CzBadgeTone.Success
}

internal sealed interface LodgingSheet {
    data object None : LodgingSheet
    data class Editor(val form: LodgingForm) : LodgingSheet
    data class AssignToUnit(val unitId: String) : LodgingSheet
    data class AssignPerson(val attendeeId: String) : LodgingSheet
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun LodgingScreenPreview() {
    CampzoneTheme {
        LodgingScreen(
            state = LodgingUiState.Ready(
                campingId = "camp-1",
                units = listOf(
                    LodgingUnit(id = "t1", campingId = "camp-1", name = "Tent Alpha", kind = LodgingKind.Tent, capacity = 4, genderPolicy = LodgingGenderPolicy.Male, occupantIds = listOf("a1")),
                    LodgingUnit(id = "c1", campingId = "camp-1", name = "Lakeview Cabin", kind = LodgingKind.Cabin, capacity = 6, genderPolicy = LodgingGenderPolicy.Family, notes = "Step-free access"),
                ),
                attendees = emptyList(),
            ),
            onBack = {},
            onRetry = {},
            onSave = {},
            onDelete = {},
            onSetOccupants = { _, _ -> },
            onFilter = {},
        )
    }
}
