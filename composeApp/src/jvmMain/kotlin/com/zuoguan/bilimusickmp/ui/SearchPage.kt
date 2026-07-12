package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.kamel.core.Resource
import io.kamel.image.asyncPainterResource
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.zuoguan.bilimusickmp.LocalSnackBarHostState
import com.zuoguan.bilimusickmp.models.SearchResult
import com.zuoguan.bilimusickmp.utils.UiEvent
import com.zuoguan.bilimusickmp.utils.convertImageUrl
import com.zuoguan.bilimusickmp.vm.SearchPageViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPage(
    viewModel: SearchPageViewModel = koinInject()
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

    if (state.showAddDialog) {
        SongInfoEditDialog(
            "添加歌曲",
            state.isExtractInfoLoading,
            state.allTags,
            state.songToAdd,
            viewModel::confirmAdd,
            viewModel::cancelAdd,
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
                    modifier = Modifier
                        .fillMaxSize(),
                    columns = GridCells.Adaptive(312.dp)
                ) {
                    items(state.results) { item ->
                        SearchResultItem(
                            item,
                            onItemClick = viewModel::playSong,
                            onAddButtonClick = viewModel::requestAdd
                        )
                    }
                }
            }

        }
    }
}

@Composable
fun SearchResultItem(
    item: SearchResult,
    onItemClick: (SearchResult) -> Unit,
    onAddButtonClick: (SearchResult) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onItemClick(item) }
                .padding(top = 16.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ){
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
            ) {
                when (val resource = asyncPainterResource(convertImageUrl(item.pic, 512, 320))) {
                    is Resource.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is Resource.Success -> {
                        val painter: Painter = resource.value
                        Image(
                            painter,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                    is Resource.Failure -> {
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Gray)) {
                            Text("加载失败", modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }

                Text(
                    text = item.duration,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ){
                Text(
                    text = item.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                IconButton(
                    onClick = { onAddButtonClick(item) },
                ){
                    Icon(Icons.Default.Add, contentDescription = "添加")
                }
            }

        }
    }
}
