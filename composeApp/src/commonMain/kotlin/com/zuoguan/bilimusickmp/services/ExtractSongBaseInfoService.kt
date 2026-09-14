package com.zuoguan.bilimusickmp.services

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.zuoguan.bilimusickmp.models.LLMConfig
import com.zuoguan.bilimusickmp.models.SongBaseInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 一次「测试连接」的结果。
 *
 * 每种失败单独成型，是为了让设置页能给出可操作的提示（Key 错了？地址错了？还是被限流），
 * 而不是笼统的一句「测试失败」。
 */
sealed interface LlmConnectionResult {
    /** 接口连通且有模型输出；[reply] 是模型返回内容的开头，用来确认这确实是聊天接口。 */
    data class Success(val reply: String) : LlmConnectionResult

    /** 三项配置没填全，无从测试。 */
    data object Incomplete : LlmConnectionResult

    /** 鉴权失败：Key 不对，或该 Key 没有这个模型的权限。 */
    data object Unauthorized : LlmConnectionResult

    /** 找不到接口：Base URL 指向了不存在的路径。 */
    data object NotFound : LlmConnectionResult

    /** 被限流，稍后再试。 */
    data object RateLimited : LlmConnectionResult

    /** 其它 HTTP 错误；[body] 是响应体开头，便于对照服务商的错误说明。 */
    data class HttpError(val code: Int, val body: String) : LlmConnectionResult

    /** 连不上：断网、DNS、超时等。 */
    data class NetworkError(val message: String) : LlmConnectionResult

    /** 连上了但响应不符合 OpenAI 接口格式，多半是 Base URL 指到了别的服务。 */
    data class BadResponse(val message: String) : LlmConnectionResult
}

/**
 * 用 LLM 从视频标题里抽取歌名与歌手。
 *
 * B 站标题格式没有规律（番号、活动标签、前缀后缀混在一起），用正则去猜很容易出错，
 * 所以把这一步交给 LLM：提示词限定「只提取、不猜测」，识别不出来就让字段留空。
 * 下游看到空字段会跳过平台匹配，而不是被一个看似合理的错误歌名带偏。
 *
 * 配置与响应异常都降级为空结果，调用方不需要为识别失败单独做错误处理。
 * 代价是配置填错时界面上看不出任何异常，因此另有 [testConnection] 供设置页主动验证。
 */
