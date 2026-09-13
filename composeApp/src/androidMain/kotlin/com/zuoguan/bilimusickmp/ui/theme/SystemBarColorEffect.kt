package com.zuoguan.bilimusickmp.ui.theme

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * 跟随应用主题设置系统栏图标的明暗。
 *
 * 界面使用边到边布局，状态栏与导航栏的图标颜色由系统按此设置决定。默认的
 * `enableEdgeToEdge()` 只跟随系统深色模式，因此在「浅色系统 + 深色应用」这类组合下
 * 图标会与应用背景撞色，需要按实际生效的主题再设置一次。
 *
 * @param isDark 当前实际生效的深色模式（已解析过「跟随系统」）。
 */
@Composable
fun SystemBarColorEffect(isDark: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return

    DisposableEffect(activity, isDark) {
        val transparent = AndroidColor.TRANSPARENT
        activity.enableEdgeToEdge(
            statusBarStyle = if (isDark) {
                SystemBarStyle.dark(transparent)
            } else {
                SystemBarStyle.light(transparent, transparent)
            },
            navigationBarStyle = if (isDark) {
                SystemBarStyle.dark(transparent)
            } else {
                SystemBarStyle.light(transparent, transparent)
            },
        )
        onDispose { }
    }
}
