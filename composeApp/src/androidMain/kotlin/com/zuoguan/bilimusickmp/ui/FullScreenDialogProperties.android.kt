package com.zuoguan.bilimusickmp.ui

import androidx.compose.ui.window.DialogProperties

/**
 * Android 的全屏对话框属性。
 *
 * 关掉 `decorFitsSystemWindows` 后，窗口不再自行消化系统 insets，Compose 才能拿到
 * 真实的输入法高度，由对话框内容自己用 `WindowInsets.safeDrawing` 让开键盘与系统栏。
 */
internal actual fun fullScreenDialogProperties(): DialogProperties = DialogProperties(
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false
)
