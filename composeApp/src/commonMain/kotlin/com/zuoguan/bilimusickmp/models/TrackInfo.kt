package com.zuoguan.bilimusickmp.models

import androidx.compose.runtime.Immutable


@Immutable
class TrackInfo(
    val id: String,
    val title: String,
    val author: String,
    val pic: String,
    val audioSource: AudioSource,
    val urlProvider: suspend () -> String,
    val playSource: PlaySource,
    val lyricsProvider: suspend () -> List<LyricLine>,
    val lyricBias: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrackInfo) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "TrackInfo(id=$id, title=$title)"
}
