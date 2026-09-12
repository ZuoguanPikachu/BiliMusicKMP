package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zuoguan.bilimusickmp.models.Song
import com.zuoguan.bilimusickmp.vm.SongEditorState

/**
 * 歌曲编辑对话框（桌面容器）。
 *
 * 只负责对话框这件事：标题与「确定 / 取消」按钮的摆放。
 * 表单复用 [SongEditorForm]，编辑状态来自共用的
 * [com.zuoguan.bilimusickmp.vm.SongEditorViewModel]。
 */
@Composable
fun SongInfoEditDialog(
    state: SongEditorState,
    onEdit: (transform: (Song) -> Song) -> Unit,
    onResolveLyricId: () -> Unit,
    onResolveCover: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = !state.isLoading && state.draft != null,
                onClick = onConfirm
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = {
            Text(text = if (state.isCreating) "添加歌曲" else "编辑歌曲")
        },
        text = {
            SongEditorForm(
                state = state,
                onEdit = onEdit,
                onResolveLyricId = onResolveLyricId,
                onResolveCover = onResolveCover,
                modifier = Modifier.heightIn(max = 560.dp)
            )
        }
    )
}