class ExtractSongBaseInfoService(
    private val preferencesStorageService: PreferencesStorageService
) {
    companion object {
        /** 测试连接用的提示词：只要一个极小的 JSON，够验证链路即可。 */
        private const val TEST_PROMPT = """只输出 JSON：{"ok": true}"""

        /** HTTP 错误时最多带回多少字符的响应体。 */
        private const val ERROR_BODY_MAX = 200

        private val JSON_MEDIA_TYPE: MediaType = "application/json; charset=utf-8".toMediaType()
    }

    private val httpClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val gson = Gson()

    // 提示词要求以固定结构的 JSON 返回，并把「不确定就留空」写成硬性规则：
    // 这是后续所有降级逻辑的前提 —— 空字段表示没识别出来，而不是某种猜测结果。
    private val systemPrompt = """
        你是一个API接口，从用户提交的文本中提取歌曲名和作者，以JSON格式返回：{"title": "songName", "author": "authorName"}。
        - 输入是一段文本，可能包含歌曲名和作者的描述，或仅部分信息。
        - 如果无法明确识别歌曲名或作者，对应字段返回空字符串""。
        - 只从原文本中提取信息，不得进行猜测。
        - 如果文本中无相关信息，返回{"title": "", "author": ""}。
        示例：
        - 输入："【4K60FPS】陈奕迅《人来人往》让人泪目的现场！你还相信爱情吗？" → 输出：{"title": "人来人往", "author": "陈奕迅"}
        - 输入："人来人往 翻唱" → 输出：{"title": "人来人往", "author": ""}
        - 输入："人来人往的街道" → 输出：{"title": "", "author": ""}
    """.trimIndent()

    /**
     * 从 [rawTitle] 中抽取歌名与歌手。
     *
     * @return 未配置 LLM、接口返回非 2xx 或响应结构异常时返回空的 [SongBaseInfo]；
     *   网络异常仍会向上抛出，由调用方决定怎么降级。
     */
    suspend fun extractInfo(rawTitle: String): SongBaseInfo {
        val config: LLMConfig = preferencesStorageService.getLLMConfig().first()

        val apiKey = config.apiKey
        val modelName = config.modelName

        // 三项配置缺一不可：任一项为空说明用户还没配好 LLM，直接按"没识别出信息"处理
        if (config.baseUrl.isBlank() || apiKey.isBlank() || modelName.isBlank()) {
            return SongBaseInfo()
        }

        val payload = mapOf(
            "model" to modelName,
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to rawTitle)
            ),
            // 让服务端直接输出 JSON 对象，省去从 markdown 代码块里再抠内容的麻烦
            "response_format" to mapOf("type" to "json_object")
        )

        val requestBody = gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(chatCompletionsUrl(config.baseUrl))
            .post(requestBody)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
        response.use {
            if (!it.isSuccessful) {
                return SongBaseInfo()
            }

            // 从 choices[0].message.content 里取模型输出；任何一层缺失都算识别失败
            val content = try {
                gson.fromJson(it.body.string(), JsonObject::class.java)
                    ?.getAsJsonArray("choices")
                    ?.firstOrNull()
                    ?.asJsonObject
                    ?.getAsJsonObject("message")
                    ?.get("content")
                    ?.asString
            } catch (e: Exception) {
                null
            } ?: return SongBaseInfo()

            // 模型输出的仍是字符串，需要再解析成 SongBaseInfo；解析失败同样返回空结果
            return try {
                gson.fromJson(content, SongBaseInfo::class.java) ?: SongBaseInfo()
            } catch (e: Exception) {
                SongBaseInfo()
            }
        }
    }

    /**
     * 用 [config] 发一次最小请求，供设置页的「测试连接」使用。
     *
     * 与 [extractInfo] 相反，这里不吞掉失败：鉴权、地址、限流、网络与响应格式分别成型。
     * 正式抽取时这些错误都会被静默降级成「没识别出信息」，光看播放页只会发现歌词封面没了，
     * 根本猜不到是配置写错了，所以才需要这样一个可以主动验证的入口。
     */
    suspend fun testConnection(config: LLMConfig): LlmConnectionResult {
        if (config.baseUrl.isBlank() || config.apiKey.isBlank() || config.modelName.isBlank()) {
            return LlmConnectionResult.Incomplete
        }

        val payload = mapOf(
            "model" to config.modelName,
            "messages" to listOf(
                mapOf("role" to "system", "content" to TEST_PROMPT),
                mapOf("role" to "user", "content" to "ping")
            ),
            "response_format" to mapOf("type" to "json_object")
        )

        val request = Request.Builder()
            .url(chatCompletionsUrl(config.baseUrl))
            .post(gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .build()

        val response = try {
            withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return LlmConnectionResult.NetworkError(e.message ?: "网络异常")
        }

        val (status, body) = response.use { it.code to it.body.string() }

        when {
            status == 401 || status == 403 -> return LlmConnectionResult.Unauthorized
            status == 404 -> return LlmConnectionResult.NotFound
            status == 429 -> return LlmConnectionResult.RateLimited
            status !in 200..299 ->
                return LlmConnectionResult.HttpError(status, body.trim().take(ERROR_BODY_MAX))
        }

        val content = try {
            gson.fromJson(body, JsonObject::class.java)
                ?.getAsJsonArray("choices")
                ?.firstOrNull()
                ?.asJsonObject
                ?.getAsJsonObject("message")
                ?.get("content")
                ?.asString
        } catch (e: Exception) {
            null
        } ?: return LlmConnectionResult.BadResponse("响应里没有 choices[0].message.content")

        return LlmConnectionResult.Success(content.trim())
    }

    /** 补齐 OpenAI 兼容的 chat/completions 路径：允许 Base URL 只填到服务根路径。 */
    private fun chatCompletionsUrl(baseUrl: String): String =
        if (baseUrl.endsWith("chat/completions") || baseUrl.endsWith("chat/completions/")) {
            baseUrl
        } else {
            baseUrl + if (baseUrl.endsWith("/")) "chat/completions" else "/chat/completions"
        }
}
