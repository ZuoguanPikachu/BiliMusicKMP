package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import io.kamel.core.Resource
import io.kamel.image.asyncPainterResource
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.zuoguan.bilimusickmp.LocalSnackBarHostState
import com.zuoguan.bilimusickmp.models.PlaySource
import com.zuoguan.bilimusickmp.models.Song
import com.zuoguan.bilimusickmp.utils.UiEvent
import com.zuoguan.bilimusickmp.utils.convertImageUrl
import com.zuoguan.bilimusickmp.vm.PlaylistPageViewModel
import com.zuoguan.bilimusickmp.vm.TagFilterMode


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPage(
    viewModel: PlaylistPageViewModel = koinInject()
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

    val hapticFeedback = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            viewModel.moveSong(from.index, to.index)
        }
    )

    Column {
        ToolBar(
            state.isOrdering,
            viewModel::switchOrderingMode,
            state.filterMode,
            viewModel::switchFilterMode,
            state.allTags,
            state.selectedTags,
            viewModel::toggleTag
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = 96.dp
            ),
            state = lazyListState,
        ) {
            items(state.filteredSongs, key = { it.id }) {
                ReorderableItem(reorderState, key = it.id) {_ ->
                    SongItem(
                        song = it,
                        isPlaying = (it.id == state.currentTrack?.id && state.currentTrack?.playSource == PlaySource.PLAYLIST),
                        state.isOrdering,
                        orderHandle = {
                            IconButton(
                                onClick = {},
                                modifier = Modifier.draggableHandle(
                                    onDragStarted = {
                                        viewModel.onDragStart()
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                    },
                                    onDragStopped = {
                                        viewModel.onDragEnd()
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    }
                                )
                            ) {
                                Icon(Icons.Rounded.DragHandle, contentDescription = "Reorder")
                            }
                        },
                        playSong = viewModel::playSong,
                        requestBottomSheet = viewModel::requestBottomSheet
                    )
                }
            }
        }
    }

    if (state.showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissBottomSheet,
            sheetState = rememberModalBottomSheetState(),
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        viewModel.requestEdit(state.songToHandle!!)
                        viewModel.dismissBottomSheet()
                    }
                ) {
                    ListItem(
                        headlineContent = { Text("编辑") },
                        leadingContent = { Icon(Icons.Default.Edit, null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        viewModel.requestDelete(state.songToHandle!!)
                        viewModel.dismissBottomSheet()
                    }
                ){
                    ListItem(
                        headlineContent = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        leadingContent = {
                            Icon(
                                Icons.Default.DeleteOutline,
                                null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }

    if (state.showDeleteDialog) {
        DeleteSongConfirmDialog(
            song = state.songToHandle!!,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete
        )
    }

    if (state.showEditDialog) {
        SongInfoEditDialog(
            "编辑歌曲",
            false,
            state.allTags,
            state.songToHandle!!,
            viewModel::confirmEdit,
            viewModel::cancelEdit
        )
    }
}

@Composable
fun SongItem(
    song: Song,
    isPlaying: Boolean,
    isOrdering: Boolean,
    orderHandle: @Composable () -> Unit,
    playSong: (Song) -> Unit,
    requestBottomSheet: (Song) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isPlaying) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .combinedClickable(
                indication = ripple(),
                onClick = { playSong(song) },
                onLongClick = { requestBottomSheet(song) },
                interactionSource = remember { MutableInteractionSource() },
            )
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            when (val resource = asyncPainterResource(convertImageUrl(song.pic, 128, 128))) {
                is Resource.Loading -> {}
                is Resource.Success -> {
                    val painter: Painter = resource.value
                    Image(
                        painter,
                        contentDescription = song.title,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    )
                }

                is Resource.Failure -> {
                    CoverPlaceholder()
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isOrdering) {
                orderHandle()
            }
        }
    }
}


@Composable
fun ToolBar(
    isOrdering: Boolean,
    switchOrderingMode: () -> Unit,
    mode: TagFilterMode,
    onModeChange: (TagFilterMode) -> Unit,
    tags: List<String>,
    selectedTags: Set<String>,
    onTagToggle: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (expanded) {
                TagFilterModeChips(mode, onModeChange)
            }

            AnimatedVisibility(
                visible = !expanded,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            ){
                TextButton(
                    onClick = { expanded = !expanded },
                ){
                    Text("筛选歌曲")
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "筛选歌曲"
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = slideInHorizontally() + fadeIn(),
                exit = slideOutHorizontally() + fadeOut()
            ){
                TextButton(
                    onClick = { expanded = !expanded },
                ){
                    Text("收起")
                    Icon(
                        imageVector = Icons.Default.ArrowDropUp,
                        contentDescription = "收起"
                    )
                }
            }

            if (!expanded){
                AssistChip(
                    onClick = { switchOrderingMode() },
                    label = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "排序"
                        )
                        Text("排序")
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor =
                            if (isOrdering)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tags.forEach { tag ->
                        TagChip(tag, selectedTags, onTagToggle)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun TagChip(
    tag: String,
    selectedTags: Set<String>,
    onTagToggle: (String) -> Unit
) {
    AssistChip(
        onClick = { onTagToggle(tag) },
        label = { Text(tag) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor =
                if (tag in selectedTags)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface,
            labelColor =
                if (tag in selectedTags)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurface
        )
    )
}


@Composable
fun DeleteSongConfirmDialog(
    song: Song,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("删除歌曲")
        },
        text = {
            Text("确定要删除「${song.title}」吗？")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "删除",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun TagFilterModeChips(
    mode: TagFilterMode,
    onModeChange: (TagFilterMode) -> Unit
) {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(
            onClick = { onModeChange(TagFilterMode.OR) },
            label = { Text("OR") },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (mode == TagFilterMode.OR) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                labelColor = if (mode == TagFilterMode.OR) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        )
        AssistChip(
            onClick = { onModeChange(TagFilterMode.AND) },
            label = { Text("AND") },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (mode == TagFilterMode.AND) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                labelColor = if (mode == TagFilterMode.AND) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        )
    }
}