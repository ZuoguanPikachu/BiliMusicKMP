package com.zuoguan.bilimusickmp.vm

import com.zuoguan.bilimusickmp.models.LyricLine
import com.zuoguan.bilimusickmp.models.PlaybackState
import com.zuoguan.bilimusickmp.models.TrackInfo
import com.zuoguan.bilimusickmp.services.AudioPlayService
import com.zuoguan.bilimusickmp.services.SongRepositoryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 歌词页状态。
 *
 * 跟随 [AudioPlayService] 的当前曲目加载歌词，并把播放进度与播放状态映射给 UI；
 * 没有歌词时给出一行占位内容，避免页面空白。
 */
class LyricsPageViewModel(
    private val audioPlayService: AudioPlayService,
    private val songRepositoryService: SongRepositoryService
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _uiState = MutableStateFlow(LyricsUiState())
    val uiState: StateFlow<LyricsUiState> = _uiState

    init {
        observeTrack()
    }

    private fun observeTrack() {
        scope.launch {
            audioPlayService.currentTrack
                .filterNotNull()
                .distinctUntilChangedBy { track -> track.id }
                .collect { track ->
                    loadLyricsInternal(track)
                }
        }

        scope.launch {
            audioPlayService.time.collect { pos ->
                _uiState.update { it.copy(currentPositionMs = pos) }
            }
        }

        scope.launch {
            audioPlayService.state.collect { state ->
                _uiState.update { it.copy(isPlaying = state == PlaybackState.Playing) }
            }
        }

        scope.launch {
            songRepositoryService.songs.collect { songs ->
                val trackId = _uiState.value.currentTrack?.id ?: return@collect
                val saved = songs.firstOrNull { song -> song.id == trackId }?.lyricBias
                    ?: return@collect
                _uiState.update { state ->
                    if (state.savedLyricBias == saved) state else state.copy(savedLyricBias = saved)
                }
            }
        }
    }

    private suspend fun loadLyricsInternal(track: TrackInfo) {
        val savedBias = songRepositoryService.getSongById(track.id)?.lyricBias ?: track.lyricBias

        _uiState.update { it.copy(lyrics = emptyList()) }

        val lyrics = runCatching{
            track.lyricsProvider()
        }.getOrElse { emptyList() }

        _uiState.update { state ->
            state.copy(
                lyrics = if (lyrics.isEmpty()) {
                    listOf(LyricLine(0L, "暂无歌词"))
                } else {
                    lyrics.sortedBy { line -> line.timeMs }
                },
                currentTrack = track,
                savedLyricBias = savedBias,
                lyricBiasDraft = null
            )
        }
    }

    /** 手动重新拉取指定曲目的歌词（刷新按钮）。 */
    fun loadLyrics(track: TrackInfo) {
        scope.launch {
            loadLyricsInternal(track)
        }
    }


    /** 跳转到歌词行对应的播放位置。 */
    fun seekTo(time: Long) {
        scope.launch {
            audioPlayService.seekMs(time)
        }
    }

    /** 修改歌词延时草稿（毫秒）；只改内存状态，点保存才写回仓库。 */
    fun updateLyricBias(bias: Int) {
        _uiState.update { it.copy(lyricBiasDraft = bias) }
    }

    /**
     * 把当前曲目的歌词延时保存到仓库。
     *
     * 保存成功后立刻切到「已保存」状态，不等仓库回流，避免保存按钮短暂保持点亮；
     * 曲目不在仓库里（如直接播放的搜索结果）时无法持久化，草稿保持原样。
     */
    fun saveLyricBias() {
        val state = _uiState.value
        val songId = state.currentTrack?.id ?: return
        val bias = state.lyricBias

        scope.launch {
            val song = songRepositoryService.getSongById(songId) ?: return@launch
            // Song 不可变，所以用 copy 生成新实例
            val updated = song.copy(lyricBias = bias)

            songRepositoryService.saveSong(updated)

            _uiState.update {
                it.copy(savedLyricBias = bias, lyricBiasDraft = null)
            }
        }
    }

}

data class LyricsUiState(
    val currentTrack: TrackInfo? = null,
    val lyrics: List<LyricLine> = emptyList(),
    val currentPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    /** 仓库里已保存的歌词延时（毫秒）。 */
    val savedLyricBias: Int = 0,
    /** 还没保存的歌词延时草稿；null 表示没有改动。 */
    val lyricBiasDraft: Int? = null,
) {
    /** 当前生效的歌词延时：有草稿时按草稿显示，否则用仓库里的值。 */
    val lyricBias: Int get() = lyricBiasDraft ?: savedLyricBias

    /** 草稿与仓库值不一致时为 true，用来点亮保存按钮。 */
    val isLyricBiasDirty: Boolean get() {
        val draft = lyricBiasDraft
        return draft != null && draft != savedLyricBias
    }
}