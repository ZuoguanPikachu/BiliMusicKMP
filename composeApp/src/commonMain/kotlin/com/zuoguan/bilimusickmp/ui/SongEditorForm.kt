package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.zuoguan.bilimusickmp.models.CoverSource
import com.zuoguan.bilimusickmp.models.LyricSource
import com.zuoguan.bilimusickmp.models.Song
import com.zuoguan.bilimusickmp.vm.SongEditorState

/**
 * 歌曲编辑表单（两个平台共用，只负责字段本身）。
 *
 * 完全无状态：草稿由 [SongEditorState.draft] 提供，所有改动通过 [onEdit] 回传给调用方；
 * 容器由各平台的薄壳提供（Android 整页 / 桌面对话框）。
 *
 * 布局分四段：标题作者 → 歌词 → 封面 → 标签，段落标题带图标与主题色，
 * 段内紧凑（10dp）、段间留白（16dp），窄屏与桌面共用一份实现。
 *
 * @param state 表单状态，草稿与可选标签集合都来自这里。
 * @param onEdit 以「修改函数」的形式回传草稿变更。
 * @param onResolveLyricId 切换歌词来源后重新解析歌词 ID。
 * @param onResolveCover 切换封面来源后重新解析封面。
 * @param contentPadding 字段区域的内边距，由容器按自身布局决定。
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
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 点「添加」命中的是歌单里已有的歌：先说清楚表单里显示的是哪一份数据
            if (state.isAddingExistingSong) {
                ExistingSongNotice()
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            }

            EditorSection(title = "歌词", icon = Icons.Default.Lyrics) {
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
                    label = "歌词 ID"
                )
            }

            EditorSection(title = "封面", icon = Icons.Default.Image) {
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // B 站封面不单独存 id，视频 id 就是封面来源
                        if (draft.coverSource != CoverSource.BILI_BILI) {
                            ClearableField(
                                value = draft.coverId,
                                onValueChange = { v -> onEdit { it.copy(coverId = v) } },
                                label = "封面 ID"
                            )
                        }
                        ClearableField(
                            value = draft.pic,
                            onValueChange = { v -> onEdit { it.copy(pic = v) } },
                            label = "封面 URL"
                        )
                    }

                    CoverPreviewBox(pic = draft.pic, title = draft.title, size = 96.dp)
                }
            }

            EditorSection(title = "标签", icon = Icons.Default.Sell) {
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
}

/**
 * 带图标的小标题 + 一组字段。
 *
 * 用主题色把「歌词 / 封面 / 标签」三段区分开，避免整页都是一模一样的输入框。
 */
@Composable
private fun EditorSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        content()
    }
}

/** 「歌曲已在歌单中」提示条：说明表单里显示的是歌单里已保存的那一份数据。 */
@Composable
private fun ExistingSongNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LibraryAddCheck,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "歌曲已在歌单中",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "下面是歌单里已保存的信息，保存会更新这一条。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * 通用的带「清除」按钮的单行输入框。
 *
 * 显示值与回调都由调用方提供，清除按钮同样只回调 [onValueChange]（传入空串），
 * 因此每个字段各自绑定自己的值与回调，不会互相串扰。
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
