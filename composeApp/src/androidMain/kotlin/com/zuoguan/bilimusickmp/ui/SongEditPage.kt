package com.zuoguan.bilimusickmp.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.zuoguan.bilimusickmp.models.Song

@Composable
fun SongEditPage(song: Song?) {
    Text(text = song?.title ?: "")
}