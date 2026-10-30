package com.zuoguan.bilimusickmp.vm

import com.zuoguan.bilimusickmp.AppVersion
import com.zuoguan.bilimusickmp.models.LLMConfig
import com.zuoguan.bilimusickmp.services.CloudSyncService
import com.zuoguan.bilimusickmp.services.ExtractSongBaseInfoService
import com.zuoguan.bilimusickmp.services.JsEngineService
import com.zuoguan.bilimusickmp.services.LlmConnectionResult
import com.zuoguan.bilimusickmp.services.PreferencesStorageService
import com.zuoguan.bilimusickmp.services.SongCacheService
import com.zuoguan.bilimusickmp.services.SyncUiState
import com.zuoguan.bilimusickmp.services.UpdateCheckResult
import com.zuoguan.bilimusickmp.services.UpdateCheckService
import com.zuoguan.bilimusickmp.services.getLLMConfig
import com.zuoguan.bilimusickmp.services.saveLLMConfig
import com.zuoguan.bilimusickmp.utils.UiEvent
import com.zuoguan.bilimusickmp.utils.formatBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 设置页状态。
 *
 * 汇集 LLM 配置、云同步脚本、同步状态与缓存开关，并把编辑结果写回各自的存储服务。
 * 主题色由 [ThemeViewModel] 单独管理（需要预览/确认两层状态）；
 * 检查更新由用户手动触发，结果只留在内存里。
 * 缓存 / 下载的两个开关只写本机偏好，不参与云同步。
 */
