package com.zuoguan.bilimusickmp.utils

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class SimpleCookieJar : CookieJar {

    private val cookieStore: MutableMap<String, MutableList<Cookie>> = mutableMapOf()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val list = cookieStore.getOrPut(host) { mutableListOf() }
        cookies.forEach { newCookie ->
            list.removeAll { it.name == newCookie.name }
            list.add(newCookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val now = System.currentTimeMillis()
        val cookies = cookieStore[host]?.filter { it.expiresAt > now } ?: emptyList()
        return cookies
    }

    fun set(name: String, value: String, domain: String, path: String = "/") {
        val cookie = Cookie.Builder()
            .name(name)
            .value(value)
            .domain(domain)
            .path(path)
            .build()
        val list = cookieStore.getOrPut(domain) { mutableListOf() }
        list.removeAll { it.name == name }
        list.add(cookie)
    }
}
