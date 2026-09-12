package com.zuoguan.bilimusickmp.models

import androidx.compose.runtime.Immutable


/**
 * 正在播放的曲目在运行时的视图。
 *
 * 音频地址与歌词都通过挂起函数按需获取：播放地址会过期，歌词体积较大，
 * 都不适合在构造时就取好。相等性只比较 [id]，同一首歌换一个实例不会让 Compose 重建无关 UI。
 *
 * @property urlProvider 拉取音频播放地址。
 * @property playSource 决定本曲播完后从哪里取下一首。
 * @property lyricsProvider 拉取歌词；使用方会按 [LyricLine.timeMs] 排序后再展示。
 * @property lyricBias 歌词时间偏移，单位毫秒。
 */
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
