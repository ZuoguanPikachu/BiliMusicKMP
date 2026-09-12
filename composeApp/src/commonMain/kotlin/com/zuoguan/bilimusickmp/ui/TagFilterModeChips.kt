package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zuoguan.bilimusickmp.vm.TagFilterMode

/**
 * 标签筛选模式切换：OR 命中任一标签即可，AND 需同时命中所有标签。
 *
 * @param mode 当前生效的筛选模式。
 * @param onModeChange 切换模式时回调。
 */
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