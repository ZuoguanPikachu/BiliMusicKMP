package com.zuoguan.bilimusickmp.services

import androidx.compose.ui.graphics.Color
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
