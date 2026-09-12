package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.zuoguan.bilimusickmp.models.CoverSource
import com.zuoguan.bilimusickmp.models.LyricSource
import com.zuoguan.bilimusickmp.models.Song
import com.zuoguan.bilimusickmp.vm.SongEditorState

/**
 * 歌曲编辑表单（两个平台共用，不含任何容器）。
 *
 * 完全无状态：草稿由 [SongEditorState.draft] 提供，所有改动通过 [onEdit] 回传。
 * 容器差异（Android 整页 / 桌面对话框）由各自的薄壳负责，这里只管字段。
 */
@Composable
fun SongEditorForm(
    state: SongEditorState,
    onEdit: (transform: (Song) -> Song) -> Unit,
    onResolveLyricId: () -> Unit,
    onResolveCover: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    val draft = state.draft

    if (state.isLoading || draft == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ClearableField(
                value = draft.title,
                onValueChange = { v -> onEdit { it.copy(title = v) } },
                label = "标题"
            )

            ClearableField(
                value = draft.author,
                onValueChange = { v -> onEdit { it.copy(author = v) } },
                label = "作者"
            )

            Text("歌词", style = MaterialTheme.typography.titleMedium)
            MetadataSourceDropdown(
                sources = LyricSource.entries,
                selectedSource = draft.lyricSource,
                onSourceChange = { source ->
                    onEdit { it.copy(lyricSource = source) }
                    onResolveLyricId()
                }
            )
            ClearableField(
                value = draft.lyricId,
                onValueChange = { v -> onEdit { it.copy(lyricId = v) } },
                label = "歌词ID"
            )

            Text("封面", style = MaterialTheme.typography.titleMedium)
            MetadataSourceDropdown(
                sources = CoverSource.entries,
                selectedSource = draft.coverSource,
                onSourceChange = { source ->
                    onEdit { it.copy(coverSource = source) }
                    onResolveCover()
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (draft.coverSource != CoverSource.BILI_BILI) {
                        ClearableField(
                            value = draft.coverId,
                            onValueChange = { v -> onEdit { it.copy(coverId = v) } },
                            label = "封面ID"
                        )
                    }
                    ClearableField(
                        value = draft.pic,
                        onValueChange = { v -> onEdit { it.copy(pic = v) } },
                        label = "封面URL"
                    )
                }

                CoverPreviewBox(pic = draft.pic, title = draft.title, size = 96.dp)
            }

            Text("TAG", style = MaterialTheme.typography.titleMedium)
            // 标签输入框的文本是纯 UI 临时状态，留在表单内部即可
            var newTagText by remember { mutableStateOf("") }
            TagsEditor(
                selectedTags = draft.tags,
                allTags = state.allTags,
                newTagText = newTagText,
                onNewTagTextChange = { newTagText = it },
                onAddTag = { tag ->
                    onEdit { song -> if (tag in song.tags) song else song.copy(tags = song.tags + tag) }
                },
                onToggleTag = { tag ->
                    onEdit { song ->
                        song.copy(
                            tags = if (tag in song.tags) song.tags - tag else song.tags + tag
                        )
                    }
                }
            )
        }
    }
}

/**
 * 带"清除"按钮的输入框。
 *
 * 旧代码里 `pic` 字段的清除按钮误清了 `coverId`（复制粘贴留下的 bug），
 * 这里统一成一个组件，从结构上避免同类问题。
 */
@Composable
private fun ClearableField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Default),
                    onClick = { onValueChange("") }
                ) {
                    Icon(Icons.Default.Clear, contentDescription = "清除$label")
                }
            }
        }
    )
}
