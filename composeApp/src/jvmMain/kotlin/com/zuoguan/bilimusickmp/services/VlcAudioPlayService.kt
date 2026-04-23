package com.zuoguan.bilimusickmp.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    override var playlist: List<TrackInfo> = emptyList()

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
        playlist = list
    }

    override fun togglePlayMode() {
        _playMode.value = when (_playMode.value) {
            PlayMode.SEQUENTIAL -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.SINGLE_LOOP
            PlayMode.SINGLE_LOOP -> PlayMode.SEQUENTIAL
        }
    }

    override suspend fun play(track: TrackInfo) {
        if (mediaPlayer.status().isPlaying) {
            mediaPlayer.controls().stop()
        }

        _currentTrack.value = track

        val url = track.urlProvider()

        val options = when (track.audioSource) {
            AudioSource.BILI_BILI -> arrayOf(
                ":http-referrer=https://www.bilibili.com/",
                ":http-user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
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
    }

    override suspend fun seek(position: Float) {
        mediaPlayer.controls().setPosition(position.coerceIn(0f, 1f))
    }

    override suspend fun seekMs(time: Long) {
        val duration = duration.value
        if (duration <= 0) return

        val percent = time.toFloat() / duration
        seek(percent)
    }

    override suspend fun playNext() {
        val current = currentTrack.value ?: return
        if (playlist.isEmpty()) return

        val next = when (_playMode.value) {

            PlayMode.SINGLE_LOOP -> {
                playlist.find { it.id == current.id }
            }

            PlayMode.SHUFFLE -> {
                playlist.filterNot { it.id == current.id }
                    .randomOrNull()
                    ?: playlist.first()
            }

            PlayMode.SEQUENTIAL -> {
                val index = playlist.indexOfFirst { it.id == current.id }
                if (index == -1) playlist.first()
                else playlist[(index + 1) % playlist.size]
            }
        } ?: return

        play(next)
    }

    override suspend fun playPrevious() {
        val current = currentTrack.value ?: return
        if (playlist.isEmpty()) return

        val prev = when (_playMode.value) {

            PlayMode.SINGLE_LOOP -> {
                playlist.find { it.id == current.id }
            }

            PlayMode.SHUFFLE -> {
                playlist.filterNot { it.id == current.id }
                    .randomOrNull()
                    ?: playlist.first()
            }

            PlayMode.SEQUENTIAL -> {
                val index = playlist.indexOfFirst { it.id == current.id }
                if (index <= 0) playlist.last()
                else playlist[index - 1]
            }
        } ?: return

        play(prev)
    }
}