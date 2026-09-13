package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.zuoguan.bilimusickmp.models.LLMConfig
import com.zuoguan.bilimusickmp.ui.theme.themeColorOptions
import com.zuoguan.bilimusickmp.ui.theme.themeSwatchAccentColor
import com.zuoguan.bilimusickmp.ui.theme.themeSwatchContainerColor
import com.zuoguan.bilimusickmp.vm.SettingsPageViewModel
import com.zuoguan.bilimusickmp.vm.ThemeViewModel
import org.koin.compose.koinInject

/**
 * 设置页：选择主题颜色，并编辑 LLM 配置与云同步脚本。
 *
 * 主题色点击即预览（全局立即变色但不落盘），确认后才保存并同步；输入框用本地状态保存
 * 未保存的编辑内容，只有点保存才写回存储，已保存的配置发生变化时再回填到输入框。
 */
@Composable
fun SettingsPage(
    modifier: Modifier = Modifier,
    viewModel: SettingsPageViewModel = koinInject(),
    themeViewModel: ThemeViewModel = koinInject(),
    contentPadding: PaddingValues = PaddingValues()
) {
    val state by viewModel.uiState.collectAsState()
    val themeState by themeViewModel.uiState.collectAsState()

    var apiKey by remember { mutableStateOf(state.llmConfig.apiKey) }
    var baseUrl by remember { mutableStateOf(state.llmConfig.baseUrl) }
    var modelName by remember { mutableStateOf(state.llmConfig.modelName) }
    var script by remember { mutableStateOf(state.script) }

    LaunchedEffect(state.llmConfig) {
        apiKey = state.llmConfig.apiKey
        baseUrl = state.llmConfig.baseUrl
        modelName = state.llmConfig.modelName
    }
    LaunchedEffect(state.script) {
        script = state.script
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
    ) {
        SettingsSection(title = "主题颜色") {
            Column(modifier = Modifier.padding(16.dp)) {
                ThemeColorPicker(
                    selected = themeState.appliedColor,
                    onSelect = { themeViewModel.preview(it) }
                )

                if (themeState.hasPendingPreview) {
                    Spacer(Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { themeViewModel.confirmPreview() }) {
                            Text("保存")
                        }
                        OutlinedButton(onClick = { themeViewModel.cancelPreview() }) {
                            Text("取消")
                        }
                    }
                }
            }
        }

        SettingsSection(title = "LLM 配置") {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Default),
                                onClick = {
                                    apiKey = ""
                                }
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Default),
                                onClick = {
                                    baseUrl = ""
                                }
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        modelName,
                        onValueChange = { modelName = it },
                        label = { Text("Model Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Default),
                                onClick = {
                                    modelName = ""
                                }
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    )
                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.saveConfig(LLMConfig(apiKey, baseUrl, modelName)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("保存")
                    }
                }
        }

        SettingsSection(title = "云同步Script") {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = script,
                    onValueChange = { script = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 8,
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.saveScript(script) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存")
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "同步状态：${state.syncStatus.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.padding(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            content()
        }
    }
}

/**
 * 主题色选择器：把候选种子色平铺成色块，点击即应用。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeColorPicker(
    selected: Color,
    onSelect: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        themeColorOptions.forEach { seedColor ->
            ThemeColorOption(
                seedColor = seedColor,
                selected = seedColor == selected,
                onClick = { onSelect(seedColor) }
            )
        }
    }
}

/**
 * 单个主题色选项。
 *
 * 卡片内的圆形色块用该色相的浅色调绘制，预览应用后的容器色；选中时卡片描边，
 * 并在色块中央显示同色相的对勾。
 */
@Composable
private fun ThemeColorOption(
    seedColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(16.dp)
    val selectionBorder = if (selected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, cardShape)
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(selectionBorder)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(themeSwatchContainerColor(seedColor)),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = themeSwatchAccentColor(seedColor),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}