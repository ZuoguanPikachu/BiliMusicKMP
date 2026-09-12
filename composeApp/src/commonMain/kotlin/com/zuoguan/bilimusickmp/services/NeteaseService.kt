package com.zuoguan.bilimusickmp.services

import java.math.BigInteger
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import com.google.gson.Gson
import com.zuoguan.bilimusickmp.models.AudioSource
import com.zuoguan.bilimusickmp.models.LyricLine
import com.zuoguan.bilimusickmp.models.SearchResult
import com.zuoguan.bilimusickmp.utils.NoRetryException
import com.zuoguan.bilimusickmp.utils.retry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.net.URLEncoder
import kotlin.io.encoding.Base64

/**
 * 网易云 weapi 请求体加密器。
 *
 * 服务端不接收明文参数，请求体需要这样构造：先用固定密钥做一次 AES-CBC，再用一次性随机
 * 密钥做第二次 AES-CBC 得到 `params`；随机密钥本身用服务端公钥做 RSA 加密得到 `encSecKey`。
 * 服务端用自己的私钥解出随机密钥，再逐层还原请求体。
 */
object WEAPIEncryptor {
    // 服务端 RSA 公钥：指数与十六进制模数
    private const val PUB_EXP = "010001"
    private const val PUB_MOD =
        "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7"
    // 第一次 AES 用的固定密钥（weapi 约定值）
    private const val NONCE_KEY = "0CoJUm6Qyw8W8jud"

    /**
     * 加密请求体。
     *
     * @return weapi 接口要求的两个表单字段：`params`（密文）与 `encSecKey`（加密后的随机密钥）。
     */
    fun encryptRequest(data: String): Map<String, String> {
        val randomKey = randomString()
        val first = aesEncrypt(data, NONCE_KEY)
        val second = aesEncrypt(first, randomKey)
        val encSecKey = rsaEncrypt(randomKey)
        return mapOf(
            "params" to second,
            "encSecKey" to encSecKey
        )
    }

    /** 生成 16 位字母数字随机密钥，每次请求都不同。 */
    private fun randomString(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val rnd = SecureRandom()
        return buildString {
            repeat(16) {
                append(chars[rnd.nextInt(chars.length)])
            }
        }
    }

    /** AES-CBC/PKCS5 加密并做 Base64 编码；IV 是 weapi 约定的固定值。 */
    private fun aesEncrypt(text: String, key: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(key.toByteArray(), "AES")
        val iv = IvParameterSpec("0102030405060708".toByteArray())
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, iv)
        val encrypted = cipher.doFinal(text.toByteArray())
        return Base64.encode(encrypted)
    }

    // 服务端只接受按 126 字节分块的密文，块内每 2 字节按小端拼成 16 位整数再整体 modPow，
    // 因此先补齐到 126 的整数倍，逐块加密后用十六进制拼接输出。
    private fun rsaEncrypt(text: String): String {
        val modulus = BigInteger(PUB_MOD, 16)
        val exponent = BigInteger(PUB_EXP, 16)

        val bytes = text.toByteArray().toMutableList()
        while (bytes.size % 126 != 0) bytes.add(0)

        val result = mutableListOf<String>()

        for (i in bytes.indices step 126) {
            val chunk = bytes.subList(i, i + 126)

            val digits = mutableListOf<Int>()
            for (j in chunk.indices step 2) {
                val low = chunk[j].toInt() and 0xff
                val high = if (j + 1 < chunk.size) chunk[j + 1].toInt() else 0
                digits.add(low + (high shl 8))
            }

            var big = BigInteger.ZERO
            digits.forEachIndexed { index, value ->
                big = big.add(BigInteger.valueOf(value.toLong()).shiftLeft(16 * index))
            }

            val encrypted = big.modPow(exponent, modulus)
            result.add(encrypted.toString(16))
        }
        return result.joinToString(" ")
    }
}

/**
 * 网易云音乐接口封装：搜索、封面、歌词与音频直链。
 *
 * weapi 接口的请求体都要经 [WEAPIEncryptor] 加密，且必须带 Referer/Origin 头；
 * 已知歌曲 id 时则直接抓歌曲页的 og meta，绕开整套加密流程。
 */
