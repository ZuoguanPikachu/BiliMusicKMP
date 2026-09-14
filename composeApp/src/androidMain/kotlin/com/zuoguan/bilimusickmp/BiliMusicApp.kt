package com.zuoguan.bilimusickmp

import android.app.Application
import android.content.ComponentName
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.zuoguan.bilimusickmp.di.appModule
import com.zuoguan.bilimusickmp.services.MusicPlaybackService
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.util.concurrent.Future

/**
 * 应用入口：启动 Koin，并把 Application 的 Context 提供给需要它的模块。
 *
 * 另外还会建立一条指向 [MusicPlaybackService] 的常驻控制器连接，详见
 * [playbackServiceConnection]。
 */
class BiliMusicApp : Application() {

    /**
     * 指向 [MusicPlaybackService] 的常驻连接句柄。
     *
     * [MusicPlaybackService] 是 `MediaSessionService`（绑定服务），系统只会在有控制器
     * 连接时创建它。没有这条连接，服务根本不会被实例化，它里面的 `MediaSession` 也就
     * 不会被注册——播放通知、锁屏控制、蓝牙与耳机按键会全部失效。
     *
     * 连接由 `bindService()` 建立，只有显式调用 [MediaController.releaseFuture] 才断开，
     * 所以持有句柄就代表"进程存活期间连接一直有效"：退出界面后播放通知依然可用。
     * 这也是唯一需要保留这个句柄的原因，不要因为"没人读它"而删掉赋值。
     */
    internal lateinit var playbackServiceConnection: Future<MediaController>
        private set

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@BiliMusicApp)
            modules(appModule)
        }

        // 必须放在 Koin 启动之后：服务创建时会立即从 Koin 取出播放器并建立 MediaSession
        val sessionToken = SessionToken(this, ComponentName(this, MusicPlaybackService::class.java))
        playbackServiceConnection = MediaController.Builder(this, sessionToken).buildAsync()
    }
}
