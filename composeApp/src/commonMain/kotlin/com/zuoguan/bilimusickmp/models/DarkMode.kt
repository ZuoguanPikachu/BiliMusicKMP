package com.zuoguan.bilimusickmp.models

/**
 * 界面明暗模式。
 *
 * [key] 是落盘到偏好存储的稳定标识，改动会让用户已保存的选择失效，
 * 因此展示文案 [label] 可以调整，[key] 不应修改。
 */
enum class DarkMode(val key: String, val label: String) {
    AUTO("auto", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        /** 偏好缺失或取值无法识别时的兜底模式。 */
        val fallback: DarkMode = AUTO

        /** 按持久化的 [key] 反查模式，未知取值回退到 [fallback]。 */
        fun fromKey(key: String?): DarkMode = entries.firstOrNull { it.key == key } ?: fallback
    }
}
