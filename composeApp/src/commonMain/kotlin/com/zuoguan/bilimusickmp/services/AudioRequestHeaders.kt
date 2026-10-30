package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.AudioSource

/**
 * 各音源取音频流时需要的请求头。
 *
 * 四家音源都有防盗链或 UA 校验：B 站查 Referer、网易云查 Host、酷狗只认自己的 App UA、
 * QQ 音乐同样校验站点。播放器与缓存下载必须带**同一套**头，否则会出现「能播不能下」
 * 或「下完播不了」的诡异组合，因此集中在这里维护，不再各写一份。
 */
object AudioRequestHeaders {

    /** 桌面 Chrome 的 UA：B 站、网易云、QQ 音乐都接受它。 */
    const val DESKTOP_USER_AGENT: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"

    /** 酷狗的直链接口只对自家 App 的 UA 放行。 */
    const val KU_GOU_USER_AGENT: String = "IPhone-8990-searchSong"

    /**
     * 取指定音源、指定地址的请求头。
     *
     * @param source 音源，决定 Referer / Origin / UA 的取值。
     * @param url 音频地址；网易云需要其中的 host 作为 `Host` 头。
     */
    fun forSource(source: AudioSource, url: String): Map<String, String> {
        val headers = mutableMapOf(
            "User-Agent" to DESKTOP_USER_AGENT,
            "Accept" to "*/*",
        )

        when (source) {
            AudioSource.BILI_BILI -> {
                headers["Referer"] = "https://www.bilibili.com/"
                headers["Origin"] = "https://www.bilibili.com"
            }

            AudioSource.NET_EASE -> {
                hostOf(url)?.let { headers["Host"] = it }
                headers["Referer"] = "https://music.163.com/"
                headers["Origin"] = "https://music.163.com"
            }

            AudioSource.KU_GOU -> {
                headers["User-Agent"] = KU_GOU_USER_AGENT
                headers["UNI-UserAgent"] = "iOS11.4-Phone8990-1009-0-WiFi"
            }

            AudioSource.QQ_MUSIC -> {
                // QQ 音乐的直链不校验请求头，带上站点 Referer 只是为了和网页端一致
                headers["Referer"] = "https://y.qq.com/"
                headers["Origin"] = "https://y.qq.com"
            }
        }

        return headers
    }

    /** 从 URL 里取 host；解析不出来时返回 null（此时不额外加 Host 头）。 */
    private fun hostOf(url: String): String? {
        val host = url.substringAfter("://", "")
            .substringBefore('/')
            .substringBefore('?')
            .substringAfter('@')
            .substringBefore(':')
        return host.takeIf { it.isNotEmpty() && !it.contains(' ') }
    }
}
