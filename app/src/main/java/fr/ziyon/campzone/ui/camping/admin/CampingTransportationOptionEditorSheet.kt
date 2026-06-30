package fr.ziyon.campzone.ui.camping.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTextField
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.model.CampingTransportationOption
import fr.ziyon.campzone.data.model.TransportationMode
import fr.ziyon.campzone.ui.camping.localizedDisplayName
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampingTransportationOptionEditorSheet(
    option: CampingTransportationOption?,
    onSave: (CampingTransportationOption) -> Unit,
    onDismiss: () -> Unit,
) {
    if (option == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        TransportationOptionEditorContent(option = option, onSave = onSave, onDismiss = onDismiss)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransportationOptionEditorContent(
    option: CampingTransportationOption,
    onSave: (CampingTransportationOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    var name by remember { mutableStateOf(option.name) }
    var mode by remember { mutableStateOf(option.mode) }
    var details by remember { mutableStateOf(option.details) }
    var requiresTicket by remember { mutableStateOf(option.requiresTicket) }
    var capacityText by remember { mutableStateOf(option.capacity?.toString() ?: "") }
    var feeText by remember {
        mutableStateOf(
            if ((option.feeCents ?: 0) > 0) "%.2f".format((option.feeCents ?: 0) / 100.0) else ""
        )
    }
    var currency by remember { mutableStateOf(option.currency.ifBlank { "EUR" }) }
    var modeMenuExpanded by remember { mutableStateOf(false) }

    val canSave = name.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CzSpacing.xl, vertical = CzSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.camping_editor_transport_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_close), tint = colors.textSecondary)
            }
        }

        EditorLabel(stringResource(R.string.camping_editor_section_option))
        CzTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.camping_editor_transport_name_hint),
            modifier = Modifier.fillMaxWidth(),
        )

        ExposedDropdownMenuBox(
            expanded = modeMenuExpanded,
            onExpandedChange = { modeMenuExpanded = it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            CzTextField(
                value = mode.localizedDisplayName(),
                onValueChange = {},
                readOnly = true,
                label = stringResource(R.string.camping_editor_transport_mode),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeMenuExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = modeMenuExpanded,
                onDismissRequest = { modeMenuExpanded = false },
            ) {
                TransportationMode.entries.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m.localizedDisplayName()) },
                        onClick = {
                            mode = m
                            modeMenuExpanded = false
                        },
                    )
                }
            }
        }

        CzTextField(
            value = details,
            onValueChange = { details = it },
            label = stringResource(R.string.camping_editor_transport_details_hint),
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.camping_editor_transport_ticket),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = requiresTicket,
                onCheckedChange = { requiresTicket = it },
                colors = SwitchDefaults.colors(checkedThumbColor = colors.ember, checkedTrackColor = colors.ember.copy(alpha = 0.4f)),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.camping_editor_transport_capacity),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            CzTextField(
                value = capacityText,
                onValueChange = { capacityText = it },
                label = "-",
                modifier = Modifier.width(80.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        EditorLabel(stringResource(R.string.camping_editor_transport_fee))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            CzTextField(
                value = feeText,
                onValueChange = { feeText = it },
                label = "0.00",
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            CzTextField(
                value = currency,
                onValueChange = { currency = it.uppercase() },
                label = "EUR",
                modifier = Modifier.width(80.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Characters,
                ),
            )
        }

        Spacer(Modifier.height(CzSpacing.md))
        CzButton(
            text = stringResource(R.string.common_save),
            onClick = {
                onSave(
                    CampingTransportationOption(
                        id = option.id.ifBlank { UUID.randomUUID().toString() },
                        name = name.trim(),
                        mode = mode,
                        details = details.trim(),
                        requiresTicket = requiresTicket,
                        capacity = capacityText.trim().toIntOrNull()?.takeIf { it > 0 },
                        feeCents = CampingEditorForm.feeCents(feeText),
                        currency = currency.trim().uppercase().ifBlank { "EUR" },
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = canSave,
        )
        Spacer(Modifier.height(CzSpacing.xl))
    }
}
