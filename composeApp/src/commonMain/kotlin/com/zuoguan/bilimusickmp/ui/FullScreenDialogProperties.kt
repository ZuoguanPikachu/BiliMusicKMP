package com.zuoguan.bilimusickmp.ui

import androidx.compose.ui.window.DialogProperties

/**
 * 全屏脚本编辑对话框的属性。
 *
 * Android 还要额外关掉「让系统窗口自己消化 insets」（`decorFitsSystemWindows = false`）：
 * 否则输入法 inset 传不到 Compose 这边，对话框内容里的 `WindowInsets.safeDrawing`
 * 补不出键盘高度，编辑区会被软键盘盖住。
 *
 * 该参数只在 Android 平台存在（桌面端对应的是 `useSoftwareKeyboardInset` 等另一组参数），
 * 因此按平台分流。
 */
internal expect fun fullScreenDialogProperties(): DialogProperties
