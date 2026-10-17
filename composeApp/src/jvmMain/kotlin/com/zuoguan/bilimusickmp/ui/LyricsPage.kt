package com.zuoguan.bilimusickmp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.zuoguan.bilimusickmp.vm.LyricsPageViewModel
import org.koin.compose.koinInject

/**
 * 桌面端歌词页：宽屏左右分栏（左封面右歌词），窗口变窄时自动切成上下排布。
 *
 * 底部播放栏一直可见，所以这里不传播放/暂停按钮。
 */
@Composable
fun LyricsPage(viewModel: LyricsPageViewModel = koinInject()) {
    val uiState by viewModel.uiState.collectAsState()

    LyricsScreen(
        state = uiState,
        onSeek = viewModel::seekTo,
        onBiasChange = viewModel::updateLyricBias,
        onSaveBias = viewModel::saveLyricBias,
        onRefresh = viewModel::refreshLyrics,
    )
}
