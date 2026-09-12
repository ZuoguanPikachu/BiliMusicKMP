package com.zuoguan.bilimusickmp.utils

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 内存 Cookie 存储：写入时按「名称 + 域名」去重，读取时顺带清理已过期项。
 *
 * 底层使用 CopyOnWriteArrayList，允许网络线程与 UI 线程并发读写。
 */
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

    /** 以指定域名与路径写入一条 Cookie，同名同域旧值会被覆盖。 */
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
