package fr.ziyon.campzone.ui.chat

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.model.ChatAttachment
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Compact play / scrub / duration row for a chat voice note, mirroring the iOS
 * `ChatVoiceNoteView`. Each bubble owns its own [MediaPlayer] so several notes
 * in a timeline don't fight over one instance; the player is released on
 * dispose.
 */
@Composable
fun ChatVoiceNoteView(
    attachment: ChatAttachment,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val controlTint = if (isCurrentUser) Color.White else colors.ember
    val secondaryTint = if (isCurrentUser) Color.White.copy(alpha = 0.85f) else colors.textSecondary
    val totalDuration = (attachment.durationSeconds ?: 0.0).coerceAtLeast(0.0)

    val player = remember { MediaPlayer() }
    var prepared by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentTime by remember { mutableDoubleStateOf(0.0) }

    DisposableEffect(attachment.url) {
        player.setOnPreparedListener {
            prepared = true
            preparing = false
            it.start()
            isPlaying = true
        }
        player.setOnCompletionListener {
            isPlaying = false
            currentTime = 0.0
            it.seekTo(0)
        }
        onDispose {
            runCatching { player.stop() }
            player.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentTime = player.currentPosition / 1000.0
            delay(100)
        }
    }

    fun toggle() {
        when {
            isPlaying -> {
                player.pause()
                isPlaying = false
            }
            prepared -> {
                player.start()
                isPlaying = true
            }
            !preparing -> {
                preparing = true
                runCatching {
                    player.reset()
                    player.setDataSource(attachment.url)
                    player.prepareAsync()
                }.onFailure { preparing = false }
            }
        }
    }

    val progress = if (totalDuration > 0) (currentTime / totalDuration).coerceIn(0.0, 1.0) else 0.0
    val displayedSeconds = if (isPlaying || currentTime > 0) currentTime else totalDuration

    Row(
        modifier = modifier.semantics {
            contentDescription = "Voice message, ${totalDuration.roundToInt()} seconds"
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = ::toggle) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle,
                contentDescription = stringResource(
                    if (isPlaying) R.string.chat_voice_pause else R.string.chat_voice_play,
                ),
                tint = controlTint,
                modifier = Modifier.size(30.dp),
            )
        }
        Column(
            modifier = Modifier.width(140.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LinearProgressIndicator(
                progress = { progress.toFloat() },
                color = controlTint,
                trackColor = secondaryTint.copy(alpha = 0.3f),
            )
            Text(
                text = formatVoiceTime(displayedSeconds),
                color = secondaryTint,
                fontSize = 11.sp,
                textAlign = TextAlign.Start,
            )
        }
        Icon(
            imageVector = Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = secondaryTint,
            modifier = Modifier.size(16.dp),
        )
    }
}

private fun formatVoiceTime(seconds: Double): String {
    val total = seconds.roundToInt()
    return "%d:%02d".format(total / 60, total % 60)
}
