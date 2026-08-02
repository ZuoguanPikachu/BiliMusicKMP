package com.zuoguan.bilimusickmp.services

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zuoguan.bilimusickmp.models.LLMConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.io.File


private val Context.dataStore by preferencesDataStore(name = "settings")

class AndroidPreferencesStorageService(
    private val context: Context,
    private val engine: JsEngineService
) : PreferencesStorageService {
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

    private object Keys {
        val API_KEY = stringPreferencesKey("llm_api_key")
        val BASE_URL = stringPreferencesKey("llm_base_url")
        val MODEL_NAME = stringPreferencesKey("llm_model_name")
    }

    override fun getLLMConfig(): Flow<LLMConfig> =
        context.dataStore.data.map { prefs ->
            LLMConfig(
                apiKey = prefs[Keys.API_KEY] ?: "",
                baseUrl = prefs[Keys.BASE_URL] ?: "",
                modelName = prefs[Keys.MODEL_NAME] ?: ""
            )
        }

    override suspend fun saveLLMConfig(config: LLMConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.API_KEY] = config.apiKey
            prefs[Keys.BASE_URL] = config.baseUrl
            prefs[Keys.MODEL_NAME] = config.modelName
        }
    }

    suspend fun getLLMConfigMD5(): String {
        val config = getLLMConfig().first()
        val jsonText = Json.encodeToString(config)

        return MessageDigest
            .getInstance("MD5")
            .digest(jsonText.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    suspend fun uploadLLMConfig() {
        val llmConfigJsonFile = File(context.filesDir, "llm_config.json")
        llmConfigJsonFile.writeText(Json.encodeToString(getLLMConfig().first()))
        engine.uploadFile("llm_config.json", llmConfigJsonFile)
        engine.uploadString("llm_config.md5", getLLMConfigMD5())

        llmConfigJsonFile.delete()
    }

    suspend fun syncLLMConfig() {
        val resp = engine.download("llm_config.json")
        val config: LLMConfig = Json.decodeFromString(String(resp.body, Charsets.UTF_8))
        saveLLMConfig(config)
    }
}