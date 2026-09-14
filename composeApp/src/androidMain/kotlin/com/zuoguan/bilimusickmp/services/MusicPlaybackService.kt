package com.zuoguan.bilimusickmp.services

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.*
import com.zuoguan.bilimusickmp.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

    /** 会话回调不在挂起上下文里，切歌要借它回到协程中调用 [AudioPlayService]。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
            .setCallback(PlaybackSessionCallback())
            .build()
    }

    /**
     * 把系统侧的"上一首 / 下一首"接到界面按钮用的那套歌单逻辑上。
     *
     * 通知栏、锁屏、蓝牙耳机与 Android Auto 最终都以 Player 命令的形式落到会话上，Media3 的
     * 默认处理是让 ExoPlayer 在**它自己的播放列表**里前后跳。但这里交给 ExoPlayer 的只有
     * "当前曲 + 预取下一曲"这个滑动窗口——整张歌单的音频地址是按需解析的，没法一次塞进播放器。
     * 于是默认处理必然出问题：跳过一次之后窗口里再没有下一首，上一首则因为窗口里没有前一项而
     * 按不动，界面状态还会与播放内容错位。
     *
     * 所以这里把这两类命令拦下来，改调界面同款的 [AudioPlayService.playNext] /
     * [AudioPlayService.playPrevious]（顺序、随机、单曲循环三种模式的行为因此完全一致），
     * 并返回非 SUCCESS 让 ExoPlayer 不要再跳一次。其余命令一律放行，交给 Media3 默认处理
     * （播放/暂停、停止、快进快退等本来就直接作用在同一个 ExoPlayer 上）。
     *
     * 注：`onPlayerCommandRequest` 在 Media3 中已标记为废弃，但它是目前唯一能在命令真正下发到
     * 播放器之前拦截的地方。`onMediaButtonEvent` 只覆盖通知栏与媒体按键，锁屏的系统传输控件
     * 走不到那里，所以两者都替代不了它。升级 Media3 时需留意这个覆写是否还在。
     */
    private inner class PlaybackSessionCallback : MediaSession.Callback {
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int
        ): Int {
            when (playerCommand) {
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ->
                    scope.launch { audioPlayService.playNext() }

                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ->
                    scope.launch { audioPlayService.playPrevious() }

                else -> return SessionResult.RESULT_SUCCESS
            }

            return SessionResult.RESULT_ERROR_NOT_SUPPORTED
        }
    }

    override fun onDestroy() {
        scope.cancel()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
