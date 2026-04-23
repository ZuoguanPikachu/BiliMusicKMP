package com.zuoguan.bilimusickmp.vm

import androidx.compose.material3.SnackbarDuration
import com.zuoguan.bilimusickmp.models.AudioSource
import com.zuoguan.bilimusickmp.models.PlaySource
import com.zuoguan.bilimusickmp.models.Song
import com.zuoguan.bilimusickmp.models.TrackInfo
import com.zuoguan.bilimusickmp.services.AudioPlayService
import com.zuoguan.bilimusickmp.services.BiliService
import com.zuoguan.bilimusickmp.services.NeteaseService
import com.zuoguan.bilimusickmp.services.SongRepositoryService
import com.zuoguan.bilimusickmp.utils.UiEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlaylistPageViewModel(
    private val songRepository: SongRepositoryService,
    private val audioPlayService: AudioPlayService,
    private val biliService: BiliService,
    private val neteaseService: NeteaseService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState

    private val _isDragging = MutableStateFlow(false)
    private var pendingState: PlaylistUiState? = null

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
                )
            }.collect { newState ->
                if (_isDragging.value) {
                    pendingState = newState
                } else {
                    _uiState.value = newState
                }
            }
        }
    }

    fun onDragStart() {
        _isDragging.value = true
    }

    fun onDragEnd() {
        persistOrder()
        _isDragging.value = false
    }

    fun moveSong(from: Int, to: Int) {
        val list = _uiState.value.filteredSongs.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)

        _uiState.value = _uiState.value.copy(filteredSongs = list)
    }

    private fun persistOrder() {
        val songs = _uiState.value.filteredSongs
        val tsList = songs.map{ it.ts }.sorted()

        scope.launch {
            songs.forEachIndexed { index, song ->
                song.ts = tsList[index]
                songRepository.saveSong(song, refresh = false)
            }
            songRepository.loadSongs()
        }
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
        audioPlayService.updatePlaylist(
            list.map { song ->
                TrackInfo(
                    id = song.id,
                    title = song.title,
                    author = song.author,
                    audioSource = song.audioSource,
                    playSource = PlaySource.PLAYLIST,
                    pic = song.pic,
                    urlProvider = {
                        when (song.audioSource) {
                            AudioSource.BILI_BILI -> {
                                biliService.getAudioUrl(song.id, song.cid)
                            }

                            AudioSource.NET_EASE -> {
                                neteaseService.getAudioUrl(song.neteaseId)
                            }
                        }
                    },
                    lyricBias = song.lyricBias,
                    lyricsProvider = {
                        if (song.neteaseId.isEmpty()) {
                            emptyList()
                        } else {
                            neteaseService.getLyric(song.neteaseId)
                        }
                    }
                )
            }
        )
    }

    fun playSong(song: Song) {
        val track = TrackInfo(
            id = song.id,
            title = song.title,
            author = song.author,
            audioSource = song.audioSource,
            playSource = PlaySource.PLAYLIST,
            pic = song.pic,
            urlProvider = {
                when (song.audioSource) {
                    AudioSource.BILI_BILI -> {
                        biliService.getAudioUrl(song.id, song.cid)
                    }

                    AudioSource.NET_EASE -> {
                        neteaseService.getAudioUrl(song.neteaseId)
                    }
                }
            },
            lyricBias = song.lyricBias,
            lyricsProvider = {
                if (song.neteaseId.isEmpty()) {
                    emptyList()
                } else {
                    neteaseService.getLyric(song.neteaseId)
                }
            }
        )
        scope.launch {
            try {
                audioPlayService.play(track)
            }
            catch (e: Exception){
                _uiEvents.send(
                    UiEvent.ShowSnackbar(
                        message = e.message!!,
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

    fun requestEdit(song: Song) {
        _uiState.value = _uiState.value.copy(
            showEditDialog = true,
            songToHandle = song
        )
    }

    fun confirmEdit(song: Song) {
        scope.launch {
            songRepository.saveSong(song)
        }
    }

    fun cancelEdit(){
        _uiState.value = _uiState.value.copy(showEditDialog = false)
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

    val showDeleteDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val showBottomSheet: Boolean = false,

    val songToHandle: Song? = null,
)

enum class TagFilterMode {
    OR,
    AND
}