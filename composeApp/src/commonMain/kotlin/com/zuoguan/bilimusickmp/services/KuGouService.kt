package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.AudioSource
import com.zuoguan.bilimusickmp.models.LyricLine
import com.zuoguan.bilimusickmp.models.SearchResult
import com.zuoguan.bilimusickmp.models.SearchResultPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64

/**
 * 酷狗移动端接口封装：搜索、封面、歌词与音频直链。
 *
 * 各接口分散在不同域名（搜索在 mobilecdn、封面在 m.kugou.com、歌词在 krcs/lyrics、
 * 直链在 trackercdn），请求都要带上伪装成 iPhone 客户端的固定请求头。
 */
class KuGouService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // 模拟酷狗 iPhone 客户端的 UA：接口会按 UA 下发不同的字段与播放权限
    private val defaultHeaders = mapOf(
        "User-Agent" to "IPhone-8990-searchSong",
        "UNI-UserAgent" to "iOS11.4-Phone8990-1009-0-WiFi"
    )

    private suspend fun getText(url: HttpUrl): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .apply { defaultHeaders.forEach { (k, v) -> addHeader(k, v) } }
            .build()

        client.newCall(request).execute().use { it.body.string() }
    }

    private suspend fun getJson(url: HttpUrl): JsonObject =
        Json.parseToJsonElement(getText(url)).jsonObject

    /**
     * 搜索歌曲。
     *
     * @param pageSize 每页条数，直接透传给接口的 pagesize。
     * @param page 页码，从 1 开始。
     * @return 解析不出 hash 的条目会被丢弃；接口无结果时返回空页而不是抛异常，
     *   并按接口给的 total 判断还有没有下一页。
     */
    suspend fun search(keyword: String, pageSize: Int = 10, page: Int = 1): SearchResultPage {
        val url = HttpUrl.Builder()
            .scheme("http")
            .host("mobilecdn.kugou.com")
            .addPathSegments("api/v3/search/song")
            .addQueryParameter("api_ver", "1")
            .addQueryParameter("area_code", "1")
            .addQueryParameter("correct", "1")
            .addQueryParameter("pagesize", pageSize.toString())
            .addQueryParameter("plat", "2")
            .addQueryParameter("tag", "1")
            .addQueryParameter("sver", "5")
            .addQueryParameter("showtype", "10")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("keyword", keyword)
            .addQueryParameter("version", "8990")
            .build()

        // 接口在无结果/被限流时不会返回 data.info，这里统一兜底为空页
        val data = getJson(url).get("data")?.jsonObject
            ?: return SearchResultPage(emptyList(), hasMore = false)
        val songs = data.get("info")?.jsonArray
            ?: return SearchResultPage(emptyList(), hasMore = false)

        // 封面需要逐首再查一次接口，串行会明显拖慢搜索，这里并发获取
        val items = coroutineScope {
            songs.map { item ->
                async {
                    val song = item.jsonObject
                    val hash = song["hash"]?.jsonPrimitive?.contentOrNull ?: return@async null

                    val pic = try {
                        getImageUrl(hash)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        "" // 封面拿不到不影响搜索结果本身
                    }

                    SearchResult(
                        id = hash,
                        title = song["songname"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        author = song["singername"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            .replace("、", " "),
                        duration = formatDurationFromSeconds(
                            song["duration"]?.jsonPrimitive?.int ?: 0
                        ),
                        pic = pic,
                        audioSource = AudioSource.KU_GOU
                    )
                }
            }.awaitAll().filterNotNull()
        }

        // total 是命中总数（可能是字符串），用它判断还有没有下一页；拿不到就退回"本页是否满页"
        val total = data["total"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val hasMore = total?.let { page * pageSize < it } ?: (songs.size >= pageSize)

        return SearchResultPage(items, hasMore)
    }

    /**
     * 按歌名与歌手反查歌曲 hash。
     *
     * 只认歌名完全相等、且歌手串里包含 [author] 的第一条结果 —— 搜索结果里同名歌、翻唱很多，
     * 放宽匹配会把用户要的原曲换掉。
     *
     * @return 没有匹配到时返回空串，调用方据此跳过平台匹配。
     */
    suspend fun getIdByTitleAndAuthor(title: String, author: String): String {
        val url = HttpUrl.Builder()
            .scheme("http")
            .host("mobilecdn.kugou.com")
            .addPathSegments("api/v3/search/song")
            .addQueryParameter("api_ver", "1")
            .addQueryParameter("area_code", "1")
            .addQueryParameter("correct", "1")
            .addQueryParameter("pagesize", "10")
            .addQueryParameter("plat", "2")
            .addQueryParameter("tag", "1")
            .addQueryParameter("sver", "5")
            .addQueryParameter("showtype", "10")
            .addQueryParameter("page", "1")
            .addQueryParameter("keyword", "$title $author")
            .addQueryParameter("version", "8990")
            .build()

        val songs = getJson(url)
            .get("data")?.jsonObject
            ?.get("info")?.jsonArray
            ?: return ""

        for (item in songs) {
            val song = item.jsonObject
            if (song["songname"]?.jsonPrimitive?.contentOrNull == title) {
                val artists = song["singername"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (artists.contains(author)) {
                    return song["hash"]?.jsonPrimitive?.contentOrNull.orEmpty()
                }
            }
        }

        return ""
    }

    /**
     * 取可播放的音频直链。
     *
     * 分两步：先用搜索拿到的 hash 调 get_res_privilege，从返回的 relate_goods 里取真正
     * 有播放权限的 hash；再用该 hash 与固定盐 `kgcloudv2` 的 MD5 作为 key 向 trackercdn 换地址。
     *
     * @throws IllegalStateException 接口返回异常、找不到资源或换不到链接时。
     */
    suspend fun getAudioUrl(id: String): String {
        val payload = buildJsonObject {
            put("relate", 1)
            put("userid", "0")
            put("vip", 0)
            put("appid", 1000)
            put("token", "")
            put("behavior", "download")
            put("area_code", "1")
            put("clientver", "8990")

            putJsonArray("resource") {
                addJsonObject {
                    put("id", 0)
                    put("type", "audio")
                    put("hash", id)
                }
            }
        }

        val requestBody = payload.toString()
            .toRequestBody("application/json".toMediaType())

        val privilegeBody = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("http://media.store.kugou.com/v1/get_res_privilege")
                .post(requestBody)
                .apply { defaultHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            client.newCall(request).execute().use { it.body.string() }
        }

        val data = Json.parseToJsonElement(privilegeBody).jsonObject
            .get("data")?.jsonArray
            ?: throw IllegalStateException("酷狗返回数据异常")

        val songHash = data.firstOrNull()
            ?.jsonObject?.get("relate_goods")?.jsonArray
            ?.firstOrNull()
            ?.jsonObject?.get("hash")?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("获取音频链接错误：未找到资源")

        // 播放地址的 key 由服务端约定的固定盐 "kgcloudv2" 与 hash 一起做 MD5 得到
        val key = md5(songHash + "kgcloudv2")

        val url = HttpUrl.Builder()
            .scheme("http")
            .host("trackercdn.kugou.com")
            .addPathSegments("i/v2/")
            .addQueryParameter("hash", songHash)
            .addQueryParameter("key", key)
            .addQueryParameter("pid", "3")
            .addQueryParameter("behavior", "play")
            .addQueryParameter("cmd", "25")
            .addQueryParameter("version", "8990")
            .build()

        val json = getJson(url)
        val urlElement = json["url"] ?: throw IllegalStateException("获取音频链接错误")

        return when {
            urlElement is JsonArray -> urlElement.firstOrNull()?.jsonPrimitive?.content
            else -> urlElement.jsonPrimitive.content
        } ?: throw IllegalStateException("获取音频链接错误")
    }

    /**
     * 取歌曲封面地址。
     *
     * @return 接口没返回 imgUrl 时返回空串（界面按无封面处理）。
     */
    suspend fun getImageUrl(id: String): String {
        val url = HttpUrl.Builder()
            .scheme("http")
            .host("m.kugou.com")
            .addPathSegments("app/i/getSongInfo.php")
            .addQueryParameter("cmd", "playInfo")
            .addQueryParameter("hash", id)
            .addQueryParameter("from", "mkugou")
            .build()

        return getJson(url)["imgUrl"]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    /**
     * 取歌词。
     *
     * 先用 hash 查 krc 候选拿到 accesskey 与歌词 id，再下载内容：接口返回的 content 是
     * Base64 编码的 LRC 文本，要先解码再解析。
     *
     * @return 没有候选、字段缺失或内容为空时返回空列表。
     */
    suspend fun getLyric(id: String): List<LyricLine> {
        val searchUrl = HttpUrl.Builder()
            .scheme("http")
            .host("krcs.kugou.com")
            .addPathSegment("search")
            .addQueryParameter("keyword", "%20-%20")
            .addQueryParameter("ver", "1")
            .addQueryParameter("hash", id)
            .addQueryParameter("client", "mobi")
            .addQueryParameter("man", "yes")
            .build()

        val candidate = getJson(searchUrl)["candidates"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?: return emptyList()

        val accessKey = candidate["accesskey"]?.jsonPrimitive?.contentOrNull ?: return emptyList()
        val lyricId = candidate["id"]?.jsonPrimitive?.contentOrNull ?: return emptyList()

        val lyricUrl = HttpUrl.Builder()
            .scheme("http")
            .host("lyrics.kugou.com")
            .addPathSegment("download")
            .addQueryParameter("charset", "utf8")
            .addQueryParameter("accesskey", accessKey)
            .addQueryParameter("id", lyricId)
            .addQueryParameter("client", "mobi")
            .addQueryParameter("fmt", "lrc")
            .addQueryParameter("ver", "1")
            .build()

        val content = getJson(lyricUrl)["content"]?.jsonPrimitive?.contentOrNull ?: return emptyList()

        return parseLyrics(Base64.decode(content).decodeToString())
    }

    /**
     * 把 LRC 文本解析成按时间升序排列的歌词行。
     *
     * 一行可能带多个时间戳（对唱、重复段落），会展开成多行；毫秒位为两位时按 ×10 补齐到毫秒。
     * 只有时间戳没有文字的纯节奏行会被跳过。
     */
    fun parseLyrics(lrcContent: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        val regex = """\[(\d{2}):(\d{2}\.\d{2,3})]""".toRegex()

        lrcContent.lines().forEach { line ->
            val matches = regex.findAll(line).toList()
            if (matches.isEmpty()) return@forEach

            val text = line.replace(regex, "").trim()
            if (text.isEmpty()) return@forEach

            matches.forEach { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val secondsParts = match.groupValues[2].split(".")
                val seconds = secondsParts.getOrNull(0)?.toLongOrNull() ?: 0L
                val fraction = secondsParts.getOrNull(1)?.toLongOrNull() ?: 0L
                val millis = if (secondsParts.getOrNull(1)?.length == 3) fraction else fraction * 10

                val timeMs = minutes * 60_000 + seconds * 1_000 + millis
                result.add(LyricLine(timeMs, text))
            }
        }

        return result.sortedBy { it.timeMs }
    }

    private fun md5(text: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(text.toByteArray())

        return digest.joinToString("") {
            "%02x".format(it)
        }
    }

    private fun formatDurationFromSeconds(sec: Int): String {
        return "%02d:%02d".format(sec / 60, sec % 60)
    }
}
