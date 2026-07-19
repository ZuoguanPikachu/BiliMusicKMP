package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.CoverSource
import com.zuoguan.bilimusickmp.models.LyricSource
import com.zuoguan.bilimusickmp.models.MetadataSource
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

            var songId = ""
            var pic = song.pic
            song.coverSource = CoverSource.BILI_BILI
            if (songBaseInfo.title.isNotEmpty() && songBaseInfo.author.isNotEmpty()) {
                songId = kuGouService.getIdByTitleAndAuthor(title, author)
                if (songId.isNotEmpty()){
                    pic = kuGouService.getImageUrl(songId)
                    song.coverSource = CoverSource.KU_GOU
                }
            }

            Song().apply {
                id = song.id
                audioSource = song.audioSource
                this.title = title
                this.author = author
                this.lyricSource = LyricSource.KU_GOU
                this.lyricId = songId
                this.coverSource = song.coverSource
                this.coverId = songId
                this.pic = pic
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

    suspend fun resolveSongId(
        source: MetadataSource,
        title: String,
        author: String
    ): String {
        return when(source.label){
            "酷狗音乐" -> kuGouService.getIdByTitleAndAuthor(title, author)
            "网易云音乐" -> netEaseService.getIdByTitleAndAuthor(title, author)
            else -> ""
        }
    }

    fun resolvePic(
        source: CoverSource,
        id: String
    ): String{
        return when(source){
            CoverSource.KU_GOU -> kuGouService.getImageUrl(id)
            CoverSource.NET_EASE -> netEaseService.getImageUrl(id)
            else -> ""
        }
    }
}