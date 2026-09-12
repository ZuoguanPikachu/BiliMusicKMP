package com.zuoguan.bilimusickmp.models

/** 音频流的来源平台，同时决定取播放地址时走哪家服务。 */
enum class AudioSource {
    BILI_BILI,
    KU_GOU,
    NET_EASE,

}

/** 音源的展示名，用于 UI 文案。 */
val AudioSource.label: String
    get() = when (this) {
        AudioSource.BILI_BILI -> "BiliBili"
        AudioSource.KU_GOU -> "酷狗音乐"
        AudioSource.NET_EASE -> "网易云音乐"
    }
