package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.AudioSource
import com.zuoguan.bilimusickmp.models.LyricLine
import com.zuoguan.bilimusickmp.models.SearchResult
import com.zuoguan.bilimusickmp.models.SearchResultPage
import com.zuoguan.bilimusickmp.utils.parseLrc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** QQ 音乐接口层面的失败（接口异常、解析不出、拿不到地址等）。 */
open class QQMusicException(message: String) : Exception(message)

/**
 * 曲目存在，但匿名身份拿不到可播放地址。
 *
 * 匿名只能拿普通音质：VIP / 独家 / 已下架曲目一律返回 `result=104003`。
 * 单独成类是为了让界面能提示"该曲目需要 VIP"，而不是笼统的"播放失败"。
 */
class QQMusicVipOnlyException(message: String) : QQMusicException(message)

/**
 * QQ 音乐接口封装：搜索、封面、歌词与音频直链，全程**不需要登录**。
 *
 * 四个接口分散在不同域名，且各自有必须遵守的约定（详见各方法注释）：
 * - 搜索 `c.y.qq.com/soso/fcgi-bin/search_for_qq_cp`，单页上限 30、总量硬上限 600；
 * - 封面 `y.gtimg.cn/music/photo_new/...`，纯 URL 模板，不发请求；
 * - 歌词 `c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg`，必须带 Referer；
 * - 直链 `u.y.qq.com/cgi-bin/musicu.fcg` 的 `CgiGetVkey`，返回短时效 vkey 拼出的地址。
 *
 * 对外暴露的歌曲 id 有两种形状：新曲目是真正的 `songmid`；老目录条目只有数字 `songid`，
 * 用 `id:{songid}` 标记，取歌词/直链时再按需换成 mid（详见 [LEGACY_ID_PREFIX]）。
 */
