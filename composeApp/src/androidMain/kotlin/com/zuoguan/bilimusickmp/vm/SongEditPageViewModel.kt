package com.zuoguan.bilimusickmp.vm

import com.zuoguan.bilimusickmp.models.AudioSource
import com.zuoguan.bilimusickmp.models.SearchResult
import com.zuoguan.bilimusickmp.models.Song
import com.zuoguan.bilimusickmp.services.BiliService
import com.zuoguan.bilimusickmp.services.ExtractSongBaseInfoService
import com.zuoguan.bilimusickmp.services.NeteaseService
import com.zuoguan.bilimusickmp.services.SongEditService
import com.zuoguan.bilimusickmp.services.SongRepositoryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.text.ifEmpty

class SongEditPageViewModel(
    private val songEditService: SongEditService,
    private val songRepository: SongRepositoryService,
    private val extractSongBaseInfoService: ExtractSongBaseInfoService,
    private val neteaseService: NeteaseService,
    private val biliService: BiliService,
    ) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _uiState = MutableStateFlow(SongEditUiState())
    val uiState: StateFlow<SongEditUiState> = _uiState

    init {
        scope.launch {
            songEditService.editingSongInfo.collect {editingSongInfo ->
                val song = editingSongInfo!!.song
                if (editingSongInfo.from == "Search" && song.audioSource == AudioSource.BILI_BILI){
                    _uiState.update {
                        it.copy(isLoading = true)
                    }

                    val songDeferred = async {
                        val songBaseInfo = extractSongBaseInfoService.extractInfo(song.title)
                        val title = songBaseInfo.title.ifEmpty { song.title }
                        val author = songBaseInfo.author

                        var neteaseId = ""
                        var pic = song.pic
                        if (songBaseInfo.title.isNotEmpty() && songBaseInfo.author.isNotEmpty()) {
                            neteaseId = neteaseService.getIdByTitleAndAuthor(title, author)
                            if (neteaseId.isNotEmpty()){
                                pic = neteaseService.getImageUrl(neteaseId)
                            }
                        }

                        Song().apply {
                            id = song.id
                            audioSource = song.audioSource
                            this.title = title
                            this.author = author
                            this.pic = pic
                            this.neteaseId = neteaseId
                            ts = System.currentTimeMillis()
                        }
                    }

                    val cidDeferred = async {
                        biliService.getCid(song.id)
                    }
                    _uiState.update {
                        it.copy(
                            song=songDeferred.await().apply{ cid = cidDeferred.await() },
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            song=song
                        )
                    }
                }
            }
        }

        scope.launch {
            songRepository.allTags.collect { tags ->
                _uiState.update {
                    it.copy(allTags = tags)
                }
            }
        }
    }

    fun save(song: Song) {
        scope.launch {
            songRepository.saveSong(song)
        }
    }
}

data class SongEditUiState(
    val isLoading: Boolean = false,
    val song: Song? = null,
    val allTags: List<String> = emptyList()
)