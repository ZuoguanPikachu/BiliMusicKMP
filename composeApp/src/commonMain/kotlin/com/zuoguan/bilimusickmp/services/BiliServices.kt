package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.AudioSource
import com.zuoguan.bilimusickmp.models.SearchResult
import com.zuoguan.bilimusickmp.utils.SimpleCookieJar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * B 站 Web 接口封装：负责 WBI 签名、视频搜索、取 cid 与音频直链。
 *
 * 所有请求共用一个 [OkHttpClient]，由拦截器统一补上 B 站要求的 Referer 与 User-Agent，
 * 并通过 [SimpleCookieJar] 在请求之间保持 cookie —— 缺了这些会被接口判定为异常请求。
 */
class BiliService {
    private val cookieJar = SimpleCookieJar()
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor {
            // 防盗链：B 站接口会校验 Referer/User-Agent，缺失时返回错误码或空数据
            val req = it.request().newBuilder()
                .header("Referer", "https://www.bilibili.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36")
                .build()
            it.proceed(req)
        }.build()

    // nav 接口 wbi_img.img_url 的文件名（不含扩展名），与 subKey 拼接后参与 WBI 签名
    @Volatile
    private var imgKey = ""

    // nav 接口 wbi_img.sub_url 的文件名（不含扩展名）
    @Volatile
    private var subKey = ""
    // 保证密钥只被拉取一次：并发请求同时初始化时，后来者在锁上等待而不是重复发请求
    private val wbiMutex = Mutex()
    private var wbiInitialized = false

    // WBI mixinKey 的重排下标表：按此顺序从 imgKey + subKey 中取字符，再截前 32 位
    private val mixinKeyEncTab = intArrayOf(
        46,47,18,2,53,8,23,32,15,50,10,31,58,3,45,35,27,43,5,49,
        33,9,42,19,29,28,14,39,12,38,41,13,37,48,7,16,24,55,40,
        61,26,17,0,1,60,51,30,4,22,25,54,21,56,59,6,63,57,62,11,
        36,20,34,44,52
    )

    init {
        // 预置 b_nut cookie，部分接口要求请求自带 cookie 才正常返回
        cookieJar.set("b_nut", System.currentTimeMillis().toString(), "bilibili.com")
    }

    // ---------------- WBI（请求签名） ----------------

    /**
     * 确保 WBI 密钥已就绪，未就绪时先拉取一次。
     *
     * @throws Exception 拉取密钥失败时抛出，由调用方决定是否重试。
     */
    private suspend fun ensureWbiReady() {
        if (wbiInitialized) return

        wbiMutex.withLock {
            if (wbiInitialized) return

            try {
                refreshWbiKeys()
                wbiInitialized = true
            } catch (e: Exception) {
                throw Exception("无法获取WBI签名密钥: ${e.message}", e)
            }
        }
    }

    /**
     * 从 nav 接口取 imgKey 与 subKey。
     *
     * 两个值藏在返回的图片 URL 里，即 `.../xxxxxxxx.png` 的文件名部分，所以用
     * `substringAfterLast("/")` 加 `substringBefore(".")` 剥出来。
     */
    private suspend fun refreshWbiKeys() {
        val json = get("https://api.bilibili.com/x/web-interface/nav")
        val data = JSONObject(json).getJSONObject("data").getJSONObject("wbi_img")

        imgKey = data.getString("img_url").substringAfterLast("/").substringBefore(".")
        subKey = data.getString("sub_url").substringAfterLast("/").substringBefore(".")
    }

    /** 按重排表打乱 imgKey + subKey，取前 32 位作为 mixinKey。 */
    private fun getMixinKey(orig: String): String {
        require(orig.length > mixinKeyEncTab.max()) { "WBI 密钥长度异常: ${orig.length}" }
        return mixinKeyEncTab.joinToString("") { orig[it].toString() }.substring(0, 32)
    }

