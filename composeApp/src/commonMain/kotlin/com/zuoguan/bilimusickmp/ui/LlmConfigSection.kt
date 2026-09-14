package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zuoguan.bilimusickmp.models.LLMConfig
import com.zuoguan.bilimusickmp.services.LlmConnectionResult
import com.zuoguan.bilimusickmp.vm.LlmTestState

/**
 * LLM 配置区块：状态、用途说明、三个配置项，以及「测试连接」。
 *
 * 草稿由本区块自己持有，与已保存的 [savedConfig] 比较得出「有未保存的更改」；
 * 保存与测试都用当前草稿，方便「先测通再保存」。测试结果只在输入没被改过时才展示，
 * 否则会出现「明明改了 Key，界面还写着连接正常」的误导。
 *
 * @param savedConfig 已保存的配置，界面据此回填草稿。
 * @param testState 最近一次「测试连接」的状态。
 * @param onSave 保存草稿。
 * @param onTest 用给定配置测试连接。
 */
@Composable
fun LlmConfigSection(
    savedConfig: LLMConfig,
    testState: LlmTestState,
    onSave: (LLMConfig) -> Unit,
    onTest: (LLMConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var apiKey by remember { mutableStateOf(savedConfig.apiKey) }
    var baseUrl by remember { mutableStateOf(savedConfig.baseUrl) }
    var modelName by remember { mutableStateOf(savedConfig.modelName) }
    var apiKeyVisible by remember { mutableStateOf(false) }

    LaunchedEffect(savedConfig) {
        apiKey = savedConfig.apiKey
        baseUrl = savedConfig.baseUrl
        modelName = savedConfig.modelName
    }

    // 统一去掉首尾空白：粘贴地址和 Key 时很容易带上换行或空格
    val draft = LLMConfig(apiKey.trim(), baseUrl.trim(), modelName.trim())
    val isDirty = draft != savedConfig
    val isTesting = testState.isTesting && testState.testedConfig == draft
    val result = testState.result?.takeIf { testState.testedConfig == draft }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        LlmHeader(isConfigured = savedConfig.isComplete())

        Spacer(Modifier.height(20.dp))

        ConfigField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = "API Key",
            supportingText = "保存在本机偏好里；配置了云同步时会随偏好一起同步到云端存储",
            isSecret = true,
            secretVisible = apiKeyVisible,
            onToggleSecretVisibility = { apiKeyVisible = !apiKeyVisible }
        )

        Spacer(Modifier.height(12.dp))

        ConfigField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = "Base URL",
            supportingText = "兼容 OpenAI 接口的地址；只填到服务根路径也行，会自动补 /chat/completions",
            placeholder = "https://open.bigmodel.cn/api/paas/v4"
        )

        Spacer(Modifier.height(12.dp))

        ConfigField(
            value = modelName,
            onValueChange = { modelName = it },
            label = "Model Name",
            supportingText = "服务商文档里的模型名，例如 glm-4.7-flash",
            placeholder = "glm-4.7-flash"
        )

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { onTest(draft) },
                enabled = !isTesting
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("测试中…")
                } else {
                    Text("测试连接")
                }
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = "用当前输入试一次，不会自动保存",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        result?.let { tested ->
            Spacer(Modifier.height(12.dp))
            TestResultNote(result = tested)
        }

        if (isDirty) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "有未保存的更改",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                TextButton(
                    onClick = {
                        apiKey = savedConfig.apiKey
                        baseUrl = savedConfig.baseUrl
                        modelName = savedConfig.modelName
                    }
                ) {
                    Text("撤销")
                }

                Spacer(Modifier.width(8.dp))

                Button(onClick = { onSave(draft) }) {
                    Text("保存")
                }
            }
        }
    }
}

/** 三项都填了才算配置完成，与 [LLMConfig] 的语义一致。 */
private fun LLMConfig.isComplete(): Boolean =
    apiKey.isNotBlank() && baseUrl.isNotBlank() && modelName.isNotBlank()

