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
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.zuoguan.bilimusickmp.LocalSnackBarHostState
import com.zuoguan.bilimusickmp.models.AudioSource
import com.zuoguan.bilimusickmp.models.CoverSource
import com.zuoguan.bilimusickmp.models.LyricSource
import com.zuoguan.bilimusickmp.models.Page
import com.zuoguan.bilimusickmp.models.Song
import com.zuoguan.bilimusickmp.services.NavigationService
import com.zuoguan.bilimusickmp.services.SongEditService
import com.zuoguan.bilimusickmp.utils.UiEvent
import com.zuoguan.bilimusickmp.vm.SearchPageViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPage(
    viewModel: SearchPageViewModel = koinInject(),
    navigationService: NavigationService = koinInject(),
    songEditService: SongEditService = koinInject()
) {
    val state by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackBarHostState = LocalSnackBarHostState.current

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.ShowSnackBar -> {
                    coroutineScope.launch {
                        snackBarHostState.showSnackbar(
                            message = event.message,
                            actionLabel = event.actionLabel,
                            duration = event.duration,
                            withDismissAction = true,
                        )
                    }
                }
            }
        }
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
                        items(state.results) { item ->
                            PcSearchResultItem(
                                item,
                                onItemClick = viewModel::playSong,
                                onAddButtonClick = {
                                    songEditService.editSong(Song().apply {
                                        id = item.id
                                        audioSource = item.audioSource
                                        title = item.title
                                        author = item.author
                                        lyricId = if (item.audioSource != AudioSource.BILI_BILI) item.id else ""
                                        lyricSource = when(item.audioSource) {
                                            AudioSource.BILI_BILI -> LyricSource.NONE
                                            AudioSource.KU_GOU -> LyricSource.KU_GOU
                                            AudioSource.NET_EASE -> LyricSource.NET_EASE
                                        }
                                        coverId = if (item.audioSource != AudioSource.BILI_BILI) item.id else ""
                                        coverSource  = when(item.audioSource) {
                                            AudioSource.BILI_BILI -> CoverSource.BILI_BILI
                                            AudioSource.KU_GOU -> CoverSource.KU_GOU
                                            AudioSource.NET_EASE -> CoverSource.NET_EASE
                                        }
                                        pic = item.pic
                                        ts = System.currentTimeMillis()
                                    }, "Search")
                                    navigationService.navigate(Page.SONG_EDIT)
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        state = viewModel.lazyListState
                    ) {
                        items(state.results) { item ->
                            MobileSearchResultItem(
                                item,
                                onItemClick = viewModel::playSong,
                                onAddButtonClick = {
                                    songEditService.editSong(Song().apply {
                                        id = item.id
                                        audioSource = item.audioSource
                                        title = item.title
                                        author = item.author
                                        lyricId = if (item.audioSource != AudioSource.BILI_BILI) item.id else ""
                                        lyricSource = when(item.audioSource) {
                                            AudioSource.BILI_BILI -> LyricSource.NONE
                                            AudioSource.KU_GOU -> LyricSource.KU_GOU
                                            AudioSource.NET_EASE -> LyricSource.NET_EASE
                                        }
                                        coverId = if (item.audioSource != AudioSource.BILI_BILI) item.id else ""
                                        coverSource  = when(item.audioSource) {
                                            AudioSource.BILI_BILI -> CoverSource.BILI_BILI
                                            AudioSource.KU_GOU -> CoverSource.KU_GOU
                                            AudioSource.NET_EASE -> CoverSource.NET_EASE
                                        }
                                        pic = item.pic
                                        ts = System.currentTimeMillis()
                                    }, "Search")
                                    navigationService.navigate(Page.SONG_EDIT)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
