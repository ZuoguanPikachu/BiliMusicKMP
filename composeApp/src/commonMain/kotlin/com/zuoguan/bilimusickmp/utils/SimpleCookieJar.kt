package com.zuoguan.bilimusickmp.utils

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.CopyOnWriteArrayList

class SimpleCookieJar : CookieJar {

    private val cookieStore = CopyOnWriteArrayList<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (newCookie in cookies) {
            cookieStore.removeAll { it.name == newCookie.name && it.domain == newCookie.domain }
            cookieStore.add(newCookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookieStore.removeAll { it.expiresAt < now }
        return cookieStore.filter { it.matches(url) }
    }

    fun set(name: String, value: String, domain: String, path: String = "/") {
        cookieStore.removeAll { it.name == name && it.domain == domain }
        cookieStore.add(
            Cookie.Builder()
                .name(name)
                .value(value)
                .domain(domain)
                .path(path)
                .build()
        )
    }
}
