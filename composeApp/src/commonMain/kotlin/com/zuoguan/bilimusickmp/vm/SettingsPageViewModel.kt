package com.zuoguan.bilimusickmp.vm

import com.zuoguan.bilimusickmp.models.LLMConfig
import com.zuoguan.bilimusickmp.services.CloudSyncService
import com.zuoguan.bilimusickmp.services.JsEngineService
import com.zuoguan.bilimusickmp.services.PreferencesStorageService
import com.zuoguan.bilimusickmp.services.SyncUiState
import com.zuoguan.bilimusickmp.services.getLLMConfig
import com.zuoguan.bilimusickmp.services.saveLLMConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsPageViewModel(
    private val preferencesStorageService: PreferencesStorageService,
    private val jsEngineService: JsEngineService,
    private val cloudSyncService: CloudSyncService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        observeStorage()
    }

    private fun observeStorage() {
        scope.launch {
            preferencesStorageService.getLLMConfig().collect { config ->
                _uiState.update { it.copy(llmConfig = config) }
            }
        }
        scope.launch {
            jsEngineService.getScript().collect { script ->
                _uiState.update { it.copy(script = script) }
            }
        }
        scope.launch {
            cloudSyncService.status.collect { status ->
                _uiState.update { it.copy(syncStatus = status) }
            }
        }
    }

    fun saveConfig(config: LLMConfig) {
        scope.launch {
            preferencesStorageService.saveLLMConfig(config)
        }
    }

    fun saveScript(script: String) {
        scope.launch {
            jsEngineService.saveScript(script)
        }
    }

    fun syncNow() {
        scope.launch {
            cloudSyncService.syncNow()
        }
    }
}


data class SettingsUiState(
    val llmConfig: LLMConfig = LLMConfig(),
    val script: String = "",
    val syncStatus: SyncUiState = SyncUiState()
)