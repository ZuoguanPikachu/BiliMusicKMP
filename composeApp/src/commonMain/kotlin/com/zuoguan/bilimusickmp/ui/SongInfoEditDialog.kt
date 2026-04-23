package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import com.zuoguan.bilimusickmp.models.Song
import com.zuoguan.bilimusickmp.services.NeteaseService
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
) {
    var title by remember(song) { mutableStateOf(song?.title.orEmpty()) }
    var author by remember(song) { mutableStateOf(song?.author.orEmpty()) }
    var neteaseId by remember(song) { mutableStateOf(song?.neteaseId.orEmpty()) }
    var lyricBiasText by remember(song) { mutableStateOf(song?.lyricBias?.toString().orEmpty()) }
    var pic by remember(song) { mutableStateOf(song?.pic.orEmpty()) }
    var tags by remember(song) { mutableStateOf(song?.tags.orEmpty()) }
    var newTagText by remember { mutableStateOf("") }

    val neteaseService: NeteaseService = koinInject()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun autoFill() {
        if(title.isNotEmpty() && title.isNotEmpty() && neteaseId.isEmpty()){
            neteaseId = neteaseService.getIdByTitleAndAuthor(title, author)
            println()
        }

        if (neteaseId.isNotEmpty() && pic.isEmpty()) {
            pic = neteaseService.getImageUrl(neteaseId)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = dialogTitle,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )

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
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("标题") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    TextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text("作者") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    TextField(
                        value = neteaseId,
                        onValueChange = { neteaseId = it },
                        label = { Text("网易云Id") },
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

                    TextField(
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

                    TextField(
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        enabled = !isLoading,
                        onClick = {
                            song?.let {
                                onConfirm(it.apply {
                                    this.title = title
                                    this.author = author
                                    this.neteaseId = neteaseId
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
                }
            }
        }
    }
}

@Composable
fun TagsEditor(
    tags: List<String>,
    allTags: List<String>,
    newTagText: String,
    onNewTagTextChange: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions = remember(newTagText, tags, allTags) {
        if (newTagText.isBlank()) {
            emptyList()
        } else {
            allTags
                .filter { it.contains(newTagText, ignoreCase = true) }
                .filterNot { it in tags }
                .take(3)
        }
    }

    Column(modifier) {
        var textFieldWidthPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current

        TextField(
            value = newTagText,
            onValueChange = onNewTagTextChange,
            label = { Text("添加标签") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    val text = newTagText.trim()
                    if (text.isNotBlank()) {
                        onAddTag(text)
                    }
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    textFieldWidthPx = coordinates.size.width
                },
            trailingIcon = {
                val text = newTagText.trim()
                if (text.isNotBlank()) {
                    IconButton(
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Default),
                        onClick = { onAddTag(text) }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加")
                    }
                }
            }
        )

        if (suggestions.isNotEmpty()) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, 60)
            ) {
                Card(
                    modifier = Modifier.width(
                        with(density) { textFieldWidthPx.toDp() }
                    )
                ) {
                    Column {
                        suggestions.forEach { tag ->
                            ListItem(
                                headlineContent = { Text(tag) },
                                modifier = Modifier.clickable {
                                    onAddTag(tag)
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (tags.isEmpty()) {
                AssistChip(
                    onClick = {},
                    label = { Text("Default") }
                )
            }

            tags.forEach { tag ->
                AssistChip(
                    onClick = {},
                    label = { Text(tag) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove tag",
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onRemoveTag(tag) }
                        )
                    }
                )
            }
        }
    }
}
