package com.zuoguan.bilimusickmp.models

interface MetadataSource {
    val label: String

    val isNone: Boolean get() = false
}
