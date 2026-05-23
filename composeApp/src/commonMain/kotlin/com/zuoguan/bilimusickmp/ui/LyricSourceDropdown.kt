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
import com.zuoguan.bilimusickmp.models.LyricSource
import com.zuoguan.bilimusickmp.models.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricSourceDropdown(
    selectedSource: LyricSource,
    onSourceChange: (LyricSource) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

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
            label = { Text("歌词来源") },
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
            LyricSource.entries
                .filter { it != LyricSource.NONE }
                .forEach { source ->

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