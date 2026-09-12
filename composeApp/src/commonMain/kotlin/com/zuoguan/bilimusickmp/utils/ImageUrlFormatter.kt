package com.zuoguan.bilimusickmp.utils

fun convertImageUrl(url: String, width: Int = -1, height: Int = -1): String {
    if (url.isBlank() || width <= 0 || height <= 0) return url

    return when {
        url.contains("hdslb.com") -> {
            val base = url.substringBefore('@')
            "$base@${width}w_${height}h_1c_!web-home-common-cover"
        }

        url.contains("music.126") -> {
            val base = url.substringBefore("?param=")
            val separator = if (base.contains('?')) '&' else '?'
            "$base${separator}param=${width}y${height}"
        }

        url.contains("{size}") -> url.replace("{size}", width.toString())

        else -> url
    }
}
