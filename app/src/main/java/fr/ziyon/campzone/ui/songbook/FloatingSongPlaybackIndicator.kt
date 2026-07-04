package fr.ziyon.campzone.ui.songbook

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.Crossfade
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTypeScale
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.model.Song
import kotlin.math.roundToInt

@Composable
fun FloatingSongPlaybackIndicator(
    song: Song,
    isPlaying: Boolean,
    hasNextSong: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val density = LocalDensity.current
    var collapsedEdge by remember(song.id) { mutableStateOf<PlayerEdge?>(null) }
    var expandedOffsetX by remember(song.id) { mutableStateOf(0f) }
    var expandedOffsetY by remember(song.id) { mutableStateOf(0f) }
    var dragX by remember(song.id) { mutableStateOf(0f) }
    var dragY by remember(song.id) { mutableStateOf(0f) }
    var isDragging by remember(song.id) { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val cardWidth = minOf(
            (maxWidth - CzSpacing.base * 2).coerceAtLeast(0.dp),
            maxWidth * 0.9f,
        )
        val cardHeight = 52.dp
        val cardWidthPx = with(density) { cardWidth.toPx() }
        val cardHeightPx = with(density) { cardHeight.toPx() }
        val peekPx = with(density) { 38.dp.toPx() }
        val sideInsetPx = with(density) { CzSpacing.base.toPx() }
        val topInsetPx = sideInsetPx
        val dragOvershootPx = cardWidthPx * 0.42f
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(cardWidthPx)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(cardHeightPx)
        val maxExpandedX = (((widthPx - cardWidthPx) / 2f) - sideInsetPx).coerceAtLeast(0f)
        val minExpandedX = -maxExpandedX
        val minY = -((heightPx - cardHeightPx) - topInsetPx).coerceAtLeast(0f)
        fun collapsedX(edge: PlayerEdge): Float =
            if (edge == PlayerEdge.Leading) {
                minExpandedX - cardWidthPx + peekPx
            } else {
                maxExpandedX + cardWidthPx - peekPx
            }

        val restingX = collapsedEdge?.let(::collapsedX) ?: expandedOffsetX.coerceIn(minExpandedX, maxExpandedX)
        val dragMinX = minOf(collapsedX(PlayerEdge.Leading), minExpandedX - dragOvershootPx)
        val dragMaxX = maxOf(collapsedX(PlayerEdge.Trailing), maxExpandedX + dragOvershootPx)
        val targetX = if (isDragging) {
            (restingX + dragX).coerceIn(dragMinX, dragMaxX)
        } else {
            restingX
        }
        val targetY = (expandedOffsetY + dragY).coerceIn(minY, 0f)
        val settleSpec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        val animatedX by animateFloatAsState(targetX, animationSpec = settleSpec, label = "floating-player-x")
        val animatedY by animateFloatAsState(targetY, animationSpec = settleSpec, label = "floating-player-y")
        val scale by animateFloatAsState(
            if (isDragging) 1.03f else 1f,
            animationSpec = settleSpec,
            label = "floating-player-scale",
        )
        val isCollapsed = collapsedEdge != null
        val alpha by animateFloatAsState(if (isCollapsed) 0.96f else 1f, animationSpec = settleSpec, label = "floating-player-alpha")
        val shape = RoundedCornerShape(40.dp)
        val onCardClick = {
            if (isCollapsed) {
                collapsedEdge = null
            } else {
                onOpen()
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(animatedX.roundToInt(), animatedY.roundToInt()) }
                .width(cardWidth)
                .height(cardHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .shadow(if (isCollapsed) 0.dp else 18.dp, shape, clip = false)
                .clip(shape)
                .background(colors.card)
                .border(
                    BorderStroke(
                        0.3.dp,
                        Brush.linearGradient(
                            listOf(
                                colors.divider.copy(alpha = 0.86f),
                                colors.accent.copy(alpha = 0.34f),
                                colors.divider.copy(alpha = 0.86f),
                            ),
                        ),
                    ),
                    shape,
                )
                .pointerInput(song.id, cardWidthPx, widthPx, heightPx, collapsedEdge) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragX += dragAmount.x
                            dragY += dragAmount.y
                        },
                        onDragEnd = {
                            val proposedX = restingX + dragX
                            val proposedY = (expandedOffsetY + dragY).coerceIn(minY, 0f)
                            collapsedEdge = when {
                                proposedX < minExpandedX - cardWidthPx * 0.18f -> PlayerEdge.Leading
                                proposedX > maxExpandedX + cardWidthPx * 0.18f -> PlayerEdge.Trailing
                                else -> null
                            }
                            if (collapsedEdge == null) {
                                expandedOffsetX = proposedX.coerceIn(minExpandedX, maxExpandedX)
                            }
                            expandedOffsetY = proposedY
                            dragX = 0f
                            dragY = 0f
                            isDragging = false
                        },
                        onDragCancel = {
                            dragX = 0f
                            dragY = 0f
                            isDragging = false
                        },
                    )
                }
                .clickable(onClick = onCardClick),
            contentAlignment = Alignment.Center,
        ) {
            val edge = collapsedEdge ?: PlayerEdge.Leading
            Crossfade(
                targetState = isCollapsed,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "floating-player-content",
            ) { collapsed ->
                if (collapsed) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                    ) {
                        Box(
                            modifier = Modifier
                                .align(if (edge == PlayerEdge.Leading) Alignment.CenterEnd else Alignment.CenterStart)
                                .width(38.dp)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (edge == PlayerEdge.Leading) {
                                    Icons.Rounded.ChevronRight
                                } else {
                                    Icons.Rounded.ChevronLeft
                                },
                                contentDescription = stringResource(R.string.songbook_now_playing_restore),
                                tint = colors.textSecondary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = CzSpacing.md, end = CzSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SongArtwork(song = song, size = 36.dp, isPlaying = isPlaying)

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                        ) {
                            Text(
                                text = song.title,
                                style = CzTypeScale.subhead.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = song.artist.ifBlank { song.subtitleText() },
                                style = CzTypeScale.caption,
                                color = colors.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        CircleIconButton(
                            icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(if (isPlaying) R.string.songbook_pause else R.string.songbook_play),
                            containerColor = colors.accent,
                            contentColor = Color.White,
                            onClick = onToggle,
                            size = 34.dp,
                        )
                        CircleIconButton(
                            icon = Icons.Rounded.SkipNext,
                            contentDescription = stringResource(R.string.songbook_next_song),
                            containerColor = colors.textSecondary.copy(alpha = if (hasNextSong) 0.14f else 0.07f),
                            contentColor = colors.textSecondary.copy(alpha = if (hasNextSong) 0.88f else 0.38f),
                            enabled = hasNextSong,
                            onClick = onNext,
                            size = 32.dp,
                        )
                        CircleIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.songbook_stop),
                            containerColor = colors.textSecondary.copy(alpha = 0.12f),
                            contentColor = colors.textSecondary,
                            onClick = onStop,
                            size = 30.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

private enum class PlayerEdge { Leading, Trailing }
