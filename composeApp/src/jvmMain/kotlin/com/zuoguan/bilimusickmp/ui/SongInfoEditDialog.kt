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
import com.zuoguan.bilimusickmp.models.CoverSource
import com.zuoguan.bilimusickmp.models.LyricSource
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
    var allTags by remember{mutableStateOf(allTags)}
    var title by remember(song) { mutableStateOf(song?.title.orEmpty()) }
    var author by remember(song) { mutableStateOf(song?.author.orEmpty()) }
    var lyricSource by remember(song) { mutableStateOf(song?.lyricSource) }
    var lyricId by remember(song) { mutableStateOf(song?.lyricId.orEmpty()) }
    var lyricBiasText by remember(song) { mutableStateOf(song?.lyricBias?.toString().orEmpty()) }
    var coverSource by remember(song) { mutableStateOf(song?.coverSource) }
    var coverId by remember(song) { mutableStateOf(song?.coverId.orEmpty()) }
    var pic by remember(song) { mutableStateOf(song?.pic.orEmpty()) }
    var tags by remember(song) { mutableStateOf(song?.tags.orEmpty()) }
    var newTagText by remember { mutableStateOf("") }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun resolveLyricId() {
        if(title.isNotEmpty() && author.isNotEmpty()){
            lyricId = songMetadataService.resolveSongId(lyricSource!!, title, author)
        }
    }

    suspend fun resolveCoverId() {
        if(title.isNotEmpty() && author.isNotEmpty()){
            coverId = songMetadataService.resolveSongId(coverSource!!, title, author)
            if (coverId.isNotEmpty()){
                pic = songMetadataService.resolvePic(coverSource!!, coverId)
            }
        }
    }

    fun resolvePicFromCoverId() {
        if (coverId.isNotEmpty()){
            pic = songMetadataService.resolvePic(coverSource!!, coverId)
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
                            this.coverSource = coverSource!!
                            this.coverId = coverId
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
                if (isLoading || song == null) {
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

                    Text("歌词", style = MaterialTheme.typography.titleMedium)
                    MetadataSourceDropdown(LyricSource.entries, lyricSource!!, {
                        lyricSource = it as LyricSource?
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
                                    scope.launch { resolveLyricId() }
                                }
                            ) {
                                Icon(Icons.Default.AutoFixNormal, contentDescription = "自动获取歌词ID")
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

                    Text("封面", style = MaterialTheme.typography.titleMedium)
                    MetadataSourceDropdown(CoverSource.entries,  coverSource!!, {
                        coverSource = it as CoverSource?
                    })
                    if (coverSource != CoverSource.BILI_BILI){
                        OutlinedTextField(
                            value = coverId,
                            onValueChange = { coverId = it },
                            label = { Text("封面ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Default),
                                    onClick = {
                                        scope.launch { resolveCoverId() }
                                    }
                                ) {
                                    Icon(Icons.Default.AutoFixNormal, contentDescription = "自动获取封面ID")
                                }
                            }
                        )
                    }
                    OutlinedTextField(
                        value = pic,
                        onValueChange = { pic = it },
                        label = { Text("封面URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (coverSource != CoverSource.BILI_BILI){
                                IconButton(
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Default),
                                    onClick = {
                                        scope.launch { resolvePicFromCoverId() }
                                    }
                                ) {
                                    Icon(Icons.Default.AutoFixNormal, contentDescription = "根据封面ID获取URL")
                                }
                            }
                        }
                    )

                    Text("TAG", style = MaterialTheme.typography.titleMedium)
                    TagsEditor(
                        selectedTags = tags,
                        allTags = allTags,
                        newTagText = newTagText,
                        onNewTagTextChange = { newTagText = it },
                        onAddTag = { tag ->
                            if (tag !in allTags){
                                allTags += tag
                                tags += tag
                            }
                            newTagText = ""
                        },
                        onToggleTag = { tag ->
                            if (tag in tags){
                                tags -= tag
                            }else{
                                tags += tag
                            }
                        }
                    )
                }
            }
        }
    )
}
