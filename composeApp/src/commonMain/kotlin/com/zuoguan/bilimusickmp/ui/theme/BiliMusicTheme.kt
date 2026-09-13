package com.zuoguan.bilimusickmp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import com.zuoguan.bilimusickmp.models.DarkMode
import com.zuoguan.bilimusickmp.models.DefaultSeedColor

/**
 * 应用主题：以 [seedColor] 为种子推导整套 Material3 配色并向下提供。
 *
 * 配色由 MaterialKolor 按 TonalSpot 风格生成：主色、次级色、各级容器色与表面色同出一套
 * 色调板，深色与浅色两套方案也来自同一个种子色，因此切换主题色或明暗模式时整个界面都是
 * 协调的，不需要另外手工调色。
 */
@Composable
fun BiliMusicTheme(
    seedColor: Color = DefaultSeedColor,
    darkMode: DarkMode = DarkMode.fallback,
    content: @Composable () -> Unit
) {
    val colorScheme = rememberDynamicColorScheme(
        seedColor = seedColor,
        isDark = darkMode.isDarkTheme(),
        isAmoled = false,
        style = PaletteStyle.TonalSpot,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

/**
 * 把明暗模式解析成当前是否使用深色方案。
 *
 * 只有 [DarkMode.AUTO] 需要读系统设置，主题与系统栏都应经它取结果，避免各算各的。
 */
@Composable
fun DarkMode.isDarkTheme(): Boolean = when (this) {
    DarkMode.LIGHT -> false
    DarkMode.DARK -> true
    DarkMode.AUTO -> isSystemInDarkTheme()
}
