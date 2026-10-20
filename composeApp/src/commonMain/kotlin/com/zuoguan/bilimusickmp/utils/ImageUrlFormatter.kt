package com.zuoguan.bilimusickmp.utils

/**
 * QQ 音乐封面只接受这几个预留档位（服务端预生成），其余尺寸一律 404。
 *
 * 实测 `T002R200x200`、`400`、`600`、`1000` 都是 404，所以不能把任意尺寸直接拼进 URL，
 * 必须贴到第一个不小于目标的档位，否则图就变成破图。
 */
private val QQ_MUSIC_COVER_SIZES = listOf(68, 90, 120, 150, 180, 300, 500, 800, 1200, 1500)

/**
 * 只匹配 `T002R300x300` 这种尺寸片段，避免误伤 albumMid / 歌手 mid 里的字符。
 *
 * 第一个捕获组是 `T002R` 部分，替换时原样保留，只改后面的 `宽x高`。
 */
private val QQ_MUSIC_SIZE_SEGMENT = Regex("""(T\d{3}R)\d+x\d+""")

/**
 * 按图床规则为图片 URL 拼接缩略图尺寸参数。
 *
 * - hdslb.com 图床：截掉原有后缀，重新拼成 宽w_高h 形式；
 * - music.126 图床：拼成 param=宽y高，并按现有 query 决定分隔符；
 * - QQ 音乐（y.gtimg.cn / y.qq.com）图床：改 `T002R{宽}x{高}` 里的档位，且只支持正方形；
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

        // QQ 音乐的封面是正方形档位，宽高只能取较大者；超出最大档位就停在 1500
        url.contains("gtimg.cn") || url.contains("y.qq.com/music/photo") -> {
            val target = maxOf(width, height)
            val size = QQ_MUSIC_COVER_SIZES.firstOrNull { it >= target } ?: QQ_MUSIC_COVER_SIZES.last()
            QQ_MUSIC_SIZE_SEGMENT.replace(url) { match ->
                "${match.groupValues[1]}${size}x${size}"
            }
        }

        url.contains("{size}") -> url.replace("{size}", width.toString())

        else -> url
    }
}
