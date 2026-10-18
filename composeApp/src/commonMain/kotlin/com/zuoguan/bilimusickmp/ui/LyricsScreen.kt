package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zuoguan.bilimusickmp.utils.convertImageUrl
import com.zuoguan.bilimusickmp.vm.LyricsUiState
import io.kamel.core.Resource
import io.kamel.image.asyncPainterResource

/** 宽窄屏分界：够宽时左边放封面、右边放歌词，窄屏则上下排布。 */
private val WideLayoutThreshold = 960.dp

/** 底部工具行宽度小于该值时，延时胶囊改用精简排布。 */
private val CompactBarThreshold = 560.dp

/** 歌词列表上下淡出区域的高度。 */
private val FadeEdgeHeight = 56.dp

/**
 * 歌词页自带的播放控制。
 *
 * 只有隐藏了底部播放栏的平台（Android）才需要传：歌词页是它唯一能看到当前曲目的地方，
 * 没有这两个按钮就只能暂停、不能换歌。
 *
 * @param isPlaying 当前是否在播放，决定播放键显示暂停还是播放图标。
 */
data class LyricsTransportControls(
    val isPlaying: Boolean,
    val onTogglePlay: () -> Unit,
    val onPlayNext: () -> Unit,
)

/**
 * 歌词页主体（各平台共用）：封面与曲目信息 + 逐行歌词 + 底部工具行。
 *
 * 按可用宽度切换两套排布：宽屏左右分栏，窄屏是「大封面在上、歌词在下」的竖版。
 * 高亮行由播放进度与延时共同推导，列表随后滚到该行并留出半屏空白，使高亮行落在垂直中间。
 *
 * 本页没有 Material 容器，`LocalContentColor` 还停在默认的黑色，
 * 深色主题下歌名会变成黑底黑字，所以这里显式把内容色接到 onBackground。
 *
 * @param header 页面顶部插槽，Android 用来放返回按钮；桌面端不传。
 * @param transport 非空时底部多一排播放控制；桌面端有底部播放栏，传 null。
 */
@Composable
fun LyricsScreen(
    state: LyricsUiState,
    onSeek: (Long) -> Unit,
    onBiasChange: (Int) -> Unit,
    onSaveBias: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    transport: LyricsTransportControls? = null,
) {
    val listState = rememberLazyListState()
    val bias = state.lyricBias
    // bias 也参与计算：调整延时后应当立刻重新高亮
    val currentIndex = remember(state.currentPositionMs, state.lyrics, bias) {
        state.lyrics.indexOfLast { it.timeMs + bias <= state.currentPositionMs }
    }

    LaunchedEffect(currentIndex) {
        // -1 表示还没有任何一句开始
        if (currentIndex < 0) return@LaunchedEffect
        // 用户正在手动滚动时不要抢滚动位置
        if (listState.isScrollInProgress) return@LaunchedEffect
        listState.animateScrollToItem(currentIndex)
    }

    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onBackground
    ) {
        BoxWithConstraints(modifier.fillMaxSize()) {
            val isWide = maxWidth >= WideLayoutThreshold

            Column(Modifier.fillMaxSize()) {
                header()

                if (isWide) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp, vertical = 24.dp)
                    ) {
                        NowPlayingLarge(
                            state = state,
                            modifier = Modifier.weight(0.42f).fillMaxHeight()
                        )

                        Spacer(Modifier.width(48.dp))

                        LyricsPanel(
                            state = state,
                            listState = listState,
                            currentIndex = currentIndex,
                            onSeek = onSeek,
                            onBiasChange = onBiasChange,
                            onSaveBias = onSaveBias,
                            onRefresh = onRefresh,
                            transport = transport,
                            modifier = Modifier.weight(0.58f).fillMaxHeight()
                        )
                    }
                } else {
                    // 上下各留一点空白：手机端封面贴着顶部返回键、换歌按钮贴着底部导航栏都很局促
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 10.dp)
                    ) {
                        NowPlayingCompact(
                            state = state,
                            modifier = Modifier.fillMaxWidth()
                        )

                        LyricsPanel(
                            state = state,
                            listState = listState,
                            currentIndex = currentIndex,
                            onSeek = onSeek,
                            onBiasChange = onBiasChange,
                            onSaveBias = onSaveBias,
                            onRefresh = onRefresh,
                            transport = transport,
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/** 宽屏左侧：居中的大封面与曲目信息。封面边长跟着栏宽走，窗口变窄时不会顶破布局。 */
@Composable
private fun NowPlayingLarge(
    state: LyricsUiState,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val coverSize = minOf(maxWidth - 64.dp, 360.dp)

        TrackInfoColumn(
            state = state,
            coverSize = coverSize,
            corner = 16.dp,
            titleStyle = MaterialTheme.typography.headlineSmall,
            titleMaxLines = 2,
            spacingAfterCover = 28.dp
        )
    }
}

/** 窄屏顶部：大封面居中，下面是歌名与歌手，再往下才是歌词。 */
@Composable
private fun NowPlayingCompact(
    state: LyricsUiState,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 三个上限取最小：屏宽占比、绝对上限、剩余高度占比。
        // 矮屏（横屏手机、小屏机）上封面会自己缩小，不会把歌词和工具行挤没
        val coverSize = minOf(maxWidth * 0.62f, 280.dp, maxHeight * 0.30f)

        TrackInfoColumn(
            state = state,
            coverSize = coverSize,
            corner = 14.dp,
            titleStyle = MaterialTheme.typography.titleLarge,
            titleMaxLines = 2,
            spacingAfterCover = 16.dp,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        )
    }
}

