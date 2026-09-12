package com.zuoguan.bilimusickmp.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.zuoguan.bilimusickmp.models.Song

/**
 * 删除歌曲的二次确认对话框，确认按钮用错误色以示区别。
 *
 * @param song 待删除的歌曲，仅用于在提示文案中显示标题。
 * @param onConfirm 点击「删除」时回调。
 * @param onDismiss 点击「取消」或关闭对话框时回调。
 */
@Composable
fun DeleteSongConfirmDialog(
    song: Song,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("删除歌曲")
        },
        text = {
            Text("确定要删除「${song.title}」吗？")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "删除",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}