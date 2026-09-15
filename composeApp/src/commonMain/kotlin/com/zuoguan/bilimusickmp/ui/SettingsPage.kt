package com.zuoguan.bilimusickmp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.zuoguan.bilimusickmp.LocalSnackBarHostState
import com.zuoguan.bilimusickmp.models.DarkMode
import com.zuoguan.bilimusickmp.models.ProjectInfo
import com.zuoguan.bilimusickmp.services.ReleaseInfo
import com.zuoguan.bilimusickmp.services.UpdateCheckResult
import com.zuoguan.bilimusickmp.ui.theme.themeColorOptions
import com.zuoguan.bilimusickmp.ui.theme.themeSwatchAccentColor
import com.zuoguan.bilimusickmp.ui.theme.themeSwatchContainerColor
import com.zuoguan.bilimusickmp.vm.SettingsPageViewModel
import com.zuoguan.bilimusickmp.vm.ThemeViewModel
import com.zuoguan.bilimusickmp.vm.UpdateCheckState
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 设置页：选择主题与外观，编辑 LLM 配置，配置云同步脚本，并在「关于」区块查看版本、
 * 项目链接与手动检查新版本。
 *
 * 主题区的改动（明暗模式、主题色）点击即预览：全局立即变化但不落盘，确认后才保存并同步；
 * LLM 配置与云同步脚本都用本地草稿保存未保存的编辑内容，只有点保存才写回存储，
 * 已保存的内容发生变化时再回填草稿。
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

    var script by remember { mutableStateOf(state.script) }

    LaunchedEffect(state.script) {
        script = state.script
    }

    // 脚本保存结果等一次性事件走 Snackbar；宿主由 App 根组件提供
    SnackbarEvents(viewModel.uiEvents)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
    ) {
        SettingsSection(title = "主题") {
            Column(modifier = Modifier.padding(16.dp)) {
                ThemeGroupLabel("外观")
                Spacer(Modifier.height(8.dp))

                DarkModeChips(
                    selected = themeState.appliedDarkMode,
                    onSelect = { themeViewModel.preview(it) }
                )

                Spacer(Modifier.height(16.dp))

                ThemeGroupLabel("主题颜色")
                Spacer(Modifier.height(8.dp))

                ThemeColorPicker(
                    selected = themeState.appliedColor,
                    onSelect = { themeViewModel.preview(it) }
                )

                // 外观与颜色共用一份预览，任一项改动后都是同一个「保存」生效
                if (themeState.hasPendingPreview) {
                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { themeViewModel.cancelPreview() }) {
                            Text("取消")
                        }
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = { themeViewModel.confirmPreview() }) {
                            Text("保存")
                        }
                    }
                }
            }
        }

        SettingsSection(title = "LLM 配置") {
            LlmConfigSection(
                savedConfig = state.llmConfig,
                testState = state.llmTest,
                onSave = { viewModel.saveConfig(it) },
                onTest = { viewModel.testLlmConnection(it) }
            )
        }

        SettingsSection(title = "云同步脚本") {
            CloudSyncScriptSection(
                script = script,
                savedScript = state.script,
                status = state.syncStatus,
                scriptError = state.scriptError,
                onScriptChange = { script = it },
                onSave = { viewModel.saveScript(script) },
                onSyncNow = { viewModel.syncNow() }
            )
        }

        SettingsSection(title = "关于") {
            AboutSection(
                state = state.updateCheck,
                onCheck = { viewModel.checkForUpdates() }
            )
        }
    }
}

/**
 * 「关于」区块：应用标识与版本、项目简介、技术栈，以及源码 / 反馈 / 更新日志入口。
 *
 * 外链一律交给系统默认浏览器打开，打不开则用 Snackbar 兜底提示地址；
 * 检查更新由用户点击触发（不自动轮询），结果内联展示在「检查更新」这一行下方。
 */
