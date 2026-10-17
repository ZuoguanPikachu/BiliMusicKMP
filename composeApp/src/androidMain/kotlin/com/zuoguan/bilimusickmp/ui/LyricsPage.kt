package com.zuoguan.bilimusickmp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.zuoguan.bilimusickmp.services.NavigationService
import com.zuoguan.bilimusickmp.vm.LyricsPageViewModel
import org.koin.compose.koinInject

/**
 * Android 歌词页：顶部返回按钮 + 与桌面端共用的歌词主体。
 *
 * 手机竖屏走窄屏排布（大封面在上），平板横屏自动切成左右分栏。
 * 歌词页不显示底部播放栏，所以把「播放/暂停」和「下一曲」一并传进去，
 * 分列在延时胶囊左右两侧。
 */
@Composable
fun LyricsPage(
    viewModel: LyricsPageViewModel = koinInject(),
    navigationService: NavigationService = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()

    BackHandler(enabled = true) {
        navigationService.back()
    }

    LyricsScreen(
        state = uiState,
        onSeek = viewModel::seekTo,
        onBiasChange = viewModel::updateLyricBias,
        onSaveBias = viewModel::saveLyricBias,
        onRefresh = viewModel::refreshLyrics,
        transport = LyricsTransportControls(
            isPlaying = uiState.isPlaying,
            onTogglePlay = viewModel::togglePlayPause,
            onPlayNext = viewModel::playNext,
        ),
        header = {
            Row {
                IconButton(onClick = { navigationService.back() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                }
            }
        }
    )
}
