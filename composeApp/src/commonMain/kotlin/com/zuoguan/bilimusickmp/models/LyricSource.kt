package com.zuoguan.bilimusickmp.models

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

