package fr.ziyon.campzone.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CampzoneTheme
import fr.ziyon.campzone.core.designsystem.CzColors
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.model.Announcement
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.Program
import fr.ziyon.campzone.ui.camping.campingDateRange
import fr.ziyon.campzone.ui.camping.label
import fr.ziyon.campzone.ui.camping.previewCamping
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeRoute(
    onOpenCamping: (String) -> Unit,
    onOpenProgram: (campingId: String, programId: String) -> Unit = { _, _ -> },
    onOpenAnnouncement: (String) -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    HomeScreen(
        state = state,
        onOpenCamping = onOpenCamping,
        onOpenProgram = onOpenProgram,
        onOpenAnnouncement = onOpenAnnouncement,
        onOpenNotifications = onOpenNotifications,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenCamping: (String) -> Unit,
    onOpenProgram: (campingId: String, programId: String) -> Unit,
    onOpenAnnouncement: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.czColors.background),
    ) {
        when (val phase = state.phase) {
            HomePhase.Loading -> CzLoadingView(
                modifier = Modifier.align(Alignment.Center),
                message = stringResource(R.string.home_loading_overview),
            )

            is HomePhase.Error -> CzErrorState(
                title = stringResource(R.string.home_error_title),
                message = phase.message ?: stringResource(R.string.home_error_message),
                onRetry = onRetry,
                retryLabel = stringResource(R.string.common_retry),
                modifier = Modifier.align(Alignment.Center),
            )

            is HomePhase.Loaded -> {
                val featured = phase.featuredCamping
                if (featured == null && phase.upcomingPrograms.isEmpty() && phase.announcements.isEmpty()) {
                    CzEmptyState(
                        title = stringResource(R.string.home_empty_dashboard_title),
                        message = stringResource(R.string.home_empty_dashboard_message),
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Park,
                                contentDescription = null,
                                tint = MaterialTheme.czColors.ember,
                                modifier = Modifier.size(CzSpacing.xl),
                            )
                        },
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    HomeDashboard(
                        featuredCamping = featured,
                        upcomingPrograms = phase.upcomingPrograms,
                        announcements = phase.announcements,
                        onOpenCamping = onOpenCamping,
                        onOpenProgram = onOpenProgram,
                        onOpenAnnouncement = onOpenAnnouncement,
                        onOpenNotifications = onOpenNotifications,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeDashboard(
    featuredCamping: Camping?,
    upcomingPrograms: List<Program>,
    announcements: List<Announcement>,
    onOpenCamping: (String) -> Unit,
    onOpenProgram: (campingId: String, programId: String) -> Unit,
    onOpenAnnouncement: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val programsTitle = featuredCamping
        ?.title
        ?.takeUnless { it.isBlank() }
        ?.let { stringResource(R.string.home_programs_for_camping_title, it) }
        ?: stringResource(R.string.home_programs_title)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = CzSpacing.xl,
            end = CzSpacing.xl,
            top = CzSpacing.lg,
            bottom = CzSpacing.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.xxl),
    ) {
        item(key = "header") {
            DashboardHeader(onOpenNotifications = onOpenNotifications)
        }

        if (featuredCamping != null) {
            item(key = "featured") {
                HomeSection(
                    icon = Icons.Filled.Park,
                    title = stringResource(R.string.home_feature_title),
                ) {
                    FeaturedCampingCard(
                        camping = featuredCamping,
                        onClick = { onOpenCamping(featuredCamping.id) },
                    )
                }
            }
        }

        item(key = "programs") {
            HomeSection(
                icon = Icons.Filled.CalendarMonth,
                title = programsTitle,
            ) {
                if (upcomingPrograms.isEmpty()) {
                    HomeEmptyCard(
                        icon = Icons.Filled.CalendarMonth,
                        title = stringResource(R.string.home_programs_empty_title),
                        message = stringResource(R.string.home_programs_empty_message),
                    )
                } else {
                    HomeProgramList(
                        programs = upcomingPrograms,
                        onOpenProgram = { programId ->
                            featuredCamping?.let { onOpenProgram(it.id, programId) }
                        },
                    )
                }
            }
        }

        item(key = "announcements") {
            HomeSection(
                icon = Icons.Filled.Campaign,
                title = stringResource(R.string.home_announcements_title),
            ) {
                if (announcements.isEmpty()) {
                    HomeEmptyCard(
                        icon = Icons.Filled.Campaign,
                        title = stringResource(R.string.home_announcements_empty_title),
                        message = stringResource(R.string.home_announcements_empty_message),
                    )
                } else {
                    HomeAnnouncementList(
                        announcements = announcements,
                        onOpenAnnouncement = onOpenAnnouncement,
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
                    text = stringResource(R.string.home_slogan)
                        .uppercase(Locale.forLanguageTag(ComposeLocale.current.toLanguageTag())),
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

        IconButton(
            onClick = onOpenNotifications,
            modifier = Modifier
                .size(CzSpacing.minTouchTarget)
                .clip(RoundedCornerShape(CzRadius.md))
                .background(MaterialTheme.czColors.surface)
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = stringResource(R.string.home_notifications_content_description),
                tint = MaterialTheme.czColors.ember,
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
private fun HomeSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.sm),
    ) {
        HomeSectionHeader(icon = icon, title = title)
        content()
    }
}

@Composable
private fun HomeProgramList(
    programs: List<Program>,
    onOpenProgram: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.lg))
            .background(MaterialTheme.czColors.surface),
    ) {
        programs.forEachIndexed { index, program ->
            HomeProgramRow(
                program = program,
                onClick = { onOpenProgram(program.id) },
            )
            if (index < programs.lastIndex) {
                Box(
                    modifier = Modifier
                        .padding(start = 62.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.czColors.divider),
                )
            }
        }
    }
}

@Composable
private fun HomeProgramRow(
    program: Program,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = Date()
    val isDueTime = program.startDate <= now && program.endDate >= now
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Column(
            modifier = Modifier.width(46.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = program.startDate.homeProgramTimeText(),
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = program.startDate.homeProgramDayText(),
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }

        Box(
            modifier = Modifier
                .width(3.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (isDueTime) {
                        MaterialTheme.czColors.ember
                    } else {
                        MaterialTheme.czColors.divider
                    },
                ),
        )

        Text(
            text = program.title.ifBlank { stringResource(R.string.home_program_fallback_title) },
            modifier = Modifier.weight(1f),
            color = MaterialTheme.czColors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (isDueTime) {
            Text(
                text = stringResource(R.string.home_program_now),
                color = MaterialTheme.czColors.ember,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.czColors.ember.copy(alpha = 0.12f))
                    .padding(horizontal = CzSpacing.sm, vertical = 3.dp),
                maxLines = 1,
            )
        }

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.czColors.textTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun HomeAnnouncementList(
    announcements: List<Announcement>,
    onOpenAnnouncement: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CzRadius.lg))
            .background(MaterialTheme.czColors.surface),
    ) {
        announcements.forEachIndexed { index, announcement ->
            HomeAnnouncementRow(
                announcement = announcement,
                onClick = { onOpenAnnouncement(announcement.id) },
            )
            if (index < announcements.lastIndex) {
                Box(
                    modifier = Modifier
                        .padding(start = 56.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.czColors.divider),
                )
            }
        }
    }
}

@Composable
private fun HomeAnnouncementRow(
    announcement: Announcement,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CzSpacing.md, vertical = CzSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.czColors.amber.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Campaign,
                contentDescription = null,
                tint = MaterialTheme.czColors.amber,
                modifier = Modifier.size(18.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = announcement.title.ifBlank { stringResource(R.string.home_announcement_fallback_title) },
                color = MaterialTheme.czColors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = announcement.summary,
                color = MaterialTheme.czColors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.czColors.textTertiary,
            modifier = Modifier.size(18.dp),
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
        ratio >= 1f -> stringResource(R.string.home_fully_booked)
        ratio >= 0.8f -> stringResource(R.string.home_almost_full, (ratio * 100).toInt())
        else -> stringResource(R.string.home_percent_filled, (ratio * 100).toInt())
    }
    val registrationSummary = if (capacity > 0) {
        stringResource(R.string.home_registered_capacity, camping.participantCount, capacity)
    } else {
        stringResource(R.string.home_registered_count, camping.participantCount)
    }
    val heroGradient = if (isDarkMode) {
        listOf(MaterialTheme.czColors.night, MaterialTheme.czColors.twilight, Color(0xFF2D1005))
    } else {
        listOf(
            MaterialTheme.czColors.amber,
            MaterialTheme.czColors.primary,
            MaterialTheme.czColors.secondary
        )
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
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.czColors.surface.copy(alpha = 0.85f)
                                    )
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
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = camping.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
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
                                text = camping.location.ifBlank {
                                    stringResource(R.string.home_location_pending)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm),
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Text(
                        text = fillText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = progressColor,
                        maxLines = 1,
                    )
                }

                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape),
                    color = progressColor,
                    trackColor = MaterialTheme.czColors.divider,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = registrationSummary,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.czColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

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
                contentDescription = stringResource(R.string.camping_logo_content_description, title),
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
            modifier = Modifier.weight(1f),
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

private fun Date.homeProgramTimeText(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(this)

private fun Date.homeProgramDayText(): String =
    SimpleDateFormat("EEE", Locale.getDefault()).format(this)

@Composable
@Preview(showBackground = true)
fun HomeScreenPreview() {
    CampzoneTheme {
        val camping = previewCamping(
            "summer-2026",
            "Summer Pathfinder Camp",
            2026, 6
        )
        HomeScreen(
            state = HomeUiState(
                HomePhase.Loaded(
                    featuredCamping = camping,
                    upcomingPrograms = listOf(
                        Program(
                            id = "morning-devotion",
                            campingId = camping.id,
                            campDayId = "${camping.id}-day-preview",
                            title = "Morning devotion",
                            startDate = Date(System.currentTimeMillis() + 60 * 60 * 1000),
                            endDate = Date(System.currentTimeMillis() + 2 * 60 * 60 * 1000),
                        ),
                        Program(
                            id = "team-games",
                            campingId = camping.id,
                            campDayId = "${camping.id}-day-preview",
                            title = "Team games",
                            startDate = Date(System.currentTimeMillis() + 4 * 60 * 60 * 1000),
                            endDate = Date(System.currentTimeMillis() + 5 * 60 * 60 * 1000),
                        ),
                    ),
                )
            ),
            onOpenCamping = {},
            onOpenProgram = { _, _ -> },
            onOpenAnnouncement = {},
            onOpenNotifications = {},
            onRetry = {},
        )
    }
}
