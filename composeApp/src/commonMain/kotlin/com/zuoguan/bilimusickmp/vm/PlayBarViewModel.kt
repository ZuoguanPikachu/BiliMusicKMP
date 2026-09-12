package com.zuoguan.bilimusickmp.vm

import com.zuoguan.bilimusickmp.models.PlayMode
import com.zuoguan.bilimusickmp.models.PlaybackState
import com.zuoguan.bilimusickmp.models.TrackInfo
import com.zuoguan.bilimusickmp.services.AudioPlayService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 播放条状态。
 *
 * 把 [AudioPlayService] 的播放状态、当前曲目、进度与播放模式映射给 UI，
 * 并把播放条上的按钮操作转成对播放服务的调用。
 */
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

    /**
     * 播放/暂停切换。
     *
     * 已停止、播放结束或出错时从当前曲目重新开始播放；播放失败则落到
     * [PlaybackState.Error]，由 UI 呈现。
     */
    fun togglePlayPause() {
        val state = _uiState.value
        when (state.playbackState) {
            PlaybackState.Playing -> pause()
            PlaybackState.Paused -> resume()
            PlaybackState.Stopped,
            PlaybackState.Ended,
            PlaybackState.Error -> {
                val track = state.currentTrack ?: return
                scope.launch {
                    try {
                        audioPlayService.play(track)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _uiState.update { it.copy(playbackState = PlaybackState.Error) }
                    }
                }
            }
        }
    }

    /** 按进度比例跳转，[p] 为 0..1 的播放进度。 */
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