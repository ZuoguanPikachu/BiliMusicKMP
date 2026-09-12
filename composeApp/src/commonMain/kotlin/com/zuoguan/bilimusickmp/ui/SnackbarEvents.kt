package com.zuoguan.bilimusickmp.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.zuoguan.bilimusickmp.LocalSnackBarHostState
import com.zuoguan.bilimusickmp.utils.UiEvent
import kotlinx.coroutines.flow.Flow

/**
 * 把 VM 的一次性 UI 事件流消费成 Snackbar 提示。
 *
 * 以 [eventFlow] 作为 key，事件流被替换时自动重新收集。
 */
@Composable
fun SnackbarEvents(eventFlow: Flow<UiEvent>) {
    val hostState: SnackbarHostState = LocalSnackBarHostState.current

    LaunchedEffect(eventFlow) {
        eventFlow.collect { event ->
            when (event) {
                is UiEvent.ShowSnackBar -> hostState.showSnackbar(
                    message = event.message,
                    actionLabel = event.actionLabel,
                    duration = event.duration,
                    withDismissAction = true,
                )
            }
        }
    }
}
