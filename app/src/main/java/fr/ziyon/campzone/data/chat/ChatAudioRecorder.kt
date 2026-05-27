package fr.ziyon.campzone.data.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.UUID

/**
 * Records short voice notes for chat via [MediaRecorder] (AAC in an MP4/`.m4a`
 * container), the Android counterpart of the iOS `ChatAudioRecorder`. Created by
 * the composer with the local [Context] and held in `remember`; exposes a tiny
 * Compose-observable surface (recording state) plus elapsed time computed from
 * the start timestamp so the recording bar can tick without an internal timer.
 */
class ChatAudioRecorder(private val context: Context) {

    var isRecording by mutableStateOf(false)
        private set

    /** Set by the composer when the OS mic permission was denied. */
    var permissionDenied by mutableStateOf(false)

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0L

    /** Starts recording to a temp `.m4a`. Returns false if the device refused. */
    fun start(): Boolean {
        if (isRecording) return true
        val file = File(context.cacheDir, "voice-${UUID.randomUUID()}.m4a")
        val recorder = newRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44_100)
            setAudioEncodingBitRate(96_000)
            setMaxDuration(MAX_DURATION_MS.toInt())
            setOutputFile(file.absolutePath)
        }
        return try {
            recorder.prepare()
            recorder.start()
            this.recorder = recorder
            this.outputFile = file
            this.startedAtMs = System.currentTimeMillis()
            this.isRecording = true
            true
        } catch (e: Exception) {
            recorder.runCatching { reset() }
            recorder.runCatching { release() }
            file.delete()
            false
        }
    }

    /** Elapsed recording time in seconds (0 when not recording). */
    fun elapsedSeconds(): Double =
        if (isRecording) (System.currentTimeMillis() - startedAtMs) / 1000.0 else 0.0

    /**
     * Stops recording and returns the file + measured duration, or null when the
     * clip was too short to keep (an accidental tap) — the file is discarded.
     */
    fun stop(): RecordedVoice? {
        val recorder = recorder ?: return null
        val file = outputFile
        val duration = (System.currentTimeMillis() - startedAtMs) / 1000.0
        val finished = recorder.runCatching { stop() }.isSuccess
        recorder.runCatching { release() }
        cleanup()
        if (!finished || file == null || duration < MIN_DURATION_SECONDS) {
            file?.delete()
            return null
        }
        return RecordedVoice(file = file, durationSeconds = duration)
    }

    /** Aborts the current recording and deletes the temp file. */
    fun cancel() {
        val recorder = recorder ?: return
        recorder.runCatching { stop() }
        recorder.runCatching { release() }
        outputFile?.delete()
        cleanup()
    }

    private fun cleanup() {
        recorder = null
        outputFile = null
        startedAtMs = 0L
        isRecording = false
    }

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    data class RecordedVoice(val file: File, val durationSeconds: Double)

    companion object {
        /** Clips shorter than this are discarded as accidental taps. */
        const val MIN_DURATION_SECONDS = 1.0

        /** Hard cap so a forgotten recording can't balloon the upload. */
        const val MAX_DURATION_MS = 5L * 60 * 1000
    }
}
