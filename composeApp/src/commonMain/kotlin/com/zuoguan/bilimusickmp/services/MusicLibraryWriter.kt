package com.zuoguan.bilimusickmp.services

/**
 * 把下载好的音频写进系统的「音乐」目录。
 *
 * 两端落地方式差别很大，因此抽成接口由平台实现：
 * - Android 10 起受分区存储限制，只能通过 MediaStore 往公共音乐库写（文件会出现在
 *   系统音乐 App 里）；更老的系统直接写公共 Music 目录再触发一次媒体扫描。
 * - 桌面端就是一个普通文件夹（Windows 上是「音乐」已知文件夹）。
 */
interface MusicLibraryWriter {

    /**
     * 查找音乐库里是否已有同名文件。
     *
     * 下载前先问一次，避免重复点击时把整首歌重新拉一遍却只是覆盖同名文件。
     *
     * @return 已存在时返回它的位置描述（与 [save] 的返回值同款），否则返回 null。
     */
    suspend fun find(fileName: String): String?

    /**
     * 保存一个音频文件；同名文件已存在时视为成功，不重复写入。
     *
     * @param fileName 展示用文件名，如 `人来人往 - 陈奕迅.m4a`。
     * @param mimeType 音频 MIME 类型，Android 侧用它给音乐库归类。
     * @param bytes 完整的音频字节。
     * @return 保存位置的可读描述（路径或「音乐库」），用于给用户提示。
     * @throws java.io.IOException 目标目录不可写或写入失败时抛出。
     */
    suspend fun save(fileName: String, mimeType: String, bytes: ByteArray): String
}
