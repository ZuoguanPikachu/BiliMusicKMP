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
import com.zuoguan.bilimusickmp.services.QQMusicService
import com.zuoguan.bilimusickmp.services.SongCacheService
import com.zuoguan.bilimusickmp.services.SongDownloadService
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

/**
 * 歌单页状态。
 *
 * 把仓库里的歌曲与标签聚合成过滤后的列表，并承担播放、拖动排序、删除等交互；
 * 歌词与音频地址在播放时才按各自来源惰性解析。
 */
class PlaylistPageViewModel(
    private val songRepository: SongRepositoryService,
    private val audioPlayService: AudioPlayService,
    private val biliService: BiliService,
    private val netEaseService: NetEaseService,
    private val kuGouService: KuGouService,
    private val qqMusicService: QQMusicService,
    private val songCacheService: SongCacheService,
    private val songDownloadService: SongDownloadService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** 列表滚动状态；放在 VM 里是为了让滚动位置跨重组保留。 */
    val lazyListState = LazyListState()

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    private val _isDragging = MutableStateFlow(false)

    private val _uiEvents = Channel<UiEvent>(Channel.BUFFERED)
    /** 一次性 UI 事件（如播放失败提示）。 */
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
                // 拖动排序期间以本地顺序为准，避免仓库回流的列表覆盖正在拖动的结果
                if (!_isDragging.value) {
                    _uiState.value = newState
                }
            }
        }
    }

    /** 开始拖动排序：期间暂停接受仓库回流的列表更新。 */
    fun onDragStart() {
        _isDragging.value = true
    }

    /** 结束拖动排序，并把当前顺序持久化到仓库。 */
    fun onDragEnd() {
        val orderedSongs = _uiState.value.filteredSongs
        _isDragging.value = false
        scope.launch {
            songRepository.persistOrder(orderedSongs)
        }
    }

    /** 拖动过程中在本地重排列表项。 */
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

    /** 把过滤后的列表同步给播放服务，使上一首/下一首按当前视图顺序播放。 */
    suspend fun updatePlaylist(list: List<Song>) {
        val tracks = list.map { it.toTrackInfo() }
        audioPlayService.updatePlaylist(tracks)

        // 正在播放的歌也可能刚被编辑过（改名/换封面）：用最新元数据刷新播放条，但不重新起播。
        // 只处理「从歌单播放」的曲目：搜索页试听的同一首歌不该因为歌单刷新就变成歌单来源
        // （那会让歌单页把它高亮成正在播放）。歌曲被标签筛掉时列表里找不到它，同样保持原样。
        val current = audioPlayService.currentTrack.value ?: return
        if (current.playSource != PlaySource.PLAYLIST) return
        tracks.firstOrNull { it.id == current.id }?.let { audioPlayService.refreshCurrentTrack(it) }
    }

    private fun Song.toTrackInfo(): TrackInfo = TrackInfo(
        id = id,
        title = title,
        author = author,
        audioSource = audioSource,
        playSource = PlaySource.PLAYLIST,
        pic = pic,
        urlProvider = { resolveAudioUrl() },
        lyricSource = lyricSource,
        lyricId = lyricId,
        lyricBias = lyricBias,
        lyricsProvider = { resolveLyrics() },
    )

    /**
     * 播放地址：本地有下载 / 缓存就直接播本地文件，否则取远端直链，
     * 并在缓存开关允许时顺带在后台缓存一份（不阻塞起播）。
     */
    private suspend fun Song.resolveAudioUrl(): String = songCacheService.resolvePlaybackUrl(
        songId = id,
        audioSource = audioSource,
        autoCache = true,
        remoteUrl = { resolveRemoteAudioUrl() }
    )

    /** 各音源的远端直链，仅在本地没有文件时才会被调用。 */
    private suspend fun Song.resolveRemoteAudioUrl(): String = when (audioSource) {
        AudioSource.BILI_BILI -> biliService.getAudioUrl(id, cid)
        AudioSource.NET_EASE -> netEaseService.getAudioUrl(id)
        AudioSource.KU_GOU -> kuGouService.getAudioUrl(id)
        AudioSource.QQ_MUSIC -> qqMusicService.getAudioUrl(id)
    }

    private suspend fun Song.resolveLyrics(): List<LyricLine> = when (lyricSource) {
        LyricSource.KU_GOU -> kuGouService.getLyric(lyricId)
        LyricSource.NET_EASE -> netEaseService.getLyric(lyricId)
        LyricSource.QQ_MUSIC -> qqMusicService.getLyric(lyricId)
        LyricSource.NONE -> emptyList()
    }

    /** 播放列表中的某首歌，失败时用 Snackbar 提示。 */
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

    /**
     * 把某首歌下载到系统的「音乐」目录，文件名用「歌曲 - 歌手」。
     *
     * 与「播放时自动缓存」相互独立：即使缓存开关是关的，用户主动点下载也照做，
     * 也不受「仅 Wi-Fi」限制。下载完成后文件归系统音乐库管理，应用不再跟踪它。
     *
     * 下载期间 [PlaylistUiState.downloadingIds] 里有这首歌，界面据此把按钮换成 loading
     * 并挡住重复点击；结束后才收起底部菜单——手机端的下载入口在菜单里，立刻收起的话
     * 用户看不到「正在下载」的反馈。
     */
    fun downloadSong(song: Song) {
        if (song.id in _uiState.value.downloadingIds) return

        _uiState.update { it.copy(downloadingIds = it.downloadingIds + song.id) }
        scope.launch {
            try {
                val result = songDownloadService.download(
                    title = song.title,
                    author = song.author,
                    audioSource = song.audioSource,
                    remoteUrl = { song.resolveRemoteAudioUrl() }
                )

                _uiEvents.send(
                    result.fold(
                        onSuccess = { location ->
                            UiEvent.ShowSnackBar("已保存到 $location")
                        },
                        onFailure = { error ->
                            UiEvent.ShowSnackBar(
                                message = "下载失败：${error.message ?: "未知错误"}",
                                duration = SnackbarDuration.Long
                            )
                        }
                    )
                )
            } finally {
                _uiState.update {
                    it.copy(
                        downloadingIds = it.downloadingIds - song.id,
                        showBottomSheet = false
                    )
                }
            }
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

    /** 请求删除某首歌：先弹出确认框，真正删除由 [confirmDelete] 执行。 */
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
            // 歌单里没有这首歌了，它留在缓存里的副本也没有意义，顺手清掉免得白占空间
            // （下载到音乐目录的文件属于用户，不动）
            songCacheService.purge(song.id)
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

    /**
     * 正在下载到音乐目录的歌曲 id。
     *
     * 只用于把下载按钮换成 loading、挡住重复点击；下载完就归系统音乐库，不再标记。
     */
    val downloadingIds: Set<String> = emptySet(),
)

/** 多标签过滤模式：[OR] 命中任一选中标签即可，[AND] 需命中全部选中标签。 */
enum class TagFilterMode {
    OR,
    AND
}