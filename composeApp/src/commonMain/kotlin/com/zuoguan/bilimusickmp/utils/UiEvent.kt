package com.zuoguan.bilimusickmp.utils

import androidx.compose.material3.SnackbarDuration

sealed interface UiEvent {
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val duration: SnackbarDuration = SnackbarDuration.Short
    ) : UiEvent
}