class QQMusicService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // 搜索与歌词接口都从网页端来的，带上站点 Referer（歌词接口缺它会被拒）
    private val defaultHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
        "Referer" to "https://y.qq.com/",
        "Origin" to "https://y.qq.com",
        "Accept-Language" to "zh-CN,zh;q=0.9"
    )

    // songmid -> albumMid：搜索结果里已经带了 albummid，取封面时可以直接命中
    private val albumCache = ConcurrentHashMap<String, String>()
    // songmid -> mediaMid：拼直链文件名用，必须来自详情接口
    private val mediaCache = ConcurrentHashMap<String, String>()
    // 对外 id -> 真 songmid：只对老目录条目（id:{songid}）有值
    private val midCache = ConcurrentHashMap<String, String>()
    // songmid -> 详情响应，避免同一首歌反复请求
    private val detailCache = ConcurrentHashMap<String, JsonObject>()

    // ---------------------------------------------------------------- 内部请求

    private suspend fun getJson(url: HttpUrl): JsonObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .apply { defaultHeaders.forEach { (k, v) -> addHeader(k, v) } }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw QQMusicException("QQ 音乐接口返回 HTTP ${response.code}")
            }
            Json.parseToJsonElement(response.body.string()).jsonObject
        }
    }

    /** 向 musicu 网关 POST 一个 module/method 调用，返回其中的 `data` 对象。 */
    private suspend fun postMusicu(module: String, method: String, param: JsonObject): JsonObject =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("comm", buildJsonObject {
                    put("cv", 4747474)
                    put("ct", 24)
                    put("format", "json")
                    put("inCharset", "utf-8")
                    put("outCharset", "utf-8")
                    put("notice", 0)
                    put("platform", "yqq.json")
                    put("needNewCode", 1)
                    put("uin", 0)
                    put("g_tk_new_20200303", 5381)
                    put("g_tk", 5381)
                })
                put("req_1", buildJsonObject {
                    put("module", module)
                    put("method", method)
                    put("param", param)
                })
            }.toString().toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(MUSICU_API)
                .post(body)
                .apply { defaultHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            val payload = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw QQMusicException("QQ 音乐接口返回 HTTP ${response.code}")
                }
                Json.parseToJsonElement(response.body.string()).jsonObject
            }.get("req_1")?.jsonObject ?: throw QQMusicException("QQ 音乐接口返回数据异常")

            val code = payload["code"]?.jsonPrimitive?.contentOrNull
            if (code != null && code != "0") {
                throw QQMusicException("QQ 音乐 $module/$method 返回 code=$code")
            }

            payload["data"] as? JsonObject ?: JsonObject(emptyMap())
        }

    // ---------------------------------------------------------------- 搜索

    /**
     * 搜索单曲（匿名）。
     *
     * 接口单页最多回 30 条，且命中总数恒为 600；翻过上限会返回空数组，因此
     * [SearchResultPage.hasMore] 只依据接口给的 total 判断。
     *
     * @param pageSize 每页条数，超过 30 会被截断到 30。
     * @param page 页码，从 1 开始。
     * @return 解析不出 songmid 的条目会被丢弃；无结果时返回空页而不是抛异常。
     */
    suspend fun search(keyword: String, pageSize: Int = 10, page: Int = 1): SearchResultPage {
        val size = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("c.y.qq.com")
            .addPathSegments("soso/fcgi-bin/search_for_qq_cp")
            .addQueryParameter("w", keyword)
            .addQueryParameter("format", "json")
            .addQueryParameter("p", page.toString())
            .addQueryParameter("n", size.toString())
            .addQueryParameter("t", "0")
            .addQueryParameter("remoteplace", "txt.yqq.song")
            .addQueryParameter("cr", "1")
            .addQueryParameter("new_json", "1")
            .addQueryParameter("platform", "yqq.json")
            .addQueryParameter("needNewCode", "0")
            .build()

        // 被限流或关键词无结果时接口不会给 data.song，这里统一按空页处理
        val section = (getJson(url)["data"] as? JsonObject)?.get("song") as? JsonObject
            ?: return SearchResultPage(emptyList(), hasMore = false)
        val songs = section["list"] as? JsonArray
            ?: return SearchResultPage(emptyList(), hasMore = false)

        val items = songs.mapNotNull { element -> parseSearchItem(element) }

        val total = section["totalnum"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            ?.coerceAtMost(MAX_TOTAL) ?: 0
        val hasMore = items.isNotEmpty() && page * size < total

        return SearchResultPage(items, hasMore)
    }

    /**
     * 把搜索接口里的一条扁平记录转成 [SearchResult]。
     *
     * 搜索接口会混着返回两种 schema：新曲目带真正的 `songmid` / `albummid`；老目录条目这两
     * 个字段都是占位值（`"songmid":"0"`、`"albummid":"0"` 或纯数字 album id），唯一能用的
     * 标识是数字 `songid`。这类条目用 [LEGACY_ID_PREFIX] 标记后仍然收下（真的丢掉的话，
     * 像「愿逐月华照君明」这种命中一堆老歌的关键词会只剩几条结果），播放时再按需换成 mid。
     *
     * @return 两种标识都没有的条目返回 null（无法唯一定位，且 id 会撞车）。
     *
     * 纯函数（不发请求），声明为 internal 只是为了让本地用例能直接喂两种 schema 的响应。
     */
    internal fun parseSearchItem(element: JsonElement): SearchResult? {
        val song = element as? JsonObject ?: return null

        val songMid = song["songmid"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val songId = song["songid"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val id = when {
            isRealMid(songMid) -> songMid
            isRealSongId(songId) -> LEGACY_ID_PREFIX + songId
            else -> return null
        }

        // 老条目的 albummid 是数字 album id，拼出来的封面是 404，只能当作"没有封面"
        val albumMid = song["albummid"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (isRealMid(albumMid)) albumCache[id] = albumMid

        val interval = song["interval"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        val singers = (song["singer"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull }
            .orEmpty()

        return SearchResult(
            id = id,
            title = song["songname"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            author = singers.joinToString(" "),
            pic = getCoverUrl(albumMid.takeIf { isRealMid(it) } ?: ""),
            duration = formatDuration(interval),
            audioSource = AudioSource.QQ_MUSIC
        )
    }

    /**
     * 按歌名与歌手反查 songmid。
     *
     * 只认歌名完全相等、且歌手串互相包含的第一条结果 —— 与酷狗/网易云保持同一套规则，
     * 放宽匹配会把用户要的原曲换成翻唱。
     *
     * @return 没有匹配到时返回空串，调用方据此跳过平台匹配。
     */
    suspend fun getIdByTitleAndAuthor(title: String, author: String): String {
        val page = search("$title $author".trim(), pageSize = MAX_PAGE_SIZE, page = 1)
        return page.items.firstOrNull { item ->
            item.title == title && (item.author.contains(author) || author.contains(item.author))
        }?.id.orEmpty()
    }

    // ---------------------------------------------------------------- 封面

    /**
     * 由 albumMid 拼封面地址，纯模板不发请求。
     *
     * 这里的 `size` 不是任意值：服务端只预生成了 {68, 90, 120, 150, 180, 300, 500, 800, 1200, 1500}
     * 这几档（其余尺寸一律 404，且只支持正方形）。存进库里的地址固定用 300 这一档，
     * 界面按需放大时由 [com.zuoguan.bilimusickmp.utils.convertImageUrl] 贴到对应档位。
     */
    fun getCoverUrl(albumMid: String, size: Int = 300): String =
        if (albumMid.isEmpty()) "" else COVER_TEMPLATE.format(size, size, albumMid)

    /**
     * 取某首歌的详情（匿名可用）。主要用途是从歌曲 id 反查 songmid / albumMid / mediaMid。
     *
     * 同时接受真 mid 与老条目的 `id:{songid}` 形式，由 [detailParams] 决定用哪个参数名。
     * 搜索阶段已经缓存了 albumMid，正常取封面用不到这个接口；
     * 但**取直链必须要 mediaMid**，所以播放时一定会走一次。
     *
     * @return 接口没有该曲目时返回 null。
     */
    private suspend fun getSongDetail(id: String): JsonObject? {
        detailCache[id]?.let { return it }

        val (keyName, keyValue) = detailParams(id)
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("c.y.qq.com")
            .addPathSegments("v8/fcg-bin/fcg_play_single_song.fcg")
            .addQueryParameter(keyName, keyValue)
            .addQueryParameter("platform", "yqq")
            .addQueryParameter("format", "json")
            .build()

        val detail = (getJson(url)["data"] as? JsonArray)?.firstOrNull() as? JsonObject
            ?: return null

        ((detail["album"] as? JsonObject)?.get("mid")?.jsonPrimitive?.contentOrNull)
            ?.takeIf { isRealMid(it) }
            ?.let { albumCache[id] = it }
        ((detail["file"] as? JsonObject)?.get("media_mid")?.jsonPrimitive?.contentOrNull)
            ?.takeIf { isRealMid(it) }
            ?.let { mediaCache[id] = it }
        // 老条目在这里才能换到真 mid，顺手缓存，省掉重复的详情请求
        detail["mid"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { isRealMid(it) }
            ?.let { midCache[id] = it }

        detailCache[id] = detail
        return detail
    }

    /**
     * 把对外的歌曲 id 拆成详情接口需要的参数。
     *
     * 真 mid 走 `songmid`；老目录条目只有数字 `songid`，详情接口用 `songid` 查也能返回
     * 完整记录（含真 mid），所以这两种形状都能定位到同一首歌。
     */
    private fun detailParams(id: String): Pair<String, String> =
        if (id.startsWith(LEGACY_ID_PREFIX)) {
            "songid" to id.removePrefix(LEGACY_ID_PREFIX)
        } else {
            "songmid" to id
        }

    /**
     * 把对外的歌曲 id 换成接口真正要的 songmid。
     *
     * 真 mid 原样返回；`id:{songid}` 先查一次详情换回 mid —— 直链与歌词接口都不认数字
     * songid（实测 result=101404 / retcode=1101），必须先换。
     *
     * @throws QQMusicException 换不到 mid 时（详情接口没有返回或该曲目已下架）。
     */
    private suspend fun resolveSongMid(id: String): String {
        if (!id.startsWith(LEGACY_ID_PREFIX)) return id

        midCache[id]?.let { return it }

        val mid = getSongDetail(id)
            ?.let { it["mid"]?.jsonPrimitive?.contentOrNull ?: it["songmid"]?.jsonPrimitive?.contentOrNull }
            .orEmpty()
        if (!isRealMid(mid)) {
            throw QQMusicException("获取音频链接错误：歌曲 ${id.removePrefix(LEGACY_ID_PREFIX)} 已无有效 mid")
        }

        midCache[id] = mid
        return mid
    }

    /**
     * 取 mediaMid —— 拼直链文件名的**必需**参数。
     *
     * 文件名形如 `M500{mediaMid}.mp3`，这里的 mediaMid 常常和 songmid 不同。用 songmid 拼时
     * 服务端照样回 `result=0` 和非空 purl，只有真正下载才会 404，所以一律以详情接口为准。
     */
    private suspend fun getMediaMid(id: String): String {
        mediaCache[id]?.let { return it }
        getSongDetail(id)
        return mediaCache[id].orEmpty()
    }

    /**
     * 对齐酷狗的 `getImageUrl(id)`：只给歌曲 id 也能拿到封面。
     *
     * 优先用搜索阶段缓存的 albumMid，没有缓存才回落到详情接口反查 ——
     * 老条目的 albumMid 在搜索里是无效占位值，只能靠详情接口给。
     *
     * @return 拿不到 albumMid 时返回空串（界面按无封面处理）。
     */
    suspend fun getImageUrl(id: String): String {
        val albumMid = albumCache[id]?.takeIf { isRealMid(it) }
            ?: getSongDetail(id)?.let { (it["album"] as? JsonObject) }
                ?.get("mid")?.jsonPrimitive?.contentOrNull.orEmpty()
        return getCoverUrl(albumMid.takeIf { isRealMid(it) } ?: "")
    }

    // ---------------------------------------------------------------- 音频直链

    /**
     * 请求一次 vkey。
     *
     * `guid` 必须固定为 [VKEY_GUID]：传随机值会得到 `retcode=104009` 和空 purl，
     * 看起来像"没有版权"，其实只是 guid 不合法。
     *
     * @param fileName 期望的文件名；不传则服务端给默认档位（m4a）。
     * @return 服务器列表与每首歌的 midurlinfo。
     */
    private suspend fun requestVkey(
        songMid: String,
        fileName: String?
    ): Pair<List<String>, List<JsonObject>> {
        val data = postMusicu(
            module = "vkey.GetVkeyServer",
            method = "CgiGetVkey",
            param = buildJsonObject {
                put("guid", VKEY_GUID)
                putJsonArray("songmid") { add(songMid) }
                putJsonArray("songtype") { add(0) }
                put("uin", "0")
                put("loginflag", 1)
                put("platform", "20")
                if (fileName != null) {
                    putJsonArray("filename") { add(fileName) }
                }
            }
        )

        val sips = (data["sip"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()
        val infos = (data["midurlinfo"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            .orEmpty()

        return sips to infos
    }

    /**
     * 取可播放的音频直链（匿名，仅非 VIP 曲目）。
     *
     * 按 [AUDIO_TIERS] 从高到低尝试，每个候选都先 HEAD 一次再返回：purl 非空并不代表文件存在
     * （文件名用错时服务端照样给地址，下载才 404）。全部候选都失败时退回"不指定文件名"，
     * 由服务端挑默认档位。
     *
     * 地址的 scheme 会被改写成 https —— vkey 不绑定 scheme，这样 Android 上不用为明文流量做额外配置。
     *
     * @throws QQMusicVipOnlyException 曲目需要 VIP / 无版权（接口 `result=104003`）。
     * @throws QQMusicException 接口异常，或所有档位都拿不到可下载的地址。
     */
    suspend fun getAudioUrl(id: String): String {
        // 老条目要先拿数字 songid 换回真 mid，vkey 接口不认 songid
        val songMid = resolveSongMid(id)

        // 详情接口失败不致命：下面还有"不传 filename"的服务端默认档位兜底
        val mediaMid = try {
            getMediaMid(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }

        val candidates = buildList {
            if (mediaMid.isNotEmpty()) {
                AUDIO_TIERS.forEach { (prefix, suffix) ->
                    add("$prefix$mediaMid$suffix")
                }
            }
            add(null) // 服务端默认档位
        }

        var lastResult: Int? = null
        val rejected = mutableListOf<String>()

        for (fileName in candidates) {
            val (sips, infos) = requestVkey(songMid, fileName)
            val info = infos.firstOrNull() ?: continue
            lastResult = info["result"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

            val purl = info["purl"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (purl.isEmpty()) {
                // 104003 是"整首歌都不给"（VIP/无版权），换低音质也只会得到同样的结果，
                // 没必要把剩下的档位全部再问一遍
                if (lastResult == VIP_RESULT_CODE) break
                continue
            }

            val base = (sips.firstOrNull() ?: DEFAULT_SIP).replace("http://", "https://")
            val url = base + purl
            if (!isDownloadable(url)) {
                rejected.add(fileName ?: info["filename"]?.jsonPrimitive?.contentOrNull.orEmpty())
                continue
            }
            return url
        }

        if (lastResult == VIP_RESULT_CODE) {
            throw QQMusicVipOnlyException("该曲目需要 VIP 或无版权，无法匿名播放")
        }
        val detail = if (rejected.isEmpty()) "" else "（候选都被拒：${rejected.joinToString()}）"
        throw QQMusicException("获取音频链接错误：result=$lastResult$detail")
    }

    /**
     * HEAD 探测直链是否真的存在。
     *
     * CDN 支持 HEAD（200 + Content-Length）；文件不存在时是 404，正文是
     * `{"errorcode":-46628,"errormsg":"file not exist"}`。
     * 网络异常时保守返回 true，把问题留给播放器，而不是误判成坏链。
     */
    private suspend fun isDownloadable(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).head().build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful &&
                        (response.header("Content-Length")?.toLongOrNull() ?: 0L) > 0L
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            true
        }
    }

    // ---------------------------------------------------------------- 歌词

    /**
     * 取歌词并解析成按时间升序的歌词行（匿名）。
     *
     * 用 `nobase64=1` 直接拿明文 LRC，省掉一次 Base64 解码；必须带 Referer。
     *
     * @return 没有歌词时返回空列表（纯音乐/钢琴曲属于正常情况）。
     */
    suspend fun getLyric(id: String): List<LyricLine> {
        // 老条目要先换回真 mid：歌词接口只认 songmid（传 songid 返回 retcode=1101）
        val songMid = resolveSongMid(id)

        val url = HttpUrl.Builder()
            .scheme("https")
            .host("c.y.qq.com")
            .addPathSegments("lyric/fcgi-bin/fcg_query_lyric_new.fcg")
            .addQueryParameter("songmid", songMid)
            .addQueryParameter("format", "json")
            .addQueryParameter("nobase64", "1")
            .addQueryParameter("g_tk", "5381")
            .addQueryParameter("loginUin", "0")
            .addQueryParameter("hostUin", "0")
            .addQueryParameter("inCharset", "utf8")
            .addQueryParameter("outCharset", "utf-8")
            .addQueryParameter("notice", "0")
            .addQueryParameter("platform", "yqq.json")
            .addQueryParameter("needNewCode", "0")
            .build()

        val lyric = getJson(url)["lyric"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (lyric.isBlank()) return emptyList()

        return parseLrc(lyric)
    }

    private fun formatDuration(seconds: Int): String =
        "%02d:%02d".format(seconds / 60, seconds % 60)

    private companion object {
        /**
         * 老目录条目的 id 前缀：这类条目搜索时不给 `songmid`，只有数字 `songid`。
         *
         * 数字 songid 不能直接当 id 用 —— 直链与歌词接口都不认它，而且一批老条目会同时
         * 都是 `"0"`，在列表里撞成重复主键（Compose 的 LazyLayout 会直接抛
         * `Key "0" was already used`）。所以用前缀标出来，用到时再换真 mid。
         */
        const val LEGACY_ID_PREFIX = "id:"

        /** mid 缺失时服务端给的占位值，和空串一样不能用。 */
        const val PLACEHOLDER_ID = "0"

        /** 占位值：空串或 "0"。 */
        fun isPlaceholder(value: String): Boolean =
            value.isEmpty() || value == PLACEHOLDER_ID

        /**
         * 真的 mid（songmid / albummid）。
         *
         * 真 mid 是 14 位 base62 串；老目录条目里对应位置填的是纯数字的内部 id
         * （`"songmid":"0"`、`"albummid":"27320831"`），拿它拼封面只会 404，所以一起排除。
         */
        fun isRealMid(value: String): Boolean =
            !isPlaceholder(value) && !value.all { it.isDigit() }

        /** 真的数字 songid：非空、非占位值、且确实是数字。 */
        fun isRealSongId(value: String): Boolean =
            !isPlaceholder(value) && value.all { it.isDigit() }

        /** musicu 网关：直链接口就在这里。 */
        const val MUSICU_API = "https://u.y.qq.com/cgi-bin/musicu.fcg"

        /** 封面模板：`T002R{size}x{size}M000{albumMid}.jpg`。 */
        const val COVER_TEMPLATE = "https://y.gtimg.cn/music/photo_new/T002R%dx%dM000%s.jpg"

        /** vkey 必须用这个固定 guid；换随机值会返回 retcode=104009。 */
        const val VKEY_GUID = "10000"

        const val DEFAULT_SIP = "https://aqqmusic.tc.qq.com/"

        /** 匿名放行的音质档位，从高到低：128k mp3 / m4a(AAC) / ogg 192k。 */
        val AUDIO_TIERS = listOf("M500" to ".mp3", "C400" to ".m4a", "O600" to ".ogg")

        /** 服务端上限：单页 30 条，命中总数 600。 */
        const val MAX_PAGE_SIZE = 30
        const val MAX_TOTAL = 600

        /** `midurlinfo[].result`：VIP / 无版权 / 超出匿名音质上限。 */
        const val VIP_RESULT_CODE = 104003

        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
