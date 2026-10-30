package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.AudioSource
import com.zuoguan.bilimusickmp.utils.createDirectories
import com.zuoguan.bilimusickmp.utils.deleteFile
import com.zuoguan.bilimusickmp.utils.fileLastModified
import com.zuoguan.bilimusickmp.utils.fileSize
import com.zuoguan.bilimusickmp.utils.listFileNames
import com.zuoguan.bilimusickmp.utils.localMediaLocation
import com.zuoguan.bilimusickmp.utils.writeBinaryFileAtomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 播放时的歌曲缓存。
 *
 * 播放一首歌时，如果本地没有它的副本，起播仍然用远端直链（不等待下载），同时在后台落一份到
 * 缓存目录；之后再播这首歌就直接用本地文件，断网也能听。缓存目录有容量上限（[cacheLimitBytes]），
 * 超出后按最后修改时间淘汰最旧的文件，设置页可以一键清空。
 *
 * 它和「下载到音乐目录」([SongDownloadService]) 是两件事：这里的文件只给应用自己用，
 * 用户看不到也不必管；下载出去的文件则交给系统音乐库，删歌时也不会被删掉。
 *
 * 两个开关（是否开启、是否仅 Wi-Fi）的偏好键以 `local.` 开头，按 [PreferencesStorageService]
 * 的约定不参与云同步：缓存开关取决于每台设备的存储与流量情况，跟着账号同步过去只会互相覆盖。
 */
