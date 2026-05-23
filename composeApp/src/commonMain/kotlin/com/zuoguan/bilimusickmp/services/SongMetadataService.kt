package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.LyricSource
import com.zuoguan.bilimusickmp.models.Song
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class SongMetadataService(
    private val extractSongBaseInfoService: ExtractSongBaseInfoService,
    private val biliService: BiliService,
    private val kuGouService: KuGouService,
    private val netEaseService: NetEaseService,
) {
    suspend fun resolve(song: Song): Song = coroutineScope {
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

    suspend fun resolveLyricId(
        source: LyricSource,
        title: String,
        author: String
    ): String {
        return when(source){
            LyricSource.KU_GOU -> kuGouService.getIdByTitleAndAuthor(title, author)
            LyricSource.NET_EASE -> netEaseService.getIdByTitleAndAuthor(title, author)
            else -> ""
        }
    }

    suspend fun resolvePic(
        source: LyricSource,
        id: String
    ): String{
        return when(source){
            LyricSource.KU_GOU -> kuGouService.getImageUrl(id)
            LyricSource.NET_EASE -> netEaseService.getImageUrl(id)
            else -> ""
        }
    }
}