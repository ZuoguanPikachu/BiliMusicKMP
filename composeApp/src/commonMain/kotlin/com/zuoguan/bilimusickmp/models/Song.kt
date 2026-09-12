package com.zuoguan.bilimusickmp.models

import androidx.compose.runtime.Immutable

/**
 * 一首歌的完整元数据。
 *
 * 全部字段为 `val`：编辑走 [copy] 生成新实例，草稿与仓库列表中的实例天然隔离，
 * 「取消编辑」不会污染列表里已有的对象；新实例也让 Compose 能正确感知变化。
 *
 * [ts] 是本地排序位次，由 [com.zuoguan.bilimusickmp.services.SongRepositoryService]
 * 依据顺序元文档推导，不参与云同步（顺序单独同步）。
 */
@Immutable
data class Song(
    val id: String = "",
    /** B 站视频的分 P id，取音频地址时使用；非 B 站音源为空串。 */
    val cid: String = "",
    val audioSource: AudioSource = AudioSource.BILI_BILI,
    val title: String = "",
    val author: String = "",
    /** 用户可编辑的分类标签，歌单按标签筛选时使用。 */
    val tags: List<String> = emptyList(),

    val lyricSource: LyricSource = LyricSource.NONE,
    /** 歌词来源平台内的歌曲 id；[lyricSource] 为 [LyricSource.NONE] 时为空串。 */
    val lyricId: String = "",
    /** 歌词时间偏移，单位毫秒；某行的展示时刻为「行时间 + 该偏移」。 */
    val lyricBias: Int = 0,

    val coverSource: CoverSource = CoverSource.BILI_BILI,
    /** 封面来源平台内的资源 id；[coverSource] 为 [CoverSource.NONE] 时为空串。 */
    val coverId: String = "",
    val pic: String = "",

    val ts: Long = 0
)
