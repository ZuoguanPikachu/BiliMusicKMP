package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zuoguan.bilimusickmp.utils.convertImageUrl
import io.kamel.core.Resource
import io.kamel.image.asyncPainterResource

/**
 * 歌曲封面预览框：按封面 URL 异步加载并展示封面。
 *
 * 加载中显示占位图并叠加进度圈，URL 为空或加载失败时只显示占位图。
 *
 * @param pic 封面原始 URL，为空表示没有封面。
 * @param title 用作图片的无障碍描述。
 * @param size 预览框边长，同时决定占位图尺寸。
 * @param radius 圆角半径。
 */
@Composable
fun CoverPreviewBox(
    pic: String,
    title: String,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
    radius: Dp = 12.dp
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .size(size)
            .shadow(2.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            pic.isBlank() -> CoverPlaceholder(size = size, radius = radius)
            else -> when (val resource = asyncPainterResource(convertImageUrl(pic, 320, 320))) {
                is Resource.Loading -> {
                    CoverPlaceholder(size = size, radius = radius)
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp
                    )
                }
                is Resource.Success -> Image(
                    painter = resource.value,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                is Resource.Failure -> CoverPlaceholder(size = size, radius = radius)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
        )
    }
}