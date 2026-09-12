package com.zuoguan.bilimusickmp

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf

val LocalSnackBarHostState = compositionLocalOf<SnackbarHostState> {
    error("No SnackBarHostState provided")
}
