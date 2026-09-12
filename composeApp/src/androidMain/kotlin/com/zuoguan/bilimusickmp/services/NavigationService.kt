package com.zuoguan.bilimusickmp.services

import androidx.compose.runtime.mutableStateListOf
import com.zuoguan.bilimusickmp.models.Page

class NavigationService {
    private val _pageStack = mutableStateListOf(Page.PLAYLIST)
    val pageStack: List<Page> get() = _pageStack

    val currentPage: Page
        get() = _pageStack.lastOrNull() ?: Page.PLAYLIST

    val canGoBack: Boolean
        get() = _pageStack.size > 1

    fun navigate(page: Page) {
        if (_pageStack.lastOrNull() == page) return
        _pageStack += page
    }

    fun back() {
        if (_pageStack.size > 1) {
            _pageStack.removeAt(_pageStack.lastIndex)
        }
    }

    fun reset(page: Page) {
        _pageStack.clear()
        _pageStack += page
    }
}