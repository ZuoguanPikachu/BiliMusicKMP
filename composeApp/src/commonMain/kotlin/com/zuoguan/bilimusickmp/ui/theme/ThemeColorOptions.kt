package com.zuoguan.bilimusickmp.ui.theme

import androidx.compose.ui.graphics.Color
import com.materialkolor.hct.Hct
import com.materialkolor.ktx.toColor
import com.materialkolor.ktx.toHct
import com.zuoguan.bilimusickmp.models.DefaultSeedColor

/** 候选项（默认色之外）的数量。 */
private const val OptionCount = 11

/** 候选项的色相步长（度），环绕整个色轮。 */
private const val HueStep = 30.0

/** 候选项的起始色相，避开品牌粉所在的色相区间。 */
private const val FirstHue = 30.0

/** 候选项的色度，取 M3 主色的量级，保证不同色相浓淡一致。 */
private const val OptionChroma = 40.0

/** 候选项的色调。 */
private const val OptionTone = 45.0

/** 色块底色所用的浅色调，对应浅色主题下的容器色。 */
private const val ContainerTone = 80.0

/**
 * 设置页可选的主题色：默认品牌色 + 一圈等间距色相的候选项。
 *
 * 候选项只改色相生成（色度与色调固定），因此每一项都能生成一整套浓淡一致的 M3 色调板。
 */
val themeColorOptions: List<Color> = buildList {
    add(DefaultSeedColor)
    repeat(OptionCount) { index ->
        add(Hct.from(FirstHue + index * HueStep, OptionChroma, OptionTone).toColor())
    }
}

/** 色块底色：同色相的浅色调，预览该主题下的容器色。 */
fun themeSwatchContainerColor(seedColor: Color): Color =
    Hct.from(seedColor.toHct().hue, OptionChroma, ContainerTone).toColor()

/** 色块选中标记的颜色：同色相的主色色调。 */
fun themeSwatchAccentColor(seedColor: Color): Color =
    Hct.from(seedColor.toHct().hue, OptionChroma, OptionTone).toColor()
