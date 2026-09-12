package com.zuoguan.bilimusickmp.utils

import androidx.compose.material3.SnackbarDuration

/** 由 ViewModel 向 UI 发送的一次性事件。 */
sealed interface UiEvent {
    /** 请求展示一条 Snackbar。 */
    data class ShowSnackBar(
        val message: String,
        val actionLabel: String? = null,
        val duration: SnackbarDuration = SnackbarDuration.Short
    ) : UiEvent
}