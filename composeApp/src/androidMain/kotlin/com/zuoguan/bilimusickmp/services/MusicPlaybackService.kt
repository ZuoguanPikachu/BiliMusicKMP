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


/**
 * 承载播放通知与系统媒体控制的 [MediaSessionService]。
 *
 * 它不自己创建播放器，而是复用 Koin 中的 [ExoAudioPlayService] 单例，把同一个 ExoPlayer
 * 交给 MediaSession，通知栏与耳机按键因此能直接控制播放。
 */
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

    /** 建立 MediaSession：点击通知回到 [MainActivity]。 */
    fun createMediaSession() {
        val player = (audioPlayService as? ExoAudioPlayService)?.player
        if (player == null) {
            // 只有 Exo 实现才对外暴露 player，其他实现无法挂到 MediaSession 上
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
