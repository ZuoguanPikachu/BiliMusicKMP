package com.zuoguan.bilimusickmp.utils

fun convertImageUrl(url: String, width: Int = -1, height: Int = -1): String {
    var convertedUrl = url

    if (url.contains("hdslb.com") && width > 0 && height > 0){
        convertedUrl = "$convertedUrl@${width}w_${height}h_1c_!web-home-common-cover"
    }

    if (url.contains("music.126") && width > 0 && height > 0){
        convertedUrl = "$convertedUrl?param=${width}y${height}"
    }

    return convertedUrl
}