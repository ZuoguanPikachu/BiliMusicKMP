package com.zuoguan.bilimusickmp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zuoguan.bilimusickmp.services.NavigationService
import com.zuoguan.bilimusickmp.vm.SongEditPageViewModel
import org.koin.compose.koinInject

@Composable
fun SongEditPage(
    viewModel: SongEditPageViewModel = koinInject(),
    navigationService: NavigationService = koinInject()
) {
    val state by viewModel.uiState.collectAsState()
    val song = state.song

    BackHandler(enabled = true) {
        navigationService.back()
    }

    Column(modifier = Modifier.fillMaxSize()){
        IconButton(onClick = { navigationService.back() }) {
            Icon(
                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                contentDescription = "返回"
            )
        }

        if (state.isLoading  || song == null){
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return
        }

        var title by remember(song){ mutableStateOf(song.title) }
        var author by remember(song) { mutableStateOf(song.author) }
        var lyricSource by remember(song) { mutableStateOf(song.lyricSource) }
        var lyricId by remember(song) { mutableStateOf(song.lyricId) }
        var lyricBiasText by remember(song) { mutableStateOf(song.lyricBias.toString()) }
        var pic by remember(song) { mutableStateOf(song.pic) }
        var tags by remember(song) { mutableStateOf(song.tags) }
        var newTagText by remember { mutableStateOf("") }
        var allTags by remember { mutableStateOf(state.allTags) }

        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ){
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

            SearchSourceDropdown(lyricSource, {
                lyricSource = it
            })

            TextField(
                value = lyricId,
                onValueChange = { lyricId = it },
                label = { Text("歌词ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Default),
                        onClick = {

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

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    viewModel.save(
                        song.apply {
                            this.title = title
                            this.author = author
                            this.lyricSource = lyricSource
                            this.lyricId = lyricId
                            lyricBias = lyricBiasText.toIntOrNull() ?: 0
                            this.pic = pic
                            this.tags = tags.ifEmpty { listOf("Default") }
                            this.ts = song.ts
                        }
                    )
                    navigationService.back()
                }
            ) {
                Text("确定")
            }
        }
    }
}