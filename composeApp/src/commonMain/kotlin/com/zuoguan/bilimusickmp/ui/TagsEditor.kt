package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup


@Composable
fun TagsEditor(
    tags: List<String>,
    allTags: List<String>,
    newTagText: String,
    onNewTagTextChange: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions = remember(newTagText, tags, allTags) {
        if (newTagText.isBlank()) {
            emptyList()
        } else {
            allTags
                .filter { it.contains(newTagText, ignoreCase = true) }
                .filterNot { it in tags }
                .take(3)
        }
    }

    Column(modifier) {
        var textFieldWidthPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current

        TextField(
            value = newTagText,
            onValueChange = onNewTagTextChange,
            label = { Text("添加标签") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    val text = newTagText.trim()
                    if (text.isNotBlank()) {
                        onAddTag(text)
                    }
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    textFieldWidthPx = coordinates.size.width
                },
            trailingIcon = {
                val text = newTagText.trim()
                if (text.isNotBlank()) {
                    IconButton(
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Default),
                        onClick = { onAddTag(text) }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加")
                    }
                }
            }
        )

        if (suggestions.isNotEmpty()) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, 60)
            ) {
                Card(
                    modifier = Modifier.width(
                        with(density) { textFieldWidthPx.toDp() }
                    )
                ) {
                    Column {
                        suggestions.forEach { tag ->
                            ListItem(
                                headlineContent = { Text(tag) },
                                modifier = Modifier.clickable {
                                    onAddTag(tag)
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (tags.isEmpty()) {
                AssistChip(
                    onClick = {},
                    label = { Text("Default") }
                )
            }

            tags.forEach { tag ->
                AssistChip(
                    onClick = {},
                    label = { Text(tag) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove tag",
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onRemoveTag(tag) }
                        )
                    }
                )
            }
        }
    }
}