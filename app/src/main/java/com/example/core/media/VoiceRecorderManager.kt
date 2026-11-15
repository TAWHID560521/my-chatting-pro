package com.example.core.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
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
import java.util.UUID

data class VoiceRecordingState(
    val isRecording: Boolean = false,
    val elapsedMs: Long = 0L,
    val amplitudeNormalized: Float = 0f,
    val error: String? = null
)

class VoiceRecorderManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var timerJob: Job? = null
    private var startTimeMs: Long = 0L

    private val _state = MutableStateFlow(VoiceRecordingState())
    val state: StateFlow<VoiceRecordingState> = _state.asStateFlow()

    fun startRecording(): Boolean {
        stopInternal(deleteFile = true)
        return try {
            val dir = File(context.cacheDir, "media_temp").apply { mkdirs() }
            val file = File(dir, "voice_${UUID.randomUUID()}.m4a")
            outputFile = file

            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(64_000)
            mr.setAudioSamplingRate(44_100)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()

            recorder = mr
            startTimeMs = System.currentTimeMillis()
            _state.value = VoiceRecordingState(isRecording = true, elapsedMs = 0L)

            timerJob = scope.launch {
                while (_state.value.isRecording) {
                    val amp = try {
                        ((recorder?.maxAmplitude ?: 0) / 32767f).coerceIn(0f, 1f)
                    } catch (_: Exception) {
                        0f
                    }
                    _state.value = _state.value.copy(
                        elapsedMs = System.currentTimeMillis() - startTimeMs,
                        amplitudeNormalized = amp
                    )
                    delay(120L)
                }
            }
            true
        } catch (e: Exception) {
            stopInternal(deleteFile = true)
            _state.value = VoiceRecordingState(isRecording = false, error = e.message)
            false
        }
    }

    fun stopAndCollect(): PreparedMediaFile? {
        val duration = System.currentTimeMillis() - startTimeMs
        val file = outputFile
        stopInternal(deleteFile = false)
        if (file == null || !file.exists() || file.length() == 0L || duration < 400L) {
            file?.delete()
            return null
        }
        return PreparedMediaFile(
            file = file,
            fileName = file.name,
            mimeType = "audio/mp4",
            sizeBytes = file.length()
        )
    }

    fun lastDurationMs(): Long = _state.value.elapsedMs

    fun cancelRecording() {
        stopInternal(deleteFile = true)
    }

    private fun stopInternal(deleteFile: Boolean) {
        timerJob?.cancel()
        timerJob = null
        try {
            recorder?.stop()
        } catch (_: Exception) {
            // Safe ignore if stopped before prepare/start completed
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        if (deleteFile) {
            outputFile?.delete()
            outputFile = null
        }
        _state.value = VoiceRecordingState(isRecording = false)
    }
}
