/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  presentation/screens/player/            ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.presentation.screens.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.mnmyounus.yacr.domain.model.Recording
import com.mnmyounus.yacr.domain.usecase.DecryptRecordingUseCase
import com.mnmyounus.yacr.domain.usecase.DeleteRecordingUseCase
import com.mnmyounus.yacr.domain.repository.RecordingRepository
import com.mnmyounus.yacr.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val repository: RecordingRepository,
    private val decryptRecording: DecryptRecordingUseCase,
    private val deleteRecording: DeleteRecordingUseCase
) : ViewModel() {

    data class UiState(
        val recording: Recording? = null,
        val isLoading: Boolean = true,
        val isDecrypting: Boolean = false,
        val isPlaying: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val error: String? = null
    ) {
        val progress: Float get() =
            if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val recordingId = checkNotNull(savedStateHandle.get<String>(Screen.Player.ARG_RECORDING_ID))

    /** Temporary decrypted WAV file — deleted on ViewModel cleared. */
    private var tempWavFile: File? = null
    private var exoPlayer: ExoPlayer? = null

    init {
        loadRecording()
    }

    private fun loadRecording() {
        viewModelScope.launch {
            try {
                val recording = repository.getRecordingById(recordingId)
                    ?: throw IllegalStateException("Recording $recordingId not found")
                _uiState.update { it.copy(recording = recording, isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "PlayerViewModel: Failed to load recording $recordingId")
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun onPlayPause() {
        val player = exoPlayer
        if (player == null) {
            prepareAndPlay()
        } else {
            if (player.isPlaying) player.pause() else player.play()
        }
    }

    fun onSeek(fraction: Float) {
        val durationMs = _uiState.value.durationMs
        if (durationMs > 0) {
            exoPlayer?.seekTo((fraction * durationMs).toLong())
        }
    }

    fun onSkipForward() = exoPlayer?.let { p ->
        p.seekTo(minOf(p.currentPosition + 10_000L, p.duration))
    }

    fun onSkipBackward() = exoPlayer?.let { p ->
        p.seekTo(maxOf(p.currentPosition - 10_000L, 0L))
    }

    private fun prepareAndPlay() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDecrypting = true) }
            try {
                val tempFile = withContext(Dispatchers.IO) {
                    File.createTempFile("yacr_play_", ".wav", context.cacheDir).also { f ->
                        decryptRecording(recordingId, f)
                        tempWavFile = f
                    }
                }

                exoPlayer = ExoPlayer.Builder(context).build().also { player ->
                    player.setMediaItem(MediaItem.fromUri(Uri.fromFile(tempFile)))
                    player.prepare()
                    player.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _uiState.update { it.copy(isPlaying = isPlaying) }
                        }
                        override fun onPlaybackStateChanged(state: Int) {
                            when (state) {
                                Player.STATE_READY -> {
                                    _uiState.update {
                                        it.copy(
                                            durationMs  = player.duration.coerceAtLeast(0),
                                            isDecrypting = false
                                        )
                                    }
                                }
                                Player.STATE_ENDED -> {
                                    _uiState.update { it.copy(isPlaying = false, positionMs = 0L) }
                                    player.seekTo(0)
                                }
                                else -> {}
                            }
                        }
                    })
                    player.play()
                }

                // Poll position every 250ms
                launch {
                    while (exoPlayer != null) {
                        val pos = exoPlayer?.currentPosition ?: 0L
                        _uiState.update { it.copy(positionMs = pos) }
                        kotlinx.coroutines.delay(250)
                    }
                }

            } catch (e: Exception) {
                Timber.e(e, "PlayerViewModel: Decryption/playback failed")
                _uiState.update {
                    it.copy(isDecrypting = false, error = "Playback failed: ${e.message}")
                }
            }
        }
    }

    fun onDelete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                releasePlayer()
                deleteRecording(recordingId)
                onDeleted()
            } catch (e: Exception) {
                Timber.e(e, "PlayerViewModel: Delete failed")
                _uiState.update { it.copy(error = "Delete failed") }
            }
        }
    }

    private fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
        tempWavFile?.delete()
        tempWavFile = null
    }

    override fun onCleared() {
        releasePlayer()
        super.onCleared()
    }
}
