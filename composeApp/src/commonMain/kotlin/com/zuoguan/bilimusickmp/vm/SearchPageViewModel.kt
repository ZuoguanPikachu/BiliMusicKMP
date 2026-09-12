package com.zuoguan.bilimusickmp.vm

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.SnackbarDuration
import com.zuoguan.bilimusickmp.models.AudioSource
import com.zuoguan.bilimusickmp.models.PlaySource
import com.zuoguan.bilimusickmp.models.SearchResult
import com.zuoguan.bilimusickmp.models.TrackInfo
import com.zuoguan.bilimusickmp.services.AudioPlayService
import com.zuoguan.bilimusickmp.services.BiliService
import com.zuoguan.bilimusickmp.services.KuGouService
import com.zuoguan.bilimusickmp.services.NetEaseService
import com.zuoguan.bilimusickmp.utils.UiEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 搜索页状态。
 *
 * 只负责"搜索"这一件事：歌曲的新建/编辑全部由 [SongEditorViewModel] 承担。
 */
class SearchPageViewModel(
    private val biliService: BiliService,
    private val netEaseService: NetEaseService,
    private val audioPlayService: AudioPlayService,
    private val kuGouService: KuGouService,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** 手机端列表的滚动状态。 */
    val lazyListState = LazyListState()
    /** 平板/PC 端网格的滚动状态。 */
    val lazyGridState = LazyGridState()

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // 当前进行中的搜索；新搜索会先取消它
    private var searchJob: Job? = null

    private val _uiEvents = Channel<UiEvent>(Channel.BUFFERED)
    /** 一次性 UI 事件（如播放失败提示）。 */
    val uiEvents = _uiEvents.receiveAsFlow()

    fun onKeywordChange(value: String) {
        _uiState.value = _uiState.value.copy(keyword = value)
    }

    fun onAudioSourceChange(source: AudioSource) {
        _uiState.update {
            it.copy(audioSource = source)
        }
    }

    /**
     * 按当前关键词与音源搜索。
     *
     * 新搜索会先取消上一次未完成的请求，避免旧结果覆盖新结果。
     */
    fun search() {
        val state = _uiState.value
        val keyword = state.keyword.trim()
        if (keyword.isBlank()) return

        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isSearchLoading = true,
            searchError = null
        )

        searchJob = scope.launch {
            try {
                val result = when (state.audioSource) {
                    AudioSource.BILI_BILI ->
                        biliService.search(keyword)
                            .map { it.copy(audioSource = AudioSource.BILI_BILI) }

                    AudioSource.NET_EASE ->
                        netEaseService.search(keyword)
                            .map { it.copy(audioSource = AudioSource.NET_EASE) }

                    AudioSource.KU_GOU -> {
                        kuGouService.search(keyword)
                    }
                }

                _uiState.update {
                    it.copy(
                        isSearchLoading = false,
                        results = result
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearchLoading = false,
                        searchError = e.message ?: "搜索失败"
                    )
                }
            }
        }
    }

    /** 直接播放某条搜索结果；音频地址与歌词在播放时按来源解析。 */
    fun playSong(item: SearchResult) {
        val track = TrackInfo(
            id = item.id,
            title = item.title,
            author = item.author,
            audioSource = item.audioSource,
            playSource = PlaySource.SEARCH,
            pic = item.pic,
            urlProvider = {
                when (item.audioSource) {
                    AudioSource.BILI_BILI -> {
                        val cid = biliService.getCid(item.id)
                        biliService.getAudioUrl(item.id, cid)
                    }

                    AudioSource.NET_EASE -> {
                        netEaseService.getAudioUrl(item.id)
                    }

                    AudioSource.KU_GOU -> {
                        kuGouService.getAudioUrl(item.id)
                    }
                }
            },
            lyricsProvider = {
                when (item.audioSource) {
                    // B 站搜索结果没有歌词 ID，播放时先不带歌词
                    AudioSource.BILI_BILI -> {emptyList()}

                    AudioSource.NET_EASE -> {
                        netEaseService.getLyric(item.id)
                    }

                    AudioSource.KU_GOU -> {
                        kuGouService.getLyric(item.id)
                    }
                }
            },
        )
        scope.launch {

            try {
                audioPlayService.play(track)
            }
            catch (e: CancellationException){
                throw e
            }
            catch (e: Exception){
                _uiEvents.send(
                    UiEvent.ShowSnackBar(
                        message = (e.message ?: "播放失败") + "，请重试",
                        duration = SnackbarDuration.Long
                    )
                )
            }
        }
    }

}

data class SearchUiState(
    val keyword: String = "",
    val audioSource: AudioSource = AudioSource.BILI_BILI,
    val isSearchLoading: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val searchError: String? = null
)