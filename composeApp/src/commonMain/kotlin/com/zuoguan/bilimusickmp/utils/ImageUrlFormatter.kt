package com.zuoguan.bilimusickmp.utils

/**
 * 按图床规则为图片 URL 拼接缩略图尺寸参数。
 *
 * - hdslb.com 图床：截掉原有后缀，重新拼成 宽w_高h 形式；
 * - music.126 图床：拼成 param=宽y高，并按现有 query 决定分隔符；
 * - 含 {size} 占位符的 URL：把占位符替换为宽度数值。
 *
 * @param width 目标宽度，非正数时不处理。
 * @param height 目标高度，非正数时不处理。
 * @return 处理后的 URL；URL 为空或宽高非正数时原样返回。
 */
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
