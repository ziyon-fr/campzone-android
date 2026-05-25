package fr.ziyon.campzone.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ChevronRight
import coil.compose.AsyncImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.ziyon.campzone.R
import androidx.compose.material.icons.filled.LocationOn
import fr.ziyon.campzone.core.designsystem.CzColors
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.SectionHeader
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.core.navigation.ScreenColumn
import fr.ziyon.campzone.data.profile.CampingPreview
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.Color

@Composable
fun HomeScreen() {

    ScreenColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.czColors.background)

    ) {
        // Header
        Row(
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.padding(bottom = CzSpacing.lg)

        ) {
            Column() {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Park,
                        contentDescription = null,
                        tint = MaterialTheme.czColors.pine
                    )
                    Text(
                        text = stringResource(R.string.home_slogan),
                        color = MaterialTheme.czColors.textSecondary,
                        fontStyle = MaterialTheme.typography.labelMedium.fontStyle,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = stringResource(id = R.string.home_title),
                    color = MaterialTheme.czColors.textPrimary,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.czColors.surface
                ),

                contentPadding = PaddingValues(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.czColors.ember
                )
            }
        }

        // Body
        /// featured Camping Section
        SectionHeader(
            icon = Icons.Filled.Park, title = stringResource(R.string.home_feature_title)
        ) {

            val camping = CampingPreview(
                id = "1",
                title = "Summer Camp",
                dateRange = "Jun 12 - Jun 18",
                location = "Switzerland",
                registrationStatus = "Open",
                registrationCapacity = 120f,
                registeredMembersAmount = 80f,
                logoURL = "https://..."
            )

            FeaturedCampingCard(
                camping = camping, modifier = Modifier
            )
        }
        // Upcoming sections
        SectionHeader(
            icon = Icons.Filled.CalendarMonth, title = stringResource(R.string.home_feature_title)
        ) {

        }
        /// Announcement section
        SectionHeader(
            icon = Icons.Filled.Info, title = stringResource(R.string.home_feature_title)
        ) {

        }
    }
}

@Composable
fun FeaturedCampingCard(
    camping: CampingPreview, modifier: Modifier = Modifier
) {

    val isDarkMode = isSystemInDarkTheme()

    val ratio = if (camping.registrationCapacity > 0) {
        (camping.registeredMembersAmount.toFloat() / camping.registrationCapacity.toFloat()).coerceIn(
                0f,
                1f
            )
    } else {
        0f
    }

    val progressColor = when {
        ratio < 0.5f -> MaterialTheme.czColors.leaf
        ratio < 0.8f -> MaterialTheme.czColors.amber
        else -> MaterialTheme.czColors.flame
    }

    val heroGradient = if (isDarkMode) {
        listOf(
            MaterialTheme.czColors.night, MaterialTheme.czColors.twilight, Color(0xFF2D1005)
        )
    } else {
        listOf(
            MaterialTheme.czColors.amber,
            MaterialTheme.czColors.primary,
            MaterialTheme.czColors.secondary
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CzRadius.lg.value.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.czColors.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp
        )
    ) {

        Column {

            // HERO
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
            ) {

                // Background gradient
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(heroGradient)
                        )
                )

                // Mountain background
                FeaturedMountainBackground(
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(if (isDarkMode) 0.82f else 0.4f)
                )

                // Scrim
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
                                        CzColors.BackgroundDark.copy(alpha = 0.4f)
                                    )
                                }
                            )
                        )
                )

                // Bottom content
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(CzSpacing.md.value.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(CzSpacing.md.value.dp)
                ) {

                    camping.logoURL?.let {

                        AsyncImage(
                            model = it,
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(CzRadius.md.value.dp))
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.55f),
                                    RoundedCornerShape(CzRadius.md.value.dp)
                                )
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {

                        Text(
                            text = camping.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 2
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {

                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(14.dp)
                            )

                            Text(
                                text = camping.location,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            // INFO SECTION
            Column(
                modifier = Modifier.padding(CzSpacing.md.value.dp),
                verticalArrangement = Arrangement.spacedBy(CzSpacing.sm.value.dp)

            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {

                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.czColors.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )

                        Text(
                            text = camping.dateRange,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.czColors.textSecondary
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "${(ratio * 100).toInt()}% filled",
                        style = MaterialTheme.typography.bodySmall,
                        color = progressColor
                    )
                }

                // Progress bar
                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape),
                    color = progressColor,
                    trackColor = MaterialTheme.czColors.divider
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = "${camping.registeredMembersAmount} / ${camping.registrationCapacity} registered",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.czColors.textSecondary
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


@Composable
@Preview(showBackground = true)
fun HomeScreenPreview() {
    HomeScreen()
}