package com.zuoguan.bilimusickmp.vm

import androidx.compose.ui.graphics.Color
import com.zuoguan.bilimusickmp.models.DarkMode
import com.zuoguan.bilimusickmp.models.DefaultSeedColor
import com.zuoguan.bilimusickmp.services.PreferencesStorageService
import com.zuoguan.bilimusickmp.services.observeDarkMode
import com.zuoguan.bilimusickmp.services.observeThemeColor
import com.zuoguan.bilimusickmp.services.saveDarkMode
import com.zuoguan.bilimusickmp.services.saveThemeColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 主题状态。
 *
 * 主题色与明暗模式都分「已保存」与「预览中」两层：试色或切换明暗只改内存里的预览值，
 * 界面立即变化但不落盘，因此试用期间既不会写偏好、也不会触发云同步；只有 [confirmPreview]
 * 才保存并同步，[cancelPreview] 则回到已保存的设置。两者共用同一份预览状态，所以一次确认
 * 就能同时保存颜色与明暗，只产生一次推送。
 *
 * 预览只存在于内存，进程退出后自然丢弃，因此也不会同步到其他设备。
 */
class ThemeViewModel(
    private val preferencesStorageService: PreferencesStorageService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _uiState = MutableStateFlow(ThemeUiState())
    val uiState: StateFlow<ThemeUiState> = _uiState

    init {
        scope.launch {
            preferencesStorageService.observeThemeColor().collect { saved ->
                _uiState.update { state ->
                    // 保存成功后已保存值会追上预览值，此时收起预览，界面不会回跳
                    state.copy(
                        savedColor = saved,
                        previewColor = state.previewColor?.takeIf { it != saved }
                    )
                }
            }
        }
        scope.launch {
            preferencesStorageService.observeDarkMode().collect { saved ->
                _uiState.update { state ->
                    state.copy(
                        savedDarkMode = saved,
                        previewDarkMode = state.previewDarkMode?.takeIf { it != saved }
                    )
                }
            }
        }
    }

    /** 试色：只改内存里的预览色，不写偏好、不触发云同步。选中已保存的颜色等于取消这次预览。 */
    fun preview(color: Color) {
        _uiState.update { state ->
            state.copy(previewColor = color.takeIf { it != state.savedColor })
        }
    }

    /** 预览明暗模式，同样不写偏好；选中已保存的模式等于取消这次预览。 */
    fun preview(darkMode: DarkMode) {
        _uiState.update { state ->
            state.copy(previewDarkMode = darkMode.takeIf { it != state.savedDarkMode })
        }
    }

    /** 丢弃全部预览，回到已保存的设置。 */
    fun cancelPreview() {
        _uiState.update { it.copy(previewColor = null, previewDarkMode = null) }
    }

    /** 保存预览中的颜色与明暗（各自只写有变化的项）；没有预览时不做任何事。 */
    fun confirmPreview() {
        val state = _uiState.value
        val color = state.previewColor
        val darkMode = state.previewDarkMode
        if (color == null && darkMode == null) return

        scope.launch {
            // 两次写入各自推进一次同步版本号，云同步的防抖会合并成一次推送
            color?.let { preferencesStorageService.saveThemeColor(it) }
            darkMode?.let { preferencesStorageService.saveDarkMode(it) }
        }
    }
}

/** 主题状态：已保存的设置与尚未保存的预览值。 */
data class ThemeUiState(
    val savedColor: Color = DefaultSeedColor,
    val savedDarkMode: DarkMode = DarkMode.fallback,
    val previewColor: Color? = null,
    val previewDarkMode: DarkMode? = null
) {
    /** 实际用于配色的颜色：预览优先。 */
    val appliedColor: Color get() = previewColor ?: savedColor

    /** 实际用于配色的明暗模式：预览优先。 */
    val appliedDarkMode: DarkMode get() = previewDarkMode ?: savedDarkMode

    /** 是否有尚未保存的预览（颜色或明暗任一）。 */
    val hasPendingPreview: Boolean get() = previewColor != null || previewDarkMode != null
}
