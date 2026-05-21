package com.zuoguan.bilimusickmp.utils

import com.zuoguan.bilimusickmp.models.LyricSource
import com.zuoguan.bilimusickmp.models.Song
import com.zuoguan.bilimusickmp.services.BiliService
import com.zuoguan.bilimusickmp.services.ExtractSongBaseInfoService
import com.zuoguan.bilimusickmp.services.KuGouService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.text.ifEmpty

suspend fun enrichSongInfo(
    extractSongBaseInfoService: ExtractSongBaseInfoService,
    biliService: BiliService,
    kuGouService: KuGouService,
    song: Song
): Song = coroutineScope {
    val songDeferred = async {
        val songBaseInfo = extractSongBaseInfoService.extractInfo(song.title)
        val title = songBaseInfo.title.ifEmpty { song.title }
        val author = songBaseInfo.author

        var lyricId = ""
        var pic = song.pic
        if (songBaseInfo.title.isNotEmpty() && songBaseInfo.author.isNotEmpty()) {
            lyricId = kuGouService.getIdByTitleAndAuthor(title, author)
            if (lyricId.isNotEmpty()){
                pic = kuGouService.getImageUrl(lyricId)
            }
        }

        Song().apply {
            id = song.id
            audioSource = song.audioSource
            this.title = title
            this.author = author
            this.pic = pic
            this.lyricSource = LyricSource.KU_GOU
            this.lyricId = lyricId
            ts = song.ts
        }
    }

    val cidDeferred = async {
        biliService.getCid(song.id)
    }

    songDeferred.await().apply {
        cid = cidDeferred.await()
    }
}