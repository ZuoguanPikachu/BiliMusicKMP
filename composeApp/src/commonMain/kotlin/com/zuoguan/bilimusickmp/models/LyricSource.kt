package com.zuoguan.bilimusickmp.models

/**
 * 歌词的来源平台。
 *
 * 实现 [MetadataSource] 是为了让「无歌词」与「某平台歌词」共用同一套取值与判空逻辑；
 * [NONE] 表示歌曲未设置歌词来源。
 */
enum class LyricSource : MetadataSource {
    NONE,
    KU_GOU,
    NET_EASE;

    override val label: String
        get() = when (this) {
            NONE -> "无"
            KU_GOU -> "酷狗音乐"
            NET_EASE -> "网易云音乐"
        }

    override val isNone: Boolean
        get() = this == NONE
}

