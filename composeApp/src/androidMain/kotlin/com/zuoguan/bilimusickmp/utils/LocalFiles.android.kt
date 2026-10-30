package com.zuoguan.bilimusickmp.utils

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

// Android 侧的文件操作实现；与桌面端逻辑一致，用 java.io.File + 原子替换。

internal actual fun createDirectories(path: String): Boolean {
    val dir = File(path)
    return dir.isDirectory || dir.mkdirs()
}

internal actual fun listFileNames(dirPath: String): List<String> =
    File(dirPath).list()?.toList() ?: emptyList()

internal actual fun fileSize(path: String): Long {
    val file = File(path)
    return if (file.isFile) file.length() else 0L
}

internal actual fun fileLastModified(path: String): Long {
    val file = File(path)
    return if (file.exists()) file.lastModified() else 0L
}

internal actual fun deleteFile(path: String): Boolean = runCatching { File(path).delete() }.getOrDefault(false)

/**
 * 先写同目录的 `.part` 临时文件，再整体替换目标文件。
 *
 * 缓存 / 下载的写入可能被中途打断（切歌、退出应用），直接写目标文件会留下
 * 一个「看起来存在、其实播不了」的半截音频，因此和偏好存储一样走原子替换。
 */
internal actual fun writeBinaryFileAtomic(path: String, bytes: ByteArray) {
    val file = File(path)
    val parent = file.parentFile
    requireNotNull(parent) { "无法解析文件目录: $path" }
    if (!parent.exists() && !parent.mkdirs()) {
        throw IOException("无法创建目录: $parent")
    }

    val tmp = File(parent, file.name + ".part")
    tmp.writeBytes(bytes)

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
