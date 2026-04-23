package com.zuoguan.bilimusickmp.models

data class TrackInfo(
    val id: String,
    val title: String,
    val author: String,
    val pic: String,
    val audioSource: AudioSource,
    val urlProvider: suspend () -> String,
    val playSource: PlaySource,
    val lyricsProvider: suspend () -> List<LyricLine>,
    val lyricBias: Int = 0,
)