package com.zuoguan.bilimusickmp.services

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zuoguan.bilimusickmp.models.LLMConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map


private val Context.dataStore by preferencesDataStore(name = "settings")

class AndroidPreferencesStorageService(
    private val context: Context
) : PreferencesStorageService {
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
}