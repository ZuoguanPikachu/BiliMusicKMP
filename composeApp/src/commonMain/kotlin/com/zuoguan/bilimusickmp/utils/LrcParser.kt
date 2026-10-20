package com.zuoguan.bilimusickmp.utils

import com.zuoguan.bilimusickmp.models.LyricLine

/** LRC 时间标签，形如 `[01:23.45]` 或 `[01:23.456]`。 */
private val LRC_TIME_TAG = """\[(\d{2}):(\d{2})\.(\d{2,3})]""".toRegex()

/**
 * 把 LRC 文本解析成按时间升序排列的歌词行。
 *
 * 各音源（酷狗、网易云、QQ 音乐）拿到的歌词都是同一套 LRC 文本，解析规则集中在此处维护。
 *
 * - 一行可能带多个时间戳（对唱、重复段落），会展开成多行；
 * - 毫秒位为两位时按 ×10 补齐到毫秒；
 * - 只有时间戳没有文字的纯节奏行（含 `[ti:]` 之类的 ID 标签）会被跳过。
 *
 * @return 没有任何有效歌词时返回空列表（纯音乐、钢琴曲属于正常情况，调用方按"无歌词"处理）。
 */
fun parseLrc(content: String): List<LyricLine> {
    val result = mutableListOf<LyricLine>()

    content.lines().forEach { line ->
        val matches = LRC_TIME_TAG.findAll(line).toList()
        if (matches.isEmpty()) return@forEach

        val text = line.replace(LRC_TIME_TAG, "").trim()
        if (text.isEmpty()) return@forEach

        matches.forEach { match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: 0L
            val seconds = match.groupValues[2].toLongOrNull() ?: 0L
            val fraction = match.groupValues[3]
            val millis = fraction.toLongOrNull()?.let { if (fraction.length == 3) it else it * 10 } ?: 0L

            result.add(LyricLine(minutes * 60_000 + seconds * 1_000 + millis, text))
        }
    }

    return result.sortedBy { it.timeMs }
}
