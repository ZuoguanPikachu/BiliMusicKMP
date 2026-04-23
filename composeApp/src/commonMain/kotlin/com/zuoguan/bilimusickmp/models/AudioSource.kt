package com.zuoguan.bilimusickmp.models

enum class AudioSource {
    BILI_BILI,
    NET_EASE
}

val AudioSource.label: String
    get() = when (this) {
        AudioSource.BILI_BILI -> "BiliBili"
        AudioSource.NET_EASE -> "网易云音乐"
    }
