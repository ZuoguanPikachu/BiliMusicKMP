package com.zuoguan.bilimusickmp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.rememberWindowState
import bilimusickmp.composeapp.generated.resources.Res
import bilimusickmp.composeapp.generated.resources.bili_music
import com.zuoguan.bilimusickmp.di.appModule
import com.zuoguan.bilimusickmp.models.Page
import com.zuoguan.bilimusickmp.services.AudioPlayService
import com.zuoguan.bilimusickmp.ui.*
import com.zuoguan.bilimusickmp.ui.theme.BiliMusicTheme
import com.zuoguan.bilimusickmp.ui.theme.WindowTitleBarThemeEffect
import com.zuoguan.bilimusickmp.ui.theme.isDarkTheme
import com.zuoguan.bilimusickmp.utils.getAppConfigDir
import com.zuoguan.bilimusickmp.vm.PlaylistPageViewModel
import com.zuoguan.bilimusickmp.vm.SearchPageViewModel
import com.zuoguan.bilimusickmp.vm.SongEditorViewModel
import com.zuoguan.bilimusickmp.vm.ThemeViewModel
import kotbase.CouchbaseLite
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.koin.core.Koin
import org.koin.core.context.startKoin
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import java.io.File

// 供进程退出钩子访问容器：关闭窗口或进程退出时都要释放 VLC 资源
private var koinRef: Koin? = null

/** 释放音频播放服务；容器未就绪或重复调用都不会抛异常。 */
private fun releaseAudioPlayService() {
    runCatching { koinRef?.get<AudioPlayService>()?.close() }
}

/**
 * 桌面端应用入口。
 *
 * 启动前探测 VLC 本机库、准备 Couchbase Lite 的数据与临时目录，
 * 再启动 Koin 并注册退出钩子以释放播放服务，最后打开窗口承载 Compose 界面。
 */
fun main() {
    val vlcFound = NativeDiscovery().discover()
    val cfgDir = getAppConfigDir()

    CouchbaseLite.init(debug = false,
        rootDir = cfgDir,
        scratchDir = File(cfgDir, "CouchbaseLiteTemp"
        ).also { it.mkdirs() }
    )

    koinRef = startKoin {
        modules(appModule)
    }.koin

    Runtime.getRuntime().addShutdownHook(Thread { releaseAudioPlayService() })

    application {
        Window(
            onCloseRequest = {
                releaseAudioPlayService()
                exitApplication()
            },
            title = "BiliMusic",
            state = rememberWindowState(width = 1400.dp, height = 900.dp),
            icon = painterResource(Res.drawable.bili_music)
        ) {
            val themeViewModel: ThemeViewModel = koinInject()
            val themeState by themeViewModel.uiState.collectAsState()

            // 原生标题栏不在 Compose 里，需要单独把深浅色同步过去
            WindowTitleBarThemeEffect(window, themeState.appliedDarkMode.isDarkTheme())

            BiliMusicTheme(themeState.appliedColor, themeState.appliedDarkMode) {
                if (!vlcFound) {
                    VlcMissingDialog()
                }
                App()
            }
        }
    }
}

/**
 * VLC 缺失提示对话框。
 *
 * 探测不到 VLC 本机库时给出说明与安装地址；用户确认后关闭，
 * 界面其余部分照常可用（只是无法播放音频）。
 */
@Composable
private fun VlcMissingDialog() {
    var dismissed by remember { mutableStateOf(false) }
    if (dismissed) return

    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = { Text("未找到 VLC") },
        text = {
            Text(
                "未检测到 VLC media player，无法播放音频。\n" +
                    "请安装 VLC（https://www.videolan.org/vlc/）后重启应用。"
            )
        },
        confirmButton = {
            TextButton(onClick = { dismissed = true }) { Text("知道了") }
        }
    )
}

/** 应用根界面：左侧导航栏、内容区与底部播放栏，并统一承载 Snackbar。 */
@Composable
fun App(
    playlistViewModel: PlaylistPageViewModel = koinInject(),
    searchViewModel: SearchPageViewModel = koinInject(),
    songEditorViewModel: SongEditorViewModel = koinInject(),
) {
    val snackBarHostState = remember { SnackbarHostState() }
    var currentPage by remember { mutableStateOf(Page.PLAYLIST) }

    CompositionLocalProvider(LocalSnackBarHostState provides snackBarHostState) {
        SnackbarEvents(playlistViewModel.uiEvents)
        SnackbarEvents(searchViewModel.uiEvents)
        SnackbarEvents(songEditorViewModel.uiEvents)

        Box(modifier = Modifier.fillMaxSize()) {

            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f)) {
                    AppNavigationRail(
                        selected = currentPage,
                        onSelect = { currentPage = it }
                    )
                    ContentArea(page = currentPage)
                }

                PlayBar(
                    onLyricsClick = { currentPage = Page.LYRICS }
                )
            }

            SnackbarHost(
                hostState = snackBarHostState,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )
        }
    }
}

/** 左侧导航栏，在歌单、搜索与设置页面之间切换。 */
@Composable
fun AppNavigationRail(
    selected: Page,
    onSelect: (Page) -> Unit
) {
    Surface(
        tonalElevation = 2.dp
    ) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
        ) {
            Spacer(Modifier.height(16.dp))

            NavigationRailItem(
                selected = selected == Page.PLAYLIST,
                onClick = { onSelect(Page.PLAYLIST) },
                icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "歌单") },
                label = { Text("歌单") }
            )
            NavigationRailItem(
                selected = selected == Page.SEARCH,
                onClick = { onSelect(Page.SEARCH) },
                icon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                label = { Text("搜索") }
            )
            NavigationRailItem(
                selected = selected == Page.SETTINGS,
                onClick = { onSelect(Page.SETTINGS) },
                icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                label = { Text("设置") }
            )
        }
    }
}

/** 按当前页面渲染内容；未识别的页面回退到歌单页。 */
@Composable
fun ContentArea(page: Page) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (page) {
            Page.PLAYLIST -> PlaylistPage()
            Page.SEARCH -> SearchPage()
            Page.SETTINGS -> SettingsPage()
            Page.LYRICS -> LyricsPage()
            else -> PlaylistPage()
        }
    }
}
