package com.zuoguan.bilimusickmp.di

import com.zuoguan.bilimusickmp.services.AudioPlayService
import com.zuoguan.bilimusickmp.services.BiliService
import com.zuoguan.bilimusickmp.services.ExtractSongBaseInfoService
import com.zuoguan.bilimusickmp.services.FilePreferencesStorageService
import com.zuoguan.bilimusickmp.services.NeteaseService
import com.zuoguan.bilimusickmp.services.PreferencesStorageService
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
    single { NeteaseService() }
    single<PreferencesStorageService> { FilePreferencesStorageService(File(getAppConfigDir(), "llm_config.json")) }
    single { ExtractSongBaseInfoService(get()) }
    single<AudioPlayService> { VlcAudioPlayService() }
    single { SongRepositoryService() }

    single { SearchPageViewModel(get(), get(), get(), get (), get()) }
    single {
        PlaylistPageViewModel(get(), get(), get(), get ())
    }
    single { PlayBarViewModel(get()) }
    single { LyricsPageViewModel(get()) }
    single { SettingsPageViewModel(get()) }
}