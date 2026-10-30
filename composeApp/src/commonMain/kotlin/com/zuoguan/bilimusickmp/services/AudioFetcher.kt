package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.AudioSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 音频直链的下载与格式推断，缓存与「下载到音乐目录」共用同一套实现。
 *
 * 两家都必须带上与播放完全一致的请求头（[AudioRequestHeaders]），否则会出现
 * 「能播不能下」；直链的扩展名与 MIME 也在这里统一推断，避免各处各写一份。
 */
internal object AudioFetcher {

    private val httpClient = OkHttpClient()

    /**
     * 拉取整段音频。
     *
     * @throws IllegalStateException HTTP 非 2xx 时抛出，由调用方决定提示还是忽略。
     */
    suspend fun fetch(url: String, audioSource: AudioSource): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .apply {
                for ((name, value) in AudioRequestHeaders.forSource(audioSource, url)) {
                    header(name, value)
                }
            }
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("下载失败：HTTP ${response.code}")
            }
            response.body.bytes()
        }
    }

    /** 取不到可用扩展名时的兜底：播放器靠内容嗅探，扩展名只影响提取器排序。 */
    const val FALLBACK_EXTENSION = "m4a"

    /**
     * 从音频地址里取扩展名。
     *
     * 直链常带 `?vkey=...` 之类的查询串，必须先截掉再取最后一段；取不到合法扩展名时
     * 退回 [FALLBACK_EXTENSION]。
     */
    fun extensionOf(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val extension = path.substringAfterLast('/', "").substringAfterLast('.', "")
        return if (extension.length in 1..5 && extension.all { it.isLetterOrDigit() }) {
            extension.lowercase()
        } else {
            FALLBACK_EXTENSION
        }
    }

    /** 扩展名 → MIME 类型；系统音乐库按它归类，认不出时按 mp3 处理。 */
    fun mimeTypeOf(extension: String): String = when (extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a", "m4s", "mp4" -> "audio/mp4"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "ogg", "oga" -> "audio/ogg"
        "opus" -> "audio/opus"
        "aac" -> "audio/aac"
        "weba", "webm" -> "audio/webm"
        else -> "audio/mpeg"
    }
}
