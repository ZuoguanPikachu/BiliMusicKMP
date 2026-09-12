package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.CoverSource
import com.zuoguan.bilimusickmp.models.LyricSource
import com.zuoguan.bilimusickmp.models.MetadataSource
import com.zuoguan.bilimusickmp.models.Song
import com.zuoguan.bilimusickmp.models.SongBaseInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 把收藏与搜索结果补全成可播放的歌曲元数据。
 *
 * 标题来自视频标题，通常混着前缀、番号和活动标签，因此先交给 LLM 抽取歌名与歌手，
 * 再用「歌名 + 歌手」反查歌曲 id（自动匹配走酷狗，编辑页可指定平台），
 * 最后用这个 id 同时换封面与歌词。
 *
 * 每一段匹配都可能失败：失败时保留原始信息或回落到 B 站数据，保证歌曲仍然可播，
 * 不会因为第三方平台取不到数据而让整首歌失效。
 */
class SongMetadataService(
    private val extractSongBaseInfoService: ExtractSongBaseInfoService,
    private val biliService: BiliService,
    private val kuGouService: KuGouService,
    private val netEaseService: NetEaseService,
) {
    /**
     * 补全一首歌的元数据：LLM 抽取与酷狗匹配、B 站 cid 查询并行执行，最后合并成一条 [Song]。
     *
     * 两个任务互不依赖，并发可以把总耗时压到较慢的那一个；任一环节失败都只影响它自己的字段。
     */
    suspend fun resolve(song: Song): Song = coroutineScope {
        val songDeferred = async {
            // LLM 未配置或调用失败时退化为空信息，下面会回落到原始标题
            val songBaseInfo = try {
                extractSongBaseInfoService.extractInfo(song.title)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SongBaseInfo()
            }

            // 抽不出歌名就沿用原始标题；抽不出歌手则留空
            val title = songBaseInfo.title.ifEmpty { song.title }
            val author = songBaseInfo.author

            var matchedSongId = ""
            var pic = song.pic
            var coverSource = CoverSource.BILI_BILI
            // 只有歌名和歌手都有才去酷狗匹配：只凭歌名很容易配到翻唱或同名曲
            if (songBaseInfo.title.isNotEmpty() && songBaseInfo.author.isNotEmpty()) {
                matchedSongId = kuGouService.getIdByTitleAndAuthor(title, author)
                if (matchedSongId.isNotEmpty()) {
                    // 匹配到酷狗歌曲就换成酷狗封面；换封面失败仍保留 B 站原封面
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

        // 等两条支线都完成再合并：歌名、封面来自酷狗匹配，cid 来自 B 站
        songDeferred.await().copy(cid = cidDeferred.await())
    }

    /**
     * 按歌名与歌手在指定平台反查歌曲 id。
     *
     * @param source 目标平台，由封面或歌词来源决定。
     * @return 标题为空或平台不支持时返回空串，调用方据此跳过后续匹配。
     */
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

    /**
     * 取指定平台某首歌曲的封面地址。
     *
     * @return id 为空、平台不支持或请求失败时返回空串（界面按无封面处理）。
     */
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