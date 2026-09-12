package com.zuoguan.bilimusickmp.utils

import java.io.File

/**
 * 应用配置目录。
 *
 * Windows 用 `%APPDATA%\BiliMusic`；其它平台退回用户主目录，
 * 避免 `System.getenv("APPDATA")` 为空时 `File(null, ...)` 抛 NPE
 * （桌面端也会打包成 Dmg / Deb，非 Windows 环境同样要能启动）。
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