class SettingsPageViewModel(
    private val preferencesStorageService: PreferencesStorageService,
    private val jsEngineService: JsEngineService,
    private val cloudSyncService: CloudSyncService,
    private val updateCheckService: UpdateCheckService,
    private val extractSongBaseInfoService: ExtractSongBaseInfoService,
    private val songCacheService: SongCacheService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _uiState = MutableStateFlow(
        // 草稿直接用引擎里已有的脚本初始化，避免界面先闪一帧「脚本是空的」
        SettingsUiState(script = jsEngineService.currentScript())
    )
    val uiState: StateFlow<SettingsUiState> = _uiState

    private val _uiEvents = Channel<UiEvent>(Channel.BUFFERED)
    /** 一次性 UI 事件（脚本保存结果等）。 */
    val uiEvents = _uiEvents.receiveAsFlow()

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
            jsEngineService.getScriptError().collect { error ->
                _uiState.update { it.copy(scriptError = error) }
            }
        }
        scope.launch {
            cloudSyncService.status.collect { status ->
                _uiState.update { it.copy(syncStatus = status) }
            }
        }
        observeCache()
    }

    /**
     * 缓存开关与缓存占用的状态。
     *
     * 两个开关只存在本机（键名以 `local.` 开头），不会随云同步跑到别的设备上：
     * 是否缓存取决于各自的存储与流量情况。下载到音乐目录属于系统音乐库的事，
     * 设置页不管它。
     */
    private fun observeCache() {
        scope.launch {
            songCacheService.enabled.collect { enabled ->
                _uiState.update { it.copy(cache = it.cache.copy(enabled = enabled)) }
            }
        }
        scope.launch {
            songCacheService.wifiOnly.collect { wifiOnly ->
                _uiState.update { it.copy(cache = it.cache.copy(wifiOnly = wifiOnly)) }
            }
        }
        scope.launch {
            songCacheService.cacheSizeBytes.collect { size ->
                _uiState.update { it.copy(cache = it.cache.copy(cacheSizeBytes = size)) }
            }
        }
        scope.launch {
            songCacheService.cachingIds.collect { ids ->
                _uiState.update { it.copy(cache = it.cache.copy(activeTransferCount = ids.size)) }
            }
        }
    }

    /** 保存 LLM 配置到偏好存储；结果用 Snackbar 反馈。 */
    fun saveConfig(config: LLMConfig) {
        scope.launch {
            try {
                preferencesStorageService.saveLLMConfig(config)
                _uiEvents.send(UiEvent.ShowSnackBar("LLM 配置已保存"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiEvents.send(UiEvent.ShowSnackBar("LLM 配置保存失败：${e.message ?: "未知错误"}"))
            }
        }
    }

    /**
     * 用当前输入测试一次 LLM 连接。
     *
     * 测的是界面上的草稿而不是已保存的配置，方便「先测通再保存」；
     * 测试期间重复点击直接忽略。结果连同被测配置一起记下，
     * 界面据此判断要不要展示——输入改过之后旧结果就不再显示。
     */
    fun testLlmConnection(config: LLMConfig) {
        if (_uiState.value.llmTest.isTesting) return

        _uiState.update {
            it.copy(llmTest = LlmTestState(isTesting = true, testedConfig = config))
        }
        scope.launch {
            val result = extractSongBaseInfoService.testConnection(config)
            _uiState.update {
                it.copy(
                    llmTest = LlmTestState(
                        isTesting = false,
                        testedConfig = config,
                        result = result
                    )
                )
            }
        }
    }

    /**
     * 保存云同步脚本到脚本引擎存储。
     *
     * 保存会重建 JS 引擎并触发一次同步，结果用 Snackbar 反馈；
     * 脚本本身的语法错误由 [SettingsUiState.scriptError] 持续展示。
     */
    fun saveScript(script: String) {
        scope.launch {
            try {
                jsEngineService.saveScript(script)
                _uiEvents.send(UiEvent.ShowSnackBar("脚本已保存，正在同步…"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiEvents.send(UiEvent.ShowSnackBar("脚本保存失败：${e.message ?: "未知错误"}"))
            }
        }
    }

    /** 立即同步一次，不等防抖计时。 */
    fun syncNow() {
        scope.launch {
            cloudSyncService.syncNow()
        }
    }

    /** 开关「播放时自动缓存」。改动立刻落盘，不需要点保存。 */
    fun setCacheEnabled(enabled: Boolean) {
        scope.launch {
            songCacheService.setEnabled(enabled)
        }
    }

    /** 开关「仅在 Wi-Fi 下自动缓存」。 */
    fun setCacheWifiOnly(wifiOnly: Boolean) {
        scope.launch {
            songCacheService.setWifiOnly(wifiOnly)
        }
    }

    /** 清空缓存目录，结果用 Snackbar 反馈。 */
    fun clearCache() {
        scope.launch {
            try {
                val freed = songCacheService.clearCache()
                _uiEvents.send(UiEvent.ShowSnackBar("已清理缓存，释放 ${formatBytes(freed)}"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiEvents.send(UiEvent.ShowSnackBar("清理缓存失败：${e.message ?: "未知错误"}"))
            }
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
    val llmTest: LlmTestState = LlmTestState(),
    val script: String = "",
    /** 脚本最近一次求值失败的原因；null 表示当前脚本可用。 */
    val scriptError: String? = null,
    val syncStatus: SyncUiState = SyncUiState(),
    val updateCheck: UpdateCheckState = UpdateCheckState(),
    val cache: CacheUiState = CacheUiState()
)

/**
 * 缓存区块的状态。下载到音乐目录不归设置页管，因此这里只有缓存相关字段。
 *
 * @property enabled 是否在播放时自动缓存。
 * @property wifiOnly 是否只在 Wi-Fi 下自动缓存。
 * @property cacheSizeBytes 缓存目录占用字节数。
 * @property activeTransferCount 正在后台缓存的歌曲数。
 */
data class CacheUiState(
    val enabled: Boolean = false,
    val wifiOnly: Boolean = true,
    val cacheSizeBytes: Long = 0L,
    val activeTransferCount: Int = 0
)

/**
 * 「测试连接」状态。
 *
 * @property isTesting 是否正在请求中，用于禁用按钮与展示进度。
 * @property testedConfig 这次结果对应的配置；与当前输入不一致时界面不展示旧结果。
 * @property result 最近一次测试结果；null 表示还没测过。
 */
data class LlmTestState(
    val isTesting: Boolean = false,
    val testedConfig: LLMConfig? = null,
    val result: LlmConnectionResult? = null
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