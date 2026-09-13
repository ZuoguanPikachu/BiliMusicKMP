package com.zuoguan.bilimusickmp.services

import androidx.compose.ui.graphics.Color
import com.zuoguan.bilimusickmp.models.DarkMode
import com.zuoguan.bilimusickmp.models.DefaultSeedColor
import com.zuoguan.bilimusickmp.models.seedColorFromPreference
import com.zuoguan.bilimusickmp.models.toPreferenceValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 主题色在偏好存储中的键，值为种子色的字符串形式。
 *
 * 同一个键也登记在 `SyncKeys.PREF_KEYS` 里，因此主题色会随账号同步到其他设备。
 */
const val THEME_COLOR_KEY = "theme.color"

/** 明暗模式在偏好存储中的键，取值为 [DarkMode.key]，同样参与云同步。 */
const val THEME_DARK_MODE_KEY = "theme.darkMode"

/**
 * 读取主题种子色，缺失或格式无法识别时回退到 [DefaultSeedColor]。
 *
 * 以扩展函数形式提供，无需改动 [PreferencesStorageService] 接口。
 */
fun PreferencesStorageService.observeThemeColor(): Flow<Color> =
    observeString(THEME_COLOR_KEY).map { seedColorFromPreference(it) }

/** 写入主题种子色。 */
suspend fun PreferencesStorageService.saveThemeColor(color: Color) {
    putString(THEME_COLOR_KEY, color.toPreferenceValue())
}

/** 读取明暗模式，缺失或取值无法识别时回退到 [DarkMode.fallback]。 */
fun PreferencesStorageService.observeDarkMode(): Flow<DarkMode> =
    observeString(THEME_DARK_MODE_KEY).map { DarkMode.fromKey(it) }

/** 写入明暗模式。 */
suspend fun PreferencesStorageService.saveDarkMode(darkMode: DarkMode) {
    putString(THEME_DARK_MODE_KEY, darkMode.key)
}
