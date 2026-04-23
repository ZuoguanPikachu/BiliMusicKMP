package com.zuoguan.bilimusickmp.vm

import com.zuoguan.bilimusickmp.models.PlayMode
import com.zuoguan.bilimusickmp.models.PlaybackState
import com.zuoguan.bilimusickmp.models.TrackInfo
import com.zuoguan.bilimusickmp.services.AudioPlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlayBarViewModel(
    private val audioPlayService: AudioPlayService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _uiState = MutableStateFlow(PlayBarUiState())
    val uiState: StateFlow<PlayBarUiState> = _uiState

    init {
        observePlayer()
    }

    private fun observePlayer() {
        scope.launch {
            audioPlayService.state.collect { state ->
                _uiState.update {
                    it.copy(playbackState = state)
                }
            }
        }

        scope.launch {
            audioPlayService.currentTrack.collect { track ->
                _uiState.update {
                    it.copy(currentTrack = track)
                }
            }
        }

        scope.launch {
            audioPlayService.position.collect { position ->
                _uiState.update {
                    it.copy(position = position)
                }
            }
        }

        scope.launch {
            audioPlayService.time.collect { time ->
                _uiState.update {
                    it.copy(time = time)
                }
            }
        }

        scope.launch {
            audioPlayService.playMode.collect {
                _uiState.update { s -> s.copy(playMode = it) }
            }
        }
    }


    fun pause() {
        scope.launch {
            audioPlayService.pause()
        }
    }

    fun resume() {
        scope.launch {
            audioPlayService.resume()
        }
    }

    fun seek(p: Float) {
        scope.launch {
            audioPlayService.seek(p)
        }
    }

    fun togglePlayMode() = audioPlayService.togglePlayMode()
    fun playNext() {
        scope.launch {
            audioPlayService.playNext()
        }
    }
    fun playPrevious() {
        scope.launch {
            audioPlayService.playPrevious()
        }
    }
}


data class PlayBarUiState(
    val playbackState: PlaybackState = PlaybackState.Stopped,
    val currentTrack: TrackInfo? = null,
    val position: Float = 0f,
    val time: Long = 0L,
    val playMode: PlayMode = PlayMode.SEQUENTIAL
)