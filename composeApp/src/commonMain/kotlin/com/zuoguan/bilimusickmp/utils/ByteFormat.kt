package com.zuoguan.bilimusickmp.utils

import kotlin.math.roundToLong

/**
 * 把字节数格式化成易读的容量文本（如 `12.3 MB`）。
 *
 * 不用 `String.format`：那是 JVM 专有实现，这里手算一位小数，两个平台结果一致。
 *
 * @param bytes 字节数；非正数返回 `0 B`。
 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"

    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }

    return if (index == 0) "$bytes B" else "${oneDecimal(value)} ${units[index]}"
}

/** 保留一位小数；四舍五入到 0.1，避免出现 `12.25 MB` 这样的长尾。 */
private fun oneDecimal(value: Double): String {
    val scaled = (value * 10).roundToLong()
    return "${scaled / 10}.${scaled % 10}"
}
