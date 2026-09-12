package com.zuoguan.bilimusickmp.models

/**
 * 元数据（歌词 / 封面）来源的统一抽象。
 *
 * 让调用方用同一套接口处理「无来源」与各平台来源，无需逐平台分支判空。
 */
interface MetadataSource {
    /** 来源的展示名，用于 UI 文案；「无来源」时通常为「无」。 */
    val label: String

    /** 是否为「无来源」；默认 false，由带 [NONE] 常量的实现覆盖。 */
    val isNone: Boolean get() = false
}
