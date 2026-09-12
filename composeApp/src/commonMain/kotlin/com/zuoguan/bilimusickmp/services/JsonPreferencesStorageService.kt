package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.SyncKeys
import com.zuoguan.bilimusickmp.models.SyncPrefs
import com.zuoguan.bilimusickmp.utils.currentTimeMillis
import com.zuoguan.bilimusickmp.utils.readTextFile
import com.zuoguan.bilimusickmp.utils.writeTextFileAtomic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class PrefsFileContent(
    val values: Map<String, String> = emptyMap(),
    val syncUpdatedAt: Long = 0L
)

/** 偏好存储共用的 JSON 编解码器：容忍未知字段以兼容其他版本的文件，并写出默认值。 */
internal val syncJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * 基于单个 JSON 文件的偏好存储（双平台共用，原子写 + 互斥锁）。
 *
 * 所有值以字符串形式落盘；[syncUpdatedAt] 记录可同步键最近一次本地变更的版本，
 * 键名以 "local." 开头的一律不参与同步。
 */
class JsonPreferencesStorageService(
    private val filePath: String
) : PreferencesStorageService {

    private val json = syncJson

    private val mutex = Mutex()

    private val _map = MutableStateFlow<Map<String, String>>(emptyMap())

    @Volatile
    private var syncUpdatedAt = 0L

    init {
        load()
    }

    private fun load() {
        val raw = readTextFile(filePath) ?: // 文件不存在或不可读：保持空状态，交给下一次写入重建
        return
        try {
            val parsed = json.decodeFromString<PrefsFileContent>(raw)
            _map.value = parsed.values
            syncUpdatedAt = parsed.syncUpdatedAt
        } catch (e: Exception) {
            // 解析失败时保留原始文件（下次写入前不会覆盖），并打印以便排查
            println("偏好文件解析失败，已忽略: ${e.message}")
        }
    }

    private suspend fun persist() {
        writeTextFileAtomic(filePath, json.encodeToString(PrefsFileContent(_map.value, syncUpdatedAt)))
    }

    /** 只有同步白名单内的键（且不带 local. 前缀）才推进同步版本号。 */
    private fun isSyncable(key: String) =
        key in SyncKeys.PREF_KEYS && !key.startsWith(SyncKeys.LOCAL_KEY_PREFIX)

    private suspend fun setValue(key: String, value: String?) = mutex.withLock {
        val old = _map.value[key]
        if (old == value) return@withLock // 无变化：不写盘、不推进同步版本
        val newValue = _map.value.toMutableMap().apply {
            if (value == null) remove(key) else put(key, value)
        }
        _map.value = newValue
        if (isSyncable(key)) {
            syncUpdatedAt = maxOf(currentTimeMillis(), syncUpdatedAt + 1)
        }
        persist()
    }

    // ---------- 字符串 ----------

    override fun observeString(key: String): Flow<String?> = _map.map { it[key] }

    override suspend fun getString(key: String): String? = _map.value[key]

    override suspend fun putString(key: String, value: String?) {
        setValue(key, value)
    }

    // ---------- 数字 ----------

    override fun observeLong(key: String): Flow<Long> =
        _map.map { it[key]?.toLongOrNull() ?: 0L }

    override suspend fun getLong(key: String, default: Long): Long =
        _map.value[key]?.toLongOrNull() ?: default

    override suspend fun putLong(key: String, value: Long) {
        setValue(key, value.toString())
    }

    // ---------- 布尔 ----------

    override fun observeBoolean(key: String): Flow<Boolean> =
        _map.map { it[key]?.toBooleanStrictOrNull() ?: false }

    override suspend fun getBoolean(key: String, default: Boolean): Boolean =
        _map.value[key]?.toBooleanStrictOrNull() ?: default

    override suspend fun putBoolean(key: String, value: Boolean) {
        setValue(key, value.toString())
    }

    // ---------- 云同步支持 ----------

    override suspend fun syncUpdatedAt(): Long = syncUpdatedAt

    override suspend fun syncablePayload(): SyncPrefs = mutex.withLock {
        SyncPrefs(
            updatedAt = syncUpdatedAt,
            values = _map.value.filterKeys { isSyncable(it) }
        )
    }

    override suspend fun applySyncable(prefs: SyncPrefs): Long = mutex.withLock {
        if (prefs.updatedAt < syncUpdatedAt) return@withLock Long.MIN_VALUE
        val newMap = _map.value.toMutableMap()
        for ((k, v) in prefs.values) {
            if (isSyncable(k)) {
                newMap[k] = v
            }
        }
        _map.value = newMap
        syncUpdatedAt = prefs.updatedAt
        persist()
        prefs.updatedAt
    }
}