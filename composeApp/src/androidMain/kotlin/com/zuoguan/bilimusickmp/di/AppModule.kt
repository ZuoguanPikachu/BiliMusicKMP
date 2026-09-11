package com.zuoguan.bilimusickmp.di

import com.zuoguan.bilimusickmp.services.AudioPlayService
import com.zuoguan.bilimusickmp.services.BiliService
import com.zuoguan.bilimusickmp.services.CloudSyncService
import com.zuoguan.bilimusickmp.services.ExoAudioPlayService
import com.zuoguan.bilimusickmp.services.ExtractSongBaseInfoService
import com.zuoguan.bilimusickmp.services.JsEngineService
import com.zuoguan.bilimusickmp.services.JsonPreferencesStorageService
import com.zuoguan.bilimusickmp.services.KuGouService
import com.zuoguan.bilimusickmp.services.NavigationService
import com.zuoguan.bilimusickmp.services.NetEaseService
import com.zuoguan.bilimusickmp.services.PreferencesStorageService
import com.zuoguan.bilimusickmp.services.SongEditService
import com.zuoguan.bilimusickmp.services.SongMetadataService
import com.zuoguan.bilimusickmp.services.SongRepositoryService
import com.zuoguan.bilimusickmp.utils.migrateLegacyAndroidPreferences
import com.zuoguan.bilimusickmp.vm.LyricsPageViewModel
import com.zuoguan.bilimusickmp.vm.PlayBarViewModel
import com.zuoguan.bilimusickmp.vm.PlaylistPageViewModel
import com.zuoguan.bilimusickmp.vm.SearchPageViewModel
import com.zuoguan.bilimusickmp.vm.SettingsPageViewModel
import com.zuoguan.bilimusickmp.vm.SongEditPageViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File

val appModule = module {
    single { BiliService() }
    single { NetEaseService() }
    single { KuGouService() }
    single { JsEngineService(File(androidContext().filesDir, "cloud_sync_script.js")) }
    single<PreferencesStorageService> {
        migrateLegacyAndroidPreferences(androidContext())
        JsonPreferencesStorageService(File(androidContext().filesDir, "preferences.json").absolutePath)
    }
    single { ExtractSongBaseInfoService(get()) }
    single { SongRepositoryService() }

    single<AudioPlayService> {
        ExoAudioPlayService(androidContext())
    }

    single { NavigationService() }
    single { SongEditService() }
    single { SongMetadataService(get(), get(), get(), get()) }
    single(createdAtStart = true) { CloudSyncService(get(), get(), get()) }

    single { SongEditPageViewModel(get(), get(), get()) }
    single { PlaylistPageViewModel(get(), get(), get(), get(), get()) }
    single { SearchPageViewModel(get(), get(), get(), get(), get(), get()) }
    single { SettingsPageViewModel(get(), get(), get()) }
    single { PlayBarViewModel(get()) }
    single { LyricsPageViewModel(get(), get()) }
}