package fr.ziyon.campzone.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.admin.AdminAnalyticsDashboard
import fr.ziyon.campzone.data.admin.AdminAnalyticsService
import fr.ziyon.campzone.data.admin.AdminCampingAnalytics
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AdminAnalyticsUiState {
    data object Loading : AdminAnalyticsUiState
    data class Loaded(val dashboard: AdminAnalyticsDashboard) : AdminAnalyticsUiState
    data class Error(val message: String) : AdminAnalyticsUiState
}

@HiltViewModel
class AdminAnalyticsViewModel @Inject constructor(
    private val service: AdminAnalyticsService,
) : ViewModel() {
    private val _uiState = MutableStateFlow<AdminAnalyticsUiState>(AdminAnalyticsUiState.Loading)
    val uiState: StateFlow<AdminAnalyticsUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.value = AdminAnalyticsUiState.Loading
            runCatching { service.load() }
                .onSuccess { _uiState.value = AdminAnalyticsUiState.Loaded(it) }
                .onFailure { _uiState.value = AdminAnalyticsUiState.Error(it.message ?: "Analytics could not be loaded.") }
        }
    }
}

@Composable
fun AdminAnalyticsRoute(
    authenticatedUser: AuthenticatedUser,
    onBack: () -> Unit,
    viewModel: AdminAnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(authenticatedUser.uid) { if (authenticatedUser.role.isAdmin) viewModel.load() }
    AdminAnalyticsScreen(state, authenticatedUser.role.isAdmin, onBack, viewModel::load)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminAnalyticsScreen(
    state: AdminAnalyticsUiState,
    allowed: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.czColors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.admin_analytics_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back)) } },
            )
        },
    ) { padding ->
        if (!allowed) {
            CzEmptyState(
                title = stringResource(R.string.admin_tools_restricted_title),
                message = stringResource(R.string.admin_tools_restricted_message),
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@Scaffold
        }
        when (state) {
            AdminAnalyticsUiState.Loading -> Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator() }
            is AdminAnalyticsUiState.Error -> Column(Modifier.fillMaxSize().padding(padding).padding(CzSpacing.lg), verticalArrangement = Arrangement.Center) {
                Text(state.message, color = MaterialTheme.czColors.error)
                CzButton(text = stringResource(R.string.common_retry), onClick = onRetry)
            }
            is AdminAnalyticsUiState.Loaded -> AnalyticsDashboard(state.dashboard, Modifier.padding(padding))
        }
    }
}

@Composable
private fun AnalyticsDashboard(dashboard: AdminAnalyticsDashboard, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(CzSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        item("summary") {
            Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                Text(stringResource(R.string.admin_analytics_overview), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                    MetricCard(stringResource(R.string.admin_analytics_users), dashboard.totalUsers, Modifier.weight(1f))
                    MetricCard(stringResource(R.string.admin_analytics_registrations), dashboard.totalRegistrations, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                    MetricCard(stringResource(R.string.admin_analytics_active_camps), dashboard.activeCampings, Modifier.weight(1f))
                    MetricCard(stringResource(R.string.admin_analytics_engagement), dashboard.totalEngagement, Modifier.weight(1f))
                }
            }
        }
        item("trend") {
            Surface(color = MaterialTheme.czColors.surface, shape = RoundedCornerShape(CzRadius.lg)) {
                Column(Modifier.padding(CzSpacing.md), verticalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                    Text(stringResource(R.string.admin_analytics_weekly), fontWeight = FontWeight.SemiBold)
                    val max = dashboard.weeklyRegistrations.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                    dashboard.weeklyRegistrations.forEach { point ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
                            Text(point.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.3f))
                            LinearProgressIndicator(progress = { point.count.toFloat() / max }, modifier = Modifier.weight(1f))
                            Text(point.count.toString(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item("campings-title") { Text(stringResource(R.string.admin_analytics_top_camps), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(dashboard.campings, key = AdminCampingAnalytics::id) { camping ->
            Surface(color = MaterialTheme.czColors.surface, shape = RoundedCornerShape(CzRadius.lg)) {
                Column(Modifier.fillMaxWidth().padding(CzSpacing.md), verticalArrangement = Arrangement.spacedBy(CzSpacing.xs)) {
                    Text(camping.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.czColors.textPrimary)
                    Text(stringResource(R.string.admin_analytics_camp_summary, camping.registrations, camping.approved, camping.pending, camping.engagement), color = MaterialTheme.czColors.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.czColors.surface, shape = RoundedCornerShape(CzRadius.lg)) {
        Column(Modifier.padding(CzSpacing.md)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.czColors.accent)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.czColors.textSecondary)
        }
    }
}
