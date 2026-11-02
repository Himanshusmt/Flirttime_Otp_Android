package com.flirttime.feature.chat.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

data class VoiceRecording(
    val file: File,
    val durationMs: Long
)

/**
 * Chat voice note recorder. Mirrors the iOS settings: MPEG-4/AAC, 44.1 kHz, mono,
 * high bitrate — written to a `.m4a` file in the app cache.
 */
class VoiceNoteRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    fun start(): Boolean {
        if (recorder != null) return true
        val file = File(context.cacheDir, "chat_voice_${System.currentTimeMillis()}.m4a")

        return try {
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setAudioChannels(1)
                setMaxDuration(MAX_DURATION_MS.toInt())
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            recorder = mediaRecorder
            outputFile = file
            startedAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            runCatching { recorder?.release() }
            recorder = null
            outputFile?.delete()
            outputFile = null
            false
        }
    }

    /** Stops and returns the recording, or `null` when nothing usable was captured. */
    fun stop(): VoiceRecording? {
        val mediaRecorder = recorder ?: return null
        val file = outputFile
        val durationMs = System.currentTimeMillis() - startedAt

        recorder = null
        outputFile = null

        try {
            mediaRecorder.stop()
        } catch (e: Exception) {
            // Stop throws when the clip is shorter than the codec needs.
            mediaRecorder.reset()
        } finally {
            mediaRecorder.release()
        }

        if (file == null || !file.exists() || file.length() <= 0L || durationMs < MIN_DURATION_MS) {
            file?.delete()
            return null
        }
        return VoiceRecording(file = file, durationMs = durationMs)
    }

    fun cancel() {
        val mediaRecorder = recorder
        recorder = null

        if (mediaRecorder != null) {
            runCatching { mediaRecorder.stop() }
            runCatching { mediaRecorder.reset() }
            runCatching { mediaRecorder.release() }
        }
        outputFile?.delete()
        outputFile = null
    }

    companion object {
        const val MAX_DURATION_MS = 60_000L
        const val MIN_DURATION_MS = 700L
    }
}
