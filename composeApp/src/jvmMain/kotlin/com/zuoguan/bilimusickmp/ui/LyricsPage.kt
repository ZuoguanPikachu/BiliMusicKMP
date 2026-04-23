package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import org.koin.compose.koinInject
import com.zuoguan.bilimusickmp.vm.LyricsPageViewModel


@Composable
fun LyricsPage(viewModel: LyricsPageViewModel = koinInject()) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val currentIndex = remember(uiState.currentPositionMs, uiState.lyrics) {
        uiState.lyrics.indexOfLast { it.timeMs <= uiState.currentPositionMs }.coerceAtLeast(0)
    }

    LaunchedEffect(currentIndex) {
        listState.animateScrollToItem(
            index = currentIndex,
            scrollOffset = -200
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 120.dp)
    ) {
        itemsIndexed(uiState.lyrics) { index, line ->
            LyricLineItem(
                text = line.text,
                isCurrent = index == currentIndex,
                onClick = { viewModel.seekTo(line.timeMs) }
            )
        }
    }
}


@Composable
fun LyricLineItem(
    text: String,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        onClick = onClick,
    ){
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ){
            Text(
                text = text,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = if (isCurrent) 20.sp else 16.sp,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (isCurrent) 1f else 0.5f
                ),
            )
        }

    }
}