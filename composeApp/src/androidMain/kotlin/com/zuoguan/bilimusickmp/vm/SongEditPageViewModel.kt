package com.zuoguan.bilimusickmp.vm

import com.zuoguan.bilimusickmp.models.AudioSource
import com.zuoguan.bilimusickmp.models.SearchResult
import com.zuoguan.bilimusickmp.models.Song
import com.zuoguan.bilimusickmp.services.BiliService
import com.zuoguan.bilimusickmp.services.ExtractSongBaseInfoService
import com.zuoguan.bilimusickmp.services.KuGouService
import com.zuoguan.bilimusickmp.services.NeteaseService
import com.zuoguan.bilimusickmp.services.SongEditService
import com.zuoguan.bilimusickmp.services.SongRepositoryService
import com.zuoguan.bilimusickmp.utils.enrichSongInfo
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
    private val kuGouService: KuGouService,
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

                    _uiState.update {
                        it.copy(
                            song= enrichSongInfo(
                                extractSongBaseInfoService,
                                biliService,
                                kuGouService,
                                song
                            ),
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