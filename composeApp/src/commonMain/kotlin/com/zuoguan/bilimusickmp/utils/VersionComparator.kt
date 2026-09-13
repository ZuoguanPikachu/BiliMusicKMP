package com.zuoguan.bilimusickmp.utils

/**
 * 版本号比较。
 *
 * 兼容 GitHub Release 常见的 tag 写法：可带 `v`/`V` 前缀（`v1.4.0`），可带预发布后缀
 * （`1.5.0-beta.1`）。数值段逐段按整数比较，缺省段按 0 处理；数值全部相同时，
 * 正式版高于预发布版（与语义化版本的排序一致）。
 *
 * 无法解析的段按 0 处理，因此对畸形输入只会退化为"看得越少越旧"，不会抛异常。
 */
internal fun isNewerVersion(candidate: String, current: String): Boolean =
    compareVersions(candidate, current) > 0

/**
 * 比较两个版本号。
 *
 * @return 负数表示 [a] 更旧，0 表示等价，正数表示 [a] 更新。
 */
internal fun compareVersions(a: String, b: String): Int {
    val left = parseVersion(a)
    val right = parseVersion(b)

    val segmentCount = maxOf(left.numbers.size, right.numbers.size)
    for (index in 0 until segmentCount) {
        val x = left.numbers.getOrElse(index) { 0 }
        val y = right.numbers.getOrElse(index) { 0 }
        if (x != y) return x.compareTo(y)
    }

    val leftPre = left.preRelease
    val rightPre = right.preRelease
    return when {
        leftPre == null && rightPre == null -> 0
        leftPre == null -> 1
        rightPre == null -> -1
        // 双方都是预发布：按字典序近似比较（1.5.0-alpha < 1.5.0-beta）
        else -> leftPre.compareTo(rightPre)
    }
}

/** 解析结果：数值段 + 可选的预发布后缀。 */
private data class ParsedVersion(
    val numbers: List<Int>,
    val preRelease: String?
)

private fun parseVersion(raw: String): ParsedVersion {
    val trimmed = raw.trim().removePrefix("v").removePrefix("V")
    // 预发布/构建元数据从第一个 '-' 或 '+' 起算
    val core = trimmed.takeWhile { it != '-' && it != '+' }
    val preRelease = trimmed.drop(core.length).trimStart('-', '+').ifEmpty { null }

    val numbers = core.split('.').map { segment ->
        segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
    }
    return ParsedVersion(numbers, preRelease)
}
