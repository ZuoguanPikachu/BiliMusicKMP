package com.zuoguan.bilimusickmp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

/**
 * 让原生窗口标题栏跟随应用主题（目前仅 Windows 生效）。
 *
 * Windows 10 1809 起可以用 DWM 的 `DWMWA_USE_IMMERSIVE_DARK_MODE` 属性把标题栏切成深色，
 * 该属性的编号在 20H1 之前是 19、之后是 20，两个都设置一遍即可兼容两代系统。
 * 其他平台没有对应能力：macOS 的标题栏由系统外观决定，Linux 取决于窗口管理器，
 * 此时 `dwmapi` 加载失败，全部调用会直接返回。
 */
internal object WindowTitleBarTheme {
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE_LEGACY = 19

    /** 属性值按 BOOL/INT 传递，固定 4 字节。 */
    private const val ATTRIBUTE_VALUE_SIZE = 4

    private val dwmapi: Dwmapi? = runCatching {
        Native.load("dwmapi", Dwmapi::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }.getOrNull()

    /** 把 [window] 的标题栏设为深色；[isDark] 为 false 时恢复浅色。 */
    fun setDarkTitleBar(window: Window, isDark: Boolean) {
        val api = dwmapi ?: return
        val handle = runCatching { Native.getWindowPointer(window) }.getOrNull() ?: return
        val value = IntByReference(if (isDark) 1 else 0)
        api.DwmSetWindowAttribute(handle, DWMWA_USE_IMMERSIVE_DARK_MODE, value, ATTRIBUTE_VALUE_SIZE)
        api.DwmSetWindowAttribute(handle, DWMWA_USE_IMMERSIVE_DARK_MODE_LEGACY, value, ATTRIBUTE_VALUE_SIZE)
    }

    /** dwmapi.dll 里用到的部分，签名取自 Windows SDK。 */
    private interface Dwmapi : StdCallLibrary {
        fun DwmSetWindowAttribute(
            hwnd: Pointer,
            attribute: Int,
            value: IntByReference,
            size: Int
        ): Int
    }
}

/**
 * 让 [window] 的标题栏跟随当前主题。
 *
 * 原生窗口（HWND）要在窗口显示后才拿得到，因此除了主题变化时立即设置，还在窗口打开事件里
 * 再设一次；两者都拿不到句柄时静默跳过，不影响界面。
 */
@Composable
fun WindowTitleBarThemeEffect(window: Window, isDark: Boolean) {
    val latestIsDark by rememberUpdatedState(isDark)

    DisposableEffect(window) {
        val listener = object : WindowAdapter() {
            override fun windowOpened(e: WindowEvent) {
                WindowTitleBarTheme.setDarkTitleBar(window, latestIsDark)
            }
        }
        window.addWindowListener(listener)
        onDispose { window.removeWindowListener(listener) }
    }

    LaunchedEffect(window, isDark) {
        WindowTitleBarTheme.setDarkTitleBar(window, isDark)
    }
}
