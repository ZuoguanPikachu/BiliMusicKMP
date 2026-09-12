package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.LLMConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * 读取 LLM 配置，缺失的项以空串填充。
 *
 * 以扩展函数形式提供，无需改动 [PreferencesStorageService] 接口。
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

/** 写入 LLM 配置的全部键。 */
suspend fun PreferencesStorageService.saveLLMConfig(config: LLMConfig) {
    putString("llm.apiKey", config.apiKey)
    putString("llm.baseUrl", config.baseUrl)
    putString("llm.modelName", config.modelName)
}