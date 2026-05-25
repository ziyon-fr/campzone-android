package fr.ziyon.campzone.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzColors
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.navigation.ScreenColumn
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.ui.camping.campingDateRange
import fr.ziyon.campzone.ui.camping.label
import fr.ziyon.campzone.ui.camping.previewCamping

@Composable
fun HomeRoute(
    onOpenCamping: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    HomeScreen(
        state = state,
        onOpenCamping = onOpenCamping,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenCamping: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.czColors.background),
    ) {
        DashboardHeader(onOpenNotifications = {})

        HomeSectionHeader(
            icon = Icons.Filled.Park,
            title = stringResource(R.string.home_feature_title),
        )

        when (val phase = state.phase) {
            HomePhase.Loading -> CzLoadingView(
                modifier = Modifier.fillMaxWidth(),
                message = stringResource(R.string.camping_loading),
            )

            is HomePhase.Error -> CzErrorState(
                title = stringResource(R.string.camping_error_title),
                message = phase.message,
                onRetry = onRetry,
                retryLabel = stringResource(R.string.common_retry),
            )

            is HomePhase.Loaded -> {
                val featured = phase.featuredCamping
                if (featured == null) {
                    CzEmptyState(
                        title = stringResource(R.string.camping_empty_title),
                        message = stringResource(R.string.camping_empty_message),
                    )
                } else {
                    FeaturedCampingCard(
                        camping = featured,
                        onClick = { onOpenCamping(featured.id) },
                    )
                }
            }
        }

        HomeSectionHeader(
            icon = Icons.Filled.CalendarMonth,
            title = "Upcoming Programs",
            modifier = Modifier.padding(top = CzSpacing.lg),
        )
        HomeEmptyCard(
            icon = Icons.Filled.CalendarMonth,
            title = "No programs scheduled",
            message = "Camp leaders haven't published the schedule yet - check back soon.",
        )

        HomeSectionHeader(
            icon = Icons.Filled.Info,
            title = "Announcements",
            modifier = Modifier.padding(top = CzSpacing.md),
        )
        HomeEmptyCard(
            icon = Icons.Filled.Info,
            title = "No announcements",
            message = "Important camp updates will appear here.",
        )
    }
}

@Composable
private fun DashboardHeader(
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = CzSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Park,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.czColors.leaf,
                )
                Text(
                    text = stringResource(R.string.home_slogan).uppercase(),
                    color = MaterialTheme.czColors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = stringResource(R.string.home_title),
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
        }

        Button(
            onClick = onOpenNotifications,
            shape = RoundedCornerShape(CzRadius.md),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.czColors.surface,
                contentColor = MaterialTheme.czColors.ember,
            ),
            contentPadding = PaddingValues(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = "Notifications",
            )
        }
    }
}

@Composable
internal fun HomeSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.xs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.czColors.ember,
        )
        Text(
            text = title,
            color = MaterialTheme.czColors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun FeaturedCampingCard(
    camping: Camping,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDarkMode = isSystemInDarkTheme()
    val capacity = camping.participantCapacity ?: 0
    val ratio = if (capacity > 0) {
        (camping.participantCount.toFloat() / capacity.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progressColor = when {
        ratio < 0.5f -> MaterialTheme.czColors.leaf
        ratio < 0.8f -> MaterialTheme.czColors.amber
        else -> MaterialTheme.czColors.flame
    }
    val fillText = when {
        ratio == 0f -> camping.registrationStatus.label()
        ratio >= 1f -> "Fully booked"
        ratio >= 0.8f -> "Almost full · ${(ratio * 100).toInt()}%"
        else -> "${(ratio * 100).toInt()}% filled"
    }
    val heroGradient = if (isDarkMode) {
        listOf(MaterialTheme.czColors.night, MaterialTheme.czColors.twilight, Color(0xFF2D1005))
    } else {
        listOf(MaterialTheme.czColors.amber, MaterialTheme.czColors.primary, MaterialTheme.czColors.secondary)
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.lg)),
        shape = RoundedCornerShape(CzRadius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.czColors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp),
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Brush.verticalGradient(heroGradient)),
                )

                FeaturedMountainBackground(
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(if (isDarkMode) 0.82f else 0.4f),
                )

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = if (isDarkMode) {
                                    listOf(Color.Transparent, MaterialTheme.czColors.surface.copy(alpha = 0.85f))
                                } else {
                                    listOf(
                                        CzColors.BackgroundDark.copy(alpha = 0.1f),
                                        CzColors.BackgroundDark.copy(alpha = 0.4f),
                                    )
                                },
                            ),
                        ),
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(CzSpacing.md),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                ) {
                    CampingLogoBadge(
                        logoUrl = camping.logoUrl,
                        title = camping.title,
                        size = 56,
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = camping.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 2,
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = camping.location.ifBlank { "Location pending" },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.czColors.surface)
                    .padding(CzSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.czColors.textSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = campingDateRange(camping.startDate, camping.endDate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.czColors.textSecondary,
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = fillText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = progressColor,
                    )
                }

                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape),
                    color = progressColor,
                    trackColor = MaterialTheme.czColors.divider.copy(alpha = 0.72f),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (capacity > 0) {
                            "${camping.participantCount} / $capacity registered"
                        } else {
                            "${camping.participantCount} registered"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.czColors.textSecondary,
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.czColors.textTertiary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun CampingLogoBadge(
    logoUrl: String?,
    title: String,
    size: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(CzRadius.md))
            .background(MaterialTheme.czColors.secondary.copy(alpha = 0.18f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.55f),
                shape = RoundedCornerShape(CzRadius.md),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = title,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Terrain,
                contentDescription = null,
                tint = MaterialTheme.czColors.secondary,
                modifier = Modifier.size((size * 0.48f).dp),
            )
        }
    }
}

@Composable
private fun HomeEmptyCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.lg))
            .background(MaterialTheme.czColors.surface)
            .padding(CzSpacing.md),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.czColors.textSecondary,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
@Preview(showBackground = true)
fun HomeScreenPreview() {
    CampzoneTheme {
        HomeScreen(
            state = HomeUiState(HomePhase
                .Loaded(previewCamping(
                    "summer-2026",
                    "Summer Pathfinder Camp",
                    2026, 6))),
            onOpenCamping = {},
            onRetry = {},
        )
    }
}
