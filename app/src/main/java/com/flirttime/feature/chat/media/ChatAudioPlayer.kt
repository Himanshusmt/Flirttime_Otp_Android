package com.flirttime.feature.chat.media

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plays one voice note at a time and reports state back to the list so the tapped
 * bubble can swap its play icon and scrub the waveform.
 */
class ChatAudioPlayer(
    private val onStateChanged: (messageId: String?, isPlaying: Boolean) -> Unit,
    private val onProgress: (messageId: String, fraction: Float, positionMs: Int) -> Unit
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressJob: Job? = null
    private var player: MediaPlayer? = null
    private var currentId: String? = null
    private var currentSource: String? = null

    fun toggle(messageId: String, source: String) {
        if (messageId == currentId) {
            val active = player
            if (active != null) {
                if (active.isPlaying) {
                    active.pause()
                    stopProgressLoop()
                    onStateChanged(messageId, false)
                } else {
                    active.start()
                    startProgressLoop(messageId)
                    onStateChanged(messageId, true)
                }
                return
            }
        }
        play(messageId, source)
    }

    private fun play(messageId: String, source: String) {
        // Reset the previously playing bubble before the new one takes over.
        val previousId = currentId?.takeIf { it != messageId }
        releasePlayer()
        if (previousId != null) {
            onProgress(previousId, 0f, 0)
            onStateChanged(previousId, false)
        }

        currentId = messageId
        currentSource = source

        try {
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(source)
                setOnPreparedListener {
                    it.start()
                    onStateChanged(messageId, true)
                    startProgressLoop(messageId)
                }
                setOnCompletionListener { finished -> finishPlayback(currentId) }
                setOnErrorListener { _, _, _ ->
                    finishPlayback(currentId)
                    true
                }
                prepareAsync()
            }
            player = mediaPlayer
        } catch (e: Exception) {
            finishPlayback(messageId)
        }
    }

    /** Replays the current note after a failed attempt. */
    fun retryCurrent() {
        val id = currentId ?: return
        val source = currentSource ?: return
        play(id, source)
    }

    fun stop() {
        finishPlayback(currentId)
    }

    fun release() {
        releasePlayer()
        onStateChanged(null, false)
    }

    private fun finishPlayback(messageId: String?) {
        stopProgressLoop()
        releasePlayer()
        if (messageId != null) {
            // Progress first: the state callback clears the "now playing" id, so it has
            // to land last or the bubble would keep showing the pause icon.
            onProgress(messageId, 0f, 0)
            onStateChanged(messageId, false)
        }
    }

    private fun releasePlayer() {
        player?.let {
            runCatching { it.setOnPreparedListener(null) }
            runCatching { it.setOnCompletionListener(null) }
            runCatching { it.setOnErrorListener(null) }
            runCatching { if (it.isPlaying) it.stop() }
            runCatching { it.release() }
        }
        player = null
        currentId = null
        currentSource = null
        stopProgressLoop()
    }

    private fun startProgressLoop(messageId: String) {
        stopProgressLoop()
        progressJob = scope.launch {
            while (isActive) {
                val active = player ?: break
                val duration = runCatching { active.duration }.getOrDefault(0)
                val position = runCatching { active.currentPosition }.getOrDefault(0)
                if (duration > 0) {
                    onProgress(messageId, position.toFloat() / duration, position)
                }
                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
        progressJob = null
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 120L
    }
}
