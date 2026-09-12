package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.CoverSource
import com.zuoguan.bilimusickmp.models.LyricSource
import com.zuoguan.bilimusickmp.models.MetadataSource
import com.zuoguan.bilimusickmp.models.Song
import com.zuoguan.bilimusickmp.models.SongBaseInfo
import kotlinx.coroutines.CancellationException
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
            val songBaseInfo = try {
                extractSongBaseInfoService.extractInfo(song.title)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SongBaseInfo()
            }

            val title = songBaseInfo.title.ifEmpty { song.title }
            val author = songBaseInfo.author

            var matchedSongId = ""
            var pic = song.pic
            var coverSource = CoverSource.BILI_BILI
            if (songBaseInfo.title.isNotEmpty() && songBaseInfo.author.isNotEmpty()) {
                matchedSongId = kuGouService.getIdByTitleAndAuthor(title, author)
                if (matchedSongId.isNotEmpty()) {
                    pic = try {
                        kuGouService.getImageUrl(matchedSongId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        song.pic
                    }
                    coverSource = CoverSource.KU_GOU
                }
            }

            Song(
                id = song.id,
                audioSource = song.audioSource,
                title = title,
                author = author,
                lyricSource = LyricSource.KU_GOU,
                lyricId = matchedSongId,
                coverSource = coverSource,
                coverId = matchedSongId,
                pic = pic,
                ts = song.ts
            )
        }

        val cidDeferred = async {
            biliService.getCid(song.id)
        }

        songDeferred.await().copy(cid = cidDeferred.await())
    }

    suspend fun resolveSongId(
        source: MetadataSource,
        title: String,
        author: String
    ): String {
        if (title.isEmpty()) return ""
        return when(source){
            CoverSource.KU_GOU, LyricSource.KU_GOU -> kuGouService.getIdByTitleAndAuthor(title, author)
            CoverSource.NET_EASE, LyricSource.NET_EASE -> netEaseService.getIdByTitleAndAuthor(title, author)
            else -> ""
        }
    }

    suspend fun resolvePic(
        source: CoverSource,
        id: String
    ): String{
        if (id.isEmpty()) return ""
        return try {
            when(source){
                CoverSource.KU_GOU -> kuGouService.getImageUrl(id)
                CoverSource.NET_EASE -> netEaseService.getImageUrl(id)
                else -> ""
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
    }
}