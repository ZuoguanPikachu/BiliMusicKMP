package com.zuoguan.bilimusickmp.services

import android.content.Context
import android.net.Uri
import android.util.Log
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.zuoguan.bilimusickmp.models.PlaySource
import com.zuoguan.bilimusickmp.utils.convertImageUrl
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

class ExoAudioPlayService(
    context: Context
): AudioPlayService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ExoPlayer 实例
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true
        )
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

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.value = when (playbackState) {
                    Player.STATE_IDLE -> PlaybackState.Stopped
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
                println()
            }

            @OptIn(UnstableApi::class)
            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int
            ) {
                when (reason) {
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> {
                        autoNext(mediaItem)
                    }

                    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> {
                        autoNext(mediaItem)
                    }

                    Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> {

                    }

                    Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> {

                    }
                }
            }

        })
        
        scope.launch {
            while (true) {
                withContext(Dispatchers.Main.immediate){
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

                delay(500)
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
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
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

        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)

        return mediaSource
    }

    @OptIn(UnstableApi::class)
    override suspend fun play(track: TrackInfo) {
        withContext(Dispatchers.Main.immediate) {
            player.stop()
            player.clearMediaItems()
        }

        _currentTrack.value = track
        val currMediaSource = buildMediaSource(track)

        if (track.playSource == PlaySource.PLAYLIST) {
            val nextMediaSource = buildMediaSource(getNextTrack()!!)

            withContext(Dispatchers.Main.immediate) {
                player.setMediaSources(listOf(currMediaSource, nextMediaSource), false)
                player.prepare()
                player.playWhenReady = true
            }
        }
        else {
            withContext(Dispatchers.Main.immediate) {
                player.setMediaSource(currMediaSource)
                player.prepare()
                player.playWhenReady = true
            }
        }

    }

    @OptIn(UnstableApi::class)
    fun autoNext(mediaItem: MediaItem?) {
        if (getNextTrack()!!.id == mediaItem?.mediaId){
            _currentTrack.value = playlist.first { it.id == mediaItem.mediaId }
            scope.launch {
                try{
                    val nextMediaSource = buildMediaSource(getNextTrack()!!)
                    withContext(Dispatchers.Main.immediate){
                        player.addMediaSource(nextMediaSource)
                    }
                }
                catch (e: Exception){

                }

            }
        }
        else {
            scope.launch {
                try{
                    play(getNextTrack()!!)
                }
                catch (e: Exception){

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
        val seekMs = (player.duration * safePos).toLong().coerceAtLeast(0L)
        withContext(Dispatchers.Main.immediate) {
            player.seekTo(seekMs)
        }
    }

    override suspend fun seekMs(time: Long) {
        withContext(Dispatchers.Main.immediate) {
            player.seekTo(time)
        }
    }

    private fun getNextTrack(): TrackInfo? {
        val current = currentTrack.value!!
        val nextTrack = when (_playMode.value) {
            PlayMode.SINGLE_LOOP -> current

            PlayMode.SHUFFLE -> {
                val candidates = playlist.filter { it.id != current.id }
                if (candidates.isNotEmpty()) candidates.random() else playlist.random()
            }

            PlayMode.SEQUENTIAL -> {
                val index = playlist.indexOfFirst { it.id == current.id }
                if (index == -1) playlist.firstOrNull()
                else playlist[(index + 1) % playlist.size]
            }
        }

        return nextTrack
    }

    override suspend fun playNext() {
        if (playlist.isEmpty()) return
        val nextTrack = getNextTrack() ?: return
        play(nextTrack)
    }

    private fun getPrevTrack(): TrackInfo? {
        val current = currentTrack.value!!

        val prevTrack = when (_playMode.value) {
            PlayMode.SINGLE_LOOP -> current

            PlayMode.SHUFFLE -> {
                val candidates = playlist.filter { it.id != current.id }
                if (candidates.isNotEmpty()) candidates.random() else playlist.random()
            }

            PlayMode.SEQUENTIAL -> {
                val index = playlist.indexOfFirst { it.id == current.id }
                if (index <= 0) playlist.lastOrNull()
                else playlist[index - 1]
            }
        }

        return prevTrack
    }

    override suspend fun playPrevious() {
        if (playlist.isEmpty()) return
        val prevTrack = getPrevTrack() ?: return
        play(prevTrack)
    }
}
