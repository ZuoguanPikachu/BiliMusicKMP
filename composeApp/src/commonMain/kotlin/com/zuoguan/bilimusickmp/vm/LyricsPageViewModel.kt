package com.zuoguan.bilimusickmp.vm

import com.zuoguan.bilimusickmp.models.LyricLine
import com.zuoguan.bilimusickmp.models.LyricSource
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
                // 只比 id 会漏掉「同一首歌换元数据」：搜索页试听后编辑信息再加入歌单，
                // 再从歌单播放时 id 没变，这次赋值会被丢掉，歌词页的歌名/歌手/封面就停在旧值上
                // （播放条的同类问题就是这么修的，这里漏了）。
                // 也不整条比 TrackInfo：它带着 lyricBias，保存歌词延时会产生一条「只有偏移不同」
                // 的新值，整条比会让这里重复拉一次歌词、闪一次转圈；偏移的同步由下面那个
                // songs 收集器单独负责。所以只比界面真正渲染的字段 + 歌词身份。
                // 歌词身份（lyricSource/lyricId）不能省：只换歌词源时歌名封面都没动，
                // 少了它就当作「没变」，歌词会一直停在旧来源上。
                .distinctUntilChangedBy { track ->
                    TrackSnapshot(
                        id = track.id,
                        title = track.title,
                        author = track.author,
                        pic = track.pic,
                        lyricSource = track.lyricSource,
                        lyricId = track.lyricId
                    )
                }
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

        // 先清空并置为加载中：换歌时不该继续显示上一首的歌词，界面也借此显示转圈
        _uiState.update { it.copy(lyrics = emptyList(), isLoading = true) }

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
                lyricBiasDraft = null,
                isLoading = false
            )
        }
    }

    /** 重新拉取当前曲目的歌词（刷新按钮）；还没有曲目时忽略。 */
    fun refreshLyrics() {
        val track = _uiState.value.currentTrack ?: return
        scope.launch {
            loadLyricsInternal(track)
        }
    }

    /** 播放/暂停切换：窄屏歌词页没有底部播放栏，靠它控制播放。 */
    fun togglePlayPause() {
        val state = _uiState.value
        if (state.currentTrack == null) return
        scope.launch {
            if (state.isPlaying) audioPlayService.pause() else audioPlayService.resume()
        }
    }

    /** 切到下一首：歌词页隐藏播放栏的平台靠它换歌。 */
    fun playNext() {
        scope.launch {
            audioPlayService.playNext()
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
    /** 正在拉取歌词，界面显示加载状态。 */
    val isLoading: Boolean = false,
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

/**
 * 判断「要不要重新拉歌词」的快照：界面展示字段 + 歌词身份。
 *
 * - 带上 [lyricSource]/[lyricId]：歌词源一换（哪怕歌名、封面都没动）就得重拉，
 *   这两个字段同时也是 [TrackInfo.equals] 的一部分，否则变更根本不会外发到这里。
 * - 刻意不含 [TrackInfo.lyricBias]：那是延时的保存值，改动它不该让歌词重新拉一遍。
 */
private data class TrackSnapshot(
    val id: String,
    val title: String,
    val author: String,
    val pic: String,
    val lyricSource: LyricSource,
    val lyricId: String,
)