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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
    step: Int = 1,
    enabled: Boolean = true
) {
    var textValue by remember(value) {
        mutableStateOf(value.toString())
    }

    fun updateValue(newValue: Int) {
        val result = newValue.coerceIn(
            range.first,
            range.last
        )

        textValue = result.toString()
        onValueChange(result)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            enabled = enabled,
            onClick = {
                updateValue(value - step)
            }
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Decrease"
            )
        }

        Text(text = textValue, modifier = Modifier.width(60.dp), textAlign = TextAlign.Center)

        IconButton(
            enabled = enabled,
            onClick = {
                updateValue(value + step)
            }
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Increase"
            )
        }
    }
}