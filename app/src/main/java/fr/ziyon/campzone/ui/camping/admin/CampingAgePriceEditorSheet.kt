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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTextField
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.model.CampingAgePrice
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampingAgePriceEditorSheet(
    tier: CampingAgePrice?,
    currency: String,
    onSave: (CampingAgePrice) -> Unit,
    onDismiss: () -> Unit,
) {
    if (tier == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        AgePriceEditorContent(tier = tier, currency = currency, onSave = onSave, onDismiss = onDismiss)
    }
}

@Composable
private fun AgePriceEditorContent(
    tier: CampingAgePrice,
    currency: String,
    onSave: (CampingAgePrice) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    var label by remember { mutableStateOf(tier.label) }
    var minAgeText by remember { mutableStateOf(tier.minAge.toString()) }
    var hasUpperBound by remember { mutableStateOf(tier.maxAge != null) }
    var maxAgeText by remember { mutableStateOf(tier.maxAge?.toString() ?: "") }
    var amountText by remember {
        mutableStateOf(
            if (tier.amountCents > 0) "%.2f".format(tier.amountCents / 100.0) else ""
        )
    }

    val minAge = minAgeText.trim().toIntOrNull()
    val maxAge = maxAgeText.trim().toIntOrNull()
    val parsedCents = CampingEditorForm.feeCents(amountText)
    val canSave = label.isNotBlank()
        && (minAge ?: -1) >= 0
        && (parsedCents ?: 0) > 0
        && (!hasUpperBound || (maxAge != null && maxAge >= (minAge ?: 0)))

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
                text = stringResource(R.string.camping_editor_age_price_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_close), tint = colors.textSecondary)
            }
        }

        EditorLabel(stringResource(R.string.camping_editor_age_range))
        CzTextField(
            value = label,
            onValueChange = { label = it },
            label = stringResource(R.string.camping_editor_age_price_label_hint),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.camping_editor_age_from),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            CzTextField(
                value = minAgeText,
                onValueChange = { minAgeText = it },
                label = "0",
                modifier = Modifier.width(80.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Checkbox(
                checked = hasUpperBound,
                onCheckedChange = { hasUpperBound = it },
                colors = CheckboxDefaults.colors(checkedColor = colors.ember),
            )
            Text(
                text = stringResource(R.string.camping_editor_age_has_upper),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
        }
        if (hasUpperBound) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            ) {
                Text(
                    text = stringResource(R.string.camping_editor_age_to),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                CzTextField(
                    value = maxAgeText,
                    onValueChange = { maxAgeText = it },
                    label = "17",
                    modifier = Modifier.width(80.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }

        EditorLabel(stringResource(R.string.camping_editor_age_price_amount))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            CzTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = "0.00",
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Text(
                text = currency.uppercase().ifBlank { "EUR" },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }

        Spacer(Modifier.height(CzSpacing.md))
        CzButton(
            text = stringResource(R.string.common_save),
            onClick = {
                onSave(
                    CampingAgePrice(
                        id = tier.id.ifBlank { UUID.randomUUID().toString() },
                        label = label.trim(),
                        minAge = minAge?.coerceAtLeast(0) ?: 0,
                        maxAge = if (hasUpperBound) maxAge?.coerceAtLeast(minAge ?: 0) else null,
                        amountCents = parsedCents ?: 0,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = canSave,
        )
        Spacer(Modifier.height(CzSpacing.xl))
    }
}
