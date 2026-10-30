package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.AudioSource
import com.zuoguan.bilimusickmp.utils.audioFileName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 把单曲下载到系统的「音乐」目录。
 *
 * 与 [SongCacheService] 是两件不同的事：缓存是应用内部为了少走网络而留的副本，
 * 空间受限、会按容量淘汰；下载则是把一首歌交给用户的音乐库，文件名用「歌曲 - 歌手」，
 * 之后归系统音乐 App 管，应用不再维护它，也不会在删歌时一并删掉。
 */
class SongDownloadService(
    private val musicLibrary: MusicLibraryWriter
) {

    /** 一次只处理一个下载：连点按钮时不会往音乐目录塞进两份同名文件。 */
    private val guard = Mutex()

    /**
     * 下载一首歌并保存到音乐目录。
     *
     * @param title 歌名，参与文件名。
     * @param author 歌手，参与文件名；为空时只有歌名。
     * @param audioSource 音源，决定下载时带哪些请求头。
     * @param remoteUrl 取远端直链；它的扩展名决定文件后缀，同名文件已存在时不会再取流。
     * @return 保存位置的可读描述（路径或「音乐库」）；失败时返回异常。
     */
    suspend fun download(
        title: String,
        author: String,
        audioSource: AudioSource,
        remoteUrl: suspend () -> String
    ): Result<String> = guard.withLock {
        try {
            val url = remoteUrl()
            val extension = AudioFetcher.extensionOf(url)
            val fileName = audioFileName(title, author, extension)

            // 已经下过同一首：不再拉一遍音频，直接把已有位置报给用户
            musicLibrary.find(fileName)?.let { return@withLock Result.success(it) }

            val bytes = AudioFetcher.fetch(url, audioSource)
            Result.success(musicLibrary.save(fileName, AudioFetcher.mimeTypeOf(extension), bytes))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
