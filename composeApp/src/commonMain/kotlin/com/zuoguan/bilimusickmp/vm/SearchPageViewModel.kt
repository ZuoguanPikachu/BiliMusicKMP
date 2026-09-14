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

/** 每页请求的结果条数：B 站是页码分页，网易云/酷狗按它换算偏移与页码。 */
private const val PAGE_SIZE = 20

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

    // 当前进行中的搜索（含加载下一页）；新搜索会先取消它
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
     * 按当前关键词与音源搜索第一页。
     *
     * 会先清空旧结果并取消上一次未完成的请求，避免旧结果覆盖新结果。
     */
    fun search() {
        val state = _uiState.value
        val keyword = state.keyword.trim()
        if (keyword.isBlank()) return

        searchJob?.cancel()
        _uiState.value = state.copy(
            isSearchLoading = true,
            isLoadingMore = false,
            loadMoreError = null,
            searchError = null,
            results = emptyList(),
            page = 1,
            endReached = false
        )

        searchJob = scope.launch {
            loadPage(keyword, state.audioSource, page = 1, append = false)
        }
    }

    /**
     * 触底加载下一页，结果追加在已有结果之后。
     *
     * 界面上的触底回调可能连续触发，这里用 [SearchUiState.isLoadingMore] 与
     * [SearchUiState.endReached] 去重，重复调用会直接返回。
     *
     * 上一次加载失败（[SearchUiState.loadMoreError]）后界面的触底回调是被关掉的，
     * 只有用户点底部的"重试"才会再次走到这里，因此不会自动反复重试。
     */
    fun loadMore() {
        val state = _uiState.value
        val keyword = state.keyword.trim()
        if (keyword.isBlank() ||
            state.isSearchLoading ||
            state.isLoadingMore ||
            state.endReached
        ) {
            return
        }

        _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }

        searchJob = scope.launch {
            loadPage(keyword, state.audioSource, page = state.page + 1, append = true)
        }
    }

    /**
     * 拉取某一页结果并写入状态。
     *
     * @param append true 表示为 [loadMore] 追加结果，false 表示新搜索、整体替换结果。
     */
    private suspend fun loadPage(
        keyword: String,
        audioSource: AudioSource,
        page: Int,
        append: Boolean
    ) {
        try {
            val pageResult = when (audioSource) {
                AudioSource.BILI_BILI ->
                    biliService.search(keyword, page)

                AudioSource.NET_EASE ->
                    netEaseService.search(
                        s = keyword,
                        offset = (page - 1) * PAGE_SIZE,
                        limit = PAGE_SIZE
                    )

                AudioSource.KU_GOU ->
                    kuGouService.search(keyword, pageSize = PAGE_SIZE, page = page)
            }
            val result = pageResult.items

            _uiState.update { current ->
                val results = if (append) {
                    // 分页接口偶尔会重复返回上一条，按 id 去重避免界面出现重复项
                    (current.results + result).distinctBy { it.id }
                } else {
                    result
                }

                current.copy(
                    isSearchLoading = false,
                    isLoadingMore = false,
                    loadMoreError = null,
                    results = results,
                    page = page,
                    // 到底的判定：接口自己说了没有下一页最准；再兜两层——
                    // 空页说明没有下一页，追加时一条新结果都没多出来（例如接口忽略了 page
                    // 参数）也算到底。少了这几条判断，触底回调会一直重复请求同一页。
                    endReached = !pageResult.hasMore ||
                            result.isEmpty() ||
                            results.size == current.results.size
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (append) {
                // 加载下一页失败：已有结果原样保留，只记下错误。
                // 这里不能只把 isLoadingMore 置回 false —— 列表还停在底部时触底回调会立刻
                // 再发一次请求，失败后又发，形成停不下来的重试（搜索结果少、很快就翻到
                // 最后一页时尤其明显）。改为关掉触底加载，等用户点底部"重试"。
                _uiState.update {
                    it.copy(isLoadingMore = false, loadMoreError = e.message ?: "加载失败")
                }
            } else {
                _uiState.update {
                    it.copy(
                        isSearchLoading = false,
                        isLoadingMore = false,
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
    /** 正在加载下一页；列表底部据此显示转圈。 */
    val isLoadingMore: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    /** 已加载到第几页，从 1 开始。 */
    val page: Int = 1,
    /** 已经到最后一页，触底不再请求。 */
    val endReached: Boolean = false,
    /**
     * 加载下一页失败的提示。非空时触底加载暂停（避免自动反复重试），
     * 由列表底部的"重试"清掉并继续加载。
     */
    val loadMoreError: String? = null,
    val searchError: String? = null
)