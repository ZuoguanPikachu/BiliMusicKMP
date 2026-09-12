package com.zuoguan.bilimusickmp.services

import androidx.compose.runtime.mutableStateListOf
import com.zuoguan.bilimusickmp.models.Page

/** 页面返回栈：当前页面由栈顶决定，列表本身可直接被 Compose 观察。 */
class NavigationService {
    private val _pageStack = mutableStateListOf(Page.PLAYLIST)
    val pageStack: List<Page> get() = _pageStack

    val currentPage: Page
        get() = _pageStack.lastOrNull() ?: Page.PLAYLIST

    val canGoBack: Boolean
        get() = _pageStack.size > 1

    /** 前进到新页面；目标与栈顶相同时不入栈，避免重复页面堆积。 */
    fun navigate(page: Page) {
        if (_pageStack.lastOrNull() == page) return
        _pageStack += page
    }

    /** 回退一层；栈里只剩初始页面时不做任何事。 */
    fun back() {
        if (_pageStack.size > 1) {
            _pageStack.removeAt(_pageStack.lastIndex)
        }
    }

    /** 清空栈并以指定页面重新开始，供底部导航切换顶层页面使用。 */
    fun reset(page: Page) {
        _pageStack.clear()
        _pageStack += page
    }
}