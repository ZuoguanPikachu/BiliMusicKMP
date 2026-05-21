package com.zuoguan.bilimusickmp.models

enum class LyricSource {
    NONE,
    KU_GOU,
    NET_EASE,
}

val LyricSource.label: String
    get() = when (this) {
        LyricSource.KU_GOU -> "酷狗音乐"
        LyricSource.NET_EASE -> "网易云音乐"
        LyricSource.NONE -> "NONE"
    }

