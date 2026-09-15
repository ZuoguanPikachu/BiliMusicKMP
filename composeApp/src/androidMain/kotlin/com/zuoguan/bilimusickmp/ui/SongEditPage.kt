package com.zuoguan.bilimusickmp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
 * 只负责"整页"这件事：返回手势、返回按钮与保存按钮的摆放。
 * 表单本身复用共享的 [SongEditorForm]，编辑状态来自 [SongEditorViewModel]。
 *
 * 输入法 inset 由调用方在 [modifier] 上处理（与设置页一致：先消费 Scaffold 的
 * innerPadding 再 imePadding）。这里必须让「整个 Column」被顶上去，只给表单让位的话，
 * 保存按钮占的那段高度会留在键盘上方变成一块空白。
 */
@Composable
fun SongEditPage(
    viewModel: SongEditorViewModel = koinInject(),
    navigationService: NavigationService = koinInject(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    fun cancel() {
        viewModel.dismiss()
        navigationService.back()
    }

    BackHandler(enabled = true) { cancel() }

    Column(modifier = modifier.fillMaxSize()) {
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
            modifier = Modifier.weight(1f)
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
