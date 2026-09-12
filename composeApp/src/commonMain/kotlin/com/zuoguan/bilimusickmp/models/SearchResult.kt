package com.zuoguan.bilimusickmp.models

import com.zuoguan.bilimusickmp.utils.currentTimeMillis

data class SearchResult(
    val id: String,
    val title: String,
    val author: String,
    val pic: String,
    val duration: String,
    val audioSource: AudioSource
)

/**
 * 把搜索结果转成待入库的歌曲。
 *
 * 原先两个平台各写了一份几乎相同的构造代码（Android 在 `SearchPage` 里，
 * 桌面在 `SearchPageViewModel.requestAdd` 里），这里收敛成唯一实现。
 *
 * - B 站：搜索结果里只有原始投稿标题与视频信息，歌词/封面来源留空，
 *   由 [com.zuoguan.bilimusickmp.vm.SongEditorViewModel.openForCreate] 再做补全；
 * - 网易云 / 酷狗：搜索结果本身就是歌曲，`id` 可直接作为歌词 ID 与封面 ID，
 *   封面 URL 也能直接复用。
 */
fun SearchResult.toSong(): Song = Song(
    id = id,
    audioSource = audioSource,
    title = title,
    author = author,
    lyricSource = when (audioSource) {
        AudioSource.BILI_BILI -> LyricSource.NONE
        AudioSource.KU_GOU -> LyricSource.KU_GOU
        AudioSource.NET_EASE -> LyricSource.NET_EASE
    },
    lyricId = if (audioSource == AudioSource.BILI_BILI) "" else id,
    coverSource = when (audioSource) {
        AudioSource.BILI_BILI -> CoverSource.BILI_BILI
        AudioSource.KU_GOU -> CoverSource.KU_GOU
        AudioSource.NET_EASE -> CoverSource.NET_EASE
    },
    coverId = if (audioSource == AudioSource.BILI_BILI) "" else id,
    pic = pic,
    ts = currentTimeMillis()
)
