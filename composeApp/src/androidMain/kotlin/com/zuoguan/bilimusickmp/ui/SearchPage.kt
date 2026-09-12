package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import com.zuoguan.bilimusickmp.models.Page
import com.zuoguan.bilimusickmp.models.SearchResult
import com.zuoguan.bilimusickmp.models.toSong
import com.zuoguan.bilimusickmp.services.NavigationService
import com.zuoguan.bilimusickmp.vm.SearchPageViewModel
import com.zuoguan.bilimusickmp.vm.SongEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPage(
    viewModel: SearchPageViewModel = koinInject(),
    navigationService: NavigationService = koinInject(),
    songEditorViewModel: SongEditorViewModel = koinInject()
) {
    val state by viewModel.uiState.collectAsState()

    // 搜索结果 → 新建歌曲；B 站视频会在编辑器里自动用 LLM 补全歌名/歌手
    fun openSongEditor(item: SearchResult) {
        songEditorViewModel.openForCreate(item.toSong())
        navigationService.navigate(Page.SONG_EDIT)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(8.dp)
        ) {
            SearchBar(
                expanded = false,
                onExpandedChange = {},
                modifier = Modifier
                    .fillMaxWidth(),
                windowInsets = WindowInsets(),
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
                val windowInfo = LocalWindowInfo.current
                val density = LocalDensity.current
                val windowWidthDp = with(density) { windowInfo.containerSize.width.toDp() }
                val isTablet = windowWidthDp >= 600.dp

                if (isTablet) {
                    LazyVerticalGrid(
                        modifier = Modifier.fillMaxSize(),
                        columns = GridCells.Adaptive(256.dp),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        state = viewModel.lazyGridState
                    ) {
                        items(state.results, key = { it.id }) { item ->
                            PcSearchResultItem(
                                item,
                                onItemClick = viewModel::playSong,
                                onAddButtonClick = ::openSongEditor
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        state = viewModel.lazyListState
                    ) {
                        items(state.results, key = { it.id }) { item ->
                            MobileSearchResultItem(
                                item,
                                onItemClick = viewModel::playSong,
                                onAddButtonClick = ::openSongEditor
                            )
                        }
                    }
                }
            }
        }
    }
}
