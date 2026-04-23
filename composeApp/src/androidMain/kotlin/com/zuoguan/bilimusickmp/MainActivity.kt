package com.zuoguan.bilimusickmp

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.zuoguan.bilimusickmp.di.appModule
import com.zuoguan.bilimusickmp.services.MusicPlaybackService
import com.zuoguan.bilimusickmp.ui.LyricsPage
import com.zuoguan.bilimusickmp.ui.PlayBar
import com.zuoguan.bilimusickmp.ui.PlaylistPage
import com.zuoguan.bilimusickmp.ui.SearchPage
import com.zuoguan.bilimusickmp.ui.SettingsPage
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin


class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        startKoin {
            androidContext(this@MainActivity)
            modules(appModule)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }

        val sessionToken = SessionToken(this, ComponentName(this, MusicPlaybackService::class.java))
        MediaController.Builder(this, sessionToken).buildAsync()

        setContent {
            App()
        }
    }
}


@Composable
fun App() {
    val snackBarHostState = remember { SnackbarHostState() }
    var currentPage by rememberSaveable { mutableStateOf(Page.PLAYLIST) }
    var previousPage by rememberSaveable { mutableStateOf<Page?>(null) }

    CompositionLocalProvider(LocalSnackBarHostState provides snackBarHostState) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackBarHostState) },
            bottomBar = {
                AnimatedVisibility(
                    visible = currentPage != Page.LYRICS,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                    ),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                    )
                ) {
                    BottomNavigationBar(
                        selected = currentPage,
                        onSelect = { currentPage = it }
                    )
                }

            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = currentPage != Page.LYRICS,
                    enter = fadeIn() + expandIn(),
                    exit = shrinkOut() + fadeOut()
                ) {
                    PlayBar({
                        previousPage = currentPage
                        currentPage = Page.LYRICS
                    })
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
                    onBackFromLyrics = {
                        currentPage = previousPage ?: Page.PLAYLIST
                        previousPage = null
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

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

@Composable
fun ContentArea(
    page: Page,
    onBackFromLyrics: () -> Unit,
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
                Page.LYRICS   to 3
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
                Page.SETTINGS -> SettingsPage()
                Page.LYRICS   -> LyricsPage(onBack = onBackFromLyrics)
            }
        }
    }
}
