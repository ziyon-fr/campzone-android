package fr.ziyon.campzone.ui.packing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.auth.AuthenticatedUser

@Composable
fun MyPackingChecklistCard(
    campingId: String,
    user: AuthenticatedUser,
    onOpenChecklist: () -> Unit,
    onOpenEditor: () -> Unit,
    viewModel: PackingChecklistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(campingId, user.uid) { viewModel.load(campingId, user) }
    when {
        state.loading -> Unit
        state.error != null && state.template == null -> Surface(
            color = packingOverviewCardColor(),
            shape = RoundedCornerShape(CzRadius.xl),
            border = BorderStroke(1.dp, MaterialTheme.czColors.error.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(CzSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            ) {
                Icon(Icons.Rounded.Checklist, null, tint = MaterialTheme.czColors.error, modifier = Modifier.size(44.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.packing_load_error), fontWeight = FontWeight.SemiBold)
                    Text(state.error.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.czColors.textSecondary)
                }
                TextButton(onClick = { viewModel.load(campingId, user) }) {
                    Text(stringResource(R.string.common_retry))
                }
            }
        }
        state.snapshot != null -> {
            val snapshot = requireNotNull(state.snapshot)
            Surface(
                onClick = onOpenChecklist,
                color = packingOverviewCardColor(),
                shape = RoundedCornerShape(CzRadius.xl),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(CzSpacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { snapshot.progress.coerceAtLeast(0.001f) },
                            color = if (snapshot.isComplete) MaterialTheme.czColors.success else MaterialTheme.czColors.accent,
                            trackColor = MaterialTheme.czColors.divider,
                            strokeWidth = 5.dp,
                            modifier = Modifier.size(44.dp),
                        )
                        Icon(if (snapshot.isComplete) Icons.Rounded.Check else Icons.Rounded.Luggage, null, tint = if (snapshot.isComplete) MaterialTheme.czColors.success else MaterialTheme.czColors.accent, modifier = Modifier.size(16.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.packing_title), fontWeight = FontWeight.SemiBold, color = MaterialTheme.czColors.textPrimary)
                        Text(if (snapshot.isComplete) stringResource(R.string.packing_complete) else stringResource(R.string.packing_items_ready, snapshot.checkedItems, snapshot.totalItems), style = MaterialTheme.typography.labelMedium, color = if (snapshot.isComplete) MaterialTheme.czColors.success else MaterialTheme.czColors.textSecondary)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.czColors.textSecondary)
                }
            }
        }
        state.canEdit && state.template != null -> Surface(
            onClick = onOpenEditor,
            color = packingOverviewCardColor(),
            shape = RoundedCornerShape(CzRadius.xl),
            border = BorderStroke(1.dp, MaterialTheme.czColors.accent.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(CzSpacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
                Icon(Icons.Rounded.Checklist, null, tint = MaterialTheme.czColors.accent, modifier = Modifier.size(44.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.packing_setup), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.packing_setup_subtitle), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.czColors.textSecondary)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.czColors.textSecondary)
            }
        }
        else -> Unit
    }
}

@Composable
private fun packingOverviewCardColor(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.czColors.surface else Color.White
