package com.zuoguan.bilimusickmp.vm

import androidx.compose.material3.SnackbarDuration
import com.zuoguan.bilimusickmp.models.AudioSource
import com.zuoguan.bilimusickmp.models.LyricSource
import com.zuoguan.bilimusickmp.models.PlaySource
import com.zuoguan.bilimusickmp.models.SearchResult
import com.zuoguan.bilimusickmp.models.Song
import com.zuoguan.bilimusickmp.models.TrackInfo
import com.zuoguan.bilimusickmp.services.AudioPlayService
import com.zuoguan.bilimusickmp.services.BiliService
import com.zuoguan.bilimusickmp.services.KuGouService
import com.zuoguan.bilimusickmp.services.NetEaseService
import com.zuoguan.bilimusickmp.services.SongMetadataService
import com.zuoguan.bilimusickmp.services.SongRepositoryService
import com.zuoguan.bilimusickmp.utils.UiEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchPageViewModel(
    private val biliService: BiliService,
    private val netEaseService: NetEaseService,
    private val audioPlayService: AudioPlayService,
    private val songRepository: SongRepositoryService,
    private val kuGouService: KuGouService,
    private val songMetadataService: SongMetadataService
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    private val _uiEvents = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvents = _uiEvents.receiveAsFlow()

    init {
        scope.launch {
            songRepository.allTags.collect { tags ->
                _uiState.update {
                    it.copy(allTags = tags)
                }
            }
        }
    }

    fun onKeywordChange(value: String) {
        _uiState.value = _uiState.value.copy(keyword = value)
    }

    fun onAudioSourceChange(source: AudioSource) {
        _uiState.update {
            it.copy(audioSource = source)
        }
    }

    fun search() {
        val state = _uiState.value
        val keyword = state.keyword
        if (keyword.isBlank()) return

        _uiState.value = _uiState.value.copy(
            isSearchLoading = true,
            searchError = null
        )

        scope.launch {
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
            catch (e: Exception){
                _uiEvents.send(
                    UiEvent.ShowSnackBar(
                        message = e.message!! + "，请重试",
                        duration = SnackbarDuration.Long
                    )
                )
            }
        }
    }

    fun requestAdd(item: SearchResult) {
        _uiState.value = _uiState.value.copy(
            showAddDialog = true,
            isExtractInfoLoading = true,
            songToAdd = null
        )
        scope.launch {
            try {
                var song: Song
                if (item.audioSource == AudioSource.BILI_BILI) {
                    song = songMetadataService.resolve(Song().apply {
                        id = item.id
                        audioSource = item.audioSource
                        title = item.title
                        author = item.author
                        pic = item.pic
                        ts = System.currentTimeMillis()
                    })
                }
                else
                {
                    song = Song().apply {
                        id = item.id
                        audioSource = item.audioSource
                        title = item.title
                        author = item.author
                        pic = item.pic
                        lyricSource = if (item.audioSource == AudioSource.KU_GOU) LyricSource.KU_GOU  else LyricSource.NET_EASE
                        lyricId = item.id
                        ts = System.currentTimeMillis()
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isExtractInfoLoading = false,
                    songToAdd = song
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExtractInfoLoading = false
                )
                _uiEvents.send(
                    UiEvent.ShowSnackBar(
                        message = e.message!!,
                        duration = SnackbarDuration.Long
                    )
                )
            }
        }
    }

    fun confirmAdd(song: Song) {
        scope.launch {
            songRepository.saveSong(song)
        }
    }

    fun cancelAdd() {
        _uiState.value = _uiState.value.copy(showAddDialog = false)
    }
}

data class SearchUiState(
    val keyword: String = "",
    val audioSource: AudioSource = AudioSource.BILI_BILI,
    val isSearchLoading: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val searchError: String? = null,
    val showAddDialog: Boolean = false,
    val songToAdd: Song? = null,
    val isExtractInfoLoading: Boolean = false,
    val allTags: List<String> = emptyList()
)