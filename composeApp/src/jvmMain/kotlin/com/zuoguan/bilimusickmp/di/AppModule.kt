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
import com.zuoguan.bilimusickmp.services.VlcAudioPlayService
import com.zuoguan.bilimusickmp.utils.getAppConfigDir
import com.zuoguan.bilimusickmp.utils.migrateLegacyJvmPreferences
import com.zuoguan.bilimusickmp.vm.LyricsPageViewModel
import com.zuoguan.bilimusickmp.vm.PlayBarViewModel
import com.zuoguan.bilimusickmp.vm.PlaylistPageViewModel
import com.zuoguan.bilimusickmp.vm.SearchPageViewModel
import com.zuoguan.bilimusickmp.vm.SettingsPageViewModel
import com.zuoguan.bilimusickmp.vm.SongEditorViewModel
import org.koin.dsl.module
import java.io.File


val appModule = module {
    single { BiliService() }
    single { NetEaseService() }
    single { KuGouService() }
    single { JsEngineService(File(getAppConfigDir(), "cloud_sync_script.js")) }
    single<PreferencesStorageService> {
        migrateLegacyJvmPreferences()
        JsonPreferencesStorageService(File(getAppConfigDir(), "preferences.json").absolutePath)
    }
    single { ExtractSongBaseInfoService(get()) }
    single<AudioPlayService> { VlcAudioPlayService() }
    single { SongRepositoryService() }
    single { SongMetadataService(get(), get(), get(), get()) }
    single(createdAtStart = true) { CloudSyncService(get(), get(), get()) }
    // 歌曲编辑会话：与 Android 共用同一实现，桌面端只是用对话框承载
    single { SongEditorViewModel(get(), get()) }

    single { SearchPageViewModel(get(), get(), get(), get()) }
    single {
        PlaylistPageViewModel(get(), get(), get(), get(), get())
    }
    single { PlayBarViewModel(get()) }
    single { LyricsPageViewModel(get(), get()) }
    single { SettingsPageViewModel(get(), get(), get()) }
}