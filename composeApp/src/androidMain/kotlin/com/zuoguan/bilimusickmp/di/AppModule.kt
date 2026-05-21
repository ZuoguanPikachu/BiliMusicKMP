package com.zuoguan.bilimusickmp.di

import com.zuoguan.bilimusickmp.services.AndroidPreferencesStorageService
import com.zuoguan.bilimusickmp.services.AudioPlayService
import com.zuoguan.bilimusickmp.services.BiliService
import com.zuoguan.bilimusickmp.services.ExoAudioPlayService
import com.zuoguan.bilimusickmp.services.ExtractSongBaseInfoService
import com.zuoguan.bilimusickmp.services.KuGouService
import com.zuoguan.bilimusickmp.services.NavigationService
import com.zuoguan.bilimusickmp.services.NeteaseService
import com.zuoguan.bilimusickmp.services.PreferencesStorageService
import com.zuoguan.bilimusickmp.services.SongEditService
import com.zuoguan.bilimusickmp.services.SongRepositoryService
import com.zuoguan.bilimusickmp.vm.LyricsPageViewModel
import com.zuoguan.bilimusickmp.vm.PlayBarViewModel
import com.zuoguan.bilimusickmp.vm.PlaylistPageViewModel
import com.zuoguan.bilimusickmp.vm.SearchPageViewModel
import com.zuoguan.bilimusickmp.vm.SettingsPageViewModel
import com.zuoguan.bilimusickmp.vm.SongEditPageViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    single { BiliService() }
    single { NeteaseService() }
    single { KuGouService() }
    single<PreferencesStorageService> { AndroidPreferencesStorageService(androidContext()) }
    single { ExtractSongBaseInfoService(get()) }
    single { SongRepositoryService() }

    single<AudioPlayService> {
        ExoAudioPlayService(androidContext())
    }

    single { NavigationService() }
    single { SongEditService() }

    single { SongEditPageViewModel(get(), get(), get(), get(), get()) }
    single { PlaylistPageViewModel(get(), get(), get(), get(), get()) }
    single { SearchPageViewModel(get(), get(), get(), get(), get(), get()) }
    single { SettingsPageViewModel(get()) }
    single { PlayBarViewModel(get()) }
    single { LyricsPageViewModel(get()) }
}