/** 封面 + 歌名 + 歌手；宽窄两套排布只差尺寸与字号。 */
@Composable
private fun TrackInfoColumn(
    state: LyricsUiState,
    coverSize: Dp,
    corner: Dp,
    titleStyle: TextStyle,
    titleMaxLines: Int,
    spacingAfterCover: Dp,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val track = state.currentTrack

    Column(
        modifier = Modifier.padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CoverArt(pic = track?.pic, size = coverSize, corner = corner)

        Spacer(Modifier.height(spacingAfterCover))

        Text(
            text = track?.title ?: "还没有播放歌曲",
            modifier = Modifier.fillMaxWidth(0.92f),
            style = titleStyle,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = titleMaxLines,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = track?.author ?: "从歌单里挑一首开始",
            modifier = Modifier.fillMaxWidth(0.92f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 歌词列表与底部工具行。
 *
 * 列表上下各留半屏空白，配合滚动把高亮行钉在中间；边缘盖一层由页面底色淡出的渐变，
 * 让人看清列表还在继续，而不是被硬生生裁断。
 *
 * 底部两行：上面是「重新加载歌词」，下面是「播放/暂停 + 歌词延时胶囊 + 下一曲」。
 * 拉歌词是低频操作，单独放上面；调延时居中，播放控制分列左右，一只手就能同时够到。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricsPanel(
    state: LyricsUiState,
    listState: LazyListState,
    currentIndex: Int,
    onSeek: (Long) -> Unit,
    onBiasChange: (Int) -> Unit,
    onSaveBias: () -> Unit,
    onRefresh: () -> Unit,
    transport: LyricsTransportControls?,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        // 工具行自己看栏宽决定要不要精简：左右分栏时右栏可能比整窗窄得多
        val compactBar = maxWidth < CompactBarThreshold

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                // 上下留白各占半屏，减去半行高度，高亮行才正好落在正中
                val verticalPadding = (maxHeight / 2 - 30.dp).coerceAtLeast(16.dp)

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(vertical = verticalPadding)
                ) {
                    itemsIndexed(state.lyrics) { index, line ->
                        LyricLineItem(
                            text = line.text,
                            isCurrent = index == currentIndex,
                            onClick = { onSeek(line.timeMs + state.lyricBias) }
                        )
                    }
                }

                EdgeFades()

                when {
                    state.isLoading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )

                    state.lyrics.isEmpty() -> Text(
                        text = if (state.currentTrack == null) "歌词会显示在这里" else "暂无歌词",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.currentTrack != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReloadLyricsButton(
                        isLoading = state.isLoading,
                        onClick = onRefresh,
                    )

                    // 播放/暂停在胶囊左侧、下一曲在右侧；窄到放不下时整行才折行
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(
                            if (compactBar) 6.dp else 8.dp,
                            Alignment.CenterHorizontally
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (transport != null) {
                            FilledIconButton(
                                onClick = transport.onTogglePlay,
                                modifier = Modifier.size(if (compactBar) 40.dp else 44.dp)
                            ) {
                                Icon(
                                    imageVector = if (transport.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (transport.isPlaying) "暂停" else "播放",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        LyricsControlBar(
                            bias = state.lyricBias,
                            onBiasChange = onBiasChange,
                            isDirty = state.isLyricBiasDirty,
                            onSave = onSaveBias,
                            compact = compactBar,
                        )

                        if (transport != null) {
                            FilledTonalIconButton(
                                onClick = transport.onPlayNext,
                                modifier = Modifier.size(if (compactBar) 40.dp else 44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "下一首",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 列表上下两条往页面底色淡出的遮罩；没有指针输入，不会挡住点击。 */
@Composable
private fun BoxScope.EdgeFades() {
    val background = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(FadeEdgeHeight)
            .background(
                Brush.verticalGradient(
                    colors = listOf(background, background.copy(alpha = 0f))
                )
            )
    )

    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(FadeEdgeHeight)
            .background(
                Brush.verticalGradient(
                    colors = listOf(background.copy(alpha = 0f), background)
                )
            )
    )
}

/** 封面：取不到图时退化成占位块，不留下空白。 */
@Composable
private fun CoverArt(pic: String?, size: Dp, corner: Dp) {
    val shape = RoundedCornerShape(corner)

    when (val resource = asyncPainterResource(convertImageUrl(pic ?: "", 512, 512))) {
        is Resource.Loading, is Resource.Failure -> {
            CoverPlaceholder(size = size, radius = corner)
        }

        is Resource.Success -> {
            Image(
                painter = resource.value,
                contentDescription = "封面",
                modifier = Modifier
                    .size(size)
                    .shadow(elevation = 12.dp, shape = shape)
                    .clip(shape)
            )
        }
    }
}
