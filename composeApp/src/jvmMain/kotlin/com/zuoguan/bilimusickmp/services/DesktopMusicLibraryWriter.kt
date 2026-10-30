package com.zuoguan.bilimusickmp.services

import com.sun.jna.platform.win32.Shell32Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 桌面端的「下载到音乐目录」：直接写系统音乐文件夹。
 *
 * 目录取 Windows 的「音乐」已知文件夹（用户可能把它改到别的盘），取不到时退回 `~/Music`；
 * 非 Windows 系统上 [Shell32Util] 会抛异常，同样走回退分支。
 */
class DesktopMusicLibraryWriter : MusicLibraryWriter {

    override suspend fun find(fileName: String): String? = withContext(Dispatchers.IO) {
        val file = File(musicDirectory(), fileName)
        if (file.exists()) file.absolutePath else null
    }

    override suspend fun save(fileName: String, mimeType: String, bytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            val dir = musicDirectory()
            if (!dir.isDirectory && !dir.mkdirs()) {
                throw IOException("无法创建音乐目录：${dir.absolutePath}")
            }

            val file = File(dir, fileName)
            // 同名文件视为已下载：不覆盖用户可能已经改过标签的那一份
            if (!file.exists()) {
                file.writeBytes(bytes)
            }
            file.absolutePath
        }

    /** 系统「音乐」文件夹。 */
    private fun musicDirectory(): File {
        // CSIDL_MYMUSIC（0x0D）：Windows 的「我的音乐」已知文件夹
        val known = runCatching { Shell32Util.getFolderPath(CSIDL_MYMUSIC) }.getOrNull()
        if (!known.isNullOrBlank()) return File(known)

        val home = System.getProperty("user.home")?.takeIf { it.isNotBlank() } ?: "."
        return File(home, "Music")
    }

    private companion object {
        const val CSIDL_MYMUSIC = 0x0D
    }
}
