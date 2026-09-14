package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.zuoguan.bilimusickmp.services.SyncPhase
import com.zuoguan.bilimusickmp.services.SyncUiState
import com.zuoguan.bilimusickmp.utils.isMobileUi

/**
 * 云同步脚本区块：同步状态与手动同步、脚本编辑、语法错误提示与脚本 API 说明。
 *
 * 编辑方式按平台分流：移动端（[isMobileUi]）只展示脚本预览，点「编辑脚本」进全屏编辑器，
 * 避免窄屏里嵌套滚动和输入法遮挡；桌面端直接在卡片内联编辑。
 *
 * 区块内的 `script` 是未保存的草稿，`savedScript` 是已落盘的内容，两者不等即「有未保存的更改」，
 * 保存按钮也只在这时可用。
 *
 * @param script 当前草稿内容。
 * @param savedScript 已保存的内容。
 * @param status 云同步状态。
 * @param scriptError 脚本求值失败的原因；null 表示脚本可用。
 * @param onScriptChange 草稿变更回调。
 * @param onSave 保存草稿。
 * @param onSyncNow 立即同步一次。
 */
@Composable
fun CloudSyncScriptSection(
    script: String,
    savedScript: String,
    status: SyncUiState,
    scriptError: String?,
    onScriptChange: (String) -> Unit,
    onSave: () -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDirty = script != savedScript
    val canInsertTemplate = script.isBlank()
    var showHelp by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(vertical = 16.dp)) {
        SyncHeader(
            status = status,
            onSyncNow = onSyncNow,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(16.dp))

        if (isMobileUi) {
            ScriptPreview(
                script = script,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { editing = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (canInsertTemplate) "编写脚本" else "编辑脚本")
            }
        } else {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                ScriptToolbar(
                    script = script,
                    canInsertTemplate = canInsertTemplate,
                    showHelp = showHelp,
                    onInsertTemplate = { onScriptChange(SCRIPT_TEMPLATE) },
                    onToggleHelp = { showHelp = !showHelp }
                )

                Spacer(Modifier.height(8.dp))

                ScriptEditor(
                    value = script,
                    onValueChange = onScriptChange,
                    modifier = Modifier.fillMaxWidth(),
                    fieldHeight = 260.dp
                )
            }
        }

        if (isDirty) {
            Spacer(Modifier.height(12.dp))
            ScriptSaveRow(
                onSave = onSave,
                onRevert = { onScriptChange(savedScript) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        scriptError?.let { message ->
            Spacer(Modifier.height(12.dp))
            ScriptErrorNote(
                message = message,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (isMobileUi) {
            Spacer(Modifier.height(8.dp))
            HelpToggleRow(
                expanded = showHelp,
                onToggle = { showHelp = !showHelp }
            )
        }

        if (showHelp) {
            Spacer(Modifier.height(8.dp))
            ScriptApiHelp(modifier = Modifier.padding(horizontal = 16.dp))
        }

        if (editing) {
            FullScreenScriptEditor(
                draft = script,
                savedScript = savedScript,
                error = scriptError,
                onDraftChange = onScriptChange,
                onInsertTemplate = { onScriptChange(SCRIPT_TEMPLATE) },
                onDismiss = { editing = false },
                onSave = {
                    onSave()
                    editing = false
                }
            )
        }
    }
}

/** 状态头部：云同步图标、状态指示与「立即同步」；区块标题由外层的分区标题提供，这里不再重复。 */
@Composable
private fun SyncHeader(
    status: SyncUiState,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val phaseColor = syncPhaseColor(status.phase)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (status.phase == SyncPhase.SYNCING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = phaseColor,
                    strokeWidth = 1.5.dp
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(phaseColor)
                )
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = status.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(12.dp))

        OutlinedButton(
            onClick = onSyncNow,
            enabled = status.phase != SyncPhase.SYNCING
        ) {
            Text("立即同步")
        }
    }
}

/** 同步状态指示灯颜色：成功偏强调色，失败与未配置用错误色。 */
@Composable
private fun syncPhaseColor(phase: SyncPhase): Color = when (phase) {
    SyncPhase.IDLE -> MaterialTheme.colorScheme.outline
    SyncPhase.SYNCING -> MaterialTheme.colorScheme.primary
    SyncPhase.SUCCESS -> MaterialTheme.colorScheme.tertiary
    SyncPhase.ERROR, SyncPhase.NO_SCRIPT -> MaterialTheme.colorScheme.error
}

/**
 * 移动端的脚本预览：只读、最多 6 行，且与编辑区一样不折行（长行左右滑动看）。
 *
 * 改内容仍然要走全屏编辑器。
 *
 * 这里必须用 [TextOverflow.Clip] 而不是 Ellipsis：Compose 在「softWrap = false + Ellipsis」时
 * 会把 maxLines 强制改成 1（foundation 的 `finalMaxLines`，因为原生文本布局不支持逐行省略号），
 * 预览就只剩一行了。省略号在这里本来也没意义——宽度是无限约束，压根没有可以放省略号的位置，
 * 行数靠上面的「N 行」提示告诉用户。
 */
@Composable
private fun ScriptPreview(
    script: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (script.isNotBlank()) {
            Text(
                text = scriptSizeLabel(script),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(ScriptEditorShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = script.ifBlank { "还没有脚本，点下面的按钮开始编写。" },
                style = if (script.isBlank()) {
                    MaterialTheme.typography.bodySmall
                } else {
                    MaterialTheme.typography.bodySmall.asScriptStyle()
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = false,
                maxLines = 6,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(
                    horizontal = ScriptEditorHPadding,
                    vertical = ScriptEditorVPadding
                )
            )
        }
    }
}

/** 编辑区工具条：左侧是行数/字符数，右侧是「模板」与「说明」。 */
@Composable
private fun ScriptToolbar(
    script: String,
    canInsertTemplate: Boolean,
    showHelp: Boolean,
    onInsertTemplate: () -> Unit,
    onToggleHelp: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = scriptSizeLabel(script),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (canInsertTemplate) {
            ScriptToolButton(
                icon = Icons.Default.AutoAwesome,
                label = "模板",
                onClick = onInsertTemplate
            )
        }
        ScriptToolButton(
            icon = Icons.AutoMirrored.Filled.HelpOutline,
            label = if (showHelp) "收起" else "说明",
            onClick = onToggleHelp
        )
    }
}

/** 工具条上的一个小按钮：图标 + 文字。 */
@Composable
private fun ScriptToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * 脚本编辑区：不折行 + 横向滚动，一行永远是一行。
 *
 * 输入框没有「不折行」开关，做法是先量出最长行的自然宽度 [rememberScriptTextWidth]，
 * 把输入框撑到那么宽再放进横向滚动容器；光标跑到长行末尾时，输入框内部的
 * `BringIntoViewRequester` 会把光标位置滚进可视区，不需要额外处理。
 *
 * 圆角、底色与描边都做在外层容器上（输入框本身透明），这样左右滑动时圆角收边不会跟着跑。
 *
 * @param fieldHeight 编辑区高度；为 null 时高度由 [modifier] 决定（全屏编辑器里用 weight 撑满）。
 */
@Composable
private fun ScriptEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fieldHeight: Dp? = null
) {
    val scriptStyle = LocalTextStyle.current.asScriptStyle()
    val hScrollState = rememberScrollState()
    val vScrollState = rememberScrollState()
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val textWidth = rememberScriptTextWidth(value, scriptStyle)

    BoxWithConstraints(
        modifier = modifier.then(
            if (fieldHeight != null) Modifier.height(fieldHeight) else Modifier
        )
    ) {
        val scrollable = textWidth + ScriptEditorHPadding * 2 > maxWidth
        val fieldWidth = maxOf(maxWidth, textWidth + ScriptEditorHPadding * 2)

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(ScriptEditorShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(
                        width = 1.dp,
                        color = if (isFocused) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = ScriptEditorShape
                    )
                    .horizontalScroll(hScrollState)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .width(fieldWidth)
                        .fillMaxHeight(),
                    textStyle = scriptStyle,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    interactionSource = interactionSource,
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(vScrollState)
                                .padding(
                                    horizontal = ScriptEditorHPadding,
                                    vertical = ScriptEditorVPadding
                                )
                        ) {
                            if (value.isEmpty()) {
                                Text(
                                    text = "在这里编写云同步脚本…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            // 提示只给桌面端：手机上左右滑动是本能，不需要教
            if (scrollable && !isMobileUi) {
                ScriptScrollHint()
            }
        }
    }
}

/**
 * 脚本正文样式：等宽字体，行高稍松一点便于阅读。
 *
 * 这里不处理折行：编辑区与预览都按「不折行 + 横向滚动」展示，见 [ScriptEditor]。
 */
private fun TextStyle.asScriptStyle(): TextStyle = copy(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 20.sp
)

/** 编辑区/预览的圆角。 */
private val ScriptEditorShape = RoundedCornerShape(12.dp)

/** 编辑区内部文字四周的留白；横向留白参与「最长行宽度」的计算。 */
private val ScriptEditorHPadding = 16.dp
private val ScriptEditorVPadding = 12.dp

/**
 * 量出脚本在「不折行」时需要的宽度：最长一行的宽度，外加一个等宽字符的余量。
 *
 * 那个余量是防呆用的——测量与输入框内部排版之间哪怕差一点点取整，最长行也不至于折出第二行。
 * 宽度用与正文完全相同的 [style] 测量，中英混排、emoji 的宽度差异交给文本测量自己处理，
 * 不按字符数估算。
 */
@Composable
private fun rememberScriptTextWidth(script: String, style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    return remember(script, style, measurer, density) {
        if (script.isEmpty()) {
            0.dp
        } else {
            with(density) {
                val longestLine = measurer.measure(
                    text = AnnotatedString(script),
                    style = style,
                    softWrap = false
                ).size.width
                val guard = measurer.measure(
                    text = AnnotatedString("0"),
                    style = style
                ).size.width

                (longestLine + guard).toDp()
            }
        }
    }
}

/** 长行提示（仅桌面端）：光说「可以左右查看」没用，得告诉用户具体怎么操作。 */
@Composable
private fun ScriptScrollHint(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(top = 6.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.SwapHoriz,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "长行可左右查看：Shift + 滚轮，或触摸板左右滑动",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 保存行：仅在草稿与已保存内容不一致时出现。 */
@Composable
private fun ScriptSaveRow(
    onSave: () -> Unit,
    onRevert: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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

        TextButton(onClick = onRevert) {
            Text("撤销")
        }

        Spacer(Modifier.width(8.dp))

        Button(onClick = onSave) {
            Text("保存脚本")
        }
    }
}

/** 脚本求值失败的提示，内容来自引擎的报错首行。 */
@Composable
private fun ScriptErrorNote(
    message: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp)
        )

        Spacer(Modifier.width(8.dp))

        Column {
            Text(
                text = "脚本加载失败",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 移动端的「脚本说明」折叠开关。 */
@Composable
private fun HelpToggleRow(
    expanded: Boolean,
    onToggle: () -> Unit
) {
    TextButton(
        onClick = onToggle,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(text = "脚本说明", style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * 脚本说明：必须实现的函数、容易被忽略的约定、引擎注入的宿主函数。
 *
 * 约定那几条都来自应用实际怎么调用脚本（见 JsEngineService / CloudSyncService），
 * 尤其是「key 是路径不是文件名」——自建简易服务器的用户最容易在这里踩坑。
 */
@Composable
private fun ScriptApiHelp(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .heightIn(max = 280.dp)
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        HelpSectionTitle("必须实现")

        HelpEntry(
            name = "upload(key, bytes)",
            description = "上传；可直接 return http.put(...) 的结果"
        )
        HelpEntry(
            name = "download(key)",
            description = "下载；云端没有这个对象时返回 status 404"
        )
        HelpEntry(
            name = "两者都要返回",
            description = "{ status: 数字, body: 字节, headers: 对象 }；" +
                "返回 undefined 会让同步直接报错"
        )

        Spacer(Modifier.height(12.dp))

        HelpSectionTitle("约定")

        HelpEntry(
            name = "key 是路径，不是文件名",
            description = "应用用的是 sync/v2/head.json 这类带目录的 key。" +
                "自建服务器请把 key 当不透明字符串按原样保存；只认 ?filename=xxx 或会丢掉目录的实现，" +
                "轻则上传失败，重则不同对象互相覆盖。"
        )
        HelpEntry(
            name = "同一个 key 会被反复覆盖写",
            description = "head.json 与快照都是覆盖写，服务器必须允许覆盖，不能返回 409 或做不可变存储。"
        )
        HelpEntry(
            name = "下载的 404 有特殊含义",
            description = "404 表示「云端还没有这份数据」，是正常情况；" +
                "返回 200 + 空 body 会被当成解析失败。其它非 200 一律算同步失败。"
        )
        HelpEntry(
            name = "上传失败不会当场报错",
            description = "应用目前不检查上传返回码。签名错误这类失败会先表现为其它设备报" +
                "「增量 N 不存在」，排错时可以先在脚本里 console.log 出返回码。"
        )

        Spacer(Modifier.height(12.dp))

        HelpSectionTitle("可用宿主函数")

        HOST_APIS.forEach { (name, description) ->
            HelpEntry(name = name, description = description)
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "保存脚本会重建 JS 引擎并立即触发一次同步；语法错误会显示在编辑区下方。" +
                "完整的协议说明与腾讯云 COS 参考脚本见 README。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 说明里的小标题。 */
@Composable
private fun HelpSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(6.dp))
}

/** 说明里的一行：等宽的函数签名（或要点）+ 中文解释。 */
@Composable
private fun HelpEntry(name: String, description: String) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium.asScriptStyle(),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 移动端的全屏脚本编辑器。
 *
 * 关闭（含返回键）不会丢弃草稿：草稿仍留在页面上，由「有未保存的更改」这一行提示，
 * 避免误触返回键把写了一半的脚本弄丢。
 */
@Composable
private fun FullScreenScriptEditor(
    draft: String,
    savedScript: String,
    error: String?,
    onDraftChange: (String) -> Unit,
    onInsertTemplate: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val isDirty = draft != savedScript
    var showHelp by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = fullScreenDialogProperties()
    ) {
        // Surface 铺满整屏（连状态栏区域一起上色），内容再按 safeDrawing 让开系统栏与软键盘：
        // safeDrawing 已经包含输入法 inset，键盘弹起时编辑区会被顶上去而不是被盖住。
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭编辑器")
                    }

                    Text(
                        text = "编辑云同步脚本",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )

                    TextButton(onClick = onSave, enabled = isDirty) {
                        Text("保存")
                    }
                }

                HorizontalDivider()

                ScriptEditor(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp)
                )

                if (error != null) {
                    ScriptErrorNote(
                        message = error,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (showHelp) {
                    ScriptApiHelp(modifier = Modifier.padding(horizontal = 12.dp))
                    Spacer(Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = scriptSizeLabel(draft),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (draft.isBlank()) {
                        ScriptToolButton(
                            icon = Icons.Default.AutoAwesome,
                            label = "模板",
                            onClick = onInsertTemplate
                        )
                    }
                    ScriptToolButton(
                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                        label = if (showHelp) "收起" else "说明",
                        onClick = { showHelp = !showHelp }
                    )
                }
            }
        }
    }
}

/** 「12 行 · 340 字符」，空脚本时给一句提示。 */
private fun scriptSizeLabel(script: String): String {
    if (script.isBlank()) return "还没有脚本"
    return "${script.lines().size} 行 · ${script.length} 字符"
}

/**
 * 新手模板：只给骨架与可用 API 的提示，不假设具体的对象存储实现。
 *
 * 仅在编辑区为空时可插入，避免一键覆盖已经写好的脚本。
 */
private val SCRIPT_TEMPLATE = """
    // 云同步脚本：实现下面两个函数即可
    // upload(key, bytes)  上传，返回 { status, body, headers }
    // download(key)       下载，返回 { status, body, headers }
    // 可用的宿主函数见「脚本说明」，http.get/post/put 的返回值可直接作为上面的返回结构。

    function upload(key, bytes) {
        // TODO: 把 bytes 上传到 key
    }

    function download(key) {
        // TODO: 按 key 下载
    }
""".trimIndent()

/** 引擎注入的宿主函数清单，与 [com.zuoguan.bilimusickmp.services.JsEngineService] 中的定义一一对应。 */
private val HOST_APIS = listOf(
    "console.log(...)" to "打印日志（桌面端输出到控制台）",
    "http.get(url, { headers })" to "GET 请求，返回 { status, body, headers }",
    "http.post(url, body, { contentType, headers })" to "POST 请求，返回 { status, body, headers }",
    "http.put(url, body, { contentType, headers })" to "PUT 请求，返回 { status, body, headers }",
    "crypto.sha1 / sha256 / md5(text)" to "摘要，返回十六进制小写字符串",
    "crypto.hmacSha1 / hmacSha256(key, text)" to "HMAC，返回十六进制小写字符串",
    "time.now()" to "当前 Unix 时间戳（秒）",
    "url.encode(text) / url.decode(text)" to "URL 编解码",
    "str.encode(text)" to "字符串转字节数组",
    "file.readBytes(path)" to "读取本地文件"
)
