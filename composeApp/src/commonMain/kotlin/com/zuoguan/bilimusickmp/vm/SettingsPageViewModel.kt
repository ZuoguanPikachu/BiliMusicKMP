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

/**
 * 设置页状态。
 *
 * 汇集 LLM 配置、云同步脚本与同步状态，并把编辑结果写回各自的存储服务。
 * 主题色由 [ThemeViewModel] 单独管理（需要预览/确认两层状态）。
 */
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

    /** 保存 LLM 配置到偏好存储。 */
    fun saveConfig(config: LLMConfig) {
        scope.launch {
            preferencesStorageService.saveLLMConfig(config)
        }
    }

    /** 保存云同步脚本到脚本引擎存储。 */
    fun saveScript(script: String) {
        scope.launch {
            jsEngineService.saveScript(script)
        }
    }
}


data class SettingsUiState(
    val llmConfig: LLMConfig = LLMConfig(),
    val script: String = "",
    val syncStatus: SyncUiState = SyncUiState()
)