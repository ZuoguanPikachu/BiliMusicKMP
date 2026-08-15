package com.zuoguan.bilimusickmp.vm

import com.zuoguan.bilimusickmp.models.AudioSource
import com.zuoguan.bilimusickmp.models.Song
import com.zuoguan.bilimusickmp.services.SongEditService
import com.zuoguan.bilimusickmp.services.SongMetadataService
import com.zuoguan.bilimusickmp.services.SongRepositoryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SongEditPageViewModel(
    private val songEditService: SongEditService,
    private val songRepository: SongRepositoryService,
    private val songMetadataService: SongMetadataService
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
                            song = songMetadataService.resolve(song),
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