package fr.ziyon.campzone.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.profile.AllergyFormatter
import fr.ziyon.campzone.data.profile.CommonAllergy

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllergiesEditor(
    selected: List<String>,
    onSelectedChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var customText by rememberSaveable { mutableStateOf("") }
    val haptics = LocalHapticFeedback.current
    val displayNames = CommonAllergy.entries.associateWith { allergy -> allergy.localizedName() }

    fun matchesPreset(token: String, allergy: CommonAllergy): Boolean =
        token.equals(allergy.wireValue, ignoreCase = true) ||
            token.equals(displayNames.getValue(allergy), ignoreCase = true)

    fun toggle(allergy: CommonAllergy) {
        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        onSelectedChange(
            AllergyFormatter.toggledPreset(
                tokens = selected,
                allergy = allergy,
                displayName = displayNames.getValue(allergy),
            ),
        )
    }

    fun addCustom() {
        val token = AllergyFormatter.normalizedToken(customText) { displayNames.getValue(it) } ?: return
        if (selected.none { it.equals(token, ignoreCase = true) }) {
            onSelectedChange(AllergyFormatter.cleaned(selected + token))
        }
        customText = ""
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        AllergyGroup(
            title = stringResource(R.string.allergies_food),
            allergies = CommonAllergy.foodAllergies,
            selected = selected,
            onToggle = ::toggle,
        )
        AllergyGroup(
            title = stringResource(R.string.allergies_environmental_other),
            allergies = CommonAllergy.otherAllergies,
            selected = selected,
            onToggle = ::toggle,
        )

        val customTokens = selected.filter { token ->
            CommonAllergy.entries.none { allergy -> matchesPreset(token, allergy) }
        }
        if (customTokens.isNotEmpty()) {
            Text(
                text = stringResource(R.string.allergies_custom),
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            ) {
                customTokens.forEach { token ->
                    InputChip(
                        selected = true,
                        onClick = {
                            onSelectedChange(selected.filterNot { it == token })
                        },
                        label = { Text(token) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.allergies_remove, token),
                            )
                        },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = customText,
                onValueChange = { customText = it },
                label = { Text(stringResource(R.string.allergies_add_specific)) },
                leadingIcon = { Icon(Icons.Filled.AddCircle, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addCustom() }),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            if (customText.isNotBlank()) {
                TextButton(onClick = ::addCustom) {
                    Text(stringResource(R.string.common_add))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllergyChips(
    tokens: List<String>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        tokens.forEach { token ->
            InputChip(
                selected = true,
                onClick = {},
                enabled = false,
                label = { Text(allergyDisplayName(token)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllergyGroup(
    title: String,
    allergies: List<CommonAllergy>,
    selected: List<String>,
    onToggle: (CommonAllergy) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
        Text(
            text = title,
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.xs),
        ) {
            allergies.forEach { allergy ->
                val localizedName = allergy.localizedName()
                val isSelected = selected.any {
                    it.equals(allergy.wireValue, ignoreCase = true) ||
                        it.equals(localizedName, ignoreCase = true)
                }
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggle(allergy) },
                    label = { Text(localizedName) },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(
                                    R.string.allergies_remove,
                                    localizedName,
                                ),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
fun allergyDisplayName(token: String): String =
    CommonAllergy.fromWire(token)?.localizedName() ?: token

@Composable
private fun CommonAllergy.localizedName(): String = stringResource(
    when (this) {
        CommonAllergy.Peanuts -> R.string.allergy_peanuts
        CommonAllergy.TreeNuts -> R.string.allergy_tree_nuts
        CommonAllergy.Milk -> R.string.allergy_milk
        CommonAllergy.Eggs -> R.string.allergy_eggs
        CommonAllergy.Gluten -> R.string.allergy_gluten
        CommonAllergy.Soy -> R.string.allergy_soy
        CommonAllergy.Fish -> R.string.allergy_fish
        CommonAllergy.Shellfish -> R.string.allergy_shellfish
        CommonAllergy.Sesame -> R.string.allergy_sesame
        CommonAllergy.Pollen -> R.string.allergy_pollen
        CommonAllergy.DustMites -> R.string.allergy_dust_mites
        CommonAllergy.PetDander -> R.string.allergy_pet_dander
        CommonAllergy.InsectStings -> R.string.allergy_insect_stings
        CommonAllergy.Latex -> R.string.allergy_latex
        CommonAllergy.Medication -> R.string.allergy_medication
        CommonAllergy.Mold -> R.string.allergy_mold
    },
)
