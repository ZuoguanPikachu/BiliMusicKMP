package com.zuoguan.bilimusickmp.vm

import androidx.compose.ui.graphics.Color
import com.zuoguan.bilimusickmp.models.DefaultSeedColor
import com.zuoguan.bilimusickmp.services.PreferencesStorageService
import com.zuoguan.bilimusickmp.services.observeThemeColor
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
 * 主题色分「已保存」与「预览中」两层：点色块只写预览色，界面立即变色但不落盘，
 * 因此试色期间既不会写偏好、也不会触发云同步；只有 [confirmPreview] 才保存并同步，
 * [cancelPreview] 则回到已保存的颜色。
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
                    // 保存成功后已保存值会追上预览色，此时收起预览，界面不会回跳旧色
                    state.copy(
                        savedColor = saved,
                        previewColor = state.previewColor?.takeIf { it != saved }
                    )
                }
            }
        }
    }

    /** 试色：只改内存里的预览色，不写偏好、不触发云同步。选中已保存的颜色等于取消预览。 */
    fun preview(color: Color) {
        _uiState.update { state ->
            state.copy(previewColor = color.takeIf { it != state.savedColor })
        }
    }

    /** 丢弃预览，回到已保存的颜色。 */
    fun cancelPreview() {
        _uiState.update { it.copy(previewColor = null) }
    }

    /** 保存当前预览色；没有预览时不做任何事。 */
    fun confirmPreview() {
        val color = _uiState.value.previewColor ?: return
        scope.launch {
            preferencesStorageService.saveThemeColor(color)
        }
    }
}

/** 主题状态：已保存的颜色与尚未保存的预览色。 */
data class ThemeUiState(
    val savedColor: Color = DefaultSeedColor,
    val previewColor: Color? = null
) {
    /** 实际用于配色的颜色：预览优先。 */
    val appliedColor: Color get() = previewColor ?: savedColor

    /** 是否有尚未保存的预览。 */
    val hasPendingPreview: Boolean get() = previewColor != null
}