/** 区块头部：图标 + 配置状态徽标 + 这个 LLM 到底用来干什么。 */
@Composable
private fun LlmHeader(isConfigured: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column {
            Text(
                text = if (isConfigured) "已配置" else "未配置",
                style = MaterialTheme.typography.labelMedium,
                color = if (isConfigured) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isConfigured) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "用 LLM 从视频标题里提取歌名与歌手，再据此匹配歌曲 ID、歌词与封面。" +
                    "不配置也能用，只是这些歌会保留原视频标题。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 一个配置项：单行输入框 + 说明。
 *
 * @param isSecret 为 true 时默认打码，并给出显示/隐藏切换。
 */
@Composable
private fun ConfigField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String,
    modifier: Modifier = Modifier,
    isSecret: Boolean = false,
    secretVisible: Boolean = false,
    onToggleSecretVisibility: (() -> Unit)? = null,
    placeholder: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { text ->
            { Text(text, style = MaterialTheme.typography.bodySmall) }
        },
        singleLine = true,
        visualTransformation = if (isSecret && !secretVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        supportingText = {
            Text(text = supportingText, style = MaterialTheme.typography.bodySmall)
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSecret && onToggleSecretVisibility != null) {
                    IconButton(
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Default),
                        onClick = onToggleSecretVisibility
                    ) {
                        Icon(
                            imageVector = if (secretVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (secretVisible) "隐藏 API Key" else "显示 API Key"
                        )
                    }
                }

                // 清空只在有内容时出现，空框上挂个点了没反应的按钮很别扭
                if (value.isNotEmpty()) {
                    IconButton(
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Default),
                        onClick = { onValueChange("") }
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "清空 $label")
                    }
                }
            }
        }
    )
}

/** 「测试连接」的结果提示：成功与各种失败用不同底色，正文给出下一步怎么办。 */
@Composable
private fun TestResultNote(result: LlmConnectionResult) {
    val isSuccess = result is LlmConnectionResult.Success
    val accent = if (isSuccess) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSuccess) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                }
            )
            .padding(12.dp)
    ) {
        Icon(
            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp)
        )

        Spacer(Modifier.width(8.dp))

        Column {
            Text(
                text = llmResultTitle(result),
                style = MaterialTheme.typography.labelLarge,
                color = accent
            )

            llmResultDetail(result)?.let { detail ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 结果标题：一句话说清是成功还是哪一类失败。 */
private fun llmResultTitle(result: LlmConnectionResult): String = when (result) {
    is LlmConnectionResult.Success -> "连接正常"
    LlmConnectionResult.Incomplete -> "三项都填好才能测试"
    LlmConnectionResult.Unauthorized -> "API Key 无效"
    LlmConnectionResult.NotFound -> "找不到接口地址"
    LlmConnectionResult.RateLimited -> "请求过于频繁"
    is LlmConnectionResult.HttpError -> "接口返回 HTTP ${result.code}"
    is LlmConnectionResult.NetworkError -> "网络连接失败"
    is LlmConnectionResult.BadResponse -> "响应格式不符合 OpenAI 接口"
}

/** 结果详情：给出可操作的下一步，而不是只说失败了。 */
private fun llmResultDetail(result: LlmConnectionResult): String? = when (result) {
    is LlmConnectionResult.Success -> "模型已响应：${result.reply.take(60)}"
    LlmConnectionResult.Incomplete -> null
    LlmConnectionResult.Unauthorized -> "检查 Key 是否正确，以及它是否开通了所填模型（401 / 403）"
    LlmConnectionResult.NotFound -> "检查 Base URL（404）；只填到服务根路径也行，会自动补 /chat/completions"
    LlmConnectionResult.RateLimited -> "服务商限流了，稍后重试（429）"
    is LlmConnectionResult.HttpError -> result.body.ifBlank { "服务商没有返回错误详情" }
    is LlmConnectionResult.NetworkError -> result.message
    is LlmConnectionResult.BadResponse -> "${result.message}；确认 Base URL 指向的是兼容 OpenAI 的聊天接口"
}
