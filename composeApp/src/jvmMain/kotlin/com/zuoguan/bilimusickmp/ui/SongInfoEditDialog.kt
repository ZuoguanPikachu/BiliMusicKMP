package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import com.zuoguan.bilimusickmp.models.Song
import com.zuoguan.bilimusickmp.services.SongMetadataService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SongInfoEditDialog(
    dialogTitle: String,
    isLoading: Boolean,
    allTags: List<String>,
    song: Song?,
    onConfirm: (Song) -> Unit,
    onDismiss: () -> Unit,
    songMetadataService: SongMetadataService = koinInject()
) {
    var title by remember(song) { mutableStateOf(song?.title.orEmpty()) }
    var author by remember(song) { mutableStateOf(song?.author.orEmpty()) }
    var lyricSource by remember(song) { mutableStateOf(song?.lyricSource) }
    var lyricId by remember(song) { mutableStateOf(song?.lyricId.orEmpty()) }
    var lyricBiasText by remember(song) { mutableStateOf(song?.lyricBias?.toString().orEmpty()) }
    var pic by remember(song) { mutableStateOf(song?.pic.orEmpty()) }
    var tags by remember(song) { mutableStateOf(song?.tags.orEmpty()) }
    var newTagText by remember { mutableStateOf("") }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun autoFill() {
        if(title.isNotEmpty() && lyricId.isEmpty()){
            lyricId = songMetadataService.resolveLyricId(lyricSource!!, title, author)
        }

        if (lyricId.isNotEmpty() && pic.isEmpty()) {
            pic = songMetadataService.resolvePic(lyricSource!!, lyricId)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = !isLoading,
                onClick = {
                    song?.let {
                        onConfirm(it.apply {
                            this.title = title
                            this.author = author
                            this.lyricSource = lyricSource!!
                            this.lyricId = lyricId
                            lyricBias = lyricBiasText.toIntOrNull() ?: 0
                            this.pic = pic
                            this.tags = tags.ifEmpty { listOf("Default") }
                        })
                    }
                    onDismiss()
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text(text = dialogTitle) },
        text = {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("标题") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text("作者") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    LyricSourceDropdown(lyricSource!!, {
                        lyricSource = it
                    })

                    OutlinedTextField(
                        value = lyricId,
                        onValueChange = { lyricId = it },
                        label = { Text("歌词ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Default),
                                onClick = {
                                    scope.launch { autoFill() }
                                }
                            ) {
                                Icon(Icons.Default.AutoFixNormal, contentDescription = "自动填充")
                            }
                        }
                    )

                    OutlinedTextField(
                        value = lyricBiasText,
                        onValueChange = { input ->
                            if (input.isEmpty() || Regex("^-?\\d*$").matches(input)) {
                                lyricBiasText = input
                            }
                        },
                        label = { Text("歌词延时(ms)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = pic,
                        onValueChange = { pic = it },
                        label = { Text("封面") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Default),
                                onClick = {
                                    scope.launch { autoFill() }
                                }
                            ) {
                                Icon(Icons.Default.AutoFixNormal, contentDescription = "自动填充")
                            }
                        }
                    )

                    TagsEditor(
                        tags = tags,
                        allTags = allTags,
                        newTagText = newTagText,
                        onNewTagTextChange = { newTagText = it },
                        onAddTag = { tag ->
                            if (tag !in tags) tags = tags + tag
                            newTagText = ""
                        },
                        onRemoveTag = { tag ->
                            tags = tags - tag
                        }
                    )
                }
            }
        }
    )
}
