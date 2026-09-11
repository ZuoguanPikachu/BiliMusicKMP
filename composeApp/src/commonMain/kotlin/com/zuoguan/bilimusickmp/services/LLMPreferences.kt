package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.LLMConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * LLM 配置相关的便捷扩展。未来新增偏好项时，在对应文件里追加扩展即可，
 * 不需要改动 [PreferencesStorageService] 接口。
 */
fun PreferencesStorageService.getLLMConfig(): Flow<LLMConfig> = combine(
    observeString("llm.apiKey"),
    observeString("llm.baseUrl"),
    observeString("llm.modelName")
) { apiKey, baseUrl, modelName ->
    LLMConfig(
        apiKey = apiKey ?: "",
        baseUrl = baseUrl ?: "",
        modelName = modelName ?: ""
    )
}

suspend fun PreferencesStorageService.saveLLMConfig(config: LLMConfig) {
    putString("llm.apiKey", config.apiKey)
    putString("llm.baseUrl", config.baseUrl)
    putString("llm.modelName", config.modelName)
}