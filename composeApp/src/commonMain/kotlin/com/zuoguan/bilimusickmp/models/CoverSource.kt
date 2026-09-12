package com.zuoguan.bilimusickmp.models

/**
 * 封面的来源平台。
 *
 * 实现 [MetadataSource] 是为了让「无封面」与「某平台封面」共用同一套取值与判空逻辑；
 * [NONE] 表示歌曲未设置封面来源。
 */
enum class CoverSource : MetadataSource  {
    NONE,
    KU_GOU,
    NET_EASE,
    BILI_BILI;

    override val label: String
        get() = when (this) {
            NONE -> "无"
            KU_GOU -> "酷狗音乐"
            NET_EASE -> "网易云音乐"
            BILI_BILI -> "哔哩哔哩"
        }

    override val isNone: Boolean
        get() = this == NONE
}
