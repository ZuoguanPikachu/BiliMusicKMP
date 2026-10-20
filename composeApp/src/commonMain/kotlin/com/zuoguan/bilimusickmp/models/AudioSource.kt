package com.zuoguan.bilimusickmp.models

/**
 * 音频流的来源平台，同时决定取播放地址时走哪家服务。
 *
 * 声明顺序就是搜索页音源下拉框的展示顺序：B 站是本应用的主来源，其余平台按
 * QQ 音乐 → 网易云 → 酷狗 排列。
 */
enum class AudioSource {
    BILI_BILI,
    QQ_MUSIC,
    NET_EASE,
    KU_GOU,

}

/** 音源的展示名，用于 UI 文案。 */
val AudioSource.label: String
    get() = when (this) {
        AudioSource.BILI_BILI -> "BiliBili"
        AudioSource.QQ_MUSIC -> "QQ音乐"
        AudioSource.NET_EASE -> "网易云音乐"
        AudioSource.KU_GOU -> "酷狗音乐"
    }
