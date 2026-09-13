package com.zuoguan.bilimusickmp.di

import com.zuoguan.bilimusickmp.services.AudioPlayService
import com.zuoguan.bilimusickmp.services.BiliService
import com.zuoguan.bilimusickmp.services.CloudSyncService
import com.zuoguan.bilimusickmp.services.ExtractSongBaseInfoService
import com.zuoguan.bilimusickmp.services.JsEngineService
import com.zuoguan.bilimusickmp.services.JsonPreferencesStorageService
import com.zuoguan.bilimusickmp.services.KuGouService
import com.zuoguan.bilimusickmp.services.NetEaseService
import com.zuoguan.bilimusickmp.services.PreferencesStorageService
import com.zuoguan.bilimusickmp.services.SongMetadataService
import com.zuoguan.bilimusickmp.services.SongRepositoryService
import com.zuoguan.bilimusickmp.services.UpdateCheckService
import com.zuoguan.bilimusickmp.services.VlcAudioPlayService
import com.zuoguan.bilimusickmp.utils.getAppConfigDir
import com.zuoguan.bilimusickmp.utils.migrateLegacyJvmPreferences
import com.zuoguan.bilimusickmp.vm.LyricsPageViewModel
import com.zuoguan.bilimusickmp.vm.PlayBarViewModel
import com.zuoguan.bilimusickmp.vm.PlaylistPageViewModel
import com.zuoguan.bilimusickmp.vm.SearchPageViewModel
import com.zuoguan.bilimusickmp.vm.SettingsPageViewModel
import com.zuoguan.bilimusickmp.vm.SongEditorViewModel
import com.zuoguan.bilimusickmp.vm.ThemeViewModel
import org.koin.dsl.module
import java.io.File


/**
 * 桌面端的 Koin 模块。
 *
 * 各服务与 ViewModel 集中在此绑定：偏好存储落盘为 preferences.json，
 * 音频播放由 VLC 实现。
 */
val appModule = module {
    single { BiliService() }
    single { NetEaseService() }
    single { KuGouService() }
    single { JsEngineService(File(getAppConfigDir(), "cloud_sync_script.js")) }
    // 建存储前先跑一次旧配置迁移；无旧文件时直接返回
    single<PreferencesStorageService> {
        migrateLegacyJvmPreferences()
        JsonPreferencesStorageService(File(getAppConfigDir(), "preferences.json").absolutePath)
    }
    single { ExtractSongBaseInfoService(get()) }
    single<AudioPlayService> { VlcAudioPlayService() }
    single { SongRepositoryService() }
    single { SongMetadataService(get(), get(), get(), get()) }
    single { UpdateCheckService() }
    single(createdAtStart = true) { CloudSyncService(get(), get(), get()) }
    // 歌曲编辑会话：ViewModel 双平台共用，桌面侧由对话框承载
    single { SongEditorViewModel(get(), get()) }

    single { SearchPageViewModel(get(), get(), get(), get()) }
    single {
        PlaylistPageViewModel(get(), get(), get(), get(), get())
    }
    single { PlayBarViewModel(get()) }
    single { LyricsPageViewModel(get(), get()) }
    single { SettingsPageViewModel(get(), get(), get(), get()) }
    // 主题色由根组件订阅，与设置页共用同一份偏好
    single { ThemeViewModel(get()) }
}