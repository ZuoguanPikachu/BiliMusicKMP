package com.zuoguan.bilimusickmp

import android.app.Application
import com.zuoguan.bilimusickmp.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BiliMusicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@BiliMusicApp)
            modules(appModule)
        }
    }
}