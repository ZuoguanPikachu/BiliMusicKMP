package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.PlayMode
import com.zuoguan.bilimusickmp.models.PlaybackState
import com.zuoguan.bilimusickmp.models.TrackInfo
import kotlinx.coroutines.flow.StateFlow

interface AudioPlayService {

    val state: StateFlow<PlaybackState>
    val currentTrack: StateFlow<TrackInfo?>
    val playMode: StateFlow<PlayMode>
    val playlist: List<TrackInfo>

    suspend fun updatePlaylist(list: List<TrackInfo>)

    fun togglePlayMode()

    val position: StateFlow<Float>
    val time: StateFlow<Long>
    val duration: StateFlow<Long>

    suspend fun play(track: TrackInfo)
    suspend fun pause()
    suspend fun resume()
    suspend fun stop()
    suspend fun seek(position: Float)
    suspend fun seekMs(time: Long)

    suspend fun playNext()
    suspend fun playPrevious()
}