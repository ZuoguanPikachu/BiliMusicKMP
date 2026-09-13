package com.zuoguan.bilimusickmp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zuoguan.bilimusickmp.models.Page
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.zuoguan.bilimusickmp.services.NavigationService
import com.zuoguan.bilimusickmp.ui.theme.BiliMusicTheme
import com.zuoguan.bilimusickmp.vm.PlaylistPageViewModel
import com.zuoguan.bilimusickmp.vm.SearchPageViewModel
import com.zuoguan.bilimusickmp.vm.SongEditorViewModel
import com.zuoguan.bilimusickmp.vm.ThemeViewModel
import com.zuoguan.bilimusickmp.ui.LyricsPage
import com.zuoguan.bilimusickmp.ui.PlayBar
import com.zuoguan.bilimusickmp.ui.PlaylistPage
import com.zuoguan.bilimusickmp.ui.SearchPage
import com.zuoguan.bilimusickmp.ui.SettingsPage
import com.zuoguan.bilimusickmp.ui.SnackbarEvents
import com.zuoguan.bilimusickmp.ui.SongEditPage
import org.koin.compose.koinInject

/**
 * 唯一 Activity，承载整个 Compose 界面。
 *
 * Android 13 起播放通知需要 POST_NOTIFICATIONS 运行时权限，因此在创建界面之前先申请。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }

        setContent {
            App()
        }
    }
}


/** 应用根组件：装配 Scaffold、底部导航与悬浮播放条；歌词页和编辑页会隐藏这两者。 */
@Composable
fun App(
    navigationService: NavigationService = koinInject(),
    playlistViewModel: PlaylistPageViewModel = koinInject(),
    searchViewModel: SearchPageViewModel = koinInject(),
    songEditorViewModel: SongEditorViewModel = koinInject(),
    themeViewModel: ThemeViewModel = koinInject()
) {
    val snackBarHostState = remember { SnackbarHostState() }
    val currentPage = navigationService.currentPage
    val themeState by themeViewModel.uiState.collectAsState()

    BiliMusicTheme(themeState.appliedColor) {
        CompositionLocalProvider(LocalSnackBarHostState provides snackBarHostState) {
            SnackbarEvents(playlistViewModel.uiEvents)
            SnackbarEvents(searchViewModel.uiEvents)
            SnackbarEvents(songEditorViewModel.uiEvents)

            Scaffold(
                snackbarHost = { SnackbarHost(snackBarHostState) },
                bottomBar = {
                    AnimatedVisibility(
                        visible = currentPage != Page.LYRICS && currentPage != Page.SONG_EDIT,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                        ),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                        )
                    ) {
                        BottomNavigationBar(
                            selected = currentPage,
                            onSelect = { navigationService.reset(it) }
                        )
                    }

                },
                floatingActionButton = {
                    AnimatedVisibility(
                        visible = currentPage != Page.LYRICS && currentPage != Page.SONG_EDIT,
                        enter = fadeIn() + expandIn(),
                        exit = shrinkOut() + fadeOut()
                    ) {
                        PlayBar()
                    }
                },
                floatingActionButtonPosition = FabPosition.Center,
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    ContentArea(
                        page = currentPage,
                        innerPadding = innerPadding,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** 底部导航栏：在歌单 / 搜索 / 设置三个顶层页面之间切换，点击即重置返回栈。 */
@Composable
fun BottomNavigationBar(
    selected: Page,
    onSelect: (Page) -> Unit
) {
    Surface(tonalElevation = 2.dp) {
        NavigationBar(
            windowInsets = NavigationBarDefaults.windowInsets
        ) {
            NavigationBarItem(
                selected = selected == Page.PLAYLIST,
                onClick = { onSelect(Page.PLAYLIST) },
                icon = { Icon(Icons.Default.LibraryMusic, null) },
                label = { Text("歌单") }
            )

            NavigationBarItem(
                selected = selected == Page.SEARCH,
                onClick = { onSelect(Page.SEARCH) },
                icon = { Icon(Icons.Default.Search, null) },
                label = { Text("搜索") }
            )

            NavigationBarItem(
                selected = selected == Page.SETTINGS,
                onClick = { onSelect(Page.SETTINGS) },
                icon = { Icon(Icons.Default.Settings, null) },
                label = { Text("设置") }
            )
        }
    }
}

/** 页面容器：按 [Page] 的固定顺序判断前进还是后退，播放相应的横向滑动转场。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContentArea(
    page: Page,
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val initial = initialState
            val target = targetState

            val pageOrder = mapOf(
                Page.PLAYLIST to 0,
                Page.SEARCH   to 1,
                Page.SETTINGS to 2,
                Page.LYRICS   to 3,
                Page.SONG_EDIT to 4,
            )

            val initialIndex = pageOrder[initial] ?: 0
            val targetIndex  = pageOrder[target]  ?: 0

            val isForward = targetIndex > initialIndex

            if (isForward) {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(easing = LinearOutSlowInEasing)
                ) + fadeIn() togetherWith
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(easing = FastOutLinearInEasing)
                ) + fadeOut()
            } else {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(easing = LinearOutSlowInEasing)
                ) + fadeIn() togetherWith
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(easing = FastOutLinearInEasing)
                ) + fadeOut()
            }
        }
    ) { currentPage ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentPage) {
                Page.PLAYLIST -> PlaylistPage()
                Page.SEARCH   -> SearchPage()
                Page.SETTINGS -> {
                    val isImeVisible = WindowInsets.isImeVisible
                    val bottomPadding = if (isImeVisible) 0.dp else 96.dp
                    SettingsPage(
                        modifier = Modifier
                            .fillMaxSize()
                            .consumeWindowInsets(innerPadding)
                            .imePadding(),
                        contentPadding = PaddingValues(bottom = bottomPadding)
                    )
                }
                Page.LYRICS   -> LyricsPage()
                Page.SONG_EDIT -> SongEditPage()
            }
        }
    }
}
