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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.rememberWindowState
import bilimusickmp.composeapp.generated.resources.Res
import bilimusickmp.composeapp.generated.resources.bili_music
import com.zuoguan.bilimusickmp.di.appModule
import com.zuoguan.bilimusickmp.models.Page
import com.zuoguan.bilimusickmp.ui.*
import com.zuoguan.bilimusickmp.utils.getAppConfigDir
import kotbase.CouchbaseLite
import org.jetbrains.compose.resources.painterResource
import org.koin.core.context.startKoin
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import java.io.File


fun main() {
    NativeDiscovery().discover()
    val cfgDir = getAppConfigDir()

    CouchbaseLite.init(debug = false,
        rootDir = cfgDir,
        scratchDir = File(cfgDir, "CouchbaseLiteTemp"
        ).also { it.mkdirs() }
    )

    startKoin {
        modules(appModule)
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "BiliMusic",
            state = rememberWindowState(width = 1400.dp, height = 900.dp),
            resizable = false,
            icon = painterResource(Res.drawable.bili_music)
        ) {
            MaterialTheme { App() }
        }
    }
}

@Composable
fun App() {
    val snackBarHostState = remember { SnackbarHostState() }
    var currentPage by remember { mutableStateOf(Page.PLAYLIST) }

    CompositionLocalProvider(LocalSnackBarHostState provides snackBarHostState) {
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
        }
    }
}
