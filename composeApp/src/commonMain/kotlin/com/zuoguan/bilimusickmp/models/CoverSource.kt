package com.zuoguan.bilimusickmp.models

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
