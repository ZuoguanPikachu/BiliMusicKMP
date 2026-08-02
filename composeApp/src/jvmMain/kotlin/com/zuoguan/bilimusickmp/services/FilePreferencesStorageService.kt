package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.LLMConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.io.File
import kotlinx.serialization.json.Json
import java.security.MessageDigest


class FilePreferencesStorageService(
    private val llmConfigJsonFile: File,
    private val engine: JsEngineService
) : PreferencesStorageService {
    private val _llmConfigFlow = MutableStateFlow(loadLLMConfigFromFile())
    override fun getLLMConfig(): Flow<LLMConfig> = _llmConfigFlow.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            if (engine.isScriptLoaded) {
                val resp = engine.download("llm_config.md5")
                if (resp.status == 200) {
                    val remoteLLMConfigMD5 = String(resp.body, Charsets.UTF_8)
                    val llmConfigMD5 = getLLMConfigMD5()

                    if (llmConfigMD5 != remoteLLMConfigMD5) {
                        syncLLMConfig()
                    }
                }
            }

            // 添加脚本后，开始云同步
            engine.scriptAddedEvent.collect {
                // 1. 获取云端MD5
                val resp = engine.download("llm_config.md5")
                // 2.1 云端无文件，进行上传
                if (resp.status == 404) {
                    uploadLLMConfig()
                }
                // 2.2 云端已有文件，进行比对
                else if (resp.status == 200) {
                    val remoteLLMConfigMD5 = String(resp.body, Charsets.UTF_8)
                    val llmConfigMD5 = getLLMConfigMD5()

                    // 3.1 MD5不一致，以云端为准，进行同步
                    if (llmConfigMD5 != remoteLLMConfigMD5) {
                        syncLLMConfig()
                    }
                }
            }
        }
    }

    override suspend fun saveLLMConfig(config: LLMConfig) {
        llmConfigJsonFile.writeText(Json.encodeToString(config))
        _llmConfigFlow.value = config

        if (engine.isScriptLoaded) {
            uploadLLMConfig()
        }
    }

    suspend fun uploadLLMConfig() {
        if (llmConfigJsonFile.exists()) {
            engine.uploadFile("llm_config.json", llmConfigJsonFile)
            engine.uploadString("llm_config.md5", getLLMConfigMD5())
        }
    }

    suspend fun syncLLMConfig() {
        val resp = engine.download("llm_config.json")
        llmConfigJsonFile.writeBytes(resp.body)
        _llmConfigFlow.value = loadLLMConfigFromFile()
    }

    fun getLLMConfigMD5(): String {
        var llmConfigJsonText = ""
        llmConfigJsonText = if (llmConfigJsonFile.exists()){
            llmConfigJsonFile.readText()
        } else {
            Json.encodeToString(LLMConfig())
        }
        val digest = MessageDigest.getInstance("MD5").digest(llmConfigJsonText.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun loadLLMConfigFromFile(): LLMConfig {
        return if (llmConfigJsonFile.exists()) {
            try {
                Json.decodeFromString(llmConfigJsonFile.readText())
            } catch (_: Exception) {
                LLMConfig()
            }
        } else {
            LLMConfig()
        }
    }
}