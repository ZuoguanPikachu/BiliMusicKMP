package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 数值步进器：加减按钮按 [step] 调整 [value]，结果限制在 [range] 内。
 */
@Composable
fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
    step: Int = 1,
    enabled: Boolean = true
) {
    fun updateValue(current: Int, delta: Long) {
        // 用 Long 运算并夹到 range，避免在 Int 边界附近步进时溢出
        val next = (current.toLong() + delta).coerceIn(
            range.first.toLong(),
            range.last.toLong()
        )
        onValueChange(next.toInt())
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            enabled = enabled,
            onClick = {
                updateValue(value, -step.toLong())
            }
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Decrease"
            )
        }

        Text(text = value.toString(), modifier = Modifier.width(60.dp), textAlign = TextAlign.Center)

        IconButton(
            enabled = enabled,
            onClick = {
                updateValue(value, step.toLong())
            }
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Increase"
            )
        }
    }
}