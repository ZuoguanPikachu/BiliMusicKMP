package com.zuoguan.bilimusickmp.ui

import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * 可选中的标签 chip，选中与未选中使用不同配色。
 *
 * @param tag 标签文本，同时作为点击回调的参数。
 * @param isSelected 是否处于选中状态。
 * @param onTagToggle 点击 chip 时回调，携带自身标签。
 */
@Composable
fun TagChip(
    tag: String,
    isSelected: Boolean,
    onTagToggle: (String) -> Unit
) {
    AssistChip(
        onClick = { onTagToggle(tag) },
        label = { Text(tag) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor =
                if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface,
            labelColor =
                if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurface
        )
    )
}