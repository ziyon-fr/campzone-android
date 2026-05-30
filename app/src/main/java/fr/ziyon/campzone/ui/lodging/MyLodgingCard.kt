package fr.ziyon.campzone.ui.lodging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzBadge
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.lodging.LodgingService
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.LodgingUnit
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Read-only loader for the signed-in participant's lodging. Lodging reads are
 * open to any signed-in user (`03` RBAC); failures keep the card silent.
 */
@HiltViewModel
class MyLodgingViewModel @Inject constructor(
    private val lodgingService: LodgingService,
) : ViewModel() {

    private val _units = MutableStateFlow<List<LodgingUnit>>(emptyList())
    val units: StateFlow<List<LodgingUnit>> = _units.asStateFlow()

    private var job: Job? = null
    private var loadedCampingId: String? = null

    fun load(campingId: String) {
        if (loadedCampingId == campingId) return
        loadedCampingId = campingId
        job?.cancel()
        job = viewModelScope.launch {
            runCatching {
                lodgingService.observeUnits(campingId).collect { _units.value = it }
            }
        }
    }
}

/**
 * "My Lodging" card surfaced inside My QR Passes (mirrors the iOS
 * `MyLodgingCard`): shows which unit each of the participant's managed people
 * (self + children) sleeps in. Renders nothing when the camp has no lodging or
 * none of the user's people are placed yet, so it self-silences.
 */
@Composable
fun MyLodgingCard(
    units: List<LodgingUnit>,
    managedAttendees: List<CampingAttendee>,
    modifier: Modifier = Modifier,
) {
    val managedIds = managedAttendees.map { it.id }.toSet()
    val placements = units
        .mapNotNull { unit ->
            val mine = unit.occupantIds.filter { it in managedIds }
            if (mine.isEmpty()) null else unit to mine
        }
    if (placements.isEmpty()) return

    val byId = managedAttendees.associateBy { it.id }
    val colors = MaterialTheme.czColors

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(CzRadius.xl),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                Icon(Icons.Filled.Bed, contentDescription = null, tint = colors.ember, modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(R.string.lodging_my_lodging_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
            }

            placements.forEach { (unit, mine) ->
                Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                        Icon(unit.kind.icon(), contentDescription = null, tint = colors.ember, modifier = Modifier.size(18.dp))
                        Text(
                            text = unit.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        CzBadge(text = unit.genderPolicy.label(), tone = unit.genderPolicy.tone())
                    }
                    Text(
                        text = mine.mapNotNull { byId[it]?.displayName }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    if (unit.notes.isNotBlank()) {
                        Text(
                            text = unit.notes,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}
