package com.zuoguan.bilimusickmp.utils

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 读取 UTF-8 文本文件。
 *
 * @return 文件不存在或读取失败（被占用 / 无权限 / 编码异常）时返回 null；
 * 读取失败不抛异常，调用方（DI 初始化）不应因此崩溃。
 */
internal actual fun readTextFile(path: String): String? {
    val file = File(path)
    if (!file.exists()) return null
    return try {
        file.readText(Charsets.UTF_8)
    } catch (e: IOException) {
        null
    }
}

/**
 * 原子写入 UTF-8 文本文件：先写同目录临时文件，再替换目标文件，
 * 避免"先删后写"造成的数据丢失窗口。
 */
internal actual fun writeTextFileAtomic(path: String, content: String) {
    val file = File(path)
    val parent = file.parentFile
    requireNotNull(parent) { "无法解析文件目录: $path" }
    if (!parent.exists() && !parent.mkdirs()) {
        throw IOException("无法创建目录: $parent")
    }

    val tmp = File(parent, file.name + ".tmp")
    tmp.writeText(content, Charsets.UTF_8)

    try {
        Files.move(
            tmp.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
    } catch (e: Exception) {
        // 个别文件系统不支持 ATOMIC_MOVE，退回普通替换
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (fallback: Exception) {
            tmp.delete()
            throw IOException("写入文件失败: $path", fallback)
        }
    }
}

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()
