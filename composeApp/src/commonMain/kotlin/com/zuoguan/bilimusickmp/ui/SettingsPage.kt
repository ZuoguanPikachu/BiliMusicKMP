package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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

@Composable
fun SettingsPage(
    modifier: Modifier = Modifier,
    viewModel: SettingsPageViewModel = koinInject(),
    contentPadding: PaddingValues = PaddingValues()
) {
    val state by viewModel.uiState.collectAsState()

    var apiKey by remember(state) { mutableStateOf(state.llmConfig.apiKey) }
    var baseUrl by remember(state) { mutableStateOf(state.llmConfig.baseUrl) }
    var modelName by remember(state) { mutableStateOf(state.llmConfig.modelName) }

    var script by remember(state) {mutableStateOf(state.script)}


    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding
    ) {
        item {
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
        }

        item {
            SettingsSection(title = "云同步Script") {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = script,
                        onValueChange = { script = it },
                        modifier = Modifier.fillMaxSize(),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 8,
                    )
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.saveScript(script) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存")
                }
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