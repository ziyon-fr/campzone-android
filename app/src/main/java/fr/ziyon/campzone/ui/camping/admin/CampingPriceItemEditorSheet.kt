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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import fr.ziyon.campzone.data.model.CampingPaymentOption
import fr.ziyon.campzone.data.model.CampingPriceItem
import fr.ziyon.campzone.ui.camping.localizedDisplayName
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampingPriceItemEditorSheet(
    item: CampingPriceItem?,
    onSave: (CampingPriceItem) -> Unit,
    onDismiss: () -> Unit,
) {
    if (item == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        PriceItemEditorContent(item = item, onSave = onSave, onDismiss = onDismiss)
    }
}

@Composable
private fun PriceItemEditorContent(
    item: CampingPriceItem,
    onSave: (CampingPriceItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    var name by remember { mutableStateOf(item.name) }
    var details by remember { mutableStateOf(item.details) }
    var amountText by remember {
        mutableStateOf(
            if (item.amountCents > 0) "%.2f".format(item.amountCents / 100.0) else ""
        )
    }
    var currency by remember { mutableStateOf(item.currency.ifBlank { "EUR" }) }
    var isMandatory by remember { mutableStateOf(item.isMandatory) }
    var paymentOptions by remember { mutableStateOf(item.paymentOptions.toSet()) }
    var iban by remember { mutableStateOf(item.iban ?: "") }
    var ibanHolder by remember { mutableStateOf(item.ibanHolder ?: "") }

    val offersBankTransfer = CampingPaymentOption.BankTransfer in paymentOptions
    val parsedCents = CampingEditorForm.feeCents(amountText)
    val canSave = name.isNotBlank()
        && (parsedCents ?: 0) > 0
        && paymentOptions.isNotEmpty()
        && (!offersBankTransfer || iban.isNotBlank())

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
                text = stringResource(R.string.camping_editor_price_item_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_close), tint = colors.textSecondary)
            }
        }

        EditorLabel(stringResource(R.string.camping_editor_section_item))
        CzTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.camping_editor_price_item_name_hint),
            modifier = Modifier.fillMaxWidth(),
        )
        CzTextField(
            value = details,
            onValueChange = { details = it },
            label = stringResource(R.string.camping_editor_price_item_details_hint),
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            CzTextField(
                value = amountText,
                onValueChange = { amountText = it },
                placeholder = "0.00",
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                label = stringResource(R.string.camping_editor_fee_amount),
            )
            CzTextField(
                value = currency,
                onValueChange = { currency = it.uppercase() },
                placeholder = "EUR",
                modifier = Modifier.width(80.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Characters,
                ),
                label = stringResource(R.string.camping_editor_fee_currency),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Checkbox(
                checked = isMandatory,
                onCheckedChange = { isMandatory = it },
                colors = CheckboxDefaults.colors(checkedColor = colors.ember),
            )
            Text(
                text = stringResource(R.string.camping_editor_price_item_required),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
        }

        HorizontalDivider(color = colors.divider)

        EditorLabel(stringResource(R.string.camping_editor_payment_methods))
        CampingPaymentOption.entries.forEach { option ->
            val checked = option in paymentOptions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { on ->
                        paymentOptions = if (on) paymentOptions + option else paymentOptions - option
                    },
                    colors = CheckboxDefaults.colors(checkedColor = colors.ember),
                )
                Text(
                    text = option.localizedDisplayName(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )
            }
        }

        if (offersBankTransfer) {
            HorizontalDivider(color = colors.divider)
            EditorLabel(stringResource(R.string.camping_editor_bank_transfer))
            CzTextField(
                value = iban,
                onValueChange = { iban = it.uppercase() },
                label = stringResource(R.string.camping_editor_iban_hint),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Characters,
                ),
            )
            CzTextField(
                value = ibanHolder,
                onValueChange = { ibanHolder = it },
                label = stringResource(R.string.camping_editor_iban_holder_hint),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(CzSpacing.md))
        CzButton(
            text = stringResource(R.string.common_save),
            onClick = {
                val result = CampingPriceItem(
                    id = item.id.ifBlank { UUID.randomUUID().toString() },
                    name = name.trim(),
                    details = details.trim(),
                    amountCents = parsedCents ?: 0,
                    currency = currency.trim().uppercase().ifBlank { "EUR" },
                    paymentOptions = paymentOptions.toList(),
                    iban = iban.trim().takeUnless { it.isBlank() },
                    ibanHolder = ibanHolder.trim().takeUnless { it.isBlank() },
                    isMandatory = isMandatory,
                )
                onSave(result)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = canSave,
        )
        Spacer(Modifier.height(CzSpacing.xl))
    }
}
