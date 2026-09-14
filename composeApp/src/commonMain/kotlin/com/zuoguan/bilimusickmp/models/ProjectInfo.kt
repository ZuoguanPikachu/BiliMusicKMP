package com.zuoguan.bilimusickmp.models

/**
 * 项目公开信息：名称、简介、技术栈与对外链接。
 *
 * 「关于」区块与 [com.zuoguan.bilimusickmp.services.UpdateCheckService] 共用这里的仓库坐标，
 * 仓库迁移或改名时只需改这一处。
 */
object ProjectInfo {
    /** 展示用的应用名。 */
    const val NAME = "BiliMusic KMP"

    /** 一句话简介，与 README 保持一致。 */
    const val DESCRIPTION =
        "使用 Kotlin Multiplatform 开发的音乐播放器，以哔哩哔哩、酷狗音乐、网易云音乐为音频源，" +
            "支持 Android 与 Windows。"

    /** GitHub 仓库坐标（owner/repo）。 */
    const val REPO_SLUG = "ZuoguanPikachu/BiliMusicKMP"

    /** 仓库主页。 */
    const val SOURCE_URL = "https://github.com/$REPO_SLUG"

    /** 问题反馈入口（Issue 列表）。 */
    const val ISSUES_URL = "$SOURCE_URL/issues"

    /** 历史版本与更新说明。 */
    const val RELEASES_URL = "$SOURCE_URL/releases"

    /** 页脚的版权与用途声明。 */
    const val COPYRIGHT = "© 2026 ZuoguanPikachu · 仅供学习交流，音乐版权归各平台所有"

    /** 主要技术栈，在「关于」里展示为标签。 */
    val TECH_STACK = listOf("Kotlin Multiplatform", "Compose Multiplatform", "VLC")
}
