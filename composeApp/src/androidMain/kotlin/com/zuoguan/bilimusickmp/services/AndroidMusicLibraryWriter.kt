package com.zuoguan.bilimusickmp.services

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Android 侧的「下载到音乐目录」。
 *
 * 分区存储（Android 10 / API 29）之后应用不能再直接往公共 Music 目录写文件，只能通过
 * [MediaStore] 插入：先以 `IS_PENDING=1` 占位、写完再置 0，中途失败则删掉这条记录，
 * 这样系统音乐 App 不会扫到半截文件。更老的系统仍是普通文件权限，直接写公共目录后
 * 触发一次媒体扫描，让新文件立刻出现在音乐库里。
 *
 * 两条路径都以「音乐」目录为落点，文件名用「歌曲 - 歌手」。
 */
class AndroidMusicLibraryWriter(
    private val context: Context
) : MusicLibraryWriter {

    override suspend fun find(fileName: String): String? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (mediaStoreFileExists(fileName)) "音乐库/$fileName" else null
        } else {
            val file = File(legacyMusicDirectory(), fileName)
            if (file.exists()) file.absolutePath else null
        }
    }

    override suspend fun save(fileName: String, mimeType: String, bytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(fileName, mimeType, bytes)
            } else {
                saveToLegacyPublicDir(fileName, bytes)
            }
        }

    /**
     * 音乐库里是否已有同名文件。
     *
     * 没读权限时分区存储只会返回本应用写入的条目，查询失败也按「不存在」处理——
     * 大不了多写一次（[save] 里还会再判一次）。
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaStoreFileExists(fileName: String): Boolean = runCatching {
        context.contentResolver.query(
            mediaCollection(),
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.DISPLAY_NAME} = ?",
            arrayOf(fileName),
            null
        )?.use { it.moveToFirst() } ?: false
    }.getOrDefault(false)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaCollection() =
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveViaMediaStore(fileName: String, mimeType: String, bytes: ByteArray): String {
        val resolver = context.contentResolver
        val collection = mediaCollection()

        // 已存在同名文件就直接算成功：连点按钮不该在音乐库里堆出 "x (1).mp3"
        if (mediaStoreFileExists(fileName)) return "音乐库/$fileName"

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, values)
            ?: throw IOException("无法在系统音乐目录中创建文件")

        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IOException("无法写入系统音乐目录")

            // 写完再解除 pending，让音乐库正式收录
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }

        return "音乐库/$fileName"
    }

    /** Android 9 及以下的公共音乐目录。 */
    private fun legacyMusicDirectory(): File {
        @Suppress("DEPRECATION")
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
    }

    /** Android 9 及以下：直接写公共 Music 目录，再触发媒体扫描。 */
    private fun saveToLegacyPublicDir(fileName: String, bytes: ByteArray): String {
        val dir = legacyMusicDirectory()
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw IOException("无法创建系统音乐目录：${dir.absolutePath}")
        }

        val file = File(dir, fileName)
        if (!file.exists()) {
            file.writeBytes(bytes)
        }
        // 让媒体库立刻收录，否则系统音乐 App 看不到这个文件
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
        return file.absolutePath
    }
}
