package com.zuoguan.bilimusickmp.services

import androidx.compose.runtime.mutableStateListOf
import com.zuoguan.bilimusickmp.models.Page

class NavigationService {
    private val _pageStack = mutableStateListOf(Page.PLAYLIST)
    val pageStack: List<Page> get() = _pageStack

    val currentPage: Page
        get() = _pageStack.last()

    fun navigate(page: Page) {
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