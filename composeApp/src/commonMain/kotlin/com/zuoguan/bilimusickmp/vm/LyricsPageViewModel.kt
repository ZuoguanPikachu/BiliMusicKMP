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
                // 只按 id 去重：同一首歌被重新构造成 TrackInfo 时不必重复拉歌词
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
    }

    private suspend fun loadLyricsInternal(track: TrackInfo) {
        _uiState.update { it.copy(lyrics = emptyList()) }

        val lyrics = runCatching{
            track.lyricsProvider()
        }.getOrElse { emptyList() }

        _uiState.update {
            if (lyrics.isEmpty()) {
                it.copy(lyrics = listOf(LyricLine(0L, "暂无歌词")), currentTrack = track)
            } else {
                it.copy(lyrics = lyrics.sortedBy { line -> line.timeMs }, currentTrack = track)
            }
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

    /** 持久化歌词时间轴偏移（毫秒），用来手动校准整首歌词。 */
    fun saveLyricBias(songId: String, bias: Int) {
        val song = songRepositoryService.getSongById(songId) ?: return
        // Song 不可变，所以用 copy 生成新实例
        val updated = song.copy(lyricBias = bias)

        scope.launch {
            songRepositoryService.saveSong(updated)
        }
    }

}

data class LyricsUiState(
    val currentTrack: TrackInfo? = null,
    val lyrics: List<LyricLine> = emptyList(),
    val currentPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
)