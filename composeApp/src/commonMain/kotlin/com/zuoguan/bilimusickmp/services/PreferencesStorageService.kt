package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.LLMConfig
import kotlinx.coroutines.flow.Flow

interface  PreferencesStorageService {
    fun getLLMConfig(): Flow<LLMConfig>
    suspend fun saveLLMConfig(config: LLMConfig)
}