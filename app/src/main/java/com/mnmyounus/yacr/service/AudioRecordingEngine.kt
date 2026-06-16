/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  service/AudioRecordingEngine.kt         ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ║                                                                              ║
 * ║  Core audio capture engine using the low-level AudioRecord API.             ║
 * ║                                                                              ║
 * ║  Architecture:                                                               ║
 * ║   ┌──────────────┐    PCM bytes    ┌──────────────────────────┐             ║
 * ║   │  AudioRecord │ ─────────────▶ │ EncryptingOutputStream   │ ──▶ Disk   ║
 * ║   │  (HAL layer) │    in-memory    │ (AES-GCM-256 per chunk)  │             ║
 * ║   └──────────────┘                └──────────────────────────┘             ║
 * ║                                                                              ║
 * ║  Audio Sources tried in priority order:                                     ║
 * ║   1. VOICE_COMMUNICATION (tuned for VoIP, echo/noise cancellation)         ║
 * ║   2. VOICE_CALL (raw telephony mix — both sides on supported devices)       ║
 * ║   3. MIC (fallback — microphone only)                                       ║
 * ║                                                                              ║
 * ║  NOTE: VOICE_CALL capture requires system/privileged app on Android 10+.   ║
 * ║        On non-rooted consumer devices, VOICE_COMMUNICATION is the best     ║
 * ║        option and captures the microphone input with call audio processing. ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.service

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder.AudioSource
import com.mnmyounus.yacr.data.crypto.EncryptingOutputStream
import com.mnmyounus.yacr.data.crypto.EncryptionPipeline
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRecordingEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptionPipeline: EncryptionPipeline
) {
    companion object {
        /** Preferred sample rate — gives CD-quality audio for intelligible speech. */
        private const val SAMPLE_RATE_HZ = 44100

        /** Mono recording — calls are mono by nature on most telephony stacks. */
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO

        /** 16-bit PCM — universal compatibility and sufficient for voice. */
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        /** PCM bit depth for file header. */
        private const val BIT_DEPTH = 16

        /** Number of audio channels in the recording. */
        private const val CHANNELS = 1

        /**
         * Buffer multiplier: 4x the minimum buffer size for headroom against
         * system scheduling jitter. Critical for avoiding audio dropout artifacts.
         */
        private const val BUFFER_MULTIPLIER = 4

        /**
         * Audio sources tried in priority order.
         * VOICE_CALL is last because it requires a privileged permission and
         * silently fails on most non-rooted devices.
         */
        private val AUDIO_SOURCE_PRIORITY = listOf(
            AudioSource.VOICE_COMMUNICATION,  // Best for VoIP, has NS/AEC
            AudioSource.VOICE_CALL,           // Both sides on privileged devices
            AudioSource.MIC                   // Universal fallback
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // State
    // ─────────────────────────────────────────────────────────────────────────

    sealed class EngineState {
        object Idle    : EngineState()
        object Starting: EngineState()
        data class Recording(val outputFile: File, val startTimeMs: Long) : EngineState()
        data class Stopped(val outputFile: File, val durationMs: Long, val encryptedSizeBytes: Long) : EngineState()
        data class Error(val message: String, val cause: Throwable?) : EngineState()
    }

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var encryptingStream: EncryptingOutputStream? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Start recording audio to [outputFile].
     * Must be called from a coroutine scope with IO dispatcher context.
     * This function runs indefinitely until [stop] is called or an error occurs.
     *
     * @param outputFile   Destination .yacr encrypted file.
     */
    suspend fun startRecording(outputFile: File) = withContext(Dispatchers.IO) {
        if (_state.value is EngineState.Recording) {
            Timber.w("AudioRecordingEngine: Already recording — ignoring startRecording call")
            return@withContext
        }

        _state.value = EngineState.Starting
        Timber.d("AudioRecordingEngine: Initializing audio capture → ${outputFile.name}")

        // Ensure parent directory exists
        outputFile.parentFile?.mkdirs()

        val (record, bufferSize) = createAudioRecord()
            ?: run {
                val err = "Failed to initialize AudioRecord with any available audio source"
                Timber.e(err)
                _state.value = EngineState.Error(err, null)
                return@withContext
            }

        this@AudioRecordingEngine.audioRecord = record

        // Open the encrypting output stream (writes YACR file header immediately)
        val encStream = try {
            encryptionPipeline.createEncryptingStream(
                outputFile = outputFile,
                sampleRate = SAMPLE_RATE_HZ,
                channels   = CHANNELS,
                bitDepth   = BIT_DEPTH
            )
        } catch (e: Exception) {
            Timber.e(e, "AudioRecordingEngine: Encryption stream creation failed")
            record.release()
            _state.value = EngineState.Error("Encryption pipeline failure", e)
            return@withContext
        }

        this@AudioRecordingEngine.encryptingStream = encStream

        record.startRecording()
        val startTimeMs = System.currentTimeMillis()
        _state.value = EngineState.Recording(outputFile, startTimeMs)

        Timber.i(
            "AudioRecordingEngine: Recording STARTED — source=${record.audioSource}, " +
                    "bufferSize=$bufferSize bytes, file=${outputFile.name}"
        )

        // ── Main capture loop ──────────────────────────────────────────────
        val buffer = ByteArray(bufferSize)
        try {
            while (isActive && _state.value is EngineState.Recording) {
                val bytesRead = record.read(buffer, 0, buffer.size)
                when {
                    bytesRead > 0  -> encStream.write(buffer, 0, bytesRead)
                    bytesRead == 0 -> { /* Spurious empty read — continue */ }
                    bytesRead == AudioRecord.ERROR_INVALID_OPERATION -> {
                        Timber.e("AudioRecordingEngine: ERROR_INVALID_OPERATION — stopping")
                        break
                    }
                    bytesRead == AudioRecord.ERROR_BAD_VALUE -> {
                        Timber.e("AudioRecordingEngine: ERROR_BAD_VALUE — stopping")
                        break
                    }
                    else -> {
                        Timber.e("AudioRecordingEngine: Unexpected read result: $bytesRead")
                        break
                    }
                }
            }
        } catch (e: IOException) {
            Timber.e(e, "AudioRecordingEngine: IO exception in capture loop")
            _state.value = EngineState.Error("IO failure during capture", e)
        } finally {
            finalizeRecording(outputFile, startTimeMs)
        }
    }

    /**
     * Signal the recording loop to stop.
     * The [state] will transition to [EngineState.Stopped] when the current
     * capture buffer is flushed and the encrypted stream is closed.
     */
    fun stop() {
        if (_state.value !is EngineState.Recording) {
            Timber.w("AudioRecordingEngine: stop() called but state is ${_state.value}")
            return
        }
        Timber.d("AudioRecordingEngine: Stop requested")
        // Setting state to Idle causes the capture loop's while condition to exit
        _state.value = EngineState.Idle
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun createAudioRecord(): Pair<AudioRecord, Int>? {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuffer == AudioRecord.ERROR_BAD_VALUE || minBuffer == AudioRecord.ERROR) {
            Timber.e("AudioRecordingEngine: Invalid buffer size from AudioRecord.getMinBufferSize()")
            return null
        }

        val bufferSize = minBuffer * BUFFER_MULTIPLIER

        for (source in AUDIO_SOURCE_PRIORITY) {
            try {
                val record = AudioRecord(source, SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    Timber.d("AudioRecordingEngine: AudioRecord initialized with source=$source, buffer=$bufferSize")
                    return Pair(record, bufferSize)
                } else {
                    Timber.w("AudioRecordingEngine: AudioRecord state not initialized for source=$source, releasing")
                    record.release()
                }
            } catch (e: SecurityException) {
                Timber.w(e, "AudioRecordingEngine: SecurityException for source=$source — permission denied")
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "AudioRecordingEngine: IllegalArgumentException for source=$source")
            }
        }

        return null
    }

    private fun finalizeRecording(outputFile: File, startTimeMs: Long) {
        val durationMs = System.currentTimeMillis() - startTimeMs
        val encryptedSize = encryptingStream?.totalBytesEncrypted ?: 0L

        try {
            audioRecord?.let { ar ->
                if (ar.recordingState == AudioRecord.RECORDSTATE_RECORDING) ar.stop()
                ar.release()
            }
        } catch (e: Exception) {
            Timber.e(e, "AudioRecordingEngine: Error releasing AudioRecord")
        } finally {
            audioRecord = null
        }

        try {
            encryptingStream?.close()
        } catch (e: Exception) {
            Timber.e(e, "AudioRecordingEngine: Error closing EncryptingOutputStream")
        } finally {
            encryptingStream = null
        }

        val finalSize = outputFile.length()

        if (_state.value !is EngineState.Error) {
            _state.value = EngineState.Stopped(outputFile, durationMs, finalSize)
        }

        Timber.i(
            "AudioRecordingEngine: Recording FINALIZED — ${durationMs / 1000}s, " +
                    "${finalSize / 1024}KB encrypted, ${encryptedSize / 1024}KB PCM"
        )
    }
}
