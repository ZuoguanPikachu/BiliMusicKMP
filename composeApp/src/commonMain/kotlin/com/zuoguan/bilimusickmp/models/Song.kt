package com.zuoguan.bilimusickmp.models

import androidx.compose.runtime.Immutable

/**
 * 一首歌的完整元数据。
 *
 * 不可变（全部 `val`）：编辑走 [copy]，保证"编辑中的草稿"与仓库列表里的实例天然隔离，
 * 同时 Compose 能正确感知变化 —— 旧的可变类就地赋值既不会触发重组，
 * 也会让"取消编辑"污染列表里的同一个对象。
 *
 * [ts] 是本地排序位次，由 [com.zuoguan.bilimusickmp.services.SongRepositoryService]
 * 依据顺序元文档推导，不参与云同步（顺序单独同步）。
 */
@Immutable
data class Song(
    val id: String = "",
    val cid: String = "",
    val audioSource: AudioSource = AudioSource.BILI_BILI,
    val title: String = "",
    val author: String = "",
    val tags: List<String> = emptyList(),

    val lyricSource: LyricSource = LyricSource.NONE,
    val lyricId: String = "",
    val lyricBias: Int = 0,

    val coverSource: CoverSource = CoverSource.BILI_BILI,
    val coverId: String = "",
    val pic: String = "",

    val ts: Long = 0
)
