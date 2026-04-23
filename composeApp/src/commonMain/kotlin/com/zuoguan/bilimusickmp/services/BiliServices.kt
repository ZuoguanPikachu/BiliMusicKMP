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

class BiliService {
    private val cookieJar = SimpleCookieJar()
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor {
            val req = it.request().newBuilder()
                .header("Referer", "https://www.bilibili.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36")
                .build()
            it.proceed(req)
        }.build()

    @Volatile
    private var imgKey = ""

    @Volatile
    private var subKey = ""
    private val wbiMutex = Mutex()
    private var wbiInitialized = false

    private val mixinKeyEncTab = intArrayOf(
        46,47,18,2,53,8,23,32,15,50,10,31,58,3,45,35,27,43,5,49,
        33,9,42,19,29,28,14,39,12,38,41,13,37,48,7,16,24,55,40,
        61,26,17,0,1,60,51,30,4,22,25,54,21,56,59,6,63,57,62,11,
        36,20,34,44,52
    )

    init {
        cookieJar.set("b_nut", System.currentTimeMillis().toString(), "bilibili.com")
    }

    // ---------------- WBI ----------------

    private suspend fun ensureWbiReady() {
        if (wbiInitialized) return

        wbiMutex.withLock {
            if (wbiInitialized) return

            try {
                withContext(Dispatchers.IO) {
                    refreshWbiKeys()
                }
                wbiInitialized = true
            } catch (e: Exception) {
                throw Exception("无法获取WBI签名密钥: ${e.message}", e)
            }
        }
    }

    private fun refreshWbiKeys() {
        val json = get("https://api.bilibili.com/x/web-interface/nav")
        val data = JSONObject(json).getJSONObject("data").getJSONObject("wbi_img")

        imgKey = data.getString("img_url").substringAfterLast("/").substringBefore(".")
        subKey = data.getString("sub_url").substringAfterLast("/").substringBefore(".")
    }

    private fun getMixinKey(orig: String): String =
        mixinKeyEncTab.joinToString("") { orig[it].toString() }.substring(0, 32)

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

    // ---------------- API ----------------

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

                    if (o.optString("type") != "video") {
                        return@mapNotNull null
                    }

                    SearchResult(
                        id = o.getString("bvid"),
                        title = Jsoup.parse(o.getString("title")).text().trim(),
                        author = o.getString("author"),
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

    suspend fun getAudioUrl(bvid: String, cid: String? = null): String {
        try {
            ensureWbiReady()

            val realCid = cid ?: getCid(bvid)
            val json = JSONObject(get("https://api.bilibili.com/x/player/wbi/playurl?bvid=$bvid&cid=$realCid&fnval=16"))
            val audios = json.getJSONObject("data").getJSONObject("dash").getJSONArray("audio")
            return audios.getJSONObject(0).getString("baseUrl")
        }
        catch (e: Exception) {
            throw Exception("获取音频播放地址失败: ${e.message}", e)
        }

    }

    // ---------------- HTTP ----------------

    private fun get(url: String): String =
        client.newCall(Request.Builder().url(url).build())
            .execute().use { it.body.string() }

    private fun resolveRedirectUrl(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            return response.request.url.toString()
        }
    }

    // ---------------- Utils ----------------

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun formatTime(time: String): String {
        val parts = time.split(":")
        if (parts.size != 2) return "00:00"
        return parts[0].padStart(2, '0') + ":" + parts[1].padStart(2, '0')
    }

    private fun extractBvId(url: String): String? {
        val regex = Regex("/video/(BV[0-9A-Za-z]+)")
        val match = regex.find(url)
        return match?.groups?.get(1)?.value
    }

    private fun extractUrl(text: String): String? {
        val regex = Regex("https?://[^\\s)]+")
        val match = regex.find(text)
        return match?.value
    }
}
