package com.zuoguan.bilimusickmp.services

import androidx.annotation.RequiresApi
import com.zuoguan.bilimusickmp.models.AudioSource
import com.zuoguan.bilimusickmp.models.LyricLine
import com.zuoguan.bilimusickmp.models.SearchResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.*
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import kotlin.collections.get
import kotlin.io.encoding.Base64

class KuGouService {
    private val client = OkHttpClient()

    private val defaultHeaders = mapOf(
        "User-Agent" to "IPhone-8990-searchSong",
        "UNI-UserAgent" to "iOS11.4-Phone8990-1009-0-WiFi"
    )

    fun search(keyword: String, pageSize: Int = 10, page: Int = 1): List<SearchResult> {
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

        val request = Request.Builder()
            .url(url)
            .apply {
                defaultHeaders.forEach { (k, v) -> addHeader(k, v) }
            }
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            val json = Json.parseToJsonElement(body).jsonObject

            val songs = json["data"]!!
                .jsonObject["info"]!!
                .jsonArray

            return songs.map { item ->
                val song = item.jsonObject

                val hash = song["hash"]!!.jsonPrimitive.content

                SearchResult(
                    id = hash,
                    title = song["songname"]!!.jsonPrimitive.content,
                    author = song["singername"]!!
                        .jsonPrimitive.content
                        .replace("、", " "),
                    duration = formatDurationFromSeconds(song["duration"]!!.jsonPrimitive.int),
                    pic = getImageUrl(hash),
                    audioSource = AudioSource.KU_GOU
                )
            }
        }
    }

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

        val request = Request.Builder()
            .url(url)
            .apply {
                defaultHeaders.forEach { (k, v) -> addHeader(k, v) }
            }
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            val json = Json.parseToJsonElement(body).jsonObject

            val songs = json["data"]!!
                .jsonObject["info"]!!
                .jsonArray

            for (item in songs) {
                val song = item.jsonObject

                if (song["songname"]!!.jsonPrimitive.content == title) {
                    val artists =  song["singername"]!!.jsonPrimitive.content
                    if (artists.contains(author))
                    {
                        return song["hash"]!!.jsonPrimitive.content
                    }
                }
            }

        }

        return ""
    }

    fun getAudioUrl(id: String): String {
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

        val request = Request.Builder()
            .url("http://media.store.kugou.com/v1/get_res_privilege")
            .post(requestBody)
            .apply {
                defaultHeaders.forEach { (k, v) -> addHeader(k, v) }
            }
            .build()

        val songHash: String

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            val json = Json.parseToJsonElement(body).jsonObject

            val data = json["data"]!!.jsonArray
            val song = data[0]
                .jsonObject["relate_goods"]!!
                .jsonArray[0]
                .jsonObject

            songHash = song["hash"]!!.jsonPrimitive.content
        }

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

        val playRequest = Request.Builder()
            .url(url)
            .apply {
                defaultHeaders.forEach { (k, v) -> addHeader(k, v) }
            }
            .build()

        client.newCall(playRequest).execute().use { response ->
            val body = response.body.string()
            val json = Json.parseToJsonElement(body).jsonObject

            val urlElement = json["url"]!!

            return when {
                urlElement is JsonArray -> {
                    urlElement[0].jsonPrimitive.content
                }
                else -> {
                    urlElement.jsonPrimitive.content
                }
            }
        }
    }

    fun getImageUrl(id: String): String {
        val url = HttpUrl.Builder()
            .scheme("http")
            .host("m.kugou.com")
            .addPathSegments("app/i/getSongInfo.php")
            .addQueryParameter("cmd", "playInfo")
            .addQueryParameter("hash", id)
            .addQueryParameter("from", "mkugou")
            .build()

        val request = Request.Builder()
            .url(url)
            .apply {
                defaultHeaders.forEach { (k, v) -> addHeader(k, v) }
            }
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            val json = Json.parseToJsonElement(body).jsonObject

            return json["imgUrl"]!!.jsonPrimitive.content
        }
    }

    fun getLyric(id: String): List<LyricLine> {
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

        val searchRequest = Request.Builder()
            .url(searchUrl)
            .apply {
                defaultHeaders.forEach { (k, v) -> addHeader(k, v) }
            }
            .build()

        val accessKey: String
        val lyricId: String

        client.newCall(searchRequest).execute().use { response ->
            val body = response.body.string()
            val json = Json.parseToJsonElement(body).jsonObject

            val candidate = json["candidates"]!!
                .jsonArray[0]
                .jsonObject

            accessKey = candidate["accesskey"]!!.jsonPrimitive.content
            lyricId = candidate["id"]!!.jsonPrimitive.content
        }

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

        val lyricRequest = Request.Builder()
            .url(lyricUrl)
            .apply {
                defaultHeaders.forEach { (k, v) -> addHeader(k, v) }
            }
            .build()

        client.newCall(lyricRequest).execute().use { response ->
            val body = response.body.string()
            val json = Json.parseToJsonElement(body).jsonObject

            val content = json["content"]!!.jsonPrimitive.content

            return parseLyrics(Base64.decode(content).decodeToString())
        }
    }

    fun parseLyrics(lrcContent: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        val regex = """\[(\d{2}):(\d{2}\.\d{2})]""".toRegex()

        lrcContent.lines().forEach { line ->
            val matches = regex.findAll(line).toList()
            if (matches.isEmpty()) return@forEach

            val text = line.replace(regex, "").trim()
            if (text.isEmpty()) return@forEach

            matches.forEach { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val secondsParts = match.groupValues[2].split(".")
                val seconds = secondsParts.getOrNull(0)?.toLongOrNull() ?: 0L
                val millis = secondsParts.getOrNull(1)?.toLongOrNull() ?: 0L

                val timeMs = minutes * 60_000 + seconds * 1_000 + millis * 10
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