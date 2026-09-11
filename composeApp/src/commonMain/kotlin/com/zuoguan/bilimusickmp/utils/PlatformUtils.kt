package com.zuoguan.bilimusickmp.utils

/** 读取文本文件，文件不存在返回 null。 */
internal expect fun readTextFile(path: String): String?

/** 原子写入文本文件（先写临时文件再替换）。 */
internal expect fun writeTextFileAtomic(path: String, content: String)

/** 当前毫秒时间戳。 */
internal expect fun currentTimeMillis(): Long