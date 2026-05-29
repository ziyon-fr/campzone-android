package fr.ziyon.campzone.ui.profile.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import androidx.compose.ui.graphics.*
import fr.ziyon.campzone.core.designsystem.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import fr.ziyon.campzone.data.model.Achievement
import fr.ziyon.campzone.data.model.AchievementCatalog
import fr.ziyon.campzone.data.model.displayName

@Composable
fun BadgeCard(
    achievement: Achievement,
    isEarned: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.czColors
    val rarity = achievement.rarity

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = colors.surface,
                shape = RoundedCornerShape(18.dp)
            )
            .border(
                width = if (isEarned) 1.5.dp else 0.dp,
                color = (if (isEarned) rarity.glowColor.copy(alpha = 0.5f) else Color.Transparent) as Color,
                shape = RoundedCornerShape(18.dp)
            )
            .padding(16.dp)
    ) {
        BadgeIconLayer(
            achievement = achievement,
            isEarned = isEarned
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = colors.textPrimary,
                maxLines = 1,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (isEarned)  { rarity.materialName } else  {rarity.displayName },
                fontStyle = MaterialTheme.typography.bodyMedium.fontStyle,
                color = if (isEarned) {rarity.glowColor} else {achievement.tint.color},
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BadgeIconLayer(
    achievement: Achievement,
    isEarned: Boolean
) {
    if (isEarned) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    ambientColor = achievement.rarity.glowColor.copy(alpha = 0.55f),
                    spotColor = achievement.rarity.glowColor.copy(alpha = 0.55f)
                )
                .background(
                    brush = achievement.rarity.medalBrush,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            EngravingLayer(
                imageVector = achievement.icon,
                modifier = Modifier.size(30.dp)
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.czColors.textSecondary.copy(alpha = 0.18f),
                            MaterialTheme.czColors.textSecondary.copy(alpha = 0.08f)
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = achievement.icon,
                contentDescription = null,
                tint = MaterialTheme.czColors.textSecondary.copy(alpha = 0.45f),
                modifier = Modifier.size(30.dp)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
                    .size(22.dp)
                    .background(
                        color = MaterialTheme.czColors.textPrimary.copy(alpha = 0.7f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun EngravingLayer(
    imageVector: ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.38f),
            modifier = Modifier
                .matchParentSize()
                .offset(x = 1.2.dp, y = 1.4.dp)
                .blur(0.6.dp)
        )

        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier
                .matchParentSize()
                .offset(x = (-0.8).dp, y = (-1.0).dp)
                .blur(0.3.dp)
        )

        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.20f),
            modifier = Modifier.matchParentSize()
        )
    }
}



@Preview
@Composable
private fun BadgeCardPreview() {
    val earnedIds = setOf(
        "first-adventure",
        "team-captain",
        "camp-mentor",
        "challenge-champion",
        "campfire-legend"
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.background(MaterialTheme.czColors.background)
    ) {
        items(AchievementCatalog.all) { achievement ->
            BadgeCard(
                achievement = achievement,
                isEarned = achievement.id in earnedIds
            )
        }
    }
}