class SongCacheService(
    private val preferences: PreferencesStorageService,
    private val cacheDirectory: String,
    /** 当前是否在 Wi-Fi（桌面端恒为 true，见 DI）。 */
    private val isWifiConnected: suspend () -> Boolean = { true },
    private val cacheLimitBytes: Long = DEFAULT_CACHE_LIMIT_BYTES
) {

    companion object {
        /** 是否启用播放时自动缓存；仅本地，不参与云同步。 */
        const val ENABLED_KEY = "local.songCache.enabled"

        /** 是否仅在 Wi-Fi 下自动缓存；仅本地，不参与云同步。默认开启，避免误耗流量。 */
        const val WIFI_ONLY_KEY = "local.songCache.wifiOnly"

        /** 缓存目录的容量上限：512 MB，超出后按最后修改时间淘汰最旧的文件。 */
        const val DEFAULT_CACHE_LIMIT_BYTES: Long = 512L * 1024 * 1024

        /** 自动缓存最多同时跑几个任务，避免连续切歌时堆起一长串下载。 */
        private const val MAX_CONCURRENT_TRANSFERS = 2

        /** 原子写入时的临时文件后缀，扫描目录时必须排除。 */
        private const val TEMP_SUFFIX = ".part"

        private const val HEX = "0123456789abcdef"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 串行化「判断是否有任务在跑 → 占位」，避免同一首歌被并发缓存两遍。 */
    private val transferGuard = Mutex()

    /** 正在缓存的歌曲 id；只在 [transferGuard] 内读写。 */
    private val inFlight = mutableSetOf<String>()

    private val _enabled = MutableStateFlow(false)
    /** 是否启用播放时自动缓存。 */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _wifiOnly = MutableStateFlow(true)
    /** 是否仅在 Wi-Fi 下自动缓存。 */
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val _cachingIds = MutableStateFlow<Set<String>>(emptySet())
    /** 正在后台缓存的歌曲 id，设置页据此显示「N 首进行中」。 */
    val cachingIds: StateFlow<Set<String>> = _cachingIds.asStateFlow()

    private val _cacheSizeBytes = MutableStateFlow(0L)
    /** 缓存目录当前占用字节数。 */
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    init {
        scope.launch {
            preferences.observeBoolean(ENABLED_KEY).collect { _enabled.value = it }
        }
        scope.launch {
            // 未写过时默认 true：宁可少缓存，也别在移动网络下悄悄吃掉流量
            preferences.observeString(WIFI_ONLY_KEY)
                .map { it?.toBooleanStrictOrNull() ?: true }
                .collect { _wifiOnly.value = it }
        }
        scope.launch { refreshUsage() }
    }

    // ---------- 设置 ----------

    /** 开关「播放时自动缓存」。 */
    suspend fun setEnabled(value: Boolean) {
        preferences.putBoolean(ENABLED_KEY, value)
    }

    /** 开关「仅在 Wi-Fi 下自动缓存」。 */
    suspend fun setWifiOnly(value: Boolean) {
        preferences.putString(WIFI_ONLY_KEY, value.toString())
    }

    // ---------- 播放 ----------

    /**
     * 解析一首歌实际要播放的地址：本地有缓存就用本地文件，否则取远端地址。
     *
     * 返回远端地址时会顺带在后台缓存一份（[autoCache] 为 true、缓存已开启且网络允许时），
     * 但**不等待下载完成**，起播速度不受影响。
     *
     * @param songId 歌曲 id，作为缓存文件名。
     * @param audioSource 音源，决定缓存时带哪些请求头。
     * @param autoCache 是否允许顺带缓存；搜索页试听传 false，避免缓存歌单里没有的曲目。
     * @param remoteUrl 取远端地址，仅在本地没有文件时调用。
     */
    suspend fun resolvePlaybackUrl(
        songId: String,
        audioSource: AudioSource,
        autoCache: Boolean,
        remoteUrl: suspend () -> String
    ): String {
        localPath(songId)?.let { return localMediaLocation(it) }

        val url = remoteUrl()
        if (autoCache) scheduleAutoCache(songId, audioSource, url)
        return url
    }

    /**
     * 后台缓存一首歌。触发条件不满足时直接跳过，不抛异常。
     *
     * 直链可能带时效（B 站 / QQ 音乐的地址都会过期），因此用播放时刚取到的地址下载，
     * 而不是事后再取一次。
     */
    private fun scheduleAutoCache(songId: String, audioSource: AudioSource, url: String) {
        if (!_enabled.value) return
        if (songId in _cachingIds.value) return
        // 连点切歌会不断触发缓存：同时跑的任务太多既抢带宽又占内存，超过上限就先放弃这一首
        // （下次播放还会再试）。
        if (_cachingIds.value.size >= MAX_CONCURRENT_TRANSFERS) return

        scope.launch {
            // Wi-Fi 判定放到协程里：联网状态查询本身要跨进程，不该挡住起播
            if (_wifiOnly.value && !runCatching { isWifiConnected() }.getOrDefault(false)) {
                return@launch
            }
            runCatching { store(songId, audioSource, url) }
        }
    }

    // ---------- 维护 ----------

    /** 这首歌的本地缓存文件路径；没有缓存返回 null。 */
    suspend fun localPath(songId: String): String? = findFile(songId)

    /** 清空缓存目录。返回释放的字节数。 */
    suspend fun clearCache(): Long {
        val freed = withContext(Dispatchers.IO) {
            var total = 0L
            for (name in listFileNames(cacheDirectory)) {
                val path = joinPath(cacheDirectory, name)
                val size = fileSize(path)
                if (deleteFile(path)) total += size
            }
            total
        }
        refreshUsage()
        return freed
    }

    /** 删歌时清掉它的缓存，避免残留占空间。 */
    suspend fun purge(songId: String) {
        deleteFilesMatching(songId)
        refreshUsage()
    }

    /** 重新扫描缓存目录，刷新占用大小。 */
    suspend fun refreshUsage() = withContext(Dispatchers.IO) {
        _cacheSizeBytes.value = listFileNames(cacheDirectory).sumOf {
            fileSize(joinPath(cacheDirectory, it))
        }
    }

    // ---------- 内部实现 ----------

    /** 取远端音频并写入缓存目录，随后按容量淘汰。 */
    private suspend fun store(songId: String, audioSource: AudioSource, url: String) {
        if (!beginTransfer(songId)) return

        try {
            val bytes = AudioFetcher.fetch(url, audioSource)
            val target = joinPath(cacheDirectory, "${fileStem(songId)}.${AudioFetcher.extensionOf(url)}")

            withContext(Dispatchers.IO) {
                createDirectories(cacheDirectory)
                writeBinaryFileAtomic(target, bytes)
            }

            pruneCache()
            refreshUsage()
        } finally {
            endTransfer(songId)
        }
    }

    /** 占位：返回 false 表示这首歌已经在缓存了，调用方应直接返回。 */
    private suspend fun beginTransfer(songId: String): Boolean = transferGuard.withLock {
        if (!inFlight.add(songId)) return@withLock false
        _cachingIds.update { it + songId }
        true
    }

    private suspend fun endTransfer(songId: String) = transferGuard.withLock {
        inFlight.remove(songId)
        _cachingIds.update { it - songId }
    }

    /** 缓存目录超过上限时，按最后修改时间从旧到新删除，直到回到上限以内。 */
    private suspend fun pruneCache() = withContext(Dispatchers.IO) {
        val names = listFileNames(cacheDirectory)
        var total = names.sumOf { fileSize(joinPath(cacheDirectory, it)) }
        if (total <= cacheLimitBytes) return@withContext

        names.sortedBy { fileLastModified(joinPath(cacheDirectory, it)) }
            .forEach { name ->
                if (total <= cacheLimitBytes) return@forEach
                val path = joinPath(cacheDirectory, name)
                val size = fileSize(path)
                if (deleteFile(path)) total -= size
            }
    }

    /** 在缓存目录里找这首歌的音频文件；`.part` 临时文件不算。 */
    private suspend fun findFile(songId: String): String? = withContext(Dispatchers.IO) {
        val stem = fileStem(songId)
        listFileNames(cacheDirectory)
            .firstOrNull { it.startsWith("$stem.") && !it.endsWith(TEMP_SUFFIX) }
            ?.let { joinPath(cacheDirectory, it) }
    }

    /** 删除缓存目录里属于这首歌的所有文件。 */
    private suspend fun deleteFilesMatching(songId: String) = withContext(Dispatchers.IO) {
        val stem = fileStem(songId)
        for (name in listFileNames(cacheDirectory)) {
            if (name.startsWith("$stem.")) {
                deleteFile(joinPath(cacheDirectory, name))
            }
        }
    }

    private fun joinPath(dir: String, name: String): String =
        if (dir.endsWith("/") || dir.endsWith("\\")) "$dir$name" else "$dir/$name"

    /**
     * 歌曲 id → 缓存文件名主干（十六进制）。
     *
     * 不直接拿 id 当文件名：搜索结果的 id 来自各家接口，混进 `/`、`:`、`?` 或大小写冲突
     * 都会让文件写到别处或互相覆盖。编码后既保证合法，也不会和「歌曲 - 歌手」这种
     * 人类可读的下载文件名撞车。
     */
    private fun fileStem(songId: String): String {
        val builder = StringBuilder(songId.length * 2)
        for (byte in songId.encodeToByteArray()) {
            val value = byte.toInt() and 0xFF
            builder.append(HEX[value shr 4]).append(HEX[value and 0x0F])
        }
        return builder.toString()
    }
}
