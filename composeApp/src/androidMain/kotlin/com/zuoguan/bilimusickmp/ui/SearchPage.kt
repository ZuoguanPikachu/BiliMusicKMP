package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import io.kamel.core.Resource
import io.kamel.image.asyncPainterResource
import org.koin.compose.koinInject
import com.zuoguan.bilimusickmp.LocalSnackBarHostState
import com.zuoguan.bilimusickmp.models.AudioSource
import com.zuoguan.bilimusickmp.models.SearchResult
import com.zuoguan.bilimusickmp.models.label
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
                is UiEvent.ShowSnackbar -> {
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp)
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = { onItemClick(item) },
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .weight(0.8f)
                    .aspectRatio(1.6f)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                when (val resource = asyncPainterResource(convertImageUrl(item.pic, 320, 200))) {
                    is Resource.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is Resource.Success -> {
                        Image(
                            painter = resource.value,
                            contentDescription = item.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    is Resource.Failure -> {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Gray)) {
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
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
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
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加")
                    }
                }

            }
        }
    }
}

@Composable
private fun SearchSourceDropdown(
    selectedSource: AudioSource,
    onSourceChange: (AudioSource) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        TextButton(
            onClick = { expanded = true }
        ) {
            Text(text = selectedSource.label)
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "选择平台"
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            AudioSource.entries.forEach { source ->
                DropdownMenuItem(
                    text = { Text(source.label) },
                    onClick = {
                        onSourceChange(source)
                        expanded = false
                    }
                )
            }
        }
    }
}
