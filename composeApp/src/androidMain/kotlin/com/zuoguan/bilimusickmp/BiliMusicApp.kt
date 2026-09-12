package com.zuoguan.bilimusickmp

import android.app.Application
import com.zuoguan.bilimusickmp.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/** 应用入口：启动 Koin，并把 Application 的 Context 提供给需要它的模块。 */
class BiliMusicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@BiliMusicApp)
            modules(appModule)
        }
    }
}