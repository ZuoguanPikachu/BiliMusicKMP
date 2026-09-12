package com.zuoguan.bilimusickmp.vm

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.SnackbarDuration
import com.zuoguan.bilimusickmp.models.AudioSource
import com.zuoguan.bilimusickmp.models.LyricLine
import com.zuoguan.bilimusickmp.models.LyricSource
import com.zuoguan.bilimusickmp.models.PlaySource
import com.zuoguan.bilimusickmp.models.Song
import com.zuoguan.bilimusickmp.models.TrackInfo
import com.zuoguan.bilimusickmp.services.AudioPlayService
import com.zuoguan.bilimusickmp.services.BiliService
import com.zuoguan.bilimusickmp.services.KuGouService
import com.zuoguan.bilimusickmp.services.NetEaseService
import com.zuoguan.bilimusickmp.services.SongRepositoryService
import com.zuoguan.bilimusickmp.utils.UiEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlaylistPageViewModel(
    private val songRepository: SongRepositoryService,
    private val audioPlayService: AudioPlayService,
    private val biliService: BiliService,
    private val netEaseService: NetEaseService,
    private val kuGouService: KuGouService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val lazyListState = LazyListState()

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    private val _isDragging = MutableStateFlow(false)

    private val _uiEvents = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvents = _uiEvents.receiveAsFlow()

    init {
        observeSongsAndTags()
        observeCurrentTrack()
    }

    private fun observeSongsAndTags() {
        scope.launch {
            combine(
                songRepository.songs,
                songRepository.allTags,
                _uiState.map { it.selectedTags },
                _uiState.map { it.filterMode }
            ) { songs, allTags, selectedTags, mode ->

                val filtered = when {
                    selectedTags.isEmpty() -> songs

                    mode == TagFilterMode.OR ->
                        songs.filter { song ->
                            song.tags.any { it in selectedTags }
                        }

                    else ->
                        songs.filter { song ->
                            selectedTags.all { it in song.tags }
                        }
                }
                val sorted = filtered.sortedBy { it.ts }
                updatePlaylist(sorted)

                _uiState.value.copy(
                    songs = songs,
                    filteredSongs = sorted,
                    allTags = allTags,
                    selectedTags = selectedTags,
                    filterMode = mode,
                )
            }.collect { newState ->
                if (!_isDragging.value) {
                    _uiState.value = newState
                }
            }
        }
    }

    fun onDragStart() {
        _isDragging.value = true
    }

    fun onDragEnd() {
        val orderedSongs = _uiState.value.filteredSongs
        _isDragging.value = false
        scope.launch {
            songRepository.persistOrder(orderedSongs)
        }
    }

    fun moveSong(from: Int, to: Int) {
        val list = _uiState.value.filteredSongs.toMutableList()
        if (from !in list.indices) return
        val item = list.removeAt(from)
        list.add(to.coerceIn(0, list.size), item)

        _uiState.value = _uiState.value.copy(filteredSongs = list)
    }

    private fun observeCurrentTrack() {
        scope.launch {
            audioPlayService.currentTrack.collect { track ->
                _uiState.update {
                    it.copy(currentTrack = track)
                }
            }
        }
    }

    suspend fun updatePlaylist(list: List<Song>) {
        audioPlayService.updatePlaylist(list.map { it.toTrackInfo() })
    }

    private fun Song.toTrackInfo(): TrackInfo = TrackInfo(
        id = id,
        title = title,
        author = author,
        audioSource = audioSource,
        playSource = PlaySource.PLAYLIST,
        pic = pic,
        urlProvider = { resolveAudioUrl() },
        lyricBias = lyricBias,
        lyricsProvider = { resolveLyrics() },
    )

    private suspend fun Song.resolveAudioUrl(): String = when (audioSource) {
        AudioSource.BILI_BILI -> biliService.getAudioUrl(id, cid)
        AudioSource.NET_EASE -> netEaseService.getAudioUrl(id)
        AudioSource.KU_GOU -> kuGouService.getAudioUrl(id)
    }

    private suspend fun Song.resolveLyrics(): List<LyricLine> = when (lyricSource) {
        LyricSource.KU_GOU -> kuGouService.getLyric(lyricId)
        LyricSource.NET_EASE -> netEaseService.getLyric(lyricId)
        LyricSource.NONE -> emptyList()
    }

    fun playSong(song: Song) {
        val track = song.toTrackInfo()
        scope.launch {
            try {
                audioPlayService.play(track)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiEvents.send(
                    UiEvent.ShowSnackBar(
                        message = e.message ?: "播放失败，请重试",
                        duration = SnackbarDuration.Long
                    )
                )
            }
        }
    }

    fun switchFilterMode(mode: TagFilterMode) {
        _uiState.update {
            it.copy(filterMode = mode)
        }
    }

    fun switchOrderingMode() {
        _uiState.update {
            it.copy(isOrdering = !it.isOrdering)
        }
    }

    fun toggleTag(tag: String) {
        _uiState.update { state ->
            val newSet = state.selectedTags.toMutableSet().apply {
                if (contains(tag)) remove(tag) else add(tag)
            }
            state.copy(selectedTags = newSet)
        }
    }

    fun requestDelete(song: Song) {
        _uiState.update {
            it.copy(
                showDeleteDialog = true,
                songToHandle = song
            )
        }
    }

    fun confirmDelete() {
        val song = _uiState.value.songToHandle ?: return

        scope.launch {
            songRepository.removeSong(song.id)
        }

        _uiState.update {
            it.copy(
                showDeleteDialog = false,
                songToHandle = null
            )
        }
    }

    fun cancelDelete() {
        _uiState.update {
            it.copy(
                showDeleteDialog = false,
                songToHandle = null
            )
        }
    }

    fun requestBottomSheet(song: Song) {
        _uiState.value = _uiState.value.copy(
            showBottomSheet = true,
            songToHandle = song
        )
    }

    fun dismissBottomSheet(){
        _uiState.value = _uiState.value.copy(showBottomSheet = false)
    }
}

data class PlaylistUiState(
    val songs: List<Song> = emptyList(),
    val filteredSongs: List<Song> = emptyList(),
    val currentTrack: TrackInfo? = null,

    val allTags: List<String> = emptyList(),
    val selectedTags: Set<String> = emptySet(),
    val filterMode: TagFilterMode = TagFilterMode.OR,

    val isOrdering: Boolean = false,

    val showDeleteDialog: Boolean = false,
    val showBottomSheet: Boolean = false,

    val songToHandle: Song? = null,
)

enum class TagFilterMode {
    OR,
    AND
}