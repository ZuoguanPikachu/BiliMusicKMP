package com.zuoguan.bilimusickmp

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf

/** 全局共享的 [SnackbarHostState]；读取时若上层未提供则立即抛错。 */
val LocalSnackBarHostState = compositionLocalOf<SnackbarHostState> {
    error("No SnackBarHostState provided")
}
