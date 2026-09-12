package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import org.koin.compose.koinInject
import com.zuoguan.bilimusickmp.models.toSong
import com.zuoguan.bilimusickmp.vm.SearchPageViewModel
import com.zuoguan.bilimusickmp.vm.SongEditorViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPage(
    viewModel: SearchPageViewModel = koinInject(),
    songEditorViewModel: SongEditorViewModel = koinInject(),
) {
    val state by viewModel.uiState.collectAsState()
    val editorState by songEditorViewModel.uiState.collectAsState()

    LaunchedEffect(state.results) {
        if (state.results.isNotEmpty()) {
            viewModel.lazyGridState.scrollToItem(0)
        }
    }

    if (editorState.isOpen) {
        SongInfoEditDialog(
            state = editorState,
            onEdit = songEditorViewModel::edit,
            onResolveLyricId = songEditorViewModel::resolveLyricId,
            onResolveCover = songEditorViewModel::resolveCover,
            onConfirm = songEditorViewModel::save,
            onDismiss = songEditorViewModel::dismiss,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 8.dp, top = 0.dp, end = 8.dp, bottom = 12.dp)
    ) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
        ) {
            SearchBar(
                expanded = false,
                onExpandedChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
                inputField = {
                    SearchBarDefaults.InputField(
                        query = state.keyword,
                        onQueryChange = viewModel::onKeywordChange,
                        onSearch = {
                            viewModel.search()
                        },
                        expanded = false,
                        onExpandedChange = {},
                        leadingIcon = {
                            SearchSourceDropdown(
                                selectedSource = state.audioSource,
                                onSourceChange = viewModel::onAudioSourceChange,
                                modifier = Modifier
                                    .pointerHoverIcon(PointerIcon.Default)
                                    .padding(horizontal = 8.dp),
                            )
                        },
                        trailingIcon = {
                            if (state.keyword.isNotBlank()) {
                                IconButton(
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Default),
                                    onClick = viewModel::search
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "搜索")
                                }
                            }
                        }
                    )
                },
                content = {}
            )
        }

        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            if (state.isSearchLoading) {
                CircularProgressIndicator()
            }

            state.searchError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            if (!state.isSearchLoading && state.searchError == null) {
                LazyVerticalGrid(
                    modifier = Modifier.fillMaxSize(),
                    columns = GridCells.Adaptive(312.dp),
                    state = viewModel.lazyGridState
                ) {
                    items(state.results, key = { it.id }) { item ->
                        PcSearchResultItem(
                            item,
                            onItemClick = viewModel::playSong,
                            // 与 Android 完全一致：搜索结果 → 新建歌曲会话
                            onAddButtonClick = { songEditorViewModel.openForCreate(it.toSong()) }
                        )
                    }
                }
            }
        }
    }
}


