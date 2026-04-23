package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.utils.base64Encode
import java.math.BigInteger
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import com.google.gson.Gson
import com.zuoguan.bilimusickmp.models.AudioSource
import com.zuoguan.bilimusickmp.models.LyricLine
import com.zuoguan.bilimusickmp.models.SearchResult
import com.zuoguan.bilimusickmp.utils.retry
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.net.URLEncoder
import kotlin.collections.get

object WEAPIEncryptor {
    private const val PUB_EXP = "010001"
    private const val PUB_MOD =
        "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7"
    private const val NONCE_KEY = "0CoJUm6Qyw8W8jud"

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

    private fun randomString(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val rnd = SecureRandom()
        return buildString {
            repeat(16) {
                append(chars[rnd.nextInt(chars.length)])
            }
        }
    }

    private fun aesEncrypt(text: String, key: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(key.toByteArray(), "AES")
        val iv = IvParameterSpec("0102030405060708".toByteArray())
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, iv)
        val encrypted = cipher.doFinal(text.toByteArray())
        return base64Encode(encrypted)
    }

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

class NeteaseService {
    private val client = OkHttpClient()
    private val gson = Gson()

    private fun post(url: String, data: Map<String, String>): String {
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

        client.newCall(request).execute().use {
            return it.body.string()
        }
    }

    suspend fun search(
        s: String,
        searchType: Int = 1,
        offset: Int = 0,
        limit: Int = 10
    ): List<SearchResult> {
        if (s.contains("163cn.tv")) {
            val url = extractUrl(s)!!
            val real = client.newCall(Request.Builder().url(url).build()).execute().request.url
            return searchById(extractSongId(real.toString())!!)
        }

        if (s.contains("song?id=")) {
            return searchById(extractSongId(s)!!)
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
            ) ?: throw Exception("网易云音乐搜索失败")

            val songs = ((json["result"] as Map<*, *>)["songs"] as List<Map<*, *>>)

            return@retry songs.map {
                val id = (it["id"] as Number).toLong().toString()
                val title = it["name"].toString()
                val author = (it["ar"] as List<Map<*, *>>).joinToString(" ") { a -> a["name"].toString() }
                val duration = formatDurationFromMillis((it["dt"] as Number).toInt())
                val imageUrl = getImageUrl(id = id)
                SearchResult(id, title, author, imageUrl, duration, AudioSource.NET_EASE)
            }
        }


    }

    suspend fun searchById(id: String): List<SearchResult> {
        return retry(times = 5) {
            val html = client.newCall(
                Request.Builder()
                    .url("https://music.163.com/song?id=$id")
                    .build()
            ).execute().body.string()

            val doc = Jsoup.parse(html)
            val title = doc.select("meta[property=og:title]").attr("content")
            val artist = doc.select("meta[property=og:music:artist]").attr("content").replace("/", " ")
            val duration = formatDurationFromSeconds(
                doc.select("meta[property=music:duration]").attr("content")
            )
            val image = getImageUrl(id = id)

            return@retry listOf(SearchResult(id, title, artist, image, duration, AudioSource.NET_EASE))
        }
    }

    suspend fun getAudioUrl(id: String): String {
        return retry(times = 5){
            val payload = mapOf("ids" to listOf(id), "level" to "exhigh", "encodeType" to "acc")
            val encrypted = WEAPIEncryptor.encryptRequest(gson.toJson(payload))
            val resp = post("https://music.163.com/weapi/song/enhance/player/url/v1", encrypted)
            val json = gson.fromJson(
                resp,
                Map::class.java
            ) ?: throw Exception("获取音频链接错误")

            val url = (((json["data"] as List<*>)[0] as Map<*, *>)["url"] ?: "").toString()

            return@retry url
        }
    }

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
            ) ?: throw Exception("自动搜索失败")

            val songs = ((json["result"] as Map<*, *>)["songs"] as List<Map<*, *>>)
            for (song in songs) {
                if (song["name"] == title) {
                    val artists = song["ar"] as List<Map<*, *>>
                    if (artists.any { it["name"] == author }) {
                        return@retry (song["id"] as Number).toLong().toString()
                    }
                }
            }
            return@retry ""
        }
    }

    suspend fun getImageUrl(id: String): String {
        val html = client.newCall(
            Request.Builder().url("https://music.163.com/song?id=$id").build()
        ).execute().body.string()

        val doc = Jsoup.parse(html)
        val img = doc.select("meta[property=og:image]").attr("content")
        return img
    }

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

    private fun formatDurationFromSeconds(sec: String): String {
        val s = sec.toIntOrNull() ?: 0
        return "%02d:%02d".format(s / 60, s % 60)
    }

    private fun extractUrl(text: String): String? =
        Regex("https?://[^\\s)]+").find(text)?.value

    private fun extractSongId(url: String): String? =
        Regex("[?&]id=(\\d+)").find(url)?.groupValues?.get(1)
}