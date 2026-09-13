package com.zuoguan.bilimusickmp.vm

import com.zuoguan.bilimusickmp.AppVersion
import com.zuoguan.bilimusickmp.models.LLMConfig
import com.zuoguan.bilimusickmp.services.CloudSyncService
import com.zuoguan.bilimusickmp.services.JsEngineService
import com.zuoguan.bilimusickmp.services.PreferencesStorageService
import com.zuoguan.bilimusickmp.services.SyncUiState
import com.zuoguan.bilimusickmp.services.UpdateCheckResult
import com.zuoguan.bilimusickmp.services.UpdateCheckService
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
 * 主题色由 [ThemeViewModel] 单独管理（需要预览/确认两层状态）；
 * 检查更新由用户手动触发，结果只留在内存里。
 */
class SettingsPageViewModel(
    private val preferencesStorageService: PreferencesStorageService,
    private val jsEngineService: JsEngineService,
    private val cloudSyncService: CloudSyncService,
    private val updateCheckService: UpdateCheckService
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

    /**
     * 检查 GitHub Release 是否有新版本。
     *
     * 检查期间重复点击直接忽略；新一轮开始时先清空上次结果，避免「新结果未到、
     * 旧结果还挂在界面上」的误导。检查失败不抛异常，而是作为结果展示。
     */
    fun checkForUpdates() {
        if (_uiState.value.updateCheck.isChecking) return

        _uiState.update {
            it.copy(updateCheck = it.updateCheck.copy(isChecking = true, result = null))
        }
        scope.launch {
            val result = updateCheckService.check()
            _uiState.update {
                it.copy(updateCheck = it.updateCheck.copy(isChecking = false, result = result))
            }
        }
    }
}


data class SettingsUiState(
    val llmConfig: LLMConfig = LLMConfig(),
    val script: String = "",
    val syncStatus: SyncUiState = SyncUiState(),
    val updateCheck: UpdateCheckState = UpdateCheckState()
)

/**
 * 检查更新状态。
 *
 * @property currentVersion 当前应用版本，取自构建期生成的 [AppVersion.NAME]。
 * @property isChecking 是否正在请求中，用于禁用按钮与展示进度。
 * @property result 最近一次检查结果；null 表示本次进入页面后还没查过。
 */
data class UpdateCheckState(
    val currentVersion: String = AppVersion.NAME,
    val isChecking: Boolean = false,
    val result: UpdateCheckResult? = null
)