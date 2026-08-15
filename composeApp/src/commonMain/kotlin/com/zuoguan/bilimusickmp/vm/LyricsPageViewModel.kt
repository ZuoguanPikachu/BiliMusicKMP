package com.zuoguan.bilimusickmp.vm

import bilimusickmp.composeapp.generated.resources.Res
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
                .distinctUntilChangedBy { track -> track.id to track.lyricsProvider }
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

    fun loadLyrics(track: TrackInfo) {
        scope.launch {
            loadLyricsInternal(track)
        }
    }


    fun seekTo(time: Long) {
        scope.launch {
            audioPlayService.seekMs(time)
        }
    }

    fun saveLyricBias(songId: String, bias: Int) {
        val song = songRepositoryService.getSongById(songId)
        if (song != null) {
            song.apply {
                lyricBias = bias
            }

            scope.launch {
                songRepositoryService.saveSong(song)
            }
        }
    }

}

data class LyricsUiState(
    val currentTrack: TrackInfo? = null,
    val lyrics: List<LyricLine> = emptyList(),
    val currentPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
)