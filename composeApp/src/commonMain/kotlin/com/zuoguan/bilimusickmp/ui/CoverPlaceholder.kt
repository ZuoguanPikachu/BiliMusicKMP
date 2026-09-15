package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 无封面时的占位图：圆角底板加一个音符图标。
 *
 * @param size 占位块边长，图标按一半边长缩放；调用方用 [modifier] 撑满容器时该值会被容器约束覆盖。
 * @param radius 圆角半径。
 * @param modifier 作用于占位块本身，用于撑满固定比例的封面槽（如搜索结果卡片）。
 */
@Composable
fun CoverPlaceholder(
    size: Dp = 48.dp,
    radius: Dp = 6.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = "No Cover",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size / 2)
        )
    }
}