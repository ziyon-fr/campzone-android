package fr.ziyon.campzone.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTextField
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.church.ChurchGroup
import fr.ziyon.campzone.data.church.SDAChurch

@Composable
fun ChurchPickerSheet(
    query: String,
    groups: List<ChurchGroup>,
    selectedChurch: String,
    isLoading: Boolean,
    errorMessage: String?,
    onQueryChange: (String) -> Unit,
    onSelectChurch: (SDAChurch) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .padding(CzSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.base),
        ) {
            Text(
                text = stringResource(R.string.church_picker_title),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
            )
            if (selectedChurch.isNotBlank()) {
                TextButton(onClick = onClear) {
                    Text(
                        text = stringResource(R.string.common_clear),
                        color = MaterialTheme.czColors.error,
                    )
                }
            }
        }

        CzTextField(
            value = query,
            onValueChange = onQueryChange,
            label = stringResource(R.string.church_picker_search),
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.czColors.primary,
                    modifier = Modifier.size(18.dp),
                )
            },
        )

        when {
            isLoading -> CzLoadingView(message = stringResource(R.string.church_picker_loading))
            errorMessage != null -> Text(
                text = errorMessage,
                color = MaterialTheme.czColors.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            groups.isEmpty() -> Text(
                text = query.takeUnless { it.isBlank() }
                    ?.let { stringResource(R.string.church_picker_no_results, it) }
                    ?: stringResource(R.string.church_picker_empty),
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            else -> Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
            ) {
                groups.forEach { group ->
                    Text(
                        text = group.country,
                        color = MaterialTheme.czColors.textSecondary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    group.churches.forEach { church ->
                        ChurchPickerRow(
                            church = church,
                            isSelected = selectedChurch == church.name,
                            onClick = { onSelectChurch(church) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChurchPickerRow(
    church: SDAChurch,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.md))
            .background(
                if (isSelected) MaterialTheme.czColors.primary.copy(alpha = 0.14f) else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(CzSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = church.name,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                ),
            )
            Text(
                text = "${church.city} / ${church.region}",
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isSelected) {
            Text(
                text = "OK",
                color = MaterialTheme.czColors.primary,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}
