package com.zuoguan.bilimusickmp.services

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSessionService
import androidx.media3.session.*
import org.koin.android.ext.android.inject


class MusicPlaybackService : MediaSessionService() {
    private val audioPlayService: AudioPlayService by inject()
    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        if (mediaSession == null){
            createMediaSession()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        if (mediaSession == null) {
            createMediaSession()
        }
        return mediaSession
    }

    fun createMediaSession() {
        val player = (audioPlayService as? ExoAudioPlayService)?.player ?: return
        mediaSession = MediaSession.Builder(this, player)
            .setId("bilimusic_session")
            .build()
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
