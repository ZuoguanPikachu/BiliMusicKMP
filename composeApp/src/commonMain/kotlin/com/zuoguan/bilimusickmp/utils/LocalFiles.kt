package com.zuoguan.bilimusickmp.utils

/*
 * 歌曲缓存 / 下载用到的文件操作。
 *
 * 与 readTextFile / writeTextFileAtomic 一样以 expect/actual 形式提供：
 * 上层（com.zuoguan.bilimusickmp.services.SongCacheService）只关心「路径 + 字节」，
 * 不感知平台的文件 API。所有方法都以绝对路径为参数，路径分隔符统一用 "/"。
 */

/** 创建目录（含缺失的父目录）；已存在时返回 true。 */
internal expect fun createDirectories(path: String): Boolean

/** 列出目录下的条目名（不含路径）；目录不存在或不可读时返回空列表。 */
internal expect fun listFileNames(dirPath: String): List<String>

/** 文件字节数；不存在时返回 0。 */
internal expect fun fileSize(path: String): Long

/** 文件最后修改时间戳（毫秒）；不存在时返回 0，供缓存按 LRU 淘汰使用。 */
internal expect fun fileLastModified(path: String): Long

/** 删除文件（或空目录）；不存在或删除失败返回 false。 */
internal expect fun deleteFile(path: String): Boolean

/**
 * 原子写入二进制文件：先写同目录的临时文件再替换，避免下载中断留下半截音频。
 *
 * @throws java.io.IOException 目标目录无法创建或写入失败时抛出。
 */
internal expect fun writeBinaryFileAtomic(path: String, bytes: ByteArray)
