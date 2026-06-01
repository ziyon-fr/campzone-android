package fr.ziyon.campzone.ui.teams

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ziyon.campzone.R
import fr.ziyon.campzone.data.model.Team
import java.util.Date
import kotlinx.coroutines.delay

@Composable
fun WinnerRevealCeremonyOverlay(
    winningTeam: Team,
    modifier: Modifier = Modifier,
    revealedAt: Date? = null,
    countdownSeconds: Int = 10,
    onComplete: () -> Unit,
) {
    // Anchor the countdown on the shared `revealedAt` so every device watching
    // when the admin reveals converges on the same trophy moment
    // (`revealedAt + window`). Devices that open after the window has elapsed
    // skip straight to the trophy.
    val initialCountdown = remember(winningTeam.id) {
        revealCountdownSeconds(revealedAt, Date(), countdownSeconds)
    }
    var countdown by remember(winningTeam.id) { mutableStateOf(initialCountdown) }
    var stageVisible by remember(winningTeam.id) { mutableStateOf(initialCountdown <= 0) }
    var curtainsOpen by remember(winningTeam.id) { mutableStateOf(initialCountdown <= 0) }
    var labelsVisible by remember(winningTeam.id) { mutableStateOf(false) }
    var confettiVisible by remember(winningTeam.id) { mutableStateOf(false) }

    LaunchedEffect(winningTeam.id) {
        for (tick in initialCountdown downTo 1) {
            countdown = tick
            delay(1_000)
        }

        stageVisible = true
        curtainsOpen = true
        delay(2_400)

        confettiVisible = true
        labelsVisible = true
        delay(5_000)

        confettiVisible = false
        delay(450)
        onComplete()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val leftOffset by animateDpAsState(
            targetValue = if (curtainsOpen) -maxWidth else 0.dp,
            animationSpec = tween(durationMillis = 2_200, easing = FastOutSlowInEasing),
            label = "leftCurtain",
        )
        val rightOffset by animateDpAsState(
            targetValue = if (curtainsOpen) maxWidth else 0.dp,
            animationSpec = tween(durationMillis = 2_200, easing = FastOutSlowInEasing),
            label = "rightCurtain",
        )
        val trophyOffset by animateDpAsState(
            targetValue = if (stageVisible) 0.dp else -maxHeight,
            animationSpec = spring(dampingRatio = 0.66f, stiffness = 80f),
            label = "trophyDrop",
        )
        val spotlightAlpha by animateFloatAsState(
            targetValue = if (stageVisible) 1f else 0f,
            animationSpec = tween(durationMillis = 1_400, delayMillis = 400),
            label = "spotlight",
        )
        val labelAlpha by animateFloatAsState(
            targetValue = if (labelsVisible) 1f else 0f,
            animationSpec = tween(durationMillis = 450),
            label = "winnerLabels",
        )

        Spotlight(alpha = spotlightAlpha)

        WinnerStage(
            winningTeam = winningTeam,
            labelAlpha = labelAlpha,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = trophyOffset)
                .padding(top = if (maxHeight * 0.10f > 48.dp) maxHeight * 0.10f else 48.dp),
        )

        if (confettiVisible) {
            ConfettiField(modifier = Modifier.fillMaxSize())
        }

        CurtainPanel(
            side = CurtainSide.Leading,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = leftOffset)
                .width(maxWidth * 0.5f + 36.dp)
                .fillMaxHeight(),
        )
        CurtainPanel(
            side = CurtainSide.Trailing,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = rightOffset)
                .width(maxWidth * 0.5f + 36.dp)
                .fillMaxHeight(),
        )

        if (!stageVisible) {
            CountdownOverlay(
                value = countdown,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun WinnerStage(
    winningTeam: Team,
    labelAlpha: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.EmojiEvents,
            contentDescription = null,
            tint = Color(0xFFFFE48A),
            modifier = Modifier.size(220.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(labelAlpha)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.teams_ceremony_champions).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                ),
                color = Color(0xFFFFE48A),
                textAlign = TextAlign.Center,
            )
            Text(
                text = winningTeam.name,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 38.sp,
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.teams_ceremony_points, winningTeam.totalScore),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                ),
                color = Color(0xFFFFE48A),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CountdownOverlay(value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.teams_ceremony_revealing_in).uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
            ),
            color = Color.White.copy(alpha = 0.82f),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "$value",
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 140.sp,
                fontWeight = FontWeight.Black,
            ),
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Spotlight(alpha: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFEBA8).copy(alpha = 0.85f * alpha),
                    Color(0xFFFF9F30).copy(alpha = 0.30f * alpha),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.5f, size.height * 0.35f),
                radius = size.maxDimension * 0.45f,
            ),
            radius = size.maxDimension * 0.45f,
            center = Offset(size.width * 0.5f, size.height * 0.35f),
        )
    }
}

@Composable
private fun ConfettiField(modifier: Modifier = Modifier) {
    val specs = remember {
        List(84) { index ->
            ConfettiSpec(
                x = ((index * 37) % 100) / 100f,
                y = ((index * 53) % 100) / 100f,
                radius = 3f + (index % 5),
                color = confettiColors[index % confettiColors.size],
            )
        }
    }
    Canvas(modifier = modifier) {
        specs.forEach { spec ->
            drawCircle(
                color = spec.color,
                radius = spec.radius,
                center = Offset(size.width * spec.x, size.height * spec.y),
            )
        }
    }
}

private enum class CurtainSide { Leading, Trailing }

@Composable
private fun CurtainPanel(side: CurtainSide, modifier: Modifier = Modifier) {
    val colors = if (side == CurtainSide.Leading) {
        listOf(Color(0xFF260706), Color(0xFF5C0F0E), Color(0xFF8B1A18), Color(0xFF5C0F0E))
    } else {
        listOf(Color(0xFF5C0F0E), Color(0xFF8B1A18), Color(0xFF5C0F0E), Color(0xFF260706))
    }
    Box(
        modifier = modifier
            .background(Brush.horizontalGradient(colors))
            .drawBehind {
                val stripeWidth = size.width / 14f
                repeat(14) { index ->
                    drawRect(
                        color = if (index % 2 == 0) Color.Black.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                        topLeft = Offset(stripeWidth * index, 0f),
                        size = Size(stripeWidth, size.height),
                    )
                }
            },
    )
}

private data class ConfettiSpec(
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: Color,
)

private val confettiColors = listOf(
    Color(0xFFFFE48A),
    Color(0xFFFFB347),
    Color(0xFFFF6B35),
    Color(0xFF66BB6A),
    Color(0xFFFFFFFF),
)

/**
 * Whole seconds left in the reveal countdown, measured from the shared
 * `revealedAt` anchor (`revealedAt + windowSeconds`). Because every device
 * computes against the same absolute anchor, their countdowns converge on the
 * same trophy moment. Returns 0 (no countdown → straight to the trophy) when
 * `revealedAt` is null or the window has already elapsed; never exceeds
 * `windowSeconds`.
 */
internal fun revealCountdownSeconds(
    revealedAt: Date?,
    now: Date = Date(),
    windowSeconds: Int = 10,
): Int {
    if (revealedAt == null) return 0
    val remainingMillis = (revealedAt.time + windowSeconds * 1000L) - now.time
    if (remainingMillis <= 0L) return 0
    val seconds = ((remainingMillis + 999L) / 1000L).toInt()
    return seconds.coerceIn(0, windowSeconds)
}
