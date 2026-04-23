package com.zuoguan.bilimusickmp.utils

import java.io.File

fun getAppConfigDir(): File {
    val dir = File(
        System.getenv("APPDATA"),
        "BiliMusic"
    ).also { it.mkdirs() }

    return dir
}