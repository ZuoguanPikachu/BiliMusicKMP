package com.zuoguan.bilimusickmp.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 歌词列表中的单行：当前行放大、加粗并染上主色，其余行淡化显示。
 *
 * 颜色与透明度用动画过渡：播放位置推进到下一句时，高亮是「移过去」而不是「跳过去」。
 * 字号与字重不做动画——它们会触发整行重新排版，逐帧测量在长歌词里不划算。
 *
 * @param text 该行歌词文本。
 * @param isCurrent 是否为当前播放到的那一行。
 * @param onClick 点击整行时的回调。
 */
@Composable
fun LyricLineItem(
    text: String,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val color by animateColorAsState(
        targetValue = if (isCurrent) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 220),
        label = "lyricColor"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0.45f,
        animationSpec = tween(durationMillis = 220),
        label = "lyricAlpha"
    )

    Surface(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                // 长句留出一点边距，满宽居中会显得散
                modifier = Modifier.fillMaxWidth(0.86f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = if (isCurrent) 20.sp else 15.sp,
                    lineHeight = if (isCurrent) 30.sp else 24.sp,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = color.copy(alpha = alpha),
            )
        }
    }
}
