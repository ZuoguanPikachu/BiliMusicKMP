package com.zuoguan.bilimusickmp.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.*
import com.zuoguan.bilimusickmp.models.AudioSource
import com.zuoguan.bilimusickmp.models.PlayMode
import com.zuoguan.bilimusickmp.models.PlaybackState
import com.zuoguan.bilimusickmp.models.TrackInfo
import kotlinx.coroutines.launch

class VlcAudioPlayService : AudioPlayService {

    private val factory = MediaPlayerFactory()
    private val mediaPlayer = factory.mediaPlayers().newMediaPlayer()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // -------- StateFlows --------

    private val _state = MutableStateFlow(PlaybackState.Stopped)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _currentTrack = MutableStateFlow<TrackInfo?>(null)
    override val currentTrack: StateFlow<TrackInfo?> = _currentTrack.asStateFlow()

    private val _playMode = MutableStateFlow(PlayMode.SEQUENTIAL)
    override val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    @Volatile
    private var _playlist: List<TrackInfo> = emptyList()
    override val playlist: List<TrackInfo>
        get() = _playlist

    private val _position = MutableStateFlow(0f)
    override val position: StateFlow<Float> = _position.asStateFlow()

    private val _time = MutableStateFlow(0L)
    override val time: StateFlow<Long> = _time.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()


    init {
        mediaPlayer.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {

            override fun playing(mediaPlayer: MediaPlayer) {
                _state.value = PlaybackState.Playing
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                _state.value = PlaybackState.Paused
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                _state.value = PlaybackState.Stopped
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                _state.value = PlaybackState.Ended

                scope.launch {
                    try {
                        playNext()
                    } catch (_: Exception){

                    }
                }
            }

            override fun error(mediaPlayer: MediaPlayer) {
                _state.value = PlaybackState.Error
            }

            override fun positionChanged(mediaPlayer: MediaPlayer, newPosition: Float) {
                _position.value = newPosition
            }

            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                _time.value = newTime
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                if (newLength > 0) {
                    _duration.value = newLength
                }
            }
        })
    }

    // -------- API --------

    override suspend fun updatePlaylist(list: List<TrackInfo>) {
        _playlist = list
    }

    override fun togglePlayMode() {
        _playMode.update { mode ->
            when (mode) {
                PlayMode.SEQUENTIAL -> PlayMode.SHUFFLE
                PlayMode.SHUFFLE -> PlayMode.SINGLE_LOOP
                PlayMode.SINGLE_LOOP -> PlayMode.SEQUENTIAL
            }
        }
    }

    override suspend fun play(track: TrackInfo) {
        val url = track.urlProvider()

        _position.value = 0f
        _time.value = 0L
        _duration.value = 0L
        _currentTrack.value = track

        val options = when (track.audioSource) {
            AudioSource.BILI_BILI -> arrayOf(
                ":http-referrer=https://www.bilibili.com/",
                ":http-user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )

            AudioSource.KU_GOU -> arrayOf(
                ":http-uni-useragent=iOS11.4-Phone8990-1009-0-WiFi",
                ":http-user-agent=IPhone-8990-searchSong"
            )

            AudioSource.NET_EASE -> arrayOf(
                ":http-user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
        }

        mediaPlayer.media().play(url, *options)
    }

    override suspend fun pause() {
        mediaPlayer.controls().pause()
    }

    override suspend fun resume() {
        mediaPlayer.controls().play()
    }

    override suspend fun stop() {
        mediaPlayer.controls().stop()
        _currentTrack.value = null
        _position.value = 0f
        _time.value = 0L
        _duration.value = 0L
        _state.value = PlaybackState.Stopped
    }

    override suspend fun seek(position: Float) {
        mediaPlayer.controls().setPosition(position.coerceIn(0f, 1f))
    }

    override suspend fun seekMs(time: Long) {
        val duration = mediaPlayer.status().length().takeIf { it > 0 } ?: duration.value
        if (duration <= 0) return

        val percent = time.toFloat() / duration
        seek(percent)
    }

    override suspend fun playNext() {
        val next = nextTrack() ?: return
        play(next)
    }

    private fun nextTrack(): TrackInfo? {
        val current = _currentTrack.value ?: return null
        val list = playlist
        if (list.isEmpty()) return null

        return when (_playMode.value) {
            PlayMode.SINGLE_LOOP -> list.find { it.id == current.id } ?: current

            PlayMode.SHUFFLE -> list.filterNot { it.id == current.id }
                .randomOrNull()
                ?: current

            PlayMode.SEQUENTIAL -> {
                val index = list.indexOfFirst { it.id == current.id }
                if (index == -1) list.first()
                else list[(index + 1) % list.size]
            }
        }
    }

    override suspend fun playPrevious() {
        val current = _currentTrack.value ?: return
        val list = playlist
        if (list.isEmpty()) return

        val prev = when (_playMode.value) {

            PlayMode.SINGLE_LOOP -> list.find { it.id == current.id } ?: current

            PlayMode.SHUFFLE -> list.filterNot { it.id == current.id }
                .randomOrNull()
                ?: current

            PlayMode.SEQUENTIAL -> {
                val index = list.indexOfFirst { it.id == current.id }
                if (index <= 0) list.last()
                else list[index - 1]
            }
        }

        play(prev)
    }

    override fun close() {
        runCatching { mediaPlayer.release() }
        runCatching { factory.release() }
    }
}