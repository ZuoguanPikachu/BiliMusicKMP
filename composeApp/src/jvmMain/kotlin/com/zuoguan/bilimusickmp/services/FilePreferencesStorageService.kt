package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.LLMConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import java.io.File
import kotlinx.serialization.json.Json


class FilePreferencesStorageService(
    private val file: File
) : PreferencesStorageService {

    private val _flow = MutableStateFlow(loadFromFile())
    override fun getLLMConfig(): Flow<LLMConfig> = _flow.asStateFlow()

    override suspend fun saveLLMConfig(config: LLMConfig) {
        file.writeText(Json.encodeToString(config))
        _flow.value = config
    }

    private fun loadFromFile(): LLMConfig {
        return if (file.exists()) {
            try {
                Json.decodeFromString(file.readText())
            } catch (_: Exception) {
                LLMConfig()
            }
        } else {
            LLMConfig()
        }
    }
}