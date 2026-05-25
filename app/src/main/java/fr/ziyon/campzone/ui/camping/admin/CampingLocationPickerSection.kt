package fr.ziyon.campzone.ui.camping.admin

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTextField
import fr.ziyon.campzone.core.designsystem.czColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import kotlin.coroutines.resume

data class LocationResult(
    val name: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
)

data class LocationSearchUiState(
    val query: String = "",
    val results: List<LocationResult> = emptyList(),
    val isSearching: Boolean = false,
    val nearbyLocations: List<LocationResult> = emptyList(),
    val recentLocations: List<LocationResult> = emptyList(),
)

private const val LOCATION_PREFS_NAME = "campzone_location_recents"
private const val LOCATION_PREFS_KEY = "recents"
private const val MAX_RECENTS = 5

@HiltViewModel
class CampingLocationSearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationSearchUiState())
    val uiState: StateFlow<LocationSearchUiState> = _uiState.asStateFlow()

    private val geocoder = Geocoder(context)
    private val prefs: SharedPreferences =
        context.getSharedPreferences(LOCATION_PREFS_NAME, Context.MODE_PRIVATE)
    private var searchJob: Job? = null

    fun init() {
        _uiState.value = _uiState.value.copy(recentLocations = loadRecentsFromPrefs())
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(results = emptyList(), isSearching = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            performSearch(query)
        }
    }

    private suspend fun performSearch(query: String) {
        _uiState.value = _uiState.value.copy(isSearching = true)
        val results = runCatching { geocodeQuery(query) }.getOrDefault(emptyList())
        _uiState.value = _uiState.value.copy(results = results, isSearching = false)
    }

    private suspend fun geocodeQuery(query: String): List<LocationResult> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocationName(query, 10) { addresses ->
                    cont.resume(addresses.mapToResults())
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, 10)?.mapToResults() ?: emptyList()
            }
        }
    }

    fun loadNearby(location: Location?) {
        location ?: return
        viewModelScope.launch {
            val results = runCatching { reverseGeocode(location) }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(nearbyLocations = results)
        }
    }

    private suspend fun reverseGeocode(location: Location): List<LocationResult> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(location.latitude, location.longitude, 5) { addresses ->
                    cont.resume(addresses.mapToResults())
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(location.latitude, location.longitude, 5)?.mapToResults()
                    ?: emptyList()
            }
        }
    }

    fun addToRecents(result: LocationResult) {
        val current = loadRecentsFromPrefs().toMutableList()
        current.removeAll { it.name == result.name && it.address == result.address }
        current.add(0, result)
        val trimmed = current.take(MAX_RECENTS)
        saveRecentsToPrefs(trimmed)
        _uiState.value = _uiState.value.copy(recentLocations = trimmed)
    }

    private fun loadRecentsFromPrefs(): List<LocationResult> {
        val json = prefs.getString(LOCATION_PREFS_KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                LocationResult(
                    name = obj.getString("name"),
                    address = obj.getString("address"),
                    latitude = obj.optDouble("lat").takeUnless { it.isNaN() },
                    longitude = obj.optDouble("lng").takeUnless { it.isNaN() },
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveRecentsToPrefs(results: List<LocationResult>) {
        val arr = JSONArray()
        results.forEach { r ->
            val obj = JSONObject().apply {
                put("name", r.name)
                put("address", r.address)
                r.latitude?.let { put("lat", it) }
                r.longitude?.let { put("lng", it) }
            }
            arr.put(obj)
        }
        prefs.edit().putString(LOCATION_PREFS_KEY, arr.toString()).apply()
    }

    private fun List<Address>.mapToResults(): List<LocationResult> = mapNotNull { addr ->
        val name = addr.featureName?.takeUnless { it.isBlank() }
            ?: addr.locality
            ?: addr.adminArea
            ?: addr.countryName
            ?: return@mapNotNull null
        val parts = listOfNotNull(addr.locality, addr.adminArea, addr.countryName).distinct()
        val address = parts.joinToString(", ")
        LocationResult(name = name, address = address, latitude = addr.latitude, longitude = addr.longitude)
    }
}

@Composable
fun CampingLocationPickerSection(
    location: String,
    onLocationSelected: (name: String, lat: Double?, lng: Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    val colors = MaterialTheme.czColors

    if (location.isBlank()) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clickable { showPicker = true }
                .padding(vertical = CzSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
        ) {
            Icon(
                Icons.Rounded.LocationOn,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.camping_editor_location_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = CzSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.LocationOn,
                contentDescription = null,
                tint = colors.ember,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(CzSpacing.sm))
            Text(
                text = location,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showPicker = true }) {
                Text(stringResource(R.string.camping_editor_location_change), color = colors.ember)
            }
        }
    }

    if (showPicker) {
        CampingLocationPickerSheet(
            onSelect = { result ->
                onLocationSelected(result.name, result.latitude, result.longitude)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampingLocationPickerSheet(
    onSelect: (LocationResult) -> Unit,
    onDismiss: () -> Unit,
    viewModel: CampingLocationSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) tryLoadNearby(context, viewModel)
    }

    LaunchedEffect(Unit) {
        viewModel.init()
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            tryLoadNearby(context, viewModel)
        } else {
            permissionLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        LocationPickerContent(
            state = state,
            onQueryChange = viewModel::onQueryChange,
            onSelect = { result ->
                viewModel.addToRecents(result)
                onSelect(result)
            },
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun LocationPickerContent(
    state: LocationSearchUiState,
    onQueryChange: (String) -> Unit,
    onSelect: (LocationResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CzSpacing.xl, vertical = CzSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.camping_editor_section_location),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(CzSpacing.sm))
        CzTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = stringResource(R.string.camping_editor_location_hint),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(CzSpacing.sm))

        if (state.isSearching) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = CzSpacing.md),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.ember, strokeWidth = 2.dp)
            }
        }

        if (state.results.isNotEmpty()) {
            state.results.forEach { result ->
                LocationResultRow(result = result, onSelect = onSelect)
                HorizontalDivider(color = colors.divider)
            }
            Spacer(Modifier.height(CzSpacing.md))
        }

        if (state.query.isBlank() && state.nearbyLocations.isNotEmpty()) {
            Text(
                text = stringResource(R.string.camping_editor_location_nearby),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(vertical = CzSpacing.xs),
            )
            state.nearbyLocations.forEach { result ->
                LocationResultRow(result = result, onSelect = onSelect)
                HorizontalDivider(color = colors.divider)
            }
            Spacer(Modifier.height(CzSpacing.md))
        }

        if (state.query.isBlank() && state.recentLocations.isNotEmpty()) {
            Text(
                text = stringResource(R.string.camping_editor_location_recents),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(vertical = CzSpacing.xs),
            )
            state.recentLocations.forEach { result ->
                LocationResultRow(result = result, onSelect = onSelect)
                HorizontalDivider(color = colors.divider)
            }
        }

        Spacer(Modifier.height(CzSpacing.xxxl))
    }
}

@Composable
private fun LocationResultRow(
    result: LocationResult,
    onSelect: (LocationResult) -> Unit,
) {
    val colors = MaterialTheme.czColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(result) }
            .padding(vertical = CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Icon(
            Icons.Rounded.LocationOn,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.name,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            if (result.address.isNotBlank()) {
                Text(
                    text = result.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun tryLoadNearby(context: Context, viewModel: CampingLocationSearchViewModel) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val location = runCatching {
        locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
    }.getOrNull()
    viewModel.loadNearby(location)
}
