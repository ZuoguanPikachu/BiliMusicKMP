package com.zuoguan.bilimusickmp.models

import androidx.compose.ui.graphics.Color

/**
 * 默认主题种子色：B站品牌粉。
 *
 * 整套配色由种子色生成，因此偏好里只需要保存这一个颜色值。
 */
val DefaultSeedColor: Color = Color(0xFFFB7299)

/** 种子色在偏好存储中的字符串形式。 */
fun Color.toPreferenceValue(): String = value.toString()

/** 解析偏好里保存的种子色，缺失或格式无法识别时回退到 [DefaultSeedColor]。 */
fun seedColorFromPreference(raw: String?): Color =
    raw?.toULongOrNull()?.let { Color(it) } ?: DefaultSeedColor
