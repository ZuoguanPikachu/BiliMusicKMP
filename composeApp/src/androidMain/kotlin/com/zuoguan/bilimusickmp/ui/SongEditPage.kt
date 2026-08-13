package com.zuoguan.bilimusickmp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.zuoguan.bilimusickmp.models.CoverSource
import com.zuoguan.bilimusickmp.models.LyricSource
import com.zuoguan.bilimusickmp.services.NavigationService
import com.zuoguan.bilimusickmp.services.SongMetadataService
import com.zuoguan.bilimusickmp.vm.SongEditPageViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SongEditPage(
    viewModel: SongEditPageViewModel = koinInject(),
    navigationService: NavigationService = koinInject(),
    songMetadataService: SongMetadataService = koinInject()
) {
    val state by viewModel.uiState.collectAsState()
    val song = state.song
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    BackHandler(enabled = true) {
        navigationService.back()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        IconButton(
            onClick = { navigationService.back() }
        ) {
            Icon( imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = "返回" )
        }

        if (state.isLoading  || song == null){
            Box(
                modifier = Modifier.fillMaxSize(),
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
        var coverSource by remember(song) { mutableStateOf(song.coverSource) }
        var coverId by remember(song) { mutableStateOf(song.coverId) }
        var pic by remember(song) { mutableStateOf(song.pic) }
        var tags by remember(song) { mutableStateOf(song.tags) }
        var newTagText by remember { mutableStateOf("") }
        var allTags by remember { mutableStateOf(state.allTags) }

        suspend fun resolveLyricId() {
            if(title.isNotEmpty() && author.isNotEmpty()){
                lyricId = songMetadataService.resolveSongId(lyricSource, title, author)
            }
        }

        suspend fun resolveCover() {
            if(title.isNotEmpty() && author.isNotEmpty()){
                coverId = songMetadataService.resolveSongId(coverSource, title, author)
                if (coverId.isNotEmpty()){
                    pic = songMetadataService.resolvePic(coverSource, coverId)
                }
            }
        }

        fun resolvePicFromCoverId() {
            if (coverId.isNotEmpty()){
                pic = songMetadataService.resolvePic(coverSource, coverId)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Default),
                            onClick = {
                                title = ""
                            }
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                )
            }
            item {
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("作者") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Default),
                            onClick = {
                                author = ""
                            }
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                )
            }

            item {
                Text("歌词", style = MaterialTheme.typography.titleMedium)
            }
            item{
                MetadataSourceDropdown(LyricSource.entries, lyricSource, {
                    lyricSource = it as LyricSource
                    scope.launch { resolveLyricId() }
                })
            }
            item {
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
                                lyricId = ""
                            }
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                )
            }

            item {
                Text("封面", style = MaterialTheme.typography.titleMedium)
            }
            item {
                MetadataSourceDropdown(CoverSource.entries,  coverSource, {
                    coverSource = it as CoverSource
                    scope.launch { resolveCover() }
                })
            }
            if (coverSource != CoverSource.BILI_BILI){
                item {
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
                                    coverId = ""
                                }
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    )
                }
            }
            item {
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
                                    coverId = ""
                                }
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    }
                )
            }

            item {
                Text("TAG", style = MaterialTheme.typography.titleMedium)
            }
            item {
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

            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        viewModel.save(
                            song.apply {
                                this.title = title
                                this.author = author
                                this.lyricSource = lyricSource
                                this.lyricId = lyricId
                                this.coverSource = coverSource
                                this.coverId = coverId
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

}