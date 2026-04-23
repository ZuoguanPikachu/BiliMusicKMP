package com.zuoguan.bilimusickmp.services

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.zuoguan.bilimusickmp.models.LLMConfig
import com.zuoguan.bilimusickmp.models.SongBaseInfo
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ExtractSongBaseInfoService(
    private val preferencesStorageService: PreferencesStorageService
) {
    private val httpClient = OkHttpClient()
    private val gson = Gson()

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

    suspend fun extractInfo(rawTitle: String): SongBaseInfo {
        val config: LLMConfig = preferencesStorageService.getLLMConfig().first()

        var baseUrl = config.baseUrl
        val apiKey = config.apiKey
        val modelName = config.modelName

        if (baseUrl.isBlank() || apiKey.isBlank() || modelName.isBlank()) {
            return SongBaseInfo()
        }

        if (!baseUrl.endsWith("chat/completions") && !baseUrl.endsWith("chat/completions/")) {
            baseUrl += if (baseUrl.endsWith("/")) "chat/completions" else "/chat/completions"
        }

        val payload = mapOf(
            "model" to modelName,
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to rawTitle)
            ),
            "response_format" to mapOf("type" to "json_object")
        )

        val requestBody = gson.toJson(payload)
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(baseUrl)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        val response = httpClient.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) {
                return SongBaseInfo()
            }

            val json = gson.fromJson(it.body.string(), JsonObject::class.java)
            val content = json["choices"]
                .asJsonArray[0]
                .asJsonObject["message"]
                .asJsonObject["content"]
                .asString

            return gson.fromJson(content, SongBaseInfo::class.java) ?: SongBaseInfo()
        }
    }
}