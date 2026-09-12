package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp


@Composable
fun TagsEditor(
    selectedTags: List<String>,
    allTags: List<String>,
    newTagText: String,
    onNewTagTextChange: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    fun submitTag(raw: String) {
        val text = raw.trim()
        if (text.isBlank()) return
        onAddTag(text)
        onNewTagTextChange("")
    }

    Column(modifier) {
        OutlinedTextField(
            value = newTagText,
            onValueChange = onNewTagTextChange,
            label = { Text("添加标签") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { submitTag(newTagText) }
            ),
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (newTagText.isNotBlank()) {
                    IconButton(
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Default),
                        onClick = { submitTag(newTagText) }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加")
                    }
                }
            }
        )

        Spacer(Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            allTags.distinct().forEach { tag ->
                TagChip(tag, tag in selectedTags, onToggleTag)
            }
        }
    }
}
