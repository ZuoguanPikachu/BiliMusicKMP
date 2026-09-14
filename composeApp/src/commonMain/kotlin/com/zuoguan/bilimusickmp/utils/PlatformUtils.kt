package com.zuoguan.bilimusickmp.utils

/** 读取文本文件；文件不存在或不可读时返回 null，由调用方决定回退策略。 */
internal expect fun readTextFile(path: String): String?

/** 原子写入文本文件：先写临时文件再替换，避免中途失败留下半截内容。 */
internal expect fun writeTextFileAtomic(path: String, content: String)

/** 当前毫秒时间戳。 */
internal expect fun currentTimeMillis(): Long

/**
 * 是否为移动端界面。
 *
 * 手机屏幕窄、输入法弹起后可视区域更小，云同步脚本改用全屏编辑器编写；
 * 桌面端窗口宽裕，直接在内联编辑区里改。
 */
internal expect val isMobileUi: Boolean