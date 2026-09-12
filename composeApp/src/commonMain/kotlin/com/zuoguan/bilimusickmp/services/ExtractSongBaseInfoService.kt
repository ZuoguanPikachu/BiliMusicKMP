package com.zuoguan.bilimusickmp.services

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.zuoguan.bilimusickmp.models.LLMConfig
import com.zuoguan.bilimusickmp.models.SongBaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 用 LLM 从视频标题里抽取歌名与歌手。
 *
 * B 站标题格式没有规律（番号、活动标签、前缀后缀混在一起），用正则去猜很容易出错，
 * 所以把这一步交给 LLM：提示词限定「只提取、不猜测」，识别不出来就让字段留空。
 * 下游看到空字段会跳过平台匹配，而不是被一个看似合理的错误歌名带偏。
 *
 * 配置与响应异常都降级为空结果，调用方不需要为识别失败单独做错误处理。
 */
class ExtractSongBaseInfoService(
    private val preferencesStorageService: PreferencesStorageService
) {
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

        var baseUrl = config.baseUrl
        val apiKey = config.apiKey
        val modelName = config.modelName

        // 三项配置缺一不可：任一项为空说明用户还没配好 LLM，直接按"没识别出信息"处理
        if (baseUrl.isBlank() || apiKey.isBlank() || modelName.isBlank()) {
            return SongBaseInfo()
        }

        // baseUrl 允许用户只填到服务根路径，这里补齐 OpenAI 兼容的 chat/completions 路径
        if (!baseUrl.endsWith("chat/completions") && !baseUrl.endsWith("chat/completions/")) {
            baseUrl += if (baseUrl.endsWith("/")) "chat/completions" else "/chat/completions"
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

        val requestBody = gson.toJson(payload)
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(baseUrl)
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
}