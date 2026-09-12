package com.zuoguan.bilimusickmp.services

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.*
import com.zuoguan.bilimusickmp.MainActivity
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
        val player = (audioPlayService as? ExoAudioPlayService)?.player
        if (player == null) {
            println("无法创建 MediaSession：AudioPlayService 不是 ExoAudioPlayService")
            return
        }

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setId("bilimusic_session")
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
