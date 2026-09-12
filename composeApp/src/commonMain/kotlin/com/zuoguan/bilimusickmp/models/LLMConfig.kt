package com.zuoguan.bilimusickmp.models

import kotlinx.serialization.Serializable

/**
 * 用于提取歌名 / 歌手的 LLM 接入配置。
 *
 * 三项任一为空时视为未配置，调用方应跳过 LLM 提取并退回空结果。
 *
 * @property apiKey 接口密钥。
 * @property baseUrl 接口基地址；若未以 `chat/completions` 结尾，调用时会自动补全该路径。
 * @property modelName 请求使用的模型名。
 */
@Serializable
data class LLMConfig(
    val apiKey: String = "",
    val baseUrl: String = "",
    val modelName: String = ""
)