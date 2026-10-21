package com.zuoguan.bilimusickmp.models

import com.zuoguan.bilimusickmp.utils.currentTimeMillis

/**
 * 各平台搜索结果的统一结构。
 *
 * @property pic 封面图地址，B 站为投稿封面，其余平台为歌曲封面。
 * @property duration 已格式化好的时长文案（`mm:ss`），仅供展示。
 */
data class SearchResult(
    val id: String,
    val title: String,
    val author: String,
    val pic: String,
    val duration: String,
    val audioSource: AudioSource
)

/**
 * 这条搜索结果对应的歌词来源。
 *
 * B 站没有歌词，其余平台的搜索结果本身就是歌曲、直接对应各自的歌词源。
 * 入库（[toSong]）与直接试听（[com.zuoguan.bilimusickmp.vm.SearchPageViewModel.playSong]）
 * 都要用这个推导，规则只在这里维护。
 */
val SearchResult.lyricSource: LyricSource
    get() = when (audioSource) {
        AudioSource.BILI_BILI -> LyricSource.NONE
        AudioSource.KU_GOU -> LyricSource.KU_GOU
        AudioSource.NET_EASE -> LyricSource.NET_EASE
        AudioSource.QQ_MUSIC -> LyricSource.QQ_MUSIC
    }

/** 这条搜索结果的歌词 ID：B 站为空串，其余平台复用结果自身的 id。 */
val SearchResult.lyricId: String
    get() = if (audioSource == AudioSource.BILI_BILI) "" else id

/**
 * 把搜索结果转成待入库的歌曲。
 *
 * 各客户端平台的搜索结果都经由这一个实现转换，入库时的来源推导规则只在此处维护。
 *
 * - B 站：搜索结果里只有原始投稿标题与视频信息，歌词/封面来源留空，
 *   由 [com.zuoguan.bilimusickmp.vm.SongEditorViewModel.openForCreate] 再做补全；
 * - 网易云 / 酷狗 / QQ 音乐：搜索结果本身就是歌曲，`id` 可直接作为歌词 ID 与封面 ID，
 *   封面 URL 也能直接复用。
 */
fun SearchResult.toSong(): Song = Song(
    id = id,
    audioSource = audioSource,
    title = title,
    author = author,
    lyricSource = lyricSource,
    lyricId = lyricId,
    coverSource = when (audioSource) {
        AudioSource.BILI_BILI -> CoverSource.BILI_BILI
        AudioSource.KU_GOU -> CoverSource.KU_GOU
        AudioSource.NET_EASE -> CoverSource.NET_EASE
        AudioSource.QQ_MUSIC -> CoverSource.QQ_MUSIC
    },
    coverId = if (audioSource == AudioSource.BILI_BILI) "" else id,
    pic = pic,
    ts = currentTimeMillis()
)
