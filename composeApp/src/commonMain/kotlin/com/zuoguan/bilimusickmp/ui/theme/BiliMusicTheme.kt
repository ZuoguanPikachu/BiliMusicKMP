package com.zuoguan.bilimusickmp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import com.zuoguan.bilimusickmp.models.DefaultSeedColor

/**
 * 应用主题：以 [seedColor] 为种子推导整套 Material3 配色并向下提供。
 *
 * 配色由 MaterialKolor 按 TonalSpot 风格生成：主色、次级色、各级容器色与表面色同出一套
 * 色调板，切换主题色时整个界面协调变化，而不是只替换若干主色角色。
 * 目前只提供浅色方案。
 */
@Composable
fun BiliMusicTheme(
    seedColor: Color = DefaultSeedColor,
    content: @Composable () -> Unit
) {
    val colorScheme = rememberDynamicColorScheme(
        seedColor = seedColor,
        isDark = false,
        isAmoled = false,
        style = PaletteStyle.TonalSpot,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
