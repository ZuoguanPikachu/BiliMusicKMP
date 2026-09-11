package com.zuoguan.bilimusickmp.utils

import java.io.File

internal actual fun readTextFile(path: String): String? {
    val file = File(path)
    return if (file.exists()) file.readText(Charsets.UTF_8) else null
}

internal actual fun writeTextFileAtomic(path: String, content: String) {
    val file = File(path)
    val tmp = File(file.parentFile, file.name + ".tmp")
    tmp.writeText(content, Charsets.UTF_8)
    if (file.exists()) file.delete()
    tmp.renameTo(file)
}

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()