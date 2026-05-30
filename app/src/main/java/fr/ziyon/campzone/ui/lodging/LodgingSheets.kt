package fr.ziyon.campzone.ui.lodging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzAvatar
import fr.ziyon.campzone.core.designsystem.CzAvatarSize
import fr.ziyon.campzone.core.designsystem.CzBadge
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTextField
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.LodgingGenderPolicy
import fr.ziyon.campzone.data.model.LodgingKind
import fr.ziyon.campzone.data.model.LodgingUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LodgingEditorSheet(
    initialForm: LodgingForm,
    isSaving: Boolean,
    onSave: (LodgingForm) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var form by remember(initialForm.id) { mutableStateOf(initialForm) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.czColors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CzSpacing.lg)
                .padding(bottom = CzSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.lg),
        ) {
            Text(
                text = stringResource(
                    if (form.id == null) R.string.lodging_new_title else R.string.lodging_edit_title,
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.czColors.textPrimary,
            )

            CzTextField(
                value = form.name,
                onValueChange = { form = form.copy(name = it) },
                label = stringResource(R.string.lodging_field_name),
                placeholder = stringResource(R.string.lodging_field_name_hint),
                modifier = Modifier.fillMaxWidth(),
            )

            FieldLabel(stringResource(R.string.lodging_field_type))
            ChoiceChips(
                options = LodgingKind.entries,
                selected = form.kind,
                label = { it.label() },
                onSelect = { form = form.copy(kind = it) },
            )

            CzTextField(
                value = form.capacityText,
                onValueChange = { value -> form = form.copy(capacityText = value.filter { it.isDigit() }.take(3)) },
                label = stringResource(R.string.lodging_field_capacity),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            FieldLabel(stringResource(R.string.lodging_field_policy))
            ChoiceChips(
                options = LodgingGenderPolicy.entries,
                selected = form.genderPolicy,
                label = { it.label() },
                onSelect = { form = form.copy(genderPolicy = it) },
            )
            Text(
                text = stringResource(R.string.lodging_policy_footer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.czColors.textSecondary,
            )

            CzTextField(
                value = form.notes,
                onValueChange = { form = form.copy(notes = it) },
                label = stringResource(R.string.lodging_field_notes),
                placeholder = stringResource(R.string.lodging_field_notes_hint),
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
                CzButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = onDismiss,
                    variant = CzButtonVariant.Outline,
                    modifier = Modifier.weight(1f),
                )
                CzButton(
                    text = stringResource(R.string.common_save),
                    onClick = { onSave(form) },
                    enabled = form.isValid && !isSaving,
                    loading = isSaving,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AssignToUnitSheet(
    unit: LodgingUnit,
    candidates: List<CampingAttendee>,
    onAssign: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.czColors.background,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CzSpacing.lg).padding(bottom = CzSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.lodging_assign_to, unit.name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.czColors.textPrimary,
            )
            if (candidates.isEmpty()) {
                Text(
                    text = stringResource(R.string.lodging_no_candidates),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.czColors.textSecondary,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                    items(candidates, key = { it.id }) { person ->
                        PersonPickRow(
                            title = person.displayName,
                            subtitle = person.church,
                            photoUrl = person.photoUrl,
                            onClick = { onAssign(person.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AssignPersonSheet(
    person: CampingAttendee,
    units: List<LodgingUnit>,
    onAssign: (LodgingUnit) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.czColors.background,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CzSpacing.lg).padding(bottom = CzSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.lodging_assign_person, person.displayName),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.czColors.textPrimary,
            )
            if (units.isEmpty()) {
                Text(
                    text = stringResource(R.string.lodging_no_units),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.czColors.textSecondary,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                    items(units, key = { it.id }) { unit ->
                        UnitPickRow(unit = unit, onClick = { onAssign(unit) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.czColors.textSecondary,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

@Composable
private fun PersonPickRow(title: String, subtitle: String, photoUrl: String?, onClick: () -> Unit) {
    val colors = MaterialTheme.czColors
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(CzSpacing.md),
        modifier = Modifier.fillMaxWidth().selectable(selected = false, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            CzAvatar(
                imageUrl = photoUrl,
                contentDescription = title,
                initials = title.firstOrNull()?.toString(),
                size = CzAvatarSize.Small,
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun UnitPickRow(unit: LodgingUnit, onClick: () -> Unit) {
    val colors = MaterialTheme.czColors
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(CzSpacing.md),
        modifier = Modifier.fillMaxWidth().selectable(selected = false, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Icon(unit.kind.icon(), contentDescription = null, tint = colors.ember, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(unit.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.lodging_occupancy, unit.occupancy, unit.capacity),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
            CzBadge(text = unit.genderPolicy.label(), tone = unit.genderPolicy.tone())
        }
    }
}
