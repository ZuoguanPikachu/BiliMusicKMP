package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LyricLineItem(
    text: String,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        onClick = onClick,
    ){
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ){
            Text(
                text = text,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = if (isCurrent) 20.sp else 16.sp,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (isCurrent) 1f else 0.5f
                ),
            )
        }
    }
}