    /**
     * 给查询参数加上 WBI 签名。
     *
     * 流程：补上 `wts` 时间戳 → 按键名排序 → 过滤 `!'()*`（这些字符会被 URL 编码器改写，
     * 导致服务端算出的签名与本地不一致）→ 拼成查询串后与 mixinKey 一起做 MD5 得到 `w_rid`。
     *
     * @return 含 `wts` 与 `w_rid` 的完整参数，可直接拼成查询串。
     * @throws IllegalStateException 密钥尚未初始化时。
     */
    private fun encWbi(params: MutableMap<String, String>): Map<String, String> {
        if (imgKey.isEmpty() || subKey.isEmpty()) {
            throw IllegalStateException("WBI 密钥未初始化")
        }

        val mixinKey = getMixinKey(imgKey + subKey)
        params["wts"] = (System.currentTimeMillis() / 1000).toString()

        val sorted = params.toSortedMap()
        val filtered = sorted.mapValues { it.value.filter { c -> !"!'()*".contains(c) } }

        val query = filtered.entries.joinToString("&") {
            "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
        }

        val sign = md5(query + mixinKey)
        return filtered + ("w_rid" to sign)
    }

    // ---------------- API（搜索 / 播放地址） ----------------

    /**
     * 搜索视频。
     *
     * 搜索接口只认关键词，直接拿链接当关键词搜不到东西，所以这里先把链接归一成 BV 号：
     * b23.tv 短链需要跟随重定向拿到真实 URL，完整视频链接则可以直接提取 BV 号。
     *
     * @return 只保留 type 为 video 的结果；标题已去掉接口返回的 HTML 高亮标签。
     * @throws Exception 接口请求失败或返回的 JSON 结构不符合预期时。
     */
    suspend fun search(keyword: String): List<SearchResult> {
        try {
            ensureWbiReady()

            var actualKeyword = keyword
            if (actualKeyword.contains("https://b23.tv/")) {
                val shortUrl = extractUrl(actualKeyword)
                if (shortUrl != null) {
                    val finalUrl = resolveRedirectUrl(shortUrl)
                    val bv = finalUrl.let { extractBvId(it) }
                    if (bv != null) {
                        actualKeyword = bv
                    }
                }
            } else if (actualKeyword.contains("https://www.bilibili.com/video/BV")) {
                val bv = extractBvId(actualKeyword)
                if (bv != null) {
                    actualKeyword = bv
                }
            }

            val params = encWbi(mutableMapOf("search_type" to "video", "keyword" to actualKeyword))
            val url = "https://api.bilibili.com/x/web-interface/wbi/search/type?" +
                    params.entries.joinToString("&") { "${it.key}=${it.value}" }

            val json = JSONObject(get(url))
            val arr = json.getJSONObject("data").getJSONArray("result")

            return (0 until arr.length())
                .mapNotNull { i ->
                    val o = arr.getJSONObject(i)

                    // 搜索结果混有番剧、直播间等类型，只保留普通视频
                    if (o.optString("type") != "video") {
                        return@mapNotNull null
                    }

                    SearchResult(
                        id = o.getString("bvid"),
                        title = Jsoup.parse(o.getString("title")).text().trim(),
                        author = o.getString("author"),
                        // 接口返回的 pic 是 //i0.hdslb.com/... 这类协议相对地址，需要补上协议头
                        pic = "https:${o.getString("pic")}",
                        duration = formatTime(o.getString("duration")),
                        audioSource = AudioSource.BILI_BILI
                    )
                }
        }catch (e: JSONException) {
            throw Exception("JSON 解析失败（搜索接口）", e)
        } catch (e: Exception) {
            throw Exception("搜索视频失败: ${e.message}", e)
        }
    }


