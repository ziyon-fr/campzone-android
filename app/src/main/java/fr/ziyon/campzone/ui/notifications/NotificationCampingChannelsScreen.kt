package fr.ziyon.campzone.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cabin
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzCard
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.ui.camping.campingDateRange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCampingChannelsScreen(
    viewModel: NotificationSettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val channels by viewModel.channels.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val subscribed = (uiState as? NotificationSettingsUiState.Loaded)
        ?.settings?.subscribedCampingIds.orEmpty().toSet()

    LaunchedEffect(Unit) { viewModel.loadChannelsIfNeeded() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.notif_camping_channels_title), color = colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.notif_back_cd),
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.background,
                    scrolledContainerColor = colors.background,
                ),
                windowInsets = WindowInsets(),
            )
        },
    ) { innerPadding ->
        when {
            channels.isLoading -> CzLoadingView(
                modifier = Modifier.fillMaxWidth().padding(innerPadding),
                message = stringResource(R.string.notif_settings_loading),
            )

            channels.campings.isEmpty() -> CzEmptyState(
                title = stringResource(R.string.notif_camping_channels_empty_title),
                message = stringResource(R.string.notif_camping_channels_empty_msg),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                icon = { Icon(Icons.Rounded.Cabin, contentDescription = null, tint = colors.textSecondary) },
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CzSpacing.lg, vertical = CzSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
            ) {
                CzCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(CzSpacing.md)) {
                        channels.campings.forEach { camping ->
                            NotificationToggleRow(
                                icon = Icons.Rounded.Cabin,
                                title = camping.title,
                                description = campingDateRange(camping.startDate, camping.endDate),
                                checked = subscribed.contains(camping.id),
                                onCheckedChange = { enabled ->
                                    viewModel.toggleCampingChannel(camping.id, enabled)
                                },
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.notif_camping_channels_footer),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