class NetEaseService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    /** 以表单形式提交已加密的参数；weapi 接口会校验 Referer/Origin，缺失即判为非法请求。 */
    private suspend fun post(url: String, data: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            val body = data.entries.joinToString("&") {
                "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
            }.toRequestBody("application/x-www-form-urlencoded".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://music.163.com/")
                .header("Origin", "https://music.163.com")
                .build()

            client.newCall(request).execute().use { it.body.string() }
        }

    /**
     * 搜索歌曲。
     *
     * 支持四种输入：163cn.tv 分享短链、带 `song?id=` 的歌曲页链接、纯数字歌曲 id 以及普通关键词。
     * 前三种都归一到 [searchById]（短链要先跟随重定向，从真实 URL 里解析歌曲 id），
     * 只有关键词才走 weapi 搜索接口。
     *
     * @param searchType 网易云的搜索类型，1 表示单曲。
     * @param offset 结果偏移，配合 [limit] 分页。
     * @throws NoRetryException 分享链接或歌曲链接里解析不出 id 时，[retry] 不会重试。
     */
    suspend fun search(
        s: String,
        searchType: Int = 1,
        offset: Int = 0,
        limit: Int = 10
    ): List<SearchResult> {
        if (s.contains("163cn.tv")) {
            val songId = resolveShareLinkId(s)
                ?: throw NoRetryException("无法从分享链接解析歌曲 ID")
            return searchById(songId)
        }

        if (s.contains("song?id=")) {
            val songId = extractSongId(s)
                ?: throw NoRetryException("无法从链接解析歌曲 ID")
            return searchById(songId)
        }

        if (s.matches(Regex("^\\d+$"))) {
            return searchById(s)
        }

        return retry(times = 5) {
            val payload = mapOf(
                "s" to s,
                "type" to searchType,
                "offset" to offset,
                "limit" to limit
            )

            val encrypted = WEAPIEncryptor.encryptRequest(gson.toJson(payload))
            val json = gson.fromJson(
                post("https://music.163.com/weapi/cloudsearch/pc", encrypted),
                Map::class.java
            ) ?: throw NoRetryException("网易云音乐搜索失败")

            val result = json["result"] as? Map<*, *>
                ?: throw NoRetryException("网易云音乐搜索返回异常")
            @Suppress("UNCHECKED_CAST")
            val songs = result["songs"] as? List<Map<*, *>>
                ?: throw NoRetryException("网易云音乐搜索返回异常")

            return@retry songs.mapNotNull { song ->
                val id = (song["id"] as? Number)?.toLong()?.toString() ?: return@mapNotNull null
                val title = song["name"]?.toString().orEmpty()
                val author = (song["ar"] as? List<*>)
                    ?.joinToString(" ") { (it as? Map<*, *>)?.get("name")?.toString().orEmpty() }
                    .orEmpty()
                val duration = (song["dt"] as? Number)?.toInt()?.let { formatDurationFromMillis(it) } ?: "00:00"
                // 搜索结果里自带专辑封面，不必为每首歌再发一次 HTTP 请求
                val imageUrl = (song["al"] as? Map<*, *>)?.get("picUrl")?.toString().orEmpty()

                SearchResult(id, title, author, imageUrl, duration, AudioSource.NET_EASE)
            }
        }
    }

    /** 解析 163cn.tv 短链，拿到真实 URL 中的歌曲 id。 */
    private suspend fun resolveShareLinkId(text: String): String? {
        val url = extractUrl(text) ?: return null
        val real = withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(url).build()).execute().use { it.request.url.toString() }
        }
        return extractSongId(real)
    }

    /**
     * 已知歌曲 id 时直接抓歌曲页，从 og meta 里读标题、歌手、时长与封面，
     * 免去 weapi 的加密与签名流程。
     */
    suspend fun searchById(id: String): List<SearchResult> {
        return retry(times = 5) {
            val html = withContext(Dispatchers.IO) {
                client.newCall(
                    Request.Builder()
                        .url("https://music.163.com/song?id=$id")
                        .build()
                ).execute().use { it.body.string() }
            }

            val doc = Jsoup.parse(html)
            val title = doc.select("meta[property=og:title]").attr("content")
            val artist = doc.select("meta[property=og:music:artist]").attr("content").replace("/", " ")
            val duration = formatDurationFromSeconds(
                doc.select("meta[property=music:duration]").attr("content")
            )
            val image = doc.select("meta[property=og:image]").attr("content")

            return@retry listOf(SearchResult(id, title, artist, image, duration, AudioSource.NET_EASE))
        }
    }

    /**
     * 取音频直链。
     *
     * `level=exhigh` 请求较高音质；`data[0].url` 为空说明该曲目无版权或是 VIP 曲目。
     *
     * @throws NoRetryException 返回数据为空或拿不到 url 时（重试也无济于事，直接放弃）。
     */
    suspend fun getAudioUrl(id: String): String {
        return retry(times = 5){
            val payload = mapOf("ids" to listOf(id), "level" to "exhigh", "encodeType" to "acc")
            val encrypted = WEAPIEncryptor.encryptRequest(gson.toJson(payload))
            val resp = post("https://music.163.com/weapi/song/enhance/player/url/v1", encrypted)
            val json = gson.fromJson(
                resp,
                Map::class.java
            ) ?: throw NoRetryException("获取音频链接错误")

            val data = json["data"] as? List<*>
            if (data.isNullOrEmpty()) {
                throw NoRetryException("获取音频链接错误：返回数据为空")
            }

            val urlObj = (data[0] as? Map<*, *>)?.get("url")
                ?: throw NoRetryException("可能是VIP歌曲，无法获取音频链接")

            urlObj.toString()
        }
    }

    /**
     * 按歌名与歌手反查歌曲 id。
     *
     * 只接受歌名相等、且歌手列表里含 [author] 的结果，避免匹配到翻唱或同名歌曲。
     *
     * @return 没有匹配到时返回空串。
     */
    suspend fun getIdByTitleAndAuthor(title: String, author: String): String {
        return retry(times = 5) {
            val payload = mapOf(
                "s" to "$title $author",
                "type" to 1,
                "offset" to 0,
                "limit" to 10
            )

            val encrypted = WEAPIEncryptor.encryptRequest(gson.toJson(payload))

            val json = gson.fromJson(
                post("https://music.163.com/weapi/cloudsearch/pc", encrypted),
                Map::class.java
            ) ?: throw NoRetryException("自动搜索失败")

            val result = json["result"] as? Map<*, *> ?: return@retry ""
            @Suppress("UNCHECKED_CAST")
            val songs = result["songs"] as? List<Map<*, *>> ?: return@retry ""
            for (song in songs) {
                if (song["name"] == title) {
                    val artists = song["ar"] as? List<*> ?: continue
                    if (artists.any { (it as? Map<*, *>)?.get("name") == author }) {
                        return@retry (song["id"] as? Number)?.toLong()?.toString().orEmpty()
                    }
                }
            }
            return@retry ""
        }
    }

    /** 抓歌曲页的 og:image 取封面地址；页面没有该标签时返回空串。 */
    suspend fun getImageUrl(id: String): String = withContext(Dispatchers.IO) {
        val html = client.newCall(
            Request.Builder().url("https://music.163.com/song?id=$id").build()
        ).execute().use { it.body.string() }

        Jsoup.parse(html).select("meta[property=og:image]").attr("content")
    }

    /**
     * 取歌词并解析成按时间升序排列的歌词行。
     *
     * @param lv 原文歌词版本号，-1 表示取接口默认版本。
     * @param tv 翻译歌词版本号，-1 表示取默认版本；当前只解析原文歌词。
     * @return 接口没有歌词时返回空列表；毫秒位为两位时按 ×10 补齐到毫秒。
     */
    suspend fun getLyric(
        id: String,
        lv: Int = -1,
        tv: Int = -1
    ): List<LyricLine> {

        val payload = mapOf(
            "id" to id,
            "lv" to lv,
            "tv" to tv
        )

        val encrypted = WEAPIEncryptor.encryptRequest(gson.toJson(payload))

        return retry {
            val json = gson.fromJson(
                post("https://music.163.com/weapi/song/lyric", encrypted),
                Map::class.java
            ) ?: throw Exception("获取歌词失败")

            val lrc = (json["lrc"] as? Map<*, *>) ?: return@retry emptyList()
            val lyric = lrc["lyric"] as? String ?: return@retry emptyList()

            val result = mutableListOf<LyricLine>()

            val lines = lyric.split("\n")
            val timeRegex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})]""")

            for (line in lines) {
                val matches = timeRegex.findAll(line).toList()
                if (matches.isEmpty()) continue

                val text = line.replace(timeRegex, "").trim()
                if (text.isEmpty()) continue

                for (match in matches) {
                    val minutes = match.groupValues[1].toInt()
                    val seconds = match.groupValues[2].toInt()
                    val milliStr = match.groupValues[3]

                    val milliseconds = if (milliStr.length == 2) {
                        milliStr.toInt() * 10
                    } else {
                        milliStr.toInt()
                    }

                    val timeMs =
                        minutes * 60_000L +
                                seconds * 1_000L +
                                milliseconds

                    result.add(LyricLine(timeMs, text))
                }
            }

            return@retry result.sortedBy { it.timeMs }
        }
    }

    private fun formatDurationFromMillis(ms: Int): String =
        "%02d:%02d".format(ms / 60000, (ms / 1000) % 60)

    // meta 标签里的时长是秒数文本，页面缺失该标签时按 0 处理
    private fun formatDurationFromSeconds(sec: String): String {
        val s = sec.toIntOrNull() ?: 0
        return "%02d:%02d".format(s / 60, s % 60)
    }

    /** 从任意文本中取出第一条 http(s) 链接。 */
    private fun extractUrl(text: String): String? =
        Regex("https?://[^\\s)]+").find(text)?.value

    // 短链重定向后的真实 URL 同样带 id 查询参数，所以两种链接可以共用同一套解析
    private fun extractSongId(url: String): String? =
        Regex("[?&]id=(\\d+)").find(url)?.groupValues?.get(1)
}