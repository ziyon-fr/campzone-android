package fr.ziyon.campzone.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun FeaturedMountainBackground(
    modifier: Modifier = Modifier,
    opacity: Float = 0.15f,
) {
    Canvas(
        modifier = modifier
            // Low opacity to act as a background watermark
            .alpha(opacity.coerceIn(0f, 1f))
            // Slight offset to bleed off the edge of the card
            .offset(y = 10.dp)
    ) {
        val w = size.width
        val h = size.height

        // Background Mountain (Secondary)
        val bgPeakWidth = w * 0.6f
        val bgPeakHeight = h * 0.7f
        val bgCenterX = w * 0.75f
        val bgCenterY = h * 1.0f
        val bgTop = bgCenterY - bgPeakHeight
        val bgLeft = bgCenterX - bgPeakWidth / 2
        val bgRight = bgCenterX + bgPeakWidth / 2

        val bgPeakPath = Path().apply {
            mountainPeak(bgCenterX, bgCenterY, bgPeakWidth, bgPeakHeight)
        }
        drawPath(
            path = bgPeakPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF064E3B), Color(0xFF022C22)),
                start = Offset(bgLeft, bgTop),
                end = Offset(bgRight, bgCenterY)
            )
        )

        val bgSnowPath = Path().apply {
            mountainSnowCap(bgCenterX, h * 0.3f, bgPeakWidth, bgPeakHeight)
        }
        drawPath(
            path = bgSnowPath,
            color = Color.White.copy(alpha = 0.4f)
        )

        // Foreground Mountain (Primary)
        val fgPeakWidth = w * 0.8f
        val fgPeakHeight = h * 0.9f
        val fgCenterX = w * 0.4f
        val fgCenterY = h * 1.0f
        val fgTop = fgCenterY - fgPeakHeight
        val fgLeft = fgCenterX - fgPeakWidth / 2
        val fgRight = fgCenterX + fgPeakWidth / 2

        val fgPeakPath = Path().apply {
            mountainPeak(fgCenterX, fgCenterY, fgPeakWidth, fgPeakHeight)
        }
        drawPath(
            path = fgPeakPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF10B981), Color(0xFF059669)),
                start = Offset(fgLeft, fgTop),
                end = Offset(fgRight, fgCenterY)
            )
        )

        val fgSnowPath = Path().apply {
            mountainSnowCap(fgCenterX, h * 0.1f, fgPeakWidth, fgPeakHeight)
        }
        drawPath(
            path = fgSnowPath,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

// MARK: - Shapes (Path Extensions)

private fun Path.mountainPeak(centerX: Float, centerY: Float, width: Float, height: Float) {
    val left = centerX - width / 2
    val right = centerX + width / 2
    val top = centerY - height
    val bottom = centerY

    moveTo(centerX, top)
    lineTo(right, bottom)
    lineTo(left, bottom)
    close()
}

private fun Path.mountainSnowCap(centerX: Float, top: Float, width: Float, height: Float) {
    moveTo(centerX, top)
    lineTo(centerX + width * 0.15f, top + height * 0.3f)
    lineTo(centerX + width * 0.05f, top + height * 0.25f)
    lineTo(centerX, top + height * 0.35f)
    lineTo(centerX - width * 0.05f, top + height * 0.25f)
    lineTo(centerX - width * 0.15f, top + height * 0.3f)
    close()
}

// MARK: - Usage Example in a Card

@Composable
fun ExpeditionCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(16.dp)
            // Background Card
            .background(Color(0xFF18181B), RoundedCornerShape(32.dp))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(32.dp)),
        contentAlignment = Alignment.BottomEnd
    ) {
        // The Mountain Background Component
        FeaturedMountainBackground(
            modifier = Modifier.size(width = 180.dp, height = 120.dp)
        )

        // Card Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "SUMMER LAKE EXPEDITION",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF34D399) // emerald-400
                )

                Text(
                    text = "Pathfinder Series",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange, // Or import your specific calendar icon
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "July 18 - 24",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ExpeditionCardPreview() {
    MaterialTheme {
        Surface(color = Color.Black) {
            ExpeditionCard()
        }
    }
}
