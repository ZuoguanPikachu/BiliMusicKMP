package com.zuoguan.bilimusickmp.models

/**
 * 从原始投稿标题中提取出的基础信息，用于按歌名 / 歌手去匹配其他平台的歌词与封面。
 *
 * 字段为空表示未提取到，调用方应回退到原始标题。
 */
data class SongBaseInfo(
    val title: String = "",
    val author: String = ""
)