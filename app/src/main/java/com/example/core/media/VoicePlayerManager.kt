package com.example.core.media

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class VoicePlaybackState(
    val activeMessageId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

class VoicePlayerManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(VoicePlaybackState())
    val state: StateFlow<VoicePlaybackState> = _state.asStateFlow()

    fun togglePlay(messageId: String, file: File) {
        val current = _state.value
        if (current.activeMessageId == messageId && mediaPlayer != null) {
            if (current.isPlaying) {
                mediaPlayer?.pause()
                progressJob?.cancel()
                _state.value = current.copy(isPlaying = false)
            } else {
                mediaPlayer?.start()
                _state.value = current.copy(isPlaying = true)
                startProgressLoop(messageId)
            }
            return
        }

        stop()
        try {
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    stop()
                }
                start()
            }
            mediaPlayer = mp
            _state.value = VoicePlaybackState(
                activeMessageId = messageId,
                isPlaying = true,
                positionMs = 0L,
                durationMs = mp.duration.toLong().coerceAtLeast(1L)
            )
            startProgressLoop(messageId)
        } catch (_: Exception) {
            stop()
        }
    }

    private fun startProgressLoop(messageId: String) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (_state.value.isPlaying && _state.value.activeMessageId == messageId) {
                val mp = mediaPlayer ?: break
                _state.value = _state.value.copy(
                    positionMs = mp.currentPosition.toLong(),
                    durationMs = mp.duration.toLong().coerceAtLeast(1L)
                )
                delay(150L)
            }
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        _state.value = VoicePlaybackState()
    }
}
