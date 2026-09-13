package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** 距列表末尾还剩这么多条时就开始加载下一页，避免用户真的滑到底才等。 */
private const val LOAD_MORE_THRESHOLD = 3

/**
 * 列表滚动到接近底部时回调 [onLoadMore]，配合 ViewModel 的 loadMore() 实现触底加载。
 *
 * 首屏不足一屏时同样会触发，因此去重放在 ViewModel 里（isLoadingMore / endReached）。
 *
 * @param enabled 为 false 时不触发：首屏加载中、正在加载下一页、已经到底都应传 false。
 * @param threshold 距末尾多少条内算"接近底部"。
 */
@Composable
fun LazyListState.LoadMoreOnReachBottom(
    enabled: Boolean,
    onLoadMore: () -> Unit,
    threshold: Int = LOAD_MORE_THRESHOLD
) {
    LoadMoreOnReachBottomEffect(
        key = this,
        enabled = enabled,
        threshold = threshold,
        isNearBottom = { isNearBottom(threshold) },
        onLoadMore = onLoadMore
    )
}

/** [LazyListState.LoadMoreOnReachBottom] 的栅格版本。 */
@Composable
fun LazyGridState.LoadMoreOnReachBottom(
    enabled: Boolean,
    onLoadMore: () -> Unit,
    threshold: Int = LOAD_MORE_THRESHOLD
) {
    LoadMoreOnReachBottomEffect(
        key = this,
        enabled = enabled,
        threshold = threshold,
        isNearBottom = { isNearBottom(threshold) },
        onLoadMore = onLoadMore
    )
}

/**
 * 触底检测的公共实现：只在"接近底部"这个判断由 false 变 true（以及重新进入组合）时回调一次，
 * 因此不会因为滚动过程中的每一帧而重复触发。
 */
@Composable
private fun LoadMoreOnReachBottomEffect(
    key: Any,
    enabled: Boolean,
    threshold: Int,
    isNearBottom: () -> Boolean,
    onLoadMore: () -> Unit
) {
    // 回调每次重组都是新对象，用 rememberUpdatedState 避免它把 LaunchedEffect 重启掉
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)

    LaunchedEffect(key, enabled, threshold) {
        if (!enabled) return@LaunchedEffect

        snapshotFlow(isNearBottom)
            .distinctUntilChanged()
            .filter { it }
            .collect { currentOnLoadMore() }
    }
}

/** 列表（网格）是否已经滚到接近末尾；没有可见项或干脆没有数据时为 false。 */
private fun LazyListState.isNearBottom(threshold: Int): Boolean {
    val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return false
    return lastVisibleIndex >= layoutInfo.totalItemsCount - 1 - threshold
}

/** [isNearBottom] 的栅格版本。 */
private fun LazyGridState.isNearBottom(threshold: Int): Boolean {
    val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return false
    return lastVisibleIndex >= layoutInfo.totalItemsCount - 1 - threshold
}

/**
 * 搜索结果列表底部状态：正在加载下一页时显示转圈，已经到底时提示"没有更多了"。
 *
 * 两个状态都不成立时什么都不画，调用方可以只在需要时把它加进列表。
 */
@Composable
fun SearchLoadMoreFooter(
    isLoadingMore: Boolean,
    endReached: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isLoadingMore && !endReached) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoadingMore) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = "没有更多了",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
