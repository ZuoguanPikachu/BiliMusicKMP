package com.zuoguan.bilimusickmp.models

import kotlinx.serialization.Serializable

@Serializable
data class LLMConfig(
    val apiKey: String = "",
    val baseUrl: String = "",
    val modelName: String = ""
)