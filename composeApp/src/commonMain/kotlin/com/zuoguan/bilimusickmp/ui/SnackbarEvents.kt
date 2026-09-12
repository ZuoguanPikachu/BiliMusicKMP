package com.zuoguan.bilimusickmp.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.zuoguan.bilimusickmp.LocalSnackBarHostState
import com.zuoguan.bilimusickmp.utils.UiEvent
import kotlinx.coroutines.flow.Flow

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
