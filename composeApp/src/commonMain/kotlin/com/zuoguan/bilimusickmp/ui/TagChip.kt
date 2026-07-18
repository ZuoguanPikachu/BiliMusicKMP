package com.zuoguan.bilimusickmp.ui

import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

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