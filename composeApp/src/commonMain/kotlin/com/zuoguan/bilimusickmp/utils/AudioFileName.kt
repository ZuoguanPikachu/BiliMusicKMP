package com.zuoguan.bilimusickmp.utils

/** 文件名里不能出现的字符（Windows 最严格，按它的集合来）。 */
private val INVALID_FILE_NAME_CHARS = charArrayOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')

/** 留出空间给扩展名与 Windows 的路径长度限制。 */
private const val MAX_NAME_LENGTH = 80

/**
 * 按「歌曲 - 歌手」生成下载用的文件名。
 *
 * 下载的文件是给用户在系统音乐目录里直接看的，所以用歌名而不是歌曲 id；歌手为空时只留歌名，
 * 歌名也为空时退回「未命名」，避免生成一个只有扩展名的隐藏文件。
 *
 * 它同时也是一条**文件名清洗**：歌名来自各家平台，可能带 `/`、`:`、`?` 这类非法字符，
 * 控制字符与收尾的点、空格（Windows 会截断或拒绝）也一并处理。
 *
 * @param extension 扩展名，不含点；为空时按 `mp3` 处理。
 */
fun audioFileName(title: String, author: String, extension: String): String {
    val base = listOf(title.trim(), author.trim())
        .filter { it.isNotEmpty() }
        .joinToString(" - ")
        .ifEmpty { "未命名" }

    val safe = sanitizeFileName(base)
    val ext = extension.trim().trimStart('.').ifEmpty { "mp3" }
    return "$safe.$ext"
}

/** 去掉文件名里的非法字符，并把连续空白、收尾的点和空格清理掉。 */
private fun sanitizeFileName(raw: String): String {
    val builder = StringBuilder(raw.length)
    for (ch in raw) {
        when {
            ch in INVALID_FILE_NAME_CHARS -> builder.append('_')
            // 控制字符在部分文件系统上会直接导致创建失败
            ch.isISOControl() -> builder.append('_')
            else -> builder.append(ch)
        }
    }

    val collapsed = builder.toString()
        .replace(Regex("\\s+"), " ")
        .trim()
        // Windows 不允许文件名以点或空格结尾（会被静默截断）
        .trim('.', ' ')
        .take(MAX_NAME_LENGTH)
        .trim('.', ' ')

    return collapsed.ifEmpty { "未命名" }
}
