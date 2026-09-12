package com.zuoguan.bilimusickmp.utils

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal actual fun readTextFile(path: String): String? {
    val file = File(path)
    if (!file.exists()) return null
    // 读取失败（文件被占用/无权限/编码异常）不应让调用方（DI 初始化）崩溃
    return try {
        file.readText(Charsets.UTF_8)
    } catch (e: IOException) {
        null
    }
}

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
        // 同目录内的原子替换：不会出现"先删后写"导致的数据丢失窗口
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
