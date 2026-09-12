package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.zuoguan.bilimusickmp.models.LLMConfig
import com.zuoguan.bilimusickmp.vm.SettingsPageViewModel
import org.koin.compose.koinInject

/**
 * 设置页：编辑 LLM 配置与云同步脚本。
 *
 * 输入框用本地状态保存未保存的编辑内容，只有点保存才写回存储；
 * 已保存的配置发生变化时再回填到输入框。
 */
@Composable
fun SettingsPage(
    modifier: Modifier = Modifier,
    viewModel: SettingsPageViewModel = koinInject(),
    contentPadding: PaddingValues = PaddingValues()
) {
    val state by viewModel.uiState.collectAsState()

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