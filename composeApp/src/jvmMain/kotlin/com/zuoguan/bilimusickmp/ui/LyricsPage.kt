package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.zuoguan.bilimusickmp.utils.convertImageUrl
import org.koin.compose.koinInject
import com.zuoguan.bilimusickmp.vm.LyricsPageViewModel
import io.kamel.core.Resource
import io.kamel.image.asyncPainterResource


@Composable
fun LyricsPage(viewModel: LyricsPageViewModel = koinInject()) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTrack = uiState.currentTrack
    var bias by remember(currentTrack) {
        mutableStateOf(currentTrack?.lyricBias ?: 0)
    }
    val listState = rememberLazyListState()
    val currentIndex = remember(uiState.currentPositionMs, uiState.lyrics, bias) {
        uiState.lyrics.indexOfLast { it.timeMs + bias <= uiState.currentPositionMs }
    }
    val density = LocalDensity.current
    var height by remember { mutableStateOf(0.dp) }

    LaunchedEffect(currentIndex) {
        // -1 表示"还没有任何一句开始"
        if (currentIndex < 0) return@LaunchedEffect
        // 用户正在手动滚动时不要抢滚动位置
        if (listState.isScrollInProgress) return@LaunchedEffect
        listState.animateScrollToItem(index = currentIndex)
    }

    Row(
        modifier = Modifier.onGloballyPositioned { coordinates ->
            height = with(density) {
                coordinates.size.height.toDp()
            }
        }
    ){
        Column(
            modifier = Modifier.weight(4f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ){
            when (val resource = asyncPainterResource(convertImageUrl(uiState.currentTrack?.pic ?: "", 512, 512))) {
                is Resource.Loading -> {}
                is Resource.Success -> {
                    val painter: Painter = resource.value
                    Image(
                        painter,
                        contentDescription = "Cover",
                        modifier = Modifier
                            .size(384.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }

                is Resource.Failure -> {
                    CoverPlaceholder(size = 384.dp, radius = 8.dp)
                }
            }

            Spacer(Modifier.height(12.dp))

             Row(
                modifier = Modifier.width(384.dp),
            ){
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    if (currentTrack != null) {
                        Text(
                            text = currentTrack.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (currentTrack != null) {
                        Text(
                            text = currentTrack.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

        }

        Column(
            modifier = Modifier.weight(6f)
        ){
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(6f),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(vertical = if (height < 100.dp) 0.dp else height / 2 - 50.dp)
            ) {
                itemsIndexed(uiState.lyrics) { index, line ->
                    LyricLineItem(
                        text = line.text,
                        isCurrent = index == currentIndex,
                        onClick = { viewModel.seekTo(line.timeMs + bias) }
                    )
                }
            }

            if (currentTrack != null) {
                var saveable by remember(currentTrack) {
                    mutableStateOf(false)
                }

                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {}, enabled = false){
                            Icon(Icons.Default.Timer, contentDescription = "歌词延时")
                        }
                        Stepper(value = bias, step = 100, onValueChange = {
                            bias = it
                            saveable = true
                        })
                        IconButton(onClick = {
                            viewModel.saveLyricBias(currentTrack.id, bias)
                            saveable = false
                        }, enabled = saveable){
                            Icon(Icons.Default.Check, contentDescription = "保存")
                        }
                    }

                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ){
                        IconButton(
                            onClick = {
                                viewModel.loadLyrics(currentTrack)
                            }
                        ){
                            Icon(Icons.Default.Refresh, contentDescription = "刷新歌词")
                        }
                    }
                }
            }
        }
    }
}
