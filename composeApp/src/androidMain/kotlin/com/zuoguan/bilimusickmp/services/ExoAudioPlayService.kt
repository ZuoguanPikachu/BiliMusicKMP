package com.zuoguan.bilimusickmp.services

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.zuoguan.bilimusickmp.models.PlayMode
import com.zuoguan.bilimusickmp.models.PlaybackState
import com.zuoguan.bilimusickmp.models.TrackInfo
import com.zuoguan.bilimusickmp.models.AudioSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.URL
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri
import com.zuoguan.bilimusickmp.models.PlaySource
import com.zuoguan.bilimusickmp.utils.convertImageUrl
import kotlin.time.Duration.Companion.milliseconds

/**
 * ExoPlayer 实现的 [AudioPlayService]。
 *
 * 全局只维护一个 ExoPlayer；对播放器的读写都切回主线程执行，播放状态与进度通过
 * StateFlow 暴露给 UI。
 */
class ExoAudioPlayService(
    context: Context,
    private val onError: (String) -> Unit = {}
): AudioPlayService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 全曲目共用一个 OkHttpClient / DataSource.Factory，避免每首歌新建连接池与线程
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @OptIn(UnstableApi::class)
    private val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)

    @OptIn(UnstableApi::class)
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true // 自动处理音频焦点
        )
        // 耳机拔出时自动暂停，避免外放"社死"
        .setHandleAudioBecomingNoisy(true)
        // 息屏后仍能继续播放
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    private val _state = MutableStateFlow(PlaybackState.Stopped)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _currentTrack = MutableStateFlow<TrackInfo?>(null)
    override val currentTrack: StateFlow<TrackInfo?> = _currentTrack.asStateFlow()

    private val _playMode = MutableStateFlow(PlayMode.SEQUENTIAL)
    override val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    private val _playlist = MutableStateFlow<List<TrackInfo>>(emptyList())
    override val playlist: List<TrackInfo>
        get() = _playlist.value

    private val _position = MutableStateFlow(0f)
    override val position: StateFlow<Float> = _position.asStateFlow()

    private val _time = MutableStateFlow(0L)
    override val time: StateFlow<Long> = _time.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    /** 已释放标记：释放后不再触发任何回调逻辑。 */
    @Volatile
    private var released = false

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.value = when (playbackState) {
                    Player.STATE_IDLE -> PlaybackState.Stopped
                    // 缓冲阶段仍视为"播放中"，避免 UI 在起播瞬间闪烁
                    Player.STATE_BUFFERING -> PlaybackState.Playing
                    Player.STATE_READY -> if (player.playWhenReady) PlaybackState.Playing else PlaybackState.Paused
                    Player.STATE_ENDED -> PlaybackState.Ended
                    else -> PlaybackState.Stopped
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (player.playbackState == Player.STATE_READY) {
                    _state.value = if (isPlaying) PlaybackState.Playing else PlaybackState.Paused
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _state.value = PlaybackState.Error
                onError(error.message ?: "播放失败")
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int
            ) {
                // 只有"自动播完切下一首"才算前进；SEEK（拖动进度/切歌）不应触发自动续播，
                // 否则通知栏的"上一首"会被当成"下一首"。
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    autoNext(mediaItem)
                }
            }

        })

        scope.launch {
            // ExoPlayer 不会持续回调播放进度，位置与时长只能靠轮询同步
            while (!released) {
                withContext(Dispatchers.Main.immediate) {
                    if (player.playbackState == Player.STATE_READY) {
                        val dur = player.duration.coerceAtLeast(0L)
                        _duration.value = dur

                        val posMs = player.currentPosition.coerceAtLeast(0L)
                        _time.value = posMs

                        if (dur > 0) {
                            _position.value = (posMs.toFloat() / dur).coerceIn(0f, 1f)
                        }
                    }
                }

                delay(500.milliseconds)
            }
        }
    }

    override suspend fun updatePlaylist(list: List<TrackInfo>) {
        _playlist.value = list

    }

    override fun togglePlayMode() {
        _playMode.value = when (_playMode.value) {
            PlayMode.SEQUENTIAL -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.SINGLE_LOOP
            PlayMode.SINGLE_LOOP -> PlayMode.SEQUENTIAL
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun buildMediaSource(track: TrackInfo): MediaSource {
        val audioUrl = track.urlProvider()

        val headers = mutableMapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
            "Accept" to "*/*",
        )

        when (track.audioSource) {
            AudioSource.BILI_BILI -> {
                headers["Referer"] = "https://www.bilibili.com/"
                headers["Origin"] = "https://www.bilibili.com"
            }
            AudioSource.NET_EASE -> {
                headers["Host"] = URL(audioUrl).host
                headers["Referer"] = "https://music.163.com/"
                headers["Origin"] = "https://music.163.com"
            }
            AudioSource.KU_GOU -> {
                headers["User-Agent"] = "IPhone-8990-searchSong"
                headers["UNI-UserAgent"] = "iOS11.4-Phone8990-1009-0-WiFi"
            }
        }

        val factory = dataSourceFactory
            .setDefaultRequestProperties(headers)

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.author)
            .apply {
                setArtworkUri(convertImageUrl(track.pic, 512, 512).toUri())
            }
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(audioUrl)
            .setMediaMetadata(mediaMetadata)
            .setMediaId(track.id)
            .build()

        val mediaSource = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(mediaItem)

        return mediaSource
    }

    @OptIn(UnstableApi::class)
    override suspend fun play(track: TrackInfo) {
        val currentSource = buildMediaSource(track)
        // 预取下一首只是优化：失败不能影响当前歌曲起播。
        // 基准必须是"即将播放的 track"：此刻 _currentTrack 还停在上一首，若用默认参数，
        // 算出来的会是上一首的下一首（通常正是 track 本身），播放器时间线里就只剩一首——
        // 系统侧没有"下一曲"可用，播完也不会自动续播。
        // 注意不要在这里加 `takeIf { it.id != track.id }` 之类的过滤：单曲循环与单曲歌单的
        // "下一首"本来就是自己，那个窗口正是循环播放所依赖的第二条时间线条目。
        val nextSource = if (track.playSource == PlaySource.PLAYLIST) {
            getNextTrack(track)?.let { next ->
                try {
                    buildMediaSource(next)
                } catch (e: Exception) {
                    null
                }
            }
        } else {
            null
        }

        withContext(Dispatchers.Main.immediate) {
            _currentTrack.value = track
            player.stop()
            player.clearMediaItems()
            if (nextSource != null) {
                player.setMediaSources(listOf(currentSource, nextSource), false)
            } else {
                player.setMediaSource(currentSource)
            }
            player.prepare()
            player.playWhenReady = true
        }
    }

    /** 自动续播：接上预取好的下一首，或回退到完整地起播下一首。 */
    fun autoNext(mediaItem: MediaItem?) {
        val next = getNextTrack() ?: return
        val itemId = mediaItem?.mediaId

        if (next.id == itemId) {
            // 预取的那首已经自动接上：同步 UI 状态并继续预取再下一首
            _currentTrack.value = playlist.firstOrNull { it.id == itemId } ?: next
            scope.launch {
                try {
                    val following = getNextTrack(next) ?: return@launch
                    val nextMediaSource = buildMediaSource(following)
                    withContext(Dispatchers.Main.immediate) {
                        player.addMediaSource(nextMediaSource)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 预取失败不影响当前播放
                }
            }
        } else {
            scope.launch {
                try {
                    play(next)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.value = PlaybackState.Error
                    onError(e.message ?: "播放失败")
                }
            }
        }
    }

    override suspend fun pause() {
        withContext(Dispatchers.Main.immediate) {
            player.pause()
        }
    }

    override suspend fun resume() {
        withContext(Dispatchers.Main.immediate) {
            player.play()
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.Main.immediate) {
            player.stop()
            player.clearMediaItems()
        }
        _currentTrack.value = null
        _state.value = PlaybackState.Stopped
    }

    override suspend fun seek(position: Float) {
        val safePos = position.coerceIn(0f, 1f)
        withContext(Dispatchers.Main.immediate) {
            // ExoPlayer 只允许在应用主线程访问，duration 也必须在这读
            val duration = player.duration.coerceAtLeast(0L)
            player.seekTo((duration * safePos).toLong())
        }
    }

    override suspend fun seekMs(time: Long) {
        withContext(Dispatchers.Main.immediate) {
            player.seekTo(time)
        }
    }

    /**
     * 按当前播放模式求下一首。
     *
     * @param from 基准曲目，缺省为当前正在播放的曲目。自动续播时的语义就是"当前曲的下一首"，
     *   所以这里默认取 `_currentTrack`；而 [play] 预取下一首时必须显式传入将要播放的曲目，
     *   因为那一刻 `_currentTrack` 还没更新。
     * @return 基准曲目或歌单为空时返回 null，调用方必须判空。
     */
    private fun getNextTrack(from: TrackInfo? = _currentTrack.value): TrackInfo? {
        val current = from ?: return null
        val list = _playlist.value
        if (list.isEmpty()) return null

        return when (_playMode.value) {
            PlayMode.SINGLE_LOOP -> current

            PlayMode.SHUFFLE -> {
                val candidates = list.filter { it.id != current.id }
                if (candidates.isNotEmpty()) candidates.random() else list.random()
            }

            PlayMode.SEQUENTIAL -> {
                val index = list.indexOfFirst { it.id == current.id }
                if (index == -1) list.first()
                else list[(index + 1) % list.size]
            }
        }
    }

    override suspend fun playNext() {
        val nextTrack = getNextTrack() ?: return
        play(nextTrack)
    }

    /** 按当前播放模式求上一首；无当前曲目或歌单为空时返回 null。 */
    private fun getPrevTrack(): TrackInfo? {
        val current = _currentTrack.value ?: return null
        val list = _playlist.value
        if (list.isEmpty()) return null

        return when (_playMode.value) {
            PlayMode.SINGLE_LOOP -> current

            PlayMode.SHUFFLE -> {
                val candidates = list.filter { it.id != current.id }
                if (candidates.isNotEmpty()) candidates.random() else list.random()
            }

            PlayMode.SEQUENTIAL -> {
                val index = list.indexOfFirst { it.id == current.id }
                if (index <= 0) list.last()
                else list[index - 1]
            }
        }
    }

    override suspend fun playPrevious() {
        val prevTrack = getPrevTrack() ?: return
        play(prevTrack)
    }

    override fun close() {
        released = true
        scope.cancel()
        player.release()
    }
}
