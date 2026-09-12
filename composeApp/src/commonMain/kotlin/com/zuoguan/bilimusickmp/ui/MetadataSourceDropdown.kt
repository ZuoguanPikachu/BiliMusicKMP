package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.zuoguan.bilimusickmp.models.CoverSource
import com.zuoguan.bilimusickmp.models.LyricSource
import com.zuoguan.bilimusickmp.models.MetadataSource

/**
 * 元数据来源下拉选择器，同一个组件同时服务歌词来源与封面来源。
 *
 * @param sources 可选的来源列表，通常是 `LyricSource.entries` 或 `CoverSource.entries`。
 * @param selectedSource 当前选中的来源，显示在只读输入框中。
 * @param onSourceChange 选择来源时回调，由调用方决定是否触发重新解析。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : MetadataSource> MetadataSourceDropdown(
    sources: List<T>,
    selectedSource: T,
    onSourceChange: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    // 「无」这一项只在它已是当前选中项时展示，避免用户主动切到空来源
    val selectable = sources.filter { !it.isNone || it == selectedSource }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            expanded = !expanded
        },
        modifier = modifier.fillMaxWidth()
    ) {

        OutlinedTextField(
            value = selectedSource.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("来源") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded)
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            }
        ) {
            selectable.forEach { source ->

                    DropdownMenuItem(
                        text = {
                            Text(source.label)
                        },
                        onClick = {
                            onSourceChange(source)
                            expanded = false
                        }
                    )
                }
        }
    }
}