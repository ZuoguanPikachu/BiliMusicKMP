package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.zuoguan.bilimusickmp.models.AudioSource
import com.zuoguan.bilimusickmp.models.label

/**
 * 搜索音源平台的下拉选择器，按钮上显示当前平台的名称。
 *
 * @param selectedSource 当前选中的音源平台。
 * @param onSourceChange 选择平台时回调。
 */
@Composable
fun SearchSourceDropdown(
    selectedSource: AudioSource,
    onSourceChange: (AudioSource) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        TextButton(
            onClick = { expanded = true }
        ) {
            Text(text = selectedSource.label)
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "选择平台"
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            AudioSource.entries.forEach { source ->
                DropdownMenuItem(
                    text = { Text(source.label) },
                    onClick = {
                        onSourceChange(source)
                        expanded = false
                    }
                )
            }
        }
    }
}