    /**
     * 取视频的 cid（播放地址所需的稿件内部分段标识）。
     *
     * @throws Exception 接口请求失败或返回结构异常时。
     */
    suspend fun getCid(bvid: String): String {
        try {
            ensureWbiReady()

            val json = JSONObject(get("https://api.bilibili.com/x/web-interface/wbi/view?bvid=$bvid"))
            return json.getJSONObject("data").getLong("cid").toString()
        }
        catch (e: Exception) {
            throw Exception("获取Cid失败: ${e.message}", e)
        }
    }

    /**
     * 取音频直链。
     *
     * `fnval=16` 让接口返回 DASH 格式，其中 `dash.audio` 是同一首歌的多条码率音轨；
     * 这里取 `id` 最大的一条（B 站的音轨 id 越大音质越高），再返回它的 `baseUrl`。
     *
     * @param cid 已知的 cid；传 null 时内部再调 [getCid] 查一次。
     * @throws IllegalStateException 响应缺少 data/dash/audio、音轨列表为空或 baseUrl 缺失时。
     */
    suspend fun getAudioUrl(bvid: String, cid: String? = null): String {
        try {
            ensureWbiReady()

            val realCid = cid ?: getCid(bvid)

            val response = get(
                "https://api.bilibili.com/x/player/wbi/playurl" +
                        "?bvid=$bvid&cid=$realCid&fnval=16"
            )

            val json = JSONObject(response)

            val data = json.optJSONObject("data")
                ?: throw IllegalStateException("Response missing data field")

            val dash = data.optJSONObject("dash")
                ?: throw IllegalStateException("Response missing dash field")

            val audios = dash.optJSONArray("audio")
                ?: throw IllegalStateException("Response missing audio field")

            if (audios.length() == 0) {
                throw IllegalStateException("Audio list is empty")
            }

            val bestAudio = (0 until audios.length())
                .map { audios.getJSONObject(it) }
                .maxByOrNull { it.optInt("id", -1) }
                ?: throw IllegalStateException("No valid audio found")

            return bestAudio.optString("baseUrl")
                .takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Audio baseUrl is missing")
        }
        catch (e: Exception) {
            throw IllegalStateException(
                "获取音频播放地址失败(bvid=$bvid, cid=$cid): ${e.message}",
                e
            )
        }
    }
    // ---------------- HTTP（底层请求） ----------------

    /** 发 GET 请求并在 IO 线程读取响应体文本。 */
    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build())
            .execute().use { it.body.string() }
    }

    /**
     * 跟随重定向并返回最终 URL，用于把 b23.tv 短链还原成真正的视频链接。
     * OkHttp 默认跟随重定向，响应对象上的 request 就是重定向后的请求。
     */
    private suspend fun resolveRedirectUrl(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            response.request.url.toString()
        }
    }

    // ---------------- Utils（解析与格式化） ----------------

    /** 计算十六进制小写 MD5，用于 WBI 与音频直链的签名。 */
    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** 把接口返回的 mm:ss / hh:mm:ss 补零成固定宽度；段数不认识时返回占位的 `--:--`。 */
    private fun formatTime(time: String): String {
        val parts = time.split(":")
        return when (parts.size) {
            2 -> parts[0].padStart(2, '0') + ":" + parts[1].padStart(2, '0')
            3 -> parts[0].padStart(2, '0') + ":" +
                    parts[1].padStart(2, '0') + ":" +
                    parts[2].padStart(2, '0')
            else -> "--:--"
        }
    }

    /** 从链接中提取 BV 号；链接里没有视频路径时返回 null。 */
    private fun extractBvId(url: String): String? {
        val regex = Regex("/video/(BV[0-9A-Za-z]+)")
        val match = regex.find(url)
        return match?.groups?.get(1)?.value
    }

    /** 从任意文本中取出第一条 http(s) 链接（分享文案里链接前后可能带其他文字）。 */
    private fun extractUrl(text: String): String? {
        val regex = Regex("https?://[^\\s)]+")
        val match = regex.find(text)
        return match?.value
    }
}
