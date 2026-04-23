package com.zuoguan.bilimusickmp.models

data class SearchResult(
    val id: String,
    val title: String,
    val author: String,
    val pic: String,
    val duration: String,
    val audioSource: AudioSource
)