package com.zuoguan.bilimusickmp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zuoguan.bilimusickmp.services.NavigationService
import com.zuoguan.bilimusickmp.vm.SongEditorViewModel
import org.koin.compose.koinInject

/**
 * 歌曲编辑页（Android 容器）。
 *
 * 只负责"整页"这件事：返回手势、返回按钮、保存按钮的摆放。
 * 表单本身与桌面端共用 [SongEditorForm]，编辑状态来自 [SongEditorViewModel]。
 */
@Composable
fun SongEditPage(
    viewModel: SongEditorViewModel = koinInject(),
    navigationService: NavigationService = koinInject(),
) {
    val state by viewModel.uiState.collectAsState()

    // 取消编辑并退出页面
    fun cancel() {
        viewModel.dismiss()
        navigationService.back()
    }

    BackHandler(enabled = true) { cancel() }

    Column(modifier = Modifier.fillMaxSize()) {
        IconButton(onClick = { cancel() }) {
            Icon(
                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                contentDescription = "返回"
            )
        }

        SongEditorForm(
            state = state,
            onEdit = viewModel::edit,
            onResolveLyricId = viewModel::resolveLyricId,
            onResolveCover = viewModel::resolveCover,
            modifier = Modifier
                .weight(1f)
                .imePadding()
        )

        // 元数据补全中或还没有草稿时不给保存，避免写入半成品
        if (!state.isLoading && state.draft != null) {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                onClick = {
                    viewModel.save()
                    navigationService.back()
                }
            ) {
                Text("确定")
            }
        }
    }
}
