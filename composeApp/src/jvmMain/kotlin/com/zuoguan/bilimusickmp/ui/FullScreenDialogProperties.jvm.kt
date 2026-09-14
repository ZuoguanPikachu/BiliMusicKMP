package com.zuoguan.bilimusickmp.ui

import androidx.compose.ui.window.DialogProperties

/**
 * 桌面端的全屏对话框属性。
 *
 * 移动端专用的全屏编辑器在桌面不会启用（[com.zuoguan.bilimusickmp.utils.isMobileUi] 为 false），
 * 这里只需保证接口一致：桌面端对话框自身会处理键盘 inset，不需要额外参数。
 */
internal actual fun fullScreenDialogProperties(): DialogProperties =
    DialogProperties(usePlatformDefaultWidth = false)
