package com.zuoguan.bilimusickmp.di

import com.zuoguan.bilimusickmp.services.AudioPlayService
import com.zuoguan.bilimusickmp.services.BiliService
import com.zuoguan.bilimusickmp.services.ExtractSongBaseInfoService
import com.zuoguan.bilimusickmp.services.FilePreferencesStorageService
import com.zuoguan.bilimusickmp.services.JsEngineService
import com.zuoguan.bilimusickmp.services.KuGouService
import com.zuoguan.bilimusickmp.services.NetEaseService
import com.zuoguan.bilimusickmp.services.PreferencesStorageService
import com.zuoguan.bilimusickmp.services.SongMetadataService
import com.zuoguan.bilimusickmp.services.SongRepositoryService
import com.zuoguan.bilimusickmp.services.VlcAudioPlayService
import com.zuoguan.bilimusickmp.utils.getAppConfigDir
import com.zuoguan.bilimusickmp.vm.LyricsPageViewModel
import com.zuoguan.bilimusickmp.vm.SearchPageViewModel
import com.zuoguan.bilimusickmp.vm.PlayBarViewModel
import com.zuoguan.bilimusickmp.vm.PlaylistPageViewModel
import com.zuoguan.bilimusickmp.vm.SettingsPageViewModel
import org.koin.dsl.module
import java.io.File


val appModule = module {
    single { BiliService() }
    single { NetEaseService() }
    single { KuGouService() }
    single { JsEngineService(File(getAppConfigDir(), "cloud_sync_script.js")) }
    single<PreferencesStorageService> { FilePreferencesStorageService(File(getAppConfigDir(), "llm_config.json"), get()) }
    single { ExtractSongBaseInfoService(get()) }
    single<AudioPlayService> { VlcAudioPlayService() }
    single { SongRepositoryService(File(getAppConfigDir(), "songs-db.cblite2"), get() ) }
    single { SongMetadataService(get(), get(), get(), get()) }

    single { SearchPageViewModel(get(), get(), get(), get (), get(), get()) }
    single {
        PlaylistPageViewModel(get(), get(), get(), get (), get())
    }
    single { PlayBarViewModel(get()) }
    single { LyricsPageViewModel(get(), get()) }
    single { SettingsPageViewModel(get(), get()) }
}