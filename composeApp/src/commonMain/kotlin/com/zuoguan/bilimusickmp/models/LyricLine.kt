package com.zuoguan.bilimusickmp.models

/**
 * 一行歌词。
 *
 * 展示时的比较基准是 `timeMs + 歌曲的歌词偏移`，因此 [timeMs] 保持歌词原始时间轴，
 * 不把偏移预先算进去。
 *
 * @property timeMs 该行歌词在原始时间轴上的起点，单位毫秒。
 * @property text 该行歌词文本。
 */
data class LyricLine(
    val timeMs: Long,
    val text: String
)