@Composable
private fun AboutSection(
    state: UpdateCheckState,
    onCheck: () -> Unit
) {
    val openUrl = rememberUriOpener()

    // 纵向留白给整块，横向留白交给各小节：分隔线因此可以通到卡片边缘
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        AboutHeader(
            version = state.currentVersion,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = ProjectInfo.DESCRIPTION,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(12.dp))

        TechStackBadges(modifier = Modifier.padding(horizontal = 16.dp))

        Spacer(Modifier.height(16.dp))

        AboutLinkRow(
            icon = Icons.Default.Code,
            title = "源代码",
            subtitle = ProjectInfo.REPO_SLUG,
            onClick = { openUrl(ProjectInfo.SOURCE_URL) }
        )
        AboutLinkRow(
            icon = Icons.Default.BugReport,
            title = "问题反馈",
            subtitle = "提交 Issue 或功能建议",
            onClick = { openUrl(ProjectInfo.ISSUES_URL) }
        )
        AboutLinkRow(
            icon = Icons.Default.History,
            title = "更新日志",
            subtitle = "查看历史版本与更新说明",
            onClick = { openUrl(ProjectInfo.RELEASES_URL) }
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))

        UpdateCheckPanel(
            state = state,
            onCheck = onCheck,
            onDownload = { openUrl(it) }
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))

        Text(
            text = ProjectInfo.COPYRIGHT,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

/** 应用标识：图标、名称与版本徽标。 */
@Composable
private fun AboutHeader(
    version: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Column {
            Text(
                text = ProjectInfo.NAME,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            VersionBadge(version = version)
        }
    }
}

/** 版本徽标：小圆角标签，避免版本号与应用名抢视觉重心。 */
@Composable
private fun VersionBadge(version: String) {
    Text(
        text = "v$version",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/** 技术栈标签：圆角小胶囊，弱化展示。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TechStackBadges(modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ProjectInfo.TECH_STACK.forEach { tech ->
            Text(
                text = tech,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

/** 「关于」里的一行外链：图标 + 标题/副标题 + 打开提示，整行可点。 */
@Composable
private fun AboutLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.width(8.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * 检查更新面板：左侧是说明与状态，右侧是触发按钮；发现新版本时在下方展开下载卡片。
 */
@Composable
private fun UpdateCheckPanel(
    state: UpdateCheckState,
    onCheck: () -> Unit,
    onDownload: (String) -> Unit
) {
    val result = state.result

    val statusText = when (result) {
        null -> if (state.isChecking) "正在获取最新版本信息…" else "从 GitHub 获取最新 Release"
        UpdateCheckResult.UpToDate -> "已是最新版本"
        is UpdateCheckResult.UpdateAvailable -> "发现新版本 v${result.release.version}"
        is UpdateCheckResult.Failed -> result.message
    }
    val statusColor = when (result) {
        null -> MaterialTheme.colorScheme.onSurfaceVariant
        UpdateCheckResult.UpToDate, is UpdateCheckResult.UpdateAvailable -> MaterialTheme.colorScheme.primary
        is UpdateCheckResult.Failed -> MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.SystemUpdate,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = "检查更新", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor
            )
        }

        Spacer(Modifier.width(12.dp))

        OutlinedButton(onClick = onCheck, enabled = !state.isChecking) {
            Text(if (state.isChecking) "检查中…" else "检查")
        }
    }

    if (result is UpdateCheckResult.UpdateAvailable) {
        Spacer(Modifier.height(12.dp))
        UpdateAvailableCard(
            release = result.release,
            onDownload = { onDownload(result.release.pageUrl) }
        )
    }
}

/** 新版本提示卡片：版本号、更新说明与下载入口。 */
@Composable
private fun UpdateAvailableCard(
    release: ReleaseInfo,
    onDownload: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.NewReleases,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "新版本 v${release.version} 可用",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        release.notes?.let { notes ->
            Spacer(Modifier.height(8.dp))
            ReleaseNotes(notes)
        }

        Spacer(Modifier.height(12.dp))

        Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
            Text("前往下载")
        }
    }
}

/**
 * 更新说明区：GitHub Release 的 `body` 是 Markdown（作者可能顺手写进 HTML 片段），
 * 当纯文本渲染会把 `##`、`-`、`**`、链接地址原样露出来，所以交给 Markdown 渲染器排版。
 *
 * 两点必须覆盖库的默认值：
 * - `modifier` 默认是 `fillMaxSize`，在卡片这种外层 Column 里会把高度全占掉；
 * - 标题默认用 displayLarge/displayMedium 一档，放在设置页卡片里大得离谱，
 *   这里统一压到 bodySmall / titleSmall，链接色换成主题的 primary（默认是加粗黑字）。
 */
@Composable
private fun ReleaseNotes(markdown: String) {
    val body = MaterialTheme.typography.bodySmall
    val heading = MaterialTheme.typography.titleSmall
    val code = body.copy(fontFamily = FontFamily.Monospace)

    Markdown(
        content = markdown,
        modifier = Modifier.fillMaxWidth(),
        colors = markdownColor(text = MaterialTheme.colorScheme.onSurfaceVariant),
        typography = markdownTypography(
            h1 = heading,
            h2 = heading,
            h3 = heading,
            h4 = heading,
            h5 = heading,
            h6 = heading,
            text = body,
            paragraph = body,
            list = body,
            ordered = body,
            bullet = body,
            quote = body,
            table = body,
            code = code,
            inlineCode = code,
            textLink = TextLinkStyles(
                style = SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                )
            ),
        ),
    )
}

/**
 * 统一的链接打开方式：交给系统默认浏览器；失败时用 Snackbar 兜底提示地址，
 * 避免出现「点了没反应」。
 */
@Composable
private fun rememberUriOpener(): (String) -> Unit {
    val uriHandler: UriHandler = LocalUriHandler.current
    val snackbarHostState = LocalSnackBarHostState.current
    val scope = rememberCoroutineScope()

    return remember(uriHandler, snackbarHostState) {
        { url: String ->
            runCatching { uriHandler.openUri(url) }
                .onFailure {
                    scope.launch {
                        snackbarHostState.showSnackbar("无法打开浏览器，请手动访问：$url")
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

/** 主题分区里的小标题，用于区分「外观」与「主题颜色」两组设置。 */
@Composable
private fun ThemeGroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 明暗模式选择：跟随系统 / 浅色 / 深色。
 *
 * 只改预览状态，确认后才保存（与主题色共用同一个「保存」）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DarkModeChips(
    selected: DarkMode,
    onSelect: (DarkMode) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DarkMode.entries.forEach { mode ->
            AssistChip(
                onClick = { onSelect(mode) },
                label = { Text(mode.label) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (mode == selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                    labelColor = if (mode == selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

/**
 * 主题色选择器：把候选种子色平铺成色块，点击即预览。
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