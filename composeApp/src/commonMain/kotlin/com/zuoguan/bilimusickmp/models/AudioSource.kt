package com.zuoguan.bilimusickmp.models

enum class AudioSource {
    BILI_BILI,
    KU_GOU,
    NET_EASE,

}

val AudioSource.label: String
    get() = when (this) {
        AudioSource.BILI_BILI -> "BiliBili"
        AudioSource.KU_GOU -> "酷狗音乐"
        AudioSource.NET_EASE -> "网易云音乐"
    }
