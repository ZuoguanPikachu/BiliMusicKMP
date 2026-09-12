package com.zuoguan.bilimusickmp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.*
import androidx.compose.material.icons.Icons
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import com.zuoguan.bilimusickmp.services.NavigationService
import com.zuoguan.bilimusickmp.utils.convertImageUrl
import org.koin.compose.koinInject
import com.zuoguan.bilimusickmp.vm.LyricsPageViewModel
import io.kamel.core.Resource
import io.kamel.image.asyncPainterResource


/**
 * 歌词页：展示封面与逐行歌词，并按当前播放位置自动滚动到高亮行。
 *
 * 用户正在手动滚动时不抢位置；调整歌词延时后立即重新计算高亮行。
 */
@Composable
fun LyricsPage(
    viewModel: LyricsPageViewModel = koinInject(),
    navigationService: NavigationService = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTrack = uiState.currentTrack
    val bias = uiState.lyricBias
    val listState = rememberLazyListState()
    // bias 也参与计算：调整歌词延时后应当立即重新高亮
    val currentIndex = remember(uiState.currentPositionMs, uiState.lyrics, bias) {
        uiState.lyrics.indexOfLast { it.timeMs + bias <= uiState.currentPositionMs }
    }

    LaunchedEffect(currentIndex) {
        // -1 表示还没有任何一句开始
        if (currentIndex < 0) return@LaunchedEffect
        // 用户正在手动滚动时不要抢滚动位置
        if (listState.isScrollInProgress) return@LaunchedEffect
        listState.animateScrollToItem(index = currentIndex)
    }
    BackHandler(enabled = true) {
        navigationService.back()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        IconButton(
            onClick = { navigationService.back() }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                contentDescription = "返回"
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val contentWidth = minOf(maxWidth * 0.8f, 384.dp)
            val coverResource = asyncPainterResource(
                convertImageUrl(uiState.currentTrack?.pic ?: "", 512, 512)
            )

            Column(
                modifier = Modifier
                    .width(contentWidth)
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                when (coverResource) {
                    is Resource.Loading -> {}

                    is Resource.Success -> {
                        val painter: Painter = coverResource.value

                        Image(
                            painter = painter,
                            contentDescription = "Cover",
                            modifier = Modifier
                                .size(contentWidth)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }

                    is Resource.Failure -> {
                        CoverPlaceholder(
                            contentWidth,
                            8.dp
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (currentTrack != null) {
                    Text(
                        text = "${currentTrack.title} - ${currentTrack.author}",
                        modifier = Modifier.width(contentWidth),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().height(150.dp),
            contentPadding = PaddingValues(vertical = 50.dp)
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
                    Stepper(
                        value = bias,
                        step = 100,
                        onValueChange = viewModel::updateLyricBias
                    )
                    IconButton(onClick = {
                        viewModel.saveLyricBias()
                    }, enabled = uiState.isLyricBiasDirty){
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

        Spacer(modifier = Modifier.weight(2f))
    }
}
