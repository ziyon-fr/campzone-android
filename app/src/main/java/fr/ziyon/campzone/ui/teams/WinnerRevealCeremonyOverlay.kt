package fr.ziyon.campzone.ui.teams

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ziyon.campzone.R
import fr.ziyon.campzone.data.model.Team
import java.util.Date
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
fun WinnerRevealCeremonyOverlay(
    winningTeam: Team,
    modifier: Modifier = Modifier,
    revealedAt: Date? = null,
    countdownSeconds: Int = 10,
    onComplete: () -> Unit,
) {
    val initialCountdown = remember(winningTeam.id) {
        revealCountdownSeconds(revealedAt, Date(), countdownSeconds)
    }
    var countdown by remember(winningTeam.id) { mutableStateOf(initialCountdown) }
    var stageVisible by remember(winningTeam.id) { mutableStateOf(initialCountdown <= 0) }
    var curtainsOpen by remember(winningTeam.id) { mutableStateOf(initialCountdown <= 0) }
    var labelsVisible by remember(winningTeam.id) { mutableStateOf(false) }
    var confettiVisible by remember(winningTeam.id) { mutableStateOf(false) }

    val view = LocalView.current

    LaunchedEffect(winningTeam.id) {
        for (tick in initialCountdown downTo 1) {
            countdown = tick
            // Heavy haptic for final 3 ticks, light otherwise — mirrors iOS HapticFeedback.impact
            view.performHapticFeedback(
                if (tick <= 3) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY,
            )
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

        // Two-stage spotlight: quickly ramp to 0.6, hold 1 s, then ease to 1.0 over 1.4 s
        // Mirrors iOS: withAnimation(.easeInOut(0.4)) { opacity=0.6 } +
        //              withAnimation(.easeInOut(1.4).delay(1.0)) { opacity=1.0 }
        val spotlightAlpha by animateFloatAsState(
            targetValue = if (stageVisible) 1f else 0f,
            animationSpec = if (stageVisible) keyframes {
                durationMillis = 2_800
                0.0f at 0
                0.6f at 400 using FastOutSlowInEasing
                0.6f at 1_400
                1.0f at 2_800 using FastOutSlowInEasing
            } else tween(durationMillis = 400),
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

// MARK: - Stage

@Composable
private fun WinnerStage(
    winningTeam: Team,
    labelAlpha: Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val trophySize = (maxWidth * 0.55f).coerceAtMost(220.dp)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Canvas trophy with golden glow halo — approximates iOS
            // .shadow(color: FFE48A @ 0.55, radius: 32, y: 12)
            TrophyView(
                teamName = winningTeam.name,
                size = trophySize,
                modifier = Modifier.drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFE48A).copy(alpha = 0.55f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width / 2f, size.height * 0.6f),
                            radius = size.minDimension * 0.90f,
                        ),
                    )
                },
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
}

// MARK: - Countdown

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
        // Digit transition: scale+fade in from 50%, scale+fade out to 50%
        // Mirrors iOS .id("count-\(value)") .transition(.scale(0.5).combined(.opacity))
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (scaleIn(initialScale = 0.5f, animationSpec = tween(300)) +
                    fadeIn(animationSpec = tween(300))) togetherWith
                    (scaleOut(targetScale = 0.5f, animationSpec = tween(200)) +
                        fadeOut(animationSpec = tween(200)))
            },
            label = "countdown",
        ) { displayValue ->
            Text(
                text = "$displayValue",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 140.sp,
                    fontWeight = FontWeight.Black,
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// MARK: - Spotlight

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

// MARK: - Confetti

@Composable
private fun ConfettiField(modifier: Modifier = Modifier) {
    val specs = remember {
        List(84) { index ->
            ConfettiSpec(
                x = ((index * 37) % 100) / 100f,
                yStart = ((index * 53) % 100) / 100f,
                radius = 4f + (index % 5) * 1.5f,
                color = confettiColors[index % confettiColors.size],
                speed = 0.60f + (index % 7) * 0.12f,
                wobble = 0.02f + (index % 4) * 0.01f,
                isRect = index % 3 == 0,
                rotationSpeed = 0.8f + (index % 5) * 0.4f,
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "confetti")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "confettiProgress",
    )

    Canvas(modifier = modifier) {
        specs.forEach { spec ->
            val y = (spec.yStart + progress * spec.speed) % 1f
            val wobbleX = sin(progress * spec.rotationSpeed * 2.0 * PI).toFloat() * spec.wobble
            val x = (spec.x + wobbleX).coerceIn(0f, 1f)
            val rotation = (progress * spec.rotationSpeed * 360f) % 360f

            withTransform({
                translate(size.width * x, size.height * y)
                rotate(rotation, pivot = Offset.Zero)
            }) {
                if (spec.isRect) {
                    drawRect(
                        color = spec.color,
                        topLeft = Offset(-spec.radius, -spec.radius * 0.5f),
                        size = Size(spec.radius * 2f, spec.radius),
                    )
                } else {
                    drawCircle(color = spec.color, radius = spec.radius, center = Offset.Zero)
                }
            }
        }
    }
}

// MARK: - Curtain

private enum class CurtainSide { Leading, Trailing }

private val curtainColorStops = arrayOf(
    0f to Color(0xFF260706),
    0.30f to Color(0xFF5C0F0E),
    0.55f to Color(0xFF8B1A18),
    0.80f to Color(0xFF5C0F0E),
    1f to Color(0xFF260706),
)

@Composable
private fun CurtainPanel(side: CurtainSide, modifier: Modifier = Modifier) {
    // Use Canvas so we can layer gradient + striations + inner shadow in one pass
    Canvas(modifier = modifier) {
        // 5-stop velvet gradient mirrored per side — mirrors iOS LinearGradient with
        // startPoint: side == .leading ? .leading : .trailing
        val gradientBrush = Brush.horizontalGradient(
            colorStops = curtainColorStops,
            startX = if (side == CurtainSide.Leading) 0f else size.width,
            endX = if (side == CurtainSide.Leading) size.width else 0f,
        )
        drawRect(brush = gradientBrush)

        // Vertical pleat striations for theatrical depth
        val stripeWidth = size.width / 14f
        repeat(14) { index ->
            drawRect(
                color = if (index % 2 == 0) {
                    Color.Black.copy(alpha = 0.18f)
                } else {
                    Color.White.copy(alpha = 0.05f)
                },
                topLeft = Offset(stripeWidth * index, 0f),
                size = Size(stripeWidth, size.height),
            )
        }

        // Inner-edge shadow — approximates iOS .shadow(.black @ 0.6, radius: 12, x: ±6)
        val shadowWidth = size.width * 0.14f
        val (shadowStart, shadowEnd, shadowColors) = if (side == CurtainSide.Leading) {
            Triple(
                size.width - shadowWidth,
                size.width,
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.60f)),
            )
        } else {
            Triple(
                0f,
                shadowWidth,
                listOf(Color.Black.copy(alpha = 0.60f), Color.Transparent),
            )
        }
        drawRect(
            brush = Brush.horizontalGradient(
                colors = shadowColors,
                startX = shadowStart,
                endX = shadowEnd,
            ),
        )
    }
}

// MARK: - Models

private data class ConfettiSpec(
    val x: Float,
    val yStart: Float,
    val radius: Float,
    val color: Color,
    val speed: Float,
    val wobble: Float,
    val isRect: Boolean,
    val rotationSpeed: Float,
)

private val confettiColors = listOf(
    Color(0xFFFFE48A),
    Color(0xFFFFB347),
    Color(0xFFFF6B35),
    Color(0xFF66BB6A),
    Color(0xFFFFFFFF),
)

// MARK: - Helpers

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
