package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** 歌词延时每次调整的步长，单位毫秒。 */
private const val BIAS_STEP_MS = 100

/**
 * 歌词延时胶囊：减、当前值、加，再跟一个保存按钮。
 *
 * 延时按毫秒显示并带正负号，比裸数字更容易判断是「歌词快了」还是「慢了」；
 * 保存按钮只在草稿与已保存值不一致时点亮，避免用户为了确认状态反复点。
 *
 * @param bias 当前生效的延时（毫秒），正数表示歌词往后推。
 * @param onBiasChange 延时改动回调。
 * @param isDirty 延时草稿与已保存的值不一致时为 true，此时保存按钮才可点。
 * @param compact 窄栏模式：省掉「歌词延时」文字，保存只留图标。
 */
@Composable
fun LyricsControlBar(
    bias: Int,
    onBiasChange: (Int) -> Unit,
    isDirty: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = if (compact) 10.dp else 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (compact) {
                // 窄栏省掉文字，只留图标，仍然看得出来这组按钮是调延时
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "歌词延时",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.width(2.dp))
            } else {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.width(6.dp))

                Text(
                    text = "歌词延时",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.width(10.dp))
            }

            IconButton(
                onClick = { onBiasChange(bias - BIAS_STEP_MS) },
                modifier = Modifier.size(if (compact) 40.dp else 48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "延时减 $BIAS_STEP_MS 毫秒",
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = formatBias(bias),
                modifier = Modifier.width(if (compact) 56.dp else 72.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge
            )

            IconButton(
                onClick = { onBiasChange(bias + BIAS_STEP_MS) },
                modifier = Modifier.size(if (compact) 40.dp else 48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "延时加 $BIAS_STEP_MS 毫秒",
                    modifier = Modifier.size(18.dp)
                )
            }

            VerticalDivider(
                modifier = Modifier
                    .height(20.dp)
                    .padding(horizontal = if (compact) 2.dp else 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            if (compact) {
                FilledTonalIconButton(
                    onClick = onSave,
                    enabled = isDirty,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "保存歌词延时",
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = onSave,
                    enabled = isDirty,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("保存")
                }
            }
        }
    }
}

/**
 * 「重新加载歌词」按钮：带文字的描边按钮，和延时胶囊分开摆。
 *
 * 拉歌词是个低频操作，只放一个旋转箭头图标容易和「重播」混淆，
 * 所以宁可多占点宽度，把动作写清楚。
 *
 * @param isLoading 正在拉取时按钮禁用并显示转圈。
 */
@Composable
fun ReloadLyricsButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(8.dp))

        Text("重新加载歌词")
    }
}

/** 把延时格式化成 `+100ms` / `-2ms` 这样的文本；0 不带正号。 */
private fun formatBias(bias: Int): String =
    if (bias > 0) "+${bias}ms" else "${bias}ms"
