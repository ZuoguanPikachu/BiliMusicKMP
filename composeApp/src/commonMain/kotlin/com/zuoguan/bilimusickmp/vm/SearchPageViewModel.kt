package com.zuoguan.bilimusickmp.vm

import androidx.compose.material3.SnackbarDuration
import com.zuoguan.bilimusickmp.models.AudioSource
import com.zuoguan.bilimusickmp.models.PlaySource
import com.zuoguan.bilimusickmp.models.SearchResult
import com.zuoguan.bilimusickmp.models.Song
import com.zuoguan.bilimusickmp.models.TrackInfo
import com.zuoguan.bilimusickmp.services.AudioPlayService
import com.zuoguan.bilimusickmp.services.BiliService
import com.zuoguan.bilimusickmp.services.ExtractSongBaseInfoService
import com.zuoguan.bilimusickmp.services.NeteaseService
import com.zuoguan.bilimusickmp.services.SongRepositoryService
import com.zuoguan.bilimusickmp.utils.UiEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchPageViewModel(
    private val biliService: BiliService,
    private val neteaseService: NeteaseService,
    private val audioPlayService: AudioPlayService,
    private val extractSongBaseInfoService: ExtractSongBaseInfoService,
    private val songRepository: SongRepositoryService
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
                        neteaseService.search(keyword)
                            .map { it.copy(audioSource = AudioSource.NET_EASE) }
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
                        neteaseService.getAudioUrl(item.id)
                    }
                }
            },
            lyricsProvider = {
                when (item.audioSource) {
                    AudioSource.BILI_BILI -> {emptyList()}

                    AudioSource.NET_EASE -> {
                        neteaseService.getLyric(item.id)
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
                    UiEvent.ShowSnackbar(
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
                    val songDeferred = async {
                        val songBaseInfo = extractSongBaseInfoService.extractInfo(item.title)
                        val title = songBaseInfo.title.ifEmpty { item.title }
                        val author = songBaseInfo.author

                        var neteaseId = ""
                        var pic = item.pic
                        if (songBaseInfo.title.isNotEmpty() && songBaseInfo.author.isNotEmpty()) {
                            neteaseId = neteaseService.getIdByTitleAndAuthor(title, author)
                            if (neteaseId.isNotEmpty()){
                                pic = neteaseService.getImageUrl(neteaseId)
                            }
                        }

                        Song().apply {
                            id = item.id
                            audioSource = item.audioSource
                            this.title = title
                            this.author = author
                            this.pic = pic
                            this.neteaseId = neteaseId
                            ts = System.currentTimeMillis()
                        }
                    }

                    val cidDeferred = async {
                        biliService.getCid(item.id)
                    }

                    song = songDeferred.await().apply{ cid = cidDeferred.await() }
                }
                else
                {
                    song = Song().apply {
                        id = item.id
                        audioSource = item.audioSource
                        title = item.title
                        author = item.author
                        pic = item.pic
                        neteaseId = item.id
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
                    UiEvent.ShowSnackbar(
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