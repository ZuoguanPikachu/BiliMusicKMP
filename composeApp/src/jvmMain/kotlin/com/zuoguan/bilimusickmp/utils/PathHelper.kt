package com.zuoguan.bilimusickmp.utils

import java.io.File

/**
 * 应用配置目录。
 *
 * Windows 取 `%APPDATA%\BiliMusic`；`APPDATA` 可能为空，此时必须回退到
 * 用户主目录，否则 `File(null, ...)` 会抛 NPE。桌面端还会打包成 Dmg / Deb，
 * 非 Windows 环境同样要能启动，因此最后再退回当前目录。
 *
 * @throws IllegalStateException 目录无法创建时抛出。
 */
fun getAppConfigDir(): File {
    val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
        ?: System.getProperty("user.home")?.takeIf { it.isNotBlank() }
        ?: "."

    val dir = File(base, "BiliMusic")
    if (!dir.exists() && !dir.mkdirs()) {
        error("无法创建配置目录: ${dir.absolutePath}")
    }
    return